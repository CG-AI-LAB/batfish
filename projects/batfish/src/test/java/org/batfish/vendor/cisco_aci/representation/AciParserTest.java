package org.batfish.vendor.cisco_aci.representation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.batfish.common.util.Resources.readResource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.ParseStatus;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.junit.Test;

/** Tests extraction of the {@code basic} fabric into the ACI model. */
public class AciParserTest {

  private static final String FIXTURE =
      "org/batfish/vendor/cisco_aci/representation/snapshots/basic/aci_configs/dc1/";

  static AciConfiguration parseBasic(ParseVendorConfigurationAnswerElement pvcae) {
    Map<String, String> files = new TreeMap<>();
    for (String file : new String[] {"uni.json", "nodes.json", "endpoints.json"}) {
      files.put("snapshot/aci_configs/dc1/" + file, readResource(FIXTURE + file, UTF_8));
    }
    SortedMap<String, AciConfiguration> fabrics = AciParser.parseFabrics(files, pvcae);
    assertThat(fabrics, hasKey("dc1"));
    return fabrics.get("dc1");
  }

  @Test
  public void testFilesAndStatus() {
    ParseVendorConfigurationAnswerElement pvcae = new ParseVendorConfigurationAnswerElement();
    AciConfiguration aci = parseBasic(pvcae);
    assertThat(aci.getFilename(), equalTo("aci_configs/dc1"));
    assertThat(
        aci.getSecondaryFilenames(),
        contains(
            "aci_configs/dc1/endpoints.json",
            "aci_configs/dc1/nodes.json",
            "aci_configs/dc1/uni.json"));
    assertThat(
        pvcae.getFileFormats(),
        hasEntry("aci_configs/dc1/uni.json", ConfigurationFormat.CISCO_ACI));
    assertThat(pvcae.getParseStatus(), hasEntry("aci_configs/dc1/uni.json", ParseStatus.PASSED));
    assertThat(pvcae.getWarnings(), not(hasKey("aci_configs/dc1/uni.json")));
  }

  @Test
  public void testFabric() {
    AciConfiguration aci = parseBasic(new ParseVendorConfigurationAnswerElement());
    assertThat(aci.getNodes().keySet(), contains(1, 101, 102, 103, 201, 202));
    FabricNode leaf = aci.getNodes().get(101);
    assertThat(leaf.getName(), equalTo("leaf-101"));
    assertThat(leaf.getRole(), equalTo(FabricNode.Role.LEAF));
    assertThat(leaf.getTepAddress(), equalTo(Ip.parse("10.0.32.64")));
    assertThat(aci.getFabricAsn(), equalTo(65001L));
    assertThat(aci.getRouteReflectorNodeIds(), containsInAnyOrder(201, 202));
    assertThat(aci.getVpcPairs(), hasSize(1));
    assertThat(aci.getVpcPairs().get(0).getNodeIds(), contains(101, 102));
    assertThat(aci.getTepPool(), equalTo(Prefix.parse("10.0.0.0/16")));
  }

  @Test
  public void testAccessPolicies() {
    AciConfiguration aci = parseBasic(new ParseVendorConfigurationAnswerElement());
    AccessPolicies policies = aci.getAccessPolicies();
    // infraNodeBlk uses from_ and to_
    assertThat(policies.getLeafProfiles().get(0).selects(101), equalTo(true));
    assertThat(policies.getLeafProfiles().get(0).selects(103), equalTo(false));
    assertThat(policies.portSelectorsFor(101).get(0).getPorts(), contains("eth1/1", "eth1/2"));
    assertThat(
        policies.getPolicyGroups().get("uni/infra/funcprof/accbundle-db-vpc").getBundleType(),
        equalTo(AccessPolicies.BundleType.VPC));
  }

