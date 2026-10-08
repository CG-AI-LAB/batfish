package org.batfish.vendor.cisco_aci.representation;

import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasDefinedStructure;
import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasDefinedStructureWithDefinitionLines;
import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasNumReferrers;
import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasReferencedStructure;
import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasUndefinedReference;
import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasUndefinedReferenceWithReferenceLines;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.batfish.main.TestrigText;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests structure definitions and references across the files of one fabric: tenant t1 in {@code
 * tenants.json} uses a filter of tenant common in {@code common.json}, and access policies in
 * {@code infra.json} deploy an EPG of t1.
 */
public class AciStructuresTest {

  @ClassRule public static TemporaryFolder _folder = new TemporaryFolder();

  private static final String TENANTS = "aci_configs/dc1/tenants.json";
  private static final String COMMON = "aci_configs/dc1/common.json";
  private static final String INFRA = "aci_configs/dc1/infra.json";
  private static final String NODES = "aci_configs/dc1/nodes.json";

  private static final String TENANTS_JSON =
      String.join(
          "\n",
          "{\"imdata\": [",
          "{\"fvTenant\": {\"attributes\": {\"name\": \"t1\"}, \"children\": [",
          " {\"fvCtx\": {\"attributes\": {\"name\": \"v1\"}}},",
          " {\"fvCtx\": {\"attributes\": {\"name\": \"unused\"}}},",
          " {\"fvBD\": {\"attributes\": {\"name\": \"b1\"}, \"children\": [",
          "  {\"fvRsCtx\": {\"attributes\": {\"tnFvCtxName\": \"v1\"}}}",
          " ]}},",
          " {\"fvBD\": {\"attributes\": {\"name\": \"b2\"}, \"children\": [",
          "  {\"fvRsCtx\": {\"attributes\": {\"tnFvCtxName\": \"missing\"}}}",
          " ]}},",
          " {\"fvAp\": {\"attributes\": {\"name\": \"a\"}, \"children\": [",
          "  {\"fvAEPg\": {\"attributes\": {\"name\": \"e1\"}, \"children\": [",
          "   {\"fvRsBd\": {\"attributes\": {\"tnFvBDName\": \"b1\"}}},",
          "   {\"fvRsProv\": {\"attributes\": {\"tnVzBrCPName\": \"c1\"}}}",
          "  ]}}",
          " ]}},",
          " {\"vzBrCP\": {\"attributes\": {\"name\": \"c1\"}, \"children\": [",
          "  {\"vzSubj\": {\"attributes\": {\"name\": \"s\"}, \"children\": [",
          "   {\"vzRsSubjFiltAtt\": {\"attributes\": {\"tnVzFilterName\": \"http\"}}}",
          "  ]}}",
          " ]}},",
          " {\"vzFilter\": {\"attributes\": {\"name\": \"unused\"}}}",
          "]}}",
          "]}");

  private static final String COMMON_JSON =
      String.join(
          "\n",
          "{\"fvTenant\": {\"attributes\": {\"name\": \"common\"}, \"children\": [",
          " {\"fvCtx\": {\"attributes\": {\"name\": \"default\"}}},",
          " {\"vzFilter\": {\"attributes\": {\"name\": \"http\"}, \"children\": [",
          "  {\"vzEntry\": {\"attributes\": {\"name\": \"http\", \"etherT\": \"ip\","
              + " \"prot\": \"tcp\", \"dFromPort\": \"http\", \"dToPort\": \"http\"}}}",
          " ]}}",
          "]}}");

