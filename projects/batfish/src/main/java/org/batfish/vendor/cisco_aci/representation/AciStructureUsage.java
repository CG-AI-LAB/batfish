package org.batfish.vendor.cisco_aci.representation;

import javax.annotation.Nonnull;
import org.batfish.vendor.StructureUsage;

/** Where an APIC object references a tracked structure: the referring object and relation class. */
public enum AciStructureUsage implements StructureUsage {
  AAEP_EPG("attachable-entity-profile infraRsFuncToEpg"),
  BRIDGE_DOMAIN_L3OUT("bridge-domain fvRsBDToOut"),
  BRIDGE_DOMAIN_VRF("bridge-domain fvRsCtx"),
  CONTRACT_INTERFACE_CONTRACT("contract-interface vzRsIf"),
  CONTRACT_SUBJECT_FILTER("contract subject vzRsSubjFiltAtt"),
  CONTRACT_SUBJECT_TERM_FILTER("contract subject vzRsFiltAtt"),
  EPG_BRIDGE_DOMAIN("epg fvRsBd"),
  EPG_CONSUMED_CONTRACT("epg fvRsCons"),
  EPG_CONSUMED_CONTRACT_INTERFACE("epg fvRsConsIf"),
  EPG_PROVIDED_CONTRACT("epg fvRsProv"),
  EPG_SELF_REF("epg"),
  EPG_TABOO_CONTRACT("epg fvRsProtBy"),
  ESG_CONSUMED_CONTRACT("esg fvRsCons"),
  ESG_CONSUMED_CONTRACT_INTERFACE("esg fvRsConsIf"),
  ESG_EPG_SELECTOR("esg fvEPgSelector"),
  ESG_PROVIDED_CONTRACT("esg fvRsProv"),
  ESG_TABOO_CONTRACT("esg fvRsProtBy"),
  ESG_VRF("esg fvRsScope"),
  EXTERNAL_EPG_CONSUMED_CONTRACT("external-epg fvRsCons"),
  EXTERNAL_EPG_CONSUMED_CONTRACT_INTERFACE("external-epg fvRsConsIf"),
  EXTERNAL_EPG_PROVIDED_CONTRACT("external-epg fvRsProv"),
  EXTERNAL_EPG_TABOO_CONTRACT("external-epg fvRsProtBy"),
  INTERFACE_POLICY_GROUP_AAEP("interface-policy-group infraRsAttEntP"),
  L3OUT_SELF_REF("l3out"),
  L3OUT_VRF("l3out l3extRsEctx"),
  LEAF_PROFILE_INTERFACE_PROFILE("leaf-profile infraRsAccPortP"),
  PORT_SELECTOR_POLICY_GROUP("port-selector infraRsAccBaseGrp"),
  SYSTEM_OBJECT("APIC system object"),
  TABOO_SUBJECT_FILTER("taboo-contract subject vzRsDenyRule"),
  VZANY_CONSUMED_CONTRACT("vzAny vzRsAnyToCons"),
  VZANY_PROVIDED_CONTRACT("vzAny vzRsAnyToProv");

  AciStructureUsage(String description) {
    _description = description;
  }

  @Override
  public @Nonnull String getDescription() {
    return _description;
  }

  private final @Nonnull String _description;
}
