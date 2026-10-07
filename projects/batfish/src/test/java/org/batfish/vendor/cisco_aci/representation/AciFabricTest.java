package org.batfish.vendor.cisco_aci.representation;

import static org.batfish.datamodel.matchers.AbstractRouteDecoratorMatchers.hasNextHop;
import static org.batfish.datamodel.matchers.AbstractRouteDecoratorMatchers.hasPrefix;
import static org.batfish.datamodel.matchers.HopMatchers.hasNodeName;
import static org.batfish.datamodel.matchers.TraceMatchers.hasDisposition;
import static org.batfish.datamodel.matchers.TraceMatchers.hasLastHop;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.batfish.datamodel.AbstractRoute;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.flow.Trace;
import org.batfish.datamodel.route.nh.NextHopVtep;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.batfish.main.TestrigText;
import org.hamcrest.Matcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * End-to-end tests of the {@code basic} fabric: two spines, leaves 101 and 102 as a vPC pair, and
 * border leaf 103 peering eBGP with router {@code wan-rtr}.
 *
 * <ul>
 *   <li>EPG web (10.1.1.0/24, leaf-101) consumes web-to-app (TCP 8080) and provides http-in.
 *   <li>EPG app (10.1.2.0/24, leaf-102) provides web-to-app and consumes app-to-db.
 *   <li>EPG db (10.1.3.0/24, vPC) provides app-to-db: permit all TCP, deny SSH.
 *   <li>External EPG internet (0.0.0.0/0) consumes http-in (stateful HTTP). Only web-bd is public
 *       and associated with the L3Out.
 * </ul>
 */
public class AciFabricTest {

  @ClassRule public static TemporaryFolder _folder = new TemporaryFolder();

  private static final String SNAPSHOT =
      "org/batfish/vendor/cisco_aci/representation/snapshots/basic";
  private static final int VRF_VNI = 2490368;
  private static final String VRF = "prod:main";

  private static Batfish _batfish;

  @BeforeClass
  public static void setup() throws IOException {
    _batfish =
        BatfishTestUtils.getBatfishFromTestrigText(
            TestrigText.builder()
                .setAciFiles(
                    SNAPSHOT,
                    ImmutableList.of("dc1/uni.json", "dc1/nodes.json", "dc1/endpoints.json"))
                .setConfigurationFiles(SNAPSHOT, "wan-rtr")
                .setLayer1TopologyPrefix(SNAPSHOT)
                .build(),
            _folder);
    _batfish.computeDataPlane(_batfish.getSnapshot());
  }

  private static Set<AbstractRoute> routes(String node, String vrf) {
    DataPlane dp = _batfish.loadDataPlane(_batfish.getSnapshot());
    return dp.getRibs().get(node, vrf).getRoutes();
  }

  @Test
  public void testFabricRoutes() {
    // web-bd lives on leaf-101 only; leaf-102 reaches it over the VRF's L3 VNI.
    assertThat(
        routes("leaf-102", VRF),
        hasItem(
            allOf(
                hasPrefix(Prefix.parse("10.1.1.0/24")),
                hasNextHop(NextHopVtep.of(VRF_VNI, Ip.parse("10.0.32.64"))))));
    // and to the web endpoint directly
    assertThat(
        routes("leaf-102", VRF),
        hasItem(
            allOf(
                hasPrefix(Prefix.parse("10.1.1.10/32")),
                hasNextHop(NextHopVtep.of(VRF_VNI, Ip.parse("10.0.32.64"))))));
  }

  @Test
  public void testExternalRoutes() {
    // learned on the border leaf and distributed to the other leaves
    assertThat(routes("leaf-103", VRF), hasItem(hasPrefix(Prefix.parse("8.8.8.0/24"))));
    assertThat(
        routes("leaf-101", VRF),
        hasItem(
            allOf(
                hasPrefix(Prefix.parse("8.8.8.0/24")),
                hasNextHop(NextHopVtep.of(VRF_VNI, Ip.parse("10.0.32.66"))))));
    // Only the public BD subnet associated with the L3Out is advertised; no host routes.
    Set<AbstractRoute> wan = routes("wan-rtr", "default");
    assertThat(wan, hasItem(hasPrefix(Prefix.parse("10.1.1.0/24"))));
    assertThat(wan, not(hasItem(hasPrefix(Prefix.parse("10.1.2.0/24")))));
    assertThat(wan, not(hasItem(hasPrefix(Prefix.parse("10.1.1.10/32")))));
  }

