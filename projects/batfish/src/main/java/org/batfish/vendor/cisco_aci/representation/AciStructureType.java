package org.batfish.vendor.cisco_aci.representation;

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.vendor.StructureType;

/**
 * APIC objects tracked as structures. Each is named by its DN, such as {@code uni/tn-prod/BD-web}.
 */
public enum AciStructureType implements StructureType {
  ATTACHABLE_ENTITY_PROFILE("attachable-entity-profile", null),
  BRIDGE_DOMAIN("bridge-domain", "BD-"),
  CONTRACT("contract", "brc-"),
  CONTRACT_INTERFACE("contract-interface", "cif-"),
  EPG("epg", null),
  FILTER("filter", "flt-"),
  INTERFACE_POLICY_GROUP("interface-policy-group", null),
  INTERFACE_PROFILE("interface-profile", null),
  L3OUT("l3out", "out-"),
  TABOO_CONTRACT("taboo-contract", "taboo-"),
  VRF("vrf", "ctx-");

  AciStructureType(String description, @Nullable String tenantRnPrefix) {
    _description = description;
    _tenantRnPrefix = tenantRnPrefix;
  }

  @Override
  public @Nonnull String getDescription() {
    return _description;
  }

  /**
   * The prefix of the relative name of a tenant child of this type, such as {@code ctx-} in {@code
   * uni/tn-prod/ctx-main}. {@code null} for types that relations name only by DN.
   */
  public @Nullable String getTenantRnPrefix() {
    return _tenantRnPrefix;
  }

  /** The DN of the tenant child of this type named {@code name}. */
  public @Nonnull String dn(String tenant, String name) {
    checkState(_tenantRnPrefix != null, "%s is not a tenant child", this);
    return "uni/tn-" + tenant + "/" + _tenantRnPrefix + name;
  }

  private final @Nonnull String _description;
  private final @Nullable String _tenantRnPrefix;
}
