package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;

/** A bridge domain ({@code fvBD}). */
public final class BridgeDomain implements Serializable {

  public BridgeDomain(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _unicastRoute = true;
    _subnets = new ArrayList<>();
    _l3Outs = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/BD-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/BD-%s", _tenant, _name);
  }

  /** The VRF relation ({@code fvRsCtx}). */
  public @Nullable NamedRef getVrf() {
    return _vrf;
  }

  public void setVrf(@Nullable NamedRef vrf) {
    _vrf = vrf;
  }

  /** The BD's VXLAN network ID ({@code seg}); assigned by APIC. */
  public @Nullable Long getVnid() {
    return _vnid;
  }

  public void setVnid(@Nullable Long vnid) {
    _vnid = vnid;
  }

  /** The multicast group that carries the BD's flooded traffic ({@code bcastP}, the GIPo). */
  public @Nullable Ip getMulticastGroup() {
    return _multicastGroup;
  }

  public void setMulticastGroup(@Nullable Ip multicastGroup) {
    _multicastGroup = multicastGroup;
  }

  /** {@code unicastRoute}: whether the BD routes (has gateway addresses). Defaults to yes. */
  public boolean isUnicastRoute() {
    return _unicastRoute;
  }

  public void setUnicastRoute(boolean unicastRoute) {
    _unicastRoute = unicastRoute;
  }

  public @Nonnull List<AciSubnet> getSubnets() {
    return _subnets;
  }

  /** L3Outs the BD is associated with ({@code fvRsBDToOut}), by name. */
  public @Nonnull List<String> getL3Outs() {
    return _l3Outs;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private @Nullable NamedRef _vrf;
  private @Nullable Long _vnid;
  private @Nullable Ip _multicastGroup;
  private boolean _unicastRoute;
  private final @Nonnull List<AciSubnet> _subnets;
  private final @Nonnull List<String> _l3Outs;
}
