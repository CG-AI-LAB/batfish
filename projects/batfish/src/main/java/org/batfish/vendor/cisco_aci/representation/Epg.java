package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Prefix;

/** An application EPG ({@code fvAEPg}). */
public final class Epg implements Serializable {

  public Epg(String tenant, String applicationProfile, String name) {
    _tenant = tenant;
    _applicationProfile = applicationProfile;
    _name = name;
    _contracts = new ContractRelations();
    _staticPaths = new ArrayList<>();
    _subnets = new ArrayList<>();
    _ipAttributes = new ArrayList<>();
    _endpoints = new ArrayList<>();
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

  /** {@code uni/tn-<tenant>/ap-<ap>/epg-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/ap-%s/epg-%s", _tenant, _applicationProfile, _name);
  }

  /** The bridge domain relation ({@code fvRsBd}). */
  public @Nullable NamedRef getBridgeDomain() {
    return _bridgeDomain;
  }

  public void setBridgeDomain(@Nullable NamedRef bridgeDomain) {
    _bridgeDomain = bridgeDomain;
  }

  /** {@code pcEnfPref=enforced}: intra-EPG isolation. Defaults to {@code unenforced}. */
  public boolean isIntraEpgIsolation() {
    return _intraEpgIsolation;
  }

  public void setIntraEpgIsolation(boolean intraEpgIsolation) {
    _intraEpgIsolation = intraEpgIsolation;
  }

  /** {@code prefGrMemb=include}: member of the VRF's preferred group. */
  public boolean isPreferredGroupMember() {
    return _preferredGroupMember;
  }

  public void setPreferredGroupMember(boolean preferredGroupMember) {
    _preferredGroupMember = preferredGroupMember;
  }

  public @Nonnull ContractRelations getContracts() {
    return _contracts;
  }

  public @Nonnull List<StaticPath> getStaticPaths() {
    return _staticPaths;
  }

  /** EPG-level subnets ({@code fvSubnet} under the EPG). */
  public @Nonnull List<AciSubnet> getSubnets() {
    return _subnets;
  }

  /** uSeg IP attributes ({@code fvIpAttr}). */
  public @Nonnull List<Prefix> getIpAttributes() {
    return _ipAttributes;
  }

  public @Nonnull List<Endpoint> getEndpoints() {
    return _endpoints;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _applicationProfile;
  private final @Nonnull String _name;
  private @Nullable NamedRef _bridgeDomain;
  private boolean _intraEpgIsolation;
  private boolean _preferredGroupMember;
  private final @Nonnull ContractRelations _contracts;
  private final @Nonnull List<StaticPath> _staticPaths;
  private final @Nonnull List<AciSubnet> _subnets;
  private final @Nonnull List<Prefix> _ipAttributes;
  private final @Nonnull List<Endpoint> _endpoints;
}
