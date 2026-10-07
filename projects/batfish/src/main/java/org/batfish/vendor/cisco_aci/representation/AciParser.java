package org.batfish.vendor.cisco_aci.representation;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.batfish.common.BfConsts;
import org.batfish.common.Warning;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.answers.ParseStatus;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;

/**
 * Parses the files under a snapshot's {@code aci_configs/} folder. Each subfolder holds one fabric;
 * files placed directly in {@code aci_configs/} form a fabric named {@value #DEFAULT_FABRIC}.
 */
public final class AciParser {

  public static final String DEFAULT_FABRIC = "fabric";

  private static final String WARNING_TAG = "Cisco ACI";

  /**
   * Parses ACI input files, given as snapshot input keys mapped to contents, into one {@link
   * AciConfiguration} per fabric, keyed by fabric name.
   */
  public static @Nonnull SortedMap<String, AciConfiguration> parseFabrics(
      Map<String, String> files, ParseVendorConfigurationAnswerElement pvcae) {
    // fabric -> (relative filename -> contents), in a stable order
    SortedMap<String, SortedMap<String, String>> filesByFabric = new TreeMap<>();
    for (Entry<String, String> file : files.entrySet()) {
      Path relative = relativeToAciConfigs(Paths.get(file.getKey()));
      String fabric = relative.getNameCount() > 1 ? relative.getName(0).toString() : DEFAULT_FABRIC;
      String filename =
          String.join(
              "/", BfConsts.RELPATH_ACI_CONFIGS_DIR, relative.toString().replace('\\', '/'));
      filesByFabric.computeIfAbsent(fabric, f -> new TreeMap<>()).put(filename, file.getValue());
    }
    SortedMap<String, AciConfiguration> fabrics = new TreeMap<>();
    filesByFabric.forEach(
        (fabric, fabricFiles) -> {
          fabrics.put(fabric, parseFabric(fabric, fabricFiles, pvcae));
        });
    return fabrics;
  }

  private static @Nonnull AciConfiguration parseFabric(
      String fabric, SortedMap<String, String> files, ParseVendorConfigurationAnswerElement pvcae) {
    AciConfiguration config = new AciConfiguration(fabric);
    config.setFilename(String.join("/", BfConsts.RELPATH_ACI_CONFIGS_DIR, fabric));
    config.setSecondaryFilenames(ImmutableList.copyOf(files.keySet()));

    // Read every file first so configuration is extracted before runtime state, whatever the
    // file order.
    List<FileRoots> parsed = new ArrayList<>();
    for (Entry<String, String> file : files.entrySet()) {
      String filename = file.getKey();
      pvcae.getFileFormats().put(filename, ConfigurationFormat.CISCO_ACI);
      try {
        JsonNode json = BatfishObjectMapper.mapper().readTree(file.getValue());
        parsed.add(new FileRoots(filename, AciMoReader.read(json)));
        pvcae.getParseStatus().put(filename, ParseStatus.PASSED);
      } catch (IOException | IllegalArgumentException e) {
        pvcae.addRedFlagWarning(
            filename,
            new Warning(
                String.format("Could not parse %s as APIC JSON: %s", filename, e.getMessage()),
                WARNING_TAG));
        pvcae.getParseStatus().put(filename, ParseStatus.FAILED);
      }
    }
    for (boolean runtime : new boolean[] {false, true}) {
      for (FileRoots file : parsed) {
        AciModelExtractor extractor =
            new AciModelExtractor(
                config,
                message -> {
                  pvcae.addUnimplementedWarning(file._filename, new Warning(message, WARNING_TAG));
                  pvcae.getParseStatus().put(file._filename, ParseStatus.PARTIALLY_UNRECOGNIZED);
                });
        for (AciMo root : file._roots) {
          if (AciModelExtractor.isRuntimeObject(root) == runtime) {
            extractor.extract(root);
          }
        }
      }
    }
    return config;
  }

  /** Returns the part of {@code path} after its {@code aci_configs} element. */
  private static Path relativeToAciConfigs(Path path) {
    for (int i = 0; i < path.getNameCount(); i++) {
      if (path.getName(i).toString().equals(BfConsts.RELPATH_ACI_CONFIGS_DIR)
          && i + 1 < path.getNameCount()) {
        return path.subpath(i + 1, path.getNameCount());
      }
    }
    return path.getFileName();
  }

  private static final class FileRoots {
    private FileRoots(String filename, List<AciMo> roots) {
      _filename = filename;
      _roots = roots;
    }

    private final String _filename;
    private final List<AciMo> _roots;
  }

  private AciParser() {}
}
