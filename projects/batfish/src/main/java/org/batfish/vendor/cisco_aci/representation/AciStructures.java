package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Range;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.references.StructureManager;

/**
 * Structure definitions and references of one fabric, with one {@link StructureManager} per input
 * file.
 *
 * <p>An object in one file often refers to an object in another (an access policy to an EPG, a
 * tenant to tenant {@code common}), so references are recorded as they are read and resolved across
 * all files by {@link #resolveReferences()}, the way APIC resolves relations.
 */
public final class AciStructures implements Serializable {

  /** Records that {@code mo} in {@code filename} defines the structure {@code dn}. */
  public void define(String filename, AciStructureType type, String dn, AciMo mo) {
    manager(filename)
        .getOrDefine(type, dn)
        .addDefinitionLines(Range.closed(mo.getLine(), mo.getLastLine()));
  }

  /** Records a reference from {@code mo} in {@code filename} to the object with DN {@code dn}. */
  public void referenceDn(
      String filename, AciStructureType type, String dn, AciStructureUsage usage, AciMo mo) {
    pending().add(new PendingReference(filename, type, usage, mo.getLine(), null, null, dn));
  }

  /**
   * Records a named relation in tenant {@code tenant}, such as {@code fvRsBd}, from {@code mo} in
   * {@code filename}.
   */
  public void referenceNamed(
      String filename,
      AciStructureType type,
      String tenant,
      NamedRef ref,
      AciStructureUsage usage,
      AciMo mo) {
    pending().add(new PendingReference(filename, type, usage, mo.getLine(), tenant, ref, null));
  }

  /**
   * Resolves the references recorded so far against the definitions in every file, counting
   * referrers of the structures found and recording the rest as undefined.
   *
   * <p>A named relation resolves to its target DN if the export has it, else to the name in the
   * source tenant, then in tenant {@code common}; an empty name means {@code default}.
   */
  public void resolveReferences() {
    for (PendingReference ref : pending()) {
      List<String> candidates = ref.candidateDns();
      String target = null;
      for (String candidate : candidates) {
        if (isDefined(ref._type, candidate)) {
          target = candidate;
          break;
        }
      }
      StructureManager referrer = manager(ref._filename);
      String name = target != null ? target : candidates.get(0);
      referrer.referenceStructure(ref._type, name, ref._usage, ref._line);
      if (target == null) {
        referrer.undefined(ref._type, name, ref._usage, ref._line);
        continue;
      }
      for (StructureManager manager : _managers.values()) {
        manager
            .getDefinition(ref._type, target)
            .ifPresent(info -> info.setNumReferrers(info.getNumReferrers() + 1));
      }
    }
    _pending = null;
  }

  /** Structure managers by input filename. */
  public @Nonnull SortedMap<String, StructureManager> getManagers() {
    return ImmutableSortedMap.copyOf(_managers);
  }

  private boolean isDefined(AciStructureType type, String dn) {
    return _managers.values().stream().anyMatch(m -> m.hasDefinition(type.getDescription(), dn));
  }

  private @Nonnull StructureManager manager(String filename) {
    return _managers.computeIfAbsent(filename, f -> StructureManager.create());
  }

  private @Nonnull List<PendingReference> pending() {
    if (_pending == null) {
      _pending = new ArrayList<>();
    }
    return _pending;
  }

  /** A reference recorded before every file is read. */
  private static final class PendingReference {
    private PendingReference(
        String filename,
        AciStructureType type,
        AciStructureUsage usage,
        int line,
        @Nullable String tenant,
        @Nullable NamedRef ref,
        @Nullable String dn) {
      _filename = filename;
      _type = type;
      _usage = usage;
      _line = line;
      _tenant = tenant;
      _ref = ref;
      _dn = dn;
    }

    /** DNs the reference may resolve to, in order of precedence; never empty. */
    private @Nonnull List<String> candidateDns() {
      if (_dn != null) {
        return ImmutableList.of(_dn);
      }
      assert _ref != null && _tenant != null;
      String name = _ref.getName().isEmpty() ? "default" : _ref.getName();
      ImmutableList.Builder<String> dns = ImmutableList.builder();
      if (_ref.getTargetDn() != null) {
        dns.add(_ref.getTargetDn());
      }
      dns.add(_type.dn(_tenant, name));
      if (!_tenant.equals(Tenant.COMMON)) {
        dns.add(_type.dn(Tenant.COMMON, name));
      }
      return dns.build();
    }

    private final @Nonnull String _filename;
    private final @Nonnull AciStructureType _type;
    private final @Nonnull AciStructureUsage _usage;
    private final int _line;
    private final @Nullable String _tenant;
    private final @Nullable NamedRef _ref;
    private final @Nullable String _dn;
  }

  private final @Nonnull Map<String, StructureManager> _managers = new TreeMap<>();

  /** References awaiting {@link #resolveReferences()}; never serialized. */
  private transient @Nullable List<PendingReference> _pending;
}
