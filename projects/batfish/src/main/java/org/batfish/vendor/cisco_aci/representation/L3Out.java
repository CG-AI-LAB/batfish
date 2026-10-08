package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An L3Out ({@code l3extOut}): routed connectivity from a VRF to external networks. */
public final class L3Out implements Serializable {

  /** OSPF area type ({@code ospfExtP.areaType}). */
  public enum OspfAreaType {
    REGULAR,
    STUB,
    NSSA;

    static @Nonnull OspfAreaType fromString(@Nullable String type) {
      if (type == null) {
        return NSSA; // the APIC default
      }
      return switch (type) {
        case "regular" -> REGULAR;
        case "stub" -> STUB;
        default -> NSSA;
      };
    }
  }

  public L3Out(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _nodeProfiles = new ArrayList<>();
    _externalEpgs = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/out-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/out-%s", _tenant, _name);
  }

  /** The VRF relation ({@code l3extRsEctx}). */
  public @Nullable NamedRef getVrf() {
    return _vrf;
  }

  public void setVrf(@Nullable NamedRef vrf) {
    _vrf = vrf;
  }

  /** {@code enforceRtctrl} contains {@code import}: inbound routes must match import subnets. */
  public boolean isEnforceImportRouteControl() {
    return _enforceImportRouteControl;
  }

  public void setEnforceImportRouteControl(boolean enforceImportRouteControl) {
    _enforceImportRouteControl = enforceImportRouteControl;
  }

  /** Whether BGP is enabled on the L3Out ({@code bgpExtP} present). */
  public boolean isBgpEnabled() {
    return _bgpEnabled;
  }

  public void setBgpEnabled(boolean bgpEnabled) {
    _bgpEnabled = bgpEnabled;
  }

  /** The OSPF area ID if OSPF is enabled ({@code ospfExtP}), else {@code null}. */
  public @Nullable Long getOspfAreaId() {
    return _ospfAreaId;
  }

  public void setOspfAreaId(@Nullable Long ospfAreaId) {
    _ospfAreaId = ospfAreaId;
  }

  public @Nullable OspfAreaType getOspfAreaType() {
    return _ospfAreaType;
  }

  public void setOspfAreaType(@Nullable OspfAreaType ospfAreaType) {
    _ospfAreaType = ospfAreaType;
  }

  public @Nonnull List<L3OutNodeProfile> getNodeProfiles() {
    return _nodeProfiles;
  }

  public @Nonnull List<ExternalEpg> getExternalEpgs() {
    return _externalEpgs;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private @Nullable NamedRef _vrf;
  private boolean _enforceImportRouteControl;
  private boolean _bgpEnabled;
  private @Nullable Long _ospfAreaId;
  private @Nullable OspfAreaType _ospfAreaType;
  private final @Nonnull List<L3OutNodeProfile> _nodeProfiles;
  private final @Nonnull List<ExternalEpg> _externalEpgs;
}
