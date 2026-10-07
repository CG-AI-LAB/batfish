package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A fabric path endpoint ({@code fabricPathEp}), the target of static EPG bindings and L3Out
 * interfaces. Either a single-leaf path ({@code topology/pod-1/paths-101/pathep-[eth1/5]}) or a vPC
 * path across two leaves ({@code topology/pod-1/protpaths-101-102/pathep-[vpc-pg]}).
 */
public final class PathRef implements Serializable {

  /**
   * Parses a path DN. Returns {@code null} for paths this model does not support, such as FEX
   * paths.
   */
  public static @Nullable PathRef parse(@Nullable String dn) {
    if (dn == null) {
      return null;
    }
    List<String> rns = AciDn.split(dn);
    if (rns.size() != 4 || !rns.get(0).equals("topology") || !rns.get(1).startsWith("pod-")) {
      return null;
    }
    Integer pod = AciDn.parseIntOrNull(rns.get(1).substring("pod-".length()));
    String container = rns.get(2);
    String endpoint = rns.get(3);
    if (pod == null || !endpoint.startsWith("pathep-")) {
      return null;
    }
    String name = AciDn.unbracket(endpoint.substring("pathep-".length()));
    if (container.startsWith("paths-")) {
      Integer node = AciDn.parseIntOrNull(container.substring("paths-".length()));
      return node == null ? null : new PathRef(pod, ImmutableList.of(node), name, false);
    }
    if (container.startsWith("protpaths-")) {
      String[] ids = container.substring("protpaths-".length()).split("-");
      if (ids.length != 2) {
        return null;
      }
      Integer a = AciDn.parseIntOrNull(ids[0]);
      Integer b = AciDn.parseIntOrNull(ids[1]);
      return a == null || b == null ? null : new PathRef(pod, ImmutableList.of(a, b), name, true);
    }
    return null;
  }

  public PathRef(int podId, List<Integer> nodeIds, String name, boolean vpc) {
    _podId = podId;
    _nodeIds = ImmutableList.copyOf(nodeIds);
    _name = name;
    _vpc = vpc;
  }

  public int getPodId() {
    return _podId;
  }

  /** One node for a single-leaf path; both vPC peers for a vPC path. */
  public @Nonnull List<Integer> getNodeIds() {
    return _nodeIds;
  }

  /**
   * The path endpoint name: an interface such as {@code eth1/5} for a port, or the interface policy
   * group name for a port-channel or vPC.
   */
  public @Nonnull String getName() {
    return _name;
  }

  /** Whether this path is a vPC across two leaves. */
  public boolean isVpc() {
    return _vpc;
  }

  /** Whether this path names a physical port rather than a port-channel or vPC. */
  public boolean isPort() {
    return !_vpc && PORT_NAME.matcher(_name).matches();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PathRef)) {
      return false;
    }
    PathRef that = (PathRef) o;
    return _podId == that._podId
        && _vpc == that._vpc
        && _nodeIds.equals(that._nodeIds)
        && _name.equals(that._name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_podId, _nodeIds, _name, _vpc);
  }

  @Override
  public String toString() {
    return _vpc
        ? String.format(
            "topology/pod-%d/protpaths-%d-%d/pathep-[%s]",
            _podId, _nodeIds.get(0), _nodeIds.get(1), _name)
        : String.format("topology/pod-%d/paths-%d/pathep-[%s]", _podId, _nodeIds.get(0), _name);
  }

  private static final Pattern PORT_NAME = Pattern.compile("eth\\d+/\\d+(/\\d+)?");

  private final int _podId;
  private final @Nonnull List<Integer> _nodeIds;
  private final @Nonnull String _name;
  private final boolean _vpc;
}
