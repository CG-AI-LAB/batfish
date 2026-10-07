package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An LLDP neighbor seen on a fabric node interface ({@code lldpAdjEp}). */
public final class LldpAdjacency implements Serializable {

  public LldpAdjacency(
      int nodeId,
      String localInterface,
      @Nullable String remoteSystemName,
      @Nullable String remotePortId) {
    _nodeId = nodeId;
    _localInterface = localInterface;
    _remoteSystemName = remoteSystemName;
    _remotePortId = remotePortId;
  }

  public int getNodeId() {
    return _nodeId;
  }

  public @Nonnull String getLocalInterface() {
    return _localInterface;
  }

  /** The neighbor's system name ({@code sysName}). */
  public @Nullable String getRemoteSystemName() {
    return _remoteSystemName;
  }

  /** The neighbor's port ID ({@code portIdV}). */
  public @Nullable String getRemotePortId() {
    return _remotePortId;
  }

  private final int _nodeId;
  private final @Nonnull String _localInterface;
  private final @Nullable String _remoteSystemName;
  private final @Nullable String _remotePortId;
}