  @Test
  public void testTenant() {
    AciConfiguration aci = parseBasic(new ParseVendorConfigurationAnswerElement());
    Tenant prod = aci.getTenants().get("prod");
    AciVrf vrf = prod.getVrfs().get("main");
    assertThat(vrf.getVnid(), equalTo(2490368L));
    assertThat(vrf.isEnforced(), equalTo(true));
    BridgeDomain web = prod.getBridgeDomains().get("web-bd");
    assertThat(web.getVnid(), equalTo(16351140L));
    assertThat(web.getMulticastGroup(), equalTo(Ip.parse("225.1.156.240")));
    assertThat(web.getL3Outs(), contains("wan"));
    AciSubnet subnet = web.getSubnets().get(0);
    assertThat(subnet.getGateway(), equalTo(ConcreteInterfaceAddress.parse("10.1.1.1/24")));
    assertThat(subnet.isPublic(), equalTo(true));

    Epg epg = prod.getEpgs().get("uni/tn-prod/ap-shop/epg-db");
    StaticPath path = epg.getStaticPaths().get(0);
    assertThat(path.getPath().isVpc(), equalTo(true));
    assertThat(path.getEncapVlan(), equalTo(300));
    // endpoints come from a separate file
    assertThat(epg.getEndpoints(), hasSize(1));
    assertThat(epg.getEndpoints().get(0).getIps(), contains(Ip.parse("10.1.3.10")));
  }

  @Test
  public void testContractsAndFilters() {
    AciConfiguration aci = parseBasic(new ParseVendorConfigurationAnswerElement());
    Tenant prod = aci.getTenants().get("prod");
    ContractSubject subject = prod.getContracts().get("app-to-db").getSubjects().get(0);
    assertThat(subject.isReverseFilterPorts(), equalTo(true));
    assertThat(
        subject.getFilters().stream().map(FilterRef::getAction).toList(),
        contains(LineAction.PERMIT, LineAction.DENY));
    // named ports and protocols use the values in the APIC model
    FilterEntry http = prod.getFilters().get("http").getEntries().get(0);
    assertThat(http.getIpProtocol(), equalTo(6));
    assertThat(http.getDstFromPort(), equalTo(80));
    FilterEntry mysql = prod.getFilters().get("sql").getEntries().get(0);
    assertThat(mysql.getEtherType(), equalTo(FilterEntry.EtherType.IPV4));
    assertThat(mysql.isStateful(), equalTo(true));
  }

  @Test
  public void testL3Out() {
    AciConfiguration aci = parseBasic(new ParseVendorConfigurationAnswerElement());
    L3Out wan = aci.getTenants().get("prod").getL3Outs().get("wan");
    assertThat(wan.isBgpEnabled(), equalTo(true));
    L3OutNode node = wan.getNodeProfiles().get(0).getNodes().get(0);
    assertThat(node.getNodeId(), equalTo(103));
    assertThat(node.getRouterId(), equalTo(Ip.parse("1.1.1.103")));
    L3OutPath path = wan.getNodeProfiles().get(0).getInterfaceProfiles().get(0).getPaths().get(0);
    assertThat(path.getType(), equalTo(L3OutPath.Type.ROUTED));
    assertThat(path.getAddress(), equalTo(ConcreteInterfaceAddress.parse("192.168.100.1/30")));
    assertThat(path.getBgpPeers().get(0).getRemoteAs(), equalTo(65100L));
    ExternalSubnet subnet = wan.getExternalEpgs().get(0).getSubnets().get(0);
    assertThat(subnet.getPrefix(), equalTo(Prefix.ZERO));
    assertThat(subnet.getScope(), contains(ExternalSubnet.IMPORT_SECURITY));
  }

  @Test
  public void testWarnings() {
    ParseVendorConfigurationAnswerElement pvcae = new ParseVendorConfigurationAnswerElement();
    AciParser.parseFabrics(
        ImmutableMap.of(
            "aci_configs/f1/bad.json", "not json",
            "aci_configs/f1/other.json", "{\"imdata\":[{\"aaaUser\":{\"attributes\":{}}}]}"),
        pvcae);
    assertThat(pvcae.getParseStatus(), hasEntry("aci_configs/f1/bad.json", ParseStatus.FAILED));
    assertThat(
        pvcae.getParseStatus(),
        hasEntry("aci_configs/f1/other.json", ParseStatus.PARTIALLY_UNRECOGNIZED));
  }

  @Test
  public void testIpSelector() {
    assertThat(
        AciModelExtractor.parseIpSelector("ip=='10.1.0.0/16'"),
        equalTo(Prefix.parse("10.1.0.0/16")));
    assertThat(
        AciModelExtractor.parseIpSelector("ip == '10.1.1.5'"),
        equalTo(Prefix.parse("10.1.1.5/32")));
  }
}
