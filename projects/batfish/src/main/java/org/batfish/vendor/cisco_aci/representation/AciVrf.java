package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A tenant VRF ({@code fvCtx}) and its {@code vzAny} contract relations. */
public final class AciVrf implements Serializable {

  public AciVrf(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _enforced = true;
    _anyProvided = new ArrayList<>();
    _anyConsumed = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/ctx-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/ctx-%s", _tenant, _name);
  }

  /** The VRF's VXLAN network ID ({@code scope}, also {@code seg}); assigned by APIC. */
  public @Nullable Long getVnid() {
    return _vnid;
  }

  public void setVnid(@Nullable Long vnid) {
    _vnid = vnid;
  }

  /** {@code pcEnfPref}: whether contracts are enforced. Defaults to {@code enforced}. */
  public boolean isEnforced() {
    return _enforced;
  }

  public void setEnforced(boolean enforced) {
    _enforced = enforced;
  }

  /** Contracts provided by {@code vzAny} ({@code vzRsAnyToProv}). */
  public @Nonnull List<NamedRef> getAnyProvided() {
    return _anyProvided;
  }

  /** Contracts consumed by {@code vzAny} ({@code vzRsAnyToCons}). */
  public @Nonnull List<NamedRef> getAnyConsumed() {
    return _anyConsumed;
  }

  /** {@code vzAny.prefGrMemb=enabled}: the VRF's preferred group is enabled. */
  public boolean isPreferredGroupEnabled() {
    return _preferredGroupEnabled;
  }

  public void setPreferredGroupEnabled(boolean preferredGroupEnabled) {
    _preferredGroupEnabled = preferredGroupEnabled;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private @Nullable Long _vnid;
  private boolean _enforced;
  private final @Nonnull List<NamedRef> _anyProvided;
  private final @Nonnull List<NamedRef> _anyConsumed;
  private boolean _preferredGroupEnabled;
}