  private static final String INFRA_JSON =
      String.join(
          "\n",
          "{\"infraInfra\": {\"attributes\": {}, \"children\": [",
          " {\"infraNodeP\": {\"attributes\": {\"name\": \"leaf101\"}, \"children\": [",
          "  {\"infraLeafS\": {\"attributes\": {\"name\": \"s\", \"type\": \"range\"},"
              + " \"children\": [",
          "   {\"infraNodeBlk\": {\"attributes\": {\"from_\": \"101\", \"to_\": \"101\"}}}",
          "  ]}},",
          "  {\"infraRsAccPortP\": {\"attributes\": {\"tDn\": \"uni/infra/accportprof-prof1\"}}}",
          " ]}},",
          " {\"infraAccPortP\": {\"attributes\": {\"name\": \"prof1\"}, \"children\": [",
          "  {\"infraHPortS\": {\"attributes\": {\"name\": \"sel\", \"type\": \"range\"},"
              + " \"children\": [",
          "   {\"infraPortBlk\": {\"attributes\": {\"fromPort\": \"5\", \"toPort\": \"5\"}}},",
          "   {\"infraRsAccBaseGrp\": {\"attributes\":"
              + " {\"tDn\": \"uni/infra/funcprof/accportgrp-pg1\"}}}",
          "  ]}}",
          " ]}},",
          " {\"infraFuncP\": {\"attributes\": {}, \"children\": [",
          "  {\"infraAccPortGrp\": {\"attributes\": {\"name\": \"pg1\"}, \"children\": [",
          "   {\"infraRsAttEntP\": {\"attributes\": {\"tDn\": \"uni/infra/attentp-aaep1\"}}}",
          "  ]}}",
          " ]}},",
          " {\"infraAttEntityP\": {\"attributes\": {\"name\": \"aaep1\"}, \"children\": [",
          "  {\"infraGeneric\": {\"attributes\": {\"name\": \"default\"}, \"children\": [",
          "   {\"infraRsFuncToEpg\": {\"attributes\":"
              + " {\"tDn\": \"uni/tn-t1/ap-a/epg-e1\", \"encap\": \"vlan-10\"}}},",
          "   {\"infraRsFuncToEpg\": {\"attributes\":"
              + " {\"tDn\": \"uni/tn-t1/ap-a/epg-gone\", \"encap\": \"vlan-11\"}}}",
          "  ]}}",
          " ]}}",
          "]}}");

  private static final String NODES_JSON =
      String.join(
          "\n",
          "{\"imdata\": [",
          " {\"fabricNode\": {\"attributes\": {\"dn\": \"topology/pod-1/node-101\", \"id\":"
              + " \"101\", \"name\": \"leaf-101\", \"role\": \"leaf\", \"address\":"
              + " \"10.0.0.1\"}}},",
          " {\"fabricNode\": {\"attributes\": {\"dn\": \"topology/pod-1/node-201\", \"id\":"
              + " \"201\", \"name\": \"spine-201\", \"role\": \"spine\", \"address\":"
              + " \"10.0.0.2\"}}}",
          "]}");

  private static Batfish _batfish;
  private static ConvertConfigurationAnswerElement _ccae;

