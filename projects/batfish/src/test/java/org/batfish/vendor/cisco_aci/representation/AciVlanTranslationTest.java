package org.batfish.vendor.cisco_aci.representation;

import static org.batfish.datamodel.matchers.ConvertConfigurationAnswerElementMatchers.hasRedFlagWarning;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.IntegerSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.SwitchportMode;
import org.batfish.datamodel.Topology;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.batfish.main.TestrigText;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests that EPG encaps are translated to their bridge domain's VLAN on leaf ports, so every encap
 * reaches the bridge domain's gateway.
 *
 * <ul>
 *   <li>Bridge domain b1 has EPG e1 on eth1/1 with encap 100 and EPG e2 on eth1/2 with encap 200.
 *       Its VLAN on leaf-101 is 100, so eth1/2 translates tag 200 to VLAN 100.
 *   <li>Bridge domain b2 has EPG e3 on eth1/3 with encap 100 (per-port VLAN). VLAN 100 is taken, so
 *       b2 gets internal VLAN 3967 and eth1/3 translates tag 100 to it.
 *   <li>EPG e4 of b1 also uses encap 300 on eth1/1, which already carries b1 under encap 100; that
 *       binding is ignored.
 * </ul>
 */
public class AciVlanTranslationTest {

  @ClassRule public static TemporaryFolder _folder = new TemporaryFolder();