  private static List<Trace> trace(Flow flow) {
    return _batfish
        .getTracerouteEngine(_batfish.getSnapshot())
        .computeTraces(ImmutableSet.of(flow), false)
        .get(flow);
  }

  private static Flow.Builder tcp(String node, String iface, String src, String dst, int dstPort) {
    return Flow.builder()
        .setIngressNode(node)
        .setIngressInterface(iface)
        .setSrcIp(Ip.parse(src))
        .setDstIp(Ip.parse(dst))
        .setIpProtocol(IpProtocol.TCP)
        .setSrcPort(49152)
        .setDstPort(dstPort);
  }

  private static Matcher<Trace> delivered(String node) {
    return allOf(
        hasDisposition(in(FlowDisposition.SUCCESS_DISPOSITIONS)), hasLastHop(hasNodeName(node)));
  }

  private static Matcher<Trace> denied() {
    return hasDisposition(FlowDisposition.DENIED_IN);
  }

  @Test
  public void testContractPermitsAcrossLeaves() {
    assertThat(
        trace(tcp("leaf-101", "vlan100", "10.1.1.10", "10.1.2.10", 8080).build()),
        everyItem(delivered("leaf-102")));
  }

  @Test
  public void testNoContractDenied() {
    assertThat(
        trace(tcp("leaf-101", "vlan100", "10.1.1.10", "10.1.2.10", 22).build()),
        everyItem(denied()));
    assertThat(
        trace(tcp("leaf-101", "vlan100", "10.1.1.10", "10.1.3.10", 3306).build()),
        everyItem(denied()));
  }

  @Test
  public void testReverseFilterPorts() {
    // provider app replies to consumer web from port 8080
    Flow reply =
        tcp("leaf-102", "vlan200", "10.1.2.10", "10.1.1.10", 49152).setSrcPort(8080).build();
    assertThat(trace(reply), everyItem(delivered("leaf-101")));
  }

  @Test
  public void testDenyBeatsPermit() {
    assertThat(
        trace(tcp("leaf-102", "vlan200", "10.1.2.10", "10.1.3.10", 5432).build()),
        everyItem(delivered("leaf-102")));
    assertThat(
        trace(tcp("leaf-102", "vlan200", "10.1.2.10", "10.1.3.10", 22).build()),
        everyItem(denied()));
  }

  @Test
  public void testStatefulEntry() {
    // http-in's entry is stateful: web's replies to the internet need the ACK flag.
    Flow.Builder reply = tcp("leaf-101", "vlan100", "10.1.1.10", "8.8.8.1", 49152).setSrcPort(80);
    assertThat(trace(reply.build()), everyItem(denied()));
    assertThat(
        trace(reply.setTcpFlagsAck(true).build()),
        everyItem(hasDisposition(FlowDisposition.ACCEPTED)));
  }

  @Test
  public void testIntraEpgPermitted() {
    assertThat(
        trace(tcp("leaf-101", "vlan100", "10.1.1.10", "10.1.1.20", 22).build()),
        everyItem(hasDisposition(in(FlowDisposition.SUCCESS_DISPOSITIONS))));
  }

  @Test
  public void testExternalEpg() {
    // from the WAN router to the web EPG: http-in permits HTTP
    Flow fromWan =
        Flow.builder()
            .setIngressNode("wan-rtr")
            .setIngressVrf("default")
            .setSrcIp(Ip.parse("8.8.8.1"))
            .setDstIp(Ip.parse("10.1.1.10"))
            .setIpProtocol(IpProtocol.TCP)
            .setSrcPort(49152)
            .setDstPort(80)
            .build();
    assertThat(trace(fromWan), everyItem(delivered("leaf-101")));
    // entering the border leaf toward app: no contract with the internet EPG
    assertThat(
        trace(tcp("leaf-103", "eth1/48", "8.8.8.1", "10.1.2.10", 8080).build()),
        everyItem(denied()));
  }
}