  @BeforeClass
  public static void setup() throws IOException {
    _batfish =
        BatfishTestUtils.getBatfishFromTestrigText(
            TestrigText.builder()
                .setAciBytes(
                    ImmutableMap.of(
                        "dc1/tenants.json", bytes(TENANTS_JSON),
                        "dc1/common.json", bytes(COMMON_JSON),
                        "dc1/infra.json", bytes(INFRA_JSON),
                        "dc1/nodes.json", bytes(NODES_JSON)))
                .build(),
            _folder);
    _ccae = _batfish.loadConvertConfigurationAnswerElementOrReparse(_batfish.getSnapshot());
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void testDefinitionLines() {
    assertThat(
        _ccae,
        hasDefinedStructureWithDefinitionLines(
            TENANTS, AciStructureType.VRF, "uni/tn-t1/ctx-v1", contains(3)));
    assertThat(
        _ccae,
        hasDefinedStructureWithDefinitionLines(
            TENANTS, AciStructureType.BRIDGE_DOMAIN, "uni/tn-t1/BD-b1", contains(5, 6, 7)));
    assertThat(
        _ccae,
        hasDefinedStructureWithDefinitionLines(
            INFRA,
            AciStructureType.INTERFACE_POLICY_GROUP,
            "uni/infra/funcprof/accportgrp-pg1",
            contains(15, 16, 17)));
    // Definitions stay in the file that holds them
    assertThat(
        _ccae,
        not(hasDefinedStructure(TENANTS, AciStructureType.FILTER, "uni/tn-common/flt-http")));
  }

  @Test
  public void testReferencesWithinFile() {
    assertThat(_ccae, hasNumReferrers(TENANTS, AciStructureType.VRF, "uni/tn-t1/ctx-v1", 1));
    assertThat(
        _ccae, hasNumReferrers(TENANTS, AciStructureType.BRIDGE_DOMAIN, "uni/tn-t1/BD-b1", 1));
    assertThat(_ccae, hasNumReferrers(TENANTS, AciStructureType.CONTRACT, "uni/tn-t1/brc-c1", 1));
    assertThat(
        _ccae,
        hasReferencedStructure(
            TENANTS,
            AciStructureType.VRF,
            "uni/tn-t1/ctx-v1",
            AciStructureUsage.BRIDGE_DOMAIN_VRF));
  }

  @Test
  public void testReferencesAcrossFiles() {
    // The contract's filter resolves in tenant common, defined in another file
    assertThat(
        _ccae, hasNumReferrers(COMMON, AciStructureType.FILTER, "uni/tn-common/flt-http", 1));
    assertThat(
        _ccae,
        hasReferencedStructure(
            TENANTS,
            AciStructureType.FILTER,
            "uni/tn-common/flt-http",
            AciStructureUsage.CONTRACT_SUBJECT_FILTER));
    // The AAEP in infra.json deploys the EPG in tenants.json; EPGs also count themselves
    assertThat(_ccae, hasNumReferrers(TENANTS, AciStructureType.EPG, "uni/tn-t1/ap-a/epg-e1", 2));
    assertThat(
        _ccae,
        hasNumReferrers(
            INFRA, AciStructureType.INTERFACE_PROFILE, "uni/infra/accportprof-prof1", 1));
    assertThat(
        _ccae,
        hasNumReferrers(
            INFRA,
            AciStructureType.INTERFACE_POLICY_GROUP,
            "uni/infra/funcprof/accportgrp-pg1",
            1));
    assertThat(
        _ccae,
        hasNumReferrers(
            INFRA, AciStructureType.ATTACHABLE_ENTITY_PROFILE, "uni/infra/attentp-aaep1", 1));
  }

  @Test
  public void testUnused() {
    assertThat(_ccae, hasNumReferrers(TENANTS, AciStructureType.VRF, "uni/tn-t1/ctx-unused", 0));
    assertThat(
        _ccae, hasNumReferrers(TENANTS, AciStructureType.BRIDGE_DOMAIN, "uni/tn-t1/BD-b2", 0));
    assertThat(_ccae, hasNumReferrers(TENANTS, AciStructureType.FILTER, "uni/tn-t1/flt-unused", 0));
    // APIC's own default objects are never reported unused
    assertThat(
        _ccae, hasNumReferrers(COMMON, AciStructureType.VRF, "uni/tn-common/ctx-default", 1));
  }

  @Test
  public void testUndefined() {
    // A name resolves in the source tenant, then common; the source tenant's DN is reported
    assertThat(
        _ccae,
        hasUndefinedReferenceWithReferenceLines(
            TENANTS,
            AciStructureType.VRF,
            "uni/tn-t1/ctx-missing",
            AciStructureUsage.BRIDGE_DOMAIN_VRF,
            contains(9)));
    assertThat(
        _ccae,
        hasUndefinedReferenceWithReferenceLines(
            INFRA,
            AciStructureType.EPG,
            "uni/tn-t1/ap-a/epg-gone",
            AciStructureUsage.AAEP_EPG,
            contains(22)));
    assertThat(
        _ccae, not(hasUndefinedReference(TENANTS, AciStructureType.FILTER, "uni/tn-t1/flt-http")));
  }

  @Test
  public void testNodesMapToFabricFiles() {
    ParseVendorConfigurationAnswerElement pvcae =
        _batfish.loadParseVendorConfigurationAnswerElement(_batfish.getSnapshot());
    assertThat(
        pvcae.getFileMap().get("leaf-101"), containsInAnyOrder(COMMON, INFRA, NODES, TENANTS));
    assertThat(
        pvcae.getFileMap().get("spine-201"), containsInAnyOrder(COMMON, INFRA, NODES, TENANTS));
  }

  @Test
  public void testIsSystemObject() {
    assertThat(
        AciModelExtractor.isSystemObject(AciStructureType.VRF, "uni/tn-common/ctx-default"),
        equalTo(true));
    assertThat(
        AciModelExtractor.isSystemObject(AciStructureType.VRF, "uni/tn-mgmt/ctx-oob"),
        equalTo(true));
    assertThat(
        AciModelExtractor.isSystemObject(AciStructureType.FILTER, "uni/tn-common/flt-web-default"),
        equalTo(false));
    assertThat(
        AciModelExtractor.isSystemObject(AciStructureType.EPG, "uni/tn-t1/ap-a/epg-default"),
        equalTo(false));
  }
}