  private static final String TENANTS_JSON =
      String.join(
          "\n",
          "{\"fvTenant\": {\"attributes\": {\"name\": \"t1\"}, \"children\": [",
          " {\"fvCtx\": {\"attributes\": {\"name\": \"v1\"}}},",
          " {\"fvBD\": {\"attributes\": {\"name\": \"b1\"}, \"children\": [",
          "  {\"fvRsCtx\": {\"attributes\": {\"tnFvCtxName\": \"v1\"}}},",
          "  {\"fvSubnet\": {\"attributes\": {\"ip\": \"10.1.1.1/24\"}}}",
          " ]}},",
          " {\"fvBD\": {\"attributes\": {\"name\": \"b2\"}, \"children\": [",
          "  {\"fvRsCtx\": {\"attributes\": {\"tnFvCtxName\": \"v1\"}}},",
          "  {\"fvSubnet\": {\"attributes\": {\"ip\": \"10.2.2.1/24\"}}}",
          " ]}},",
          " {\"fvAp\": {\"attributes\": {\"name\": \"a\"}, \"children\": [",
          epg("e1", "b1", "eth1/1", 100) + ",",
          epg("e2", "b1", "eth1/2", 200) + ",",
          epg("e3", "b2", "eth1/3", 100) + ",",
          epg("e4", "b1", "eth1/1", 300),
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

  private static final String LAYER1_JSON =
      "{\"edges\": ["
          + l1Edge("host1", "GigabitEthernet0/0", "leaf-101", "eth1/2")
          + ","
          + l1Edge("host2", "GigabitEthernet0/0", "leaf-101", "eth1/3")
          + ","
          + l1Edge("host3", "GigabitEthernet0/0", "leaf-101", "eth1/1")
          + "]}";

  private static String epg(String name, String bd, String port, int encap) {
    return String.format(
        "  {\"fvAEPg\": {\"attributes\": {\"name\": \"%s\"}, \"children\": ["
            + "{\"fvRsBd\": {\"attributes\": {\"tnFvBDName\": \"%s\"}}},"
            + "{\"fvRsPathAtt\": {\"attributes\": {\"tDn\":"
            + " \"topology/pod-1/paths-101/pathep-[%s]\", \"encap\": \"vlan-%d\"}}}]}}",
        name, bd, port, encap);
  }

  private static String l1Edge(String h1, String i1, String h2, String i2) {
    String node = "{\"hostname\": \"%s\", \"interfaceName\": \"%s\"}";
    String n1 = String.format(node, h1, i1);
    String n2 = String.format(node, h2, i2);
    return String.format(
        "{\"node1\": %s, \"node2\": %s}, {\"node1\": %s, \"node2\": %s}", n1, n2, n2, n1);
  }

  /** A host with a dot1q subinterface. */
  private static String host(String hostname, int tag, String address) {
    return String.join(
        "\n",
        "hostname " + hostname,
        "interface GigabitEthernet0/0",
        " no shutdown",
        "interface GigabitEthernet0/0." + tag,
        " encapsulation dot1Q " + tag,
        " ip address " + address + " 255.255.255.0",
        "");
  }

  private static Batfish _batfish;
  private static Map<String, Configuration> _configs;

  @BeforeClass
  public static void setup() throws IOException {
    _batfish =
        BatfishTestUtils.getBatfishFromTestrigText(
            TestrigText.builder()
                .setAciBytes(
                    ImmutableMap.of(
                        "dc1/tenants.json", bytes(TENANTS_JSON),
                        "dc1/nodes.json", bytes(NODES_JSON)))
                .setConfigurationText(
                    ImmutableMap.of(
                        "host1", host("host1", 200, "10.1.1.10"),
                        "host2", host("host2", 100, "10.2.2.10"),
                        "host3", host("host3", 300, "10.1.1.30")))
                .setLayer1TopologyBytes(bytes(LAYER1_JSON))
                .build(),
            _folder);
    _configs = _batfish.loadConfigurations(_batfish.getSnapshot());
    _batfish.computeDataPlane(_batfish.getSnapshot());
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static Interface leafPort(String name) {
    return _configs.get("leaf-101").getAllInterfaces().get(name);
  }

  @Test
  public void testPortTranslations() {
    // The BD's VLAN is its lowest free encap, so eth1/1 carries it untranslated.
    Interface eth11 = leafPort("eth1/1");
    assertThat(eth11.getSwitchportMode(), equalTo(SwitchportMode.TRUNK));
    assertThat(eth11.getAllowedVlans(), equalTo(IntegerSpace.of(100)));
    assertThat(eth11.getVlanTranslations(), anEmptyMap());

    Interface eth12 = leafPort("eth1/2");
    assertThat(eth12.getAllowedVlans(), equalTo(IntegerSpace.of(100)));
    assertThat(eth12.getVlanTranslations(), equalTo(ImmutableMap.of(200, 100)));

    Interface eth13 = leafPort("eth1/3");
    assertThat(eth13.getAllowedVlans(), equalTo(IntegerSpace.of(3967)));
    assertThat(eth13.getVlanTranslations(), equalTo(ImmutableMap.of(100, 3967)));
  }

  @Test
  public void testConflictingEncapIgnored() {
    assertThat(
        _batfish.loadConvertConfigurationAnswerElementOrReparse(_batfish.getSnapshot()),
        hasRedFlagWarning(
            "leaf-101",
            containsString(
                "EPG uni/tn-t1/ap-a/epg-e4 uses encap VLAN 300 on leaf-101 eth1/1, which already"
                    + " carries bridge domain uni/tn-t1/BD-b1 under encap VLAN 100")));
  }

  @Test
  public void testHostsReachGatewaysThroughTranslation() {
    Topology topology = _batfish.getTopologyProvider().getLayer3Topology(_batfish.getSnapshot());
    // Tag 200 on eth1/2 reaches b1's gateway in VLAN 100
    assertThat(
        topology.getEdges(),
        hasItem(Edge.of("host1", "GigabitEthernet0/0.200", "leaf-101", "vlan100")));
    // Tag 100 on eth1/3 reaches b2's gateway in internal VLAN 3967
    assertThat(
        topology.getEdges(),
        hasItem(Edge.of("host2", "GigabitEthernet0/0.100", "leaf-101", "vlan3967")));
    // The ignored binding leaves tag 300 unbridged
    assertThat(
        topology.getEdges(),
        not(hasItem(Edge.of("host3", "GigabitEthernet0/0.300", "leaf-101", "vlan100"))));
  }
}
