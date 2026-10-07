package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Prefix;

/** An endpoint security group ({@code fvESg}). */
public final class Esg implements Serializable {

  public Esg(String tenant, String applicationProfile, String name) {
    _tenant = tenant;
    _applicationProfile = applicationProfile;
    _name = name;
    _contracts = new ContractRelations();
    _ipSelectors = new ArrayList<>();
    _epgSelectors = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getApplicationProfile() {
    return _applicationProfile;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/ap-<ap>/esg-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/ap-%s/esg-%s", _tenant, _applicationProfile, _name);
  }

  /** The VRF the ESG is scoped to ({@code fvRsScope}). */
  public @Nullable NamedRef getVrf() {
    return _vrf;
  }

  public void setVrf(@Nullable NamedRef vrf) {
    _vrf = vrf;
  }

  public @Nonnull ContractRelations getContracts() {
    return _contracts;
  }

  /** IP subnet selectors ({@code fvEPSelector} with {@code ip=='...'}). */
  public @Nonnull List<Prefix> getIpSelectors() {
    return _ipSelectors;
  }

  /** EPG selectors ({@code fvEPgSelector}): the DNs of EPGs whose endpoints join this ESG. */
  public @Nonnull List<String> getEpgSelectors() {
    return _epgSelectors;
  }

  /** {@code prefGrMemb=include}: member of the VRF's preferred group. */
  public boolean isPreferredGroupMember() {
    return _preferredGroupMember;
  }

  public void setPreferredGroupMember(boolean preferredGroupMember) {
    _preferredGroupMember = preferredGroupMember;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _applicationProfile;
  private final @Nonnull String _name;
  private @Nullable NamedRef _vrf;
  private final @Nonnull ContractRelations _contracts;
  private final @Nonnull List<Prefix> _ipSelectors;
  private final @Nonnull List<String> _epgSelectors;
  private boolean _preferredGroupMember;
}
