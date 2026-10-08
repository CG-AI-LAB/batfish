package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** An external EPG ({@code l3extInstP}): external prefixes classified for policy and routing. */
public final class ExternalEpg implements Serializable {

  public ExternalEpg(String tenant, String l3Out, String name) {
    _tenant = tenant;
    _l3Out = l3Out;
    _name = name;
    _contracts = new ContractRelations();
    _subnets = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getL3Out() {
    return _l3Out;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/out-<l3out>/instP-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/out-%s/instP-%s", _tenant, _l3Out, _name);
  }

  public @Nonnull ContractRelations getContracts() {
    return _contracts;
  }

  public @Nonnull List<ExternalSubnet> getSubnets() {
    return _subnets;
  }

  /** {@code prefGrMemb=include}: member of the VRF's preferred group. */
  public boolean isPreferredGroupMember() {
    return _preferredGroupMember;
  }

  public void setPreferredGroupMember(boolean preferredGroupMember) {
    _preferredGroupMember = preferredGroupMember;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _l3Out;
  private final @Nonnull String _name;
  private final @Nonnull ContractRelations _contracts;
  private final @Nonnull List<ExternalSubnet> _subnets;
  private boolean _preferredGroupMember;
}
