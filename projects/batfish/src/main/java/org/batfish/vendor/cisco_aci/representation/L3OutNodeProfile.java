package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * An L3Out logical node profile ({@code l3extLNodeP}): border leaves, their interfaces, and BGP
 * peers reached over loopbacks.
 */
public final class L3OutNodeProfile implements Serializable {

  public L3OutNodeProfile(String name) {
    _name = name;
    _nodes = new ArrayList<>();
    _interfaceProfiles = new ArrayList<>();
    _loopbackBgpPeers = new ArrayList<>();
  }

  public @Nonnull String getName() {
    return _name;
  }

  public @Nonnull List<L3OutNode> getNodes() {
    return _nodes;
  }

  public @Nonnull List<L3OutInterfaceProfile> getInterfaceProfiles() {
    return _interfaceProfiles;
  }

  /** BGP peers configured on the node profile ({@code bgpPeerP} under {@code l3extLNodeP}). */
  public @Nonnull List<BgpPeer> getLoopbackBgpPeers() {
    return _loopbackBgpPeers;
  }

  private final @Nonnull String _name;
  private final @Nonnull List<L3OutNode> _nodes;
  private final @Nonnull List<L3OutInterfaceProfile> _interfaceProfiles;
  private final @Nonnull List<BgpPeer> _loopbackBgpPeers;
}
