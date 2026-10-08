package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;

/** A border leaf in an L3Out node profile ({@code l3extRsNodeL3OutAtt}). */
public final class L3OutNode implements Serializable {

  public L3OutNode(int nodeId, int podId) {
    _nodeId = nodeId;
    _podId = podId;
    _routerIdLoopback = true;
    _loopbacks = new ArrayList<>();
    _staticRoutes = new ArrayList<>();
  }

  public int getNodeId() {
    return _nodeId;
  }

  public int getPodId() {
    return _podId;
  }

  /** {@code rtrId}. */
  public @Nullable Ip getRouterId() {
    return _routerId;
  }

  public void setRouterId(@Nullable Ip routerId) {
    _routerId = routerId;
  }

  /** {@code rtrIdLoopBack}: create a loopback with the router ID. Defaults to yes. */
  public boolean isRouterIdLoopback() {
    return _routerIdLoopback;
  }

  public void setRouterIdLoopback(boolean routerIdLoopback) {
    _routerIdLoopback = routerIdLoopback;
  }

  /** Extra loopback addresses ({@code l3extLoopBackIfP}). */
  public @Nonnull List<Ip> getLoopbacks() {
    return _loopbacks;
  }

  /** Static routes ({@code ipRouteP}). */
  public @Nonnull List<L3OutStaticRoute> getStaticRoutes() {
    return _staticRoutes;
  }

  private final int _nodeId;
  private final int _podId;
  private @Nullable Ip _routerId;
  private boolean _routerIdLoopback;
  private final @Nonnull List<Ip> _loopbacks;
  private final @Nonnull List<L3OutStaticRoute> _staticRoutes;
}
