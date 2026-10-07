package org.batfish.vendor.cisco_aci.representation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.batfish.common.Warnings;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.BumTransportMethod;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.IntegerSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.InterfaceType;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.LongSpace;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.SwitchportMode;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.batfish.datamodel.vxlan.Layer2Vni;
import org.junit.Before;
import org.junit.Test;

/** Tests conversion of the {@code basic} fabric to vendor-independent configurations. */
public class AciConversionTest {

  private Map<String, Configuration> _configs;
  private Warnings _warnings;

  @Before
  public void convert() {
    AciConfiguration aci = AciParserTest.parseBasic(new ParseVendorConfigurationAnswerElement());
    _warnings = new Warnings(true, true, true);
    aci.setWarnings(_warnings);
    _configs =
        aci.toVendorIndependentConfigurations().stream()
            .collect(Collectors.toMap(Configuration::getHostname, Function.identity()));
  }

  @Test
  public void testNodes() {
    // The APIC controller is not a switch.
    assertThat(
        _configs.keySet(),
        containsInAnyOrder("leaf-101", "leaf-102", "leaf-103", "spine-201", "spine-202"));
    assertThat(
        _configs.get("leaf-101").getVrfs().keySet(),
        containsInAnyOrder("default", "overlay-1", "prod:main"));
    assertThat(
        _configs.get("spine-201").getVrfs().keySet(), containsInAnyOrder("default", "overlay-1"));
    assertThat(_warnings.getRedFlagWarnings(), empty());
  }

  @Test
  public void testUnderlay() {
    Configuration leaf = _configs.get("leaf-101");
    Interface lo0 = leaf.getAllInterfaces().get("lo0");
    assertThat(lo0.getVrfName(), equalTo("overlay-1"));
    assertThat(lo0.getConcreteAddress(), equalTo(ConcreteInterfaceAddress.parse("10.0.32.64/32")));
    // Without LLDP data, each leaf links to each spine.
    Interface uplink = leaf.getAllInterfaces().get("fabric-201");
    assertThat(uplink.getVrfName(), equalTo("overlay-1"));
    assertThat(uplink.getConcreteAddress().getNetworkBits(), equalTo(31));
    assertThat(uplink.getIsis(), notNullValue());
    assertThat(leaf.getVrfs().get("overlay-1").getIsisProcess(), notNullValue());
  }

  @Test
  public void testAccessPortsAndBindings() {
    Configuration leaf = _configs.get("leaf-101");
    Interface eth11 = leaf.getAllInterfaces().get("eth1/1");
    assertThat(eth11.getSwitchportMode(), equalTo(SwitchportMode.TRUNK));
    assertThat(eth11.getAllowedVlans(), equalTo(IntegerSpace.of(100)));
    // eth1/2 has an access policy but no EPG on leaf-101
    assertThat(leaf.getAllInterfaces().get("eth1/2").getSwitchport(), equalTo(false));
    // vPC member and aggregate
    assertThat(leaf.getAllInterfaces().get("eth1/10").getChannelGroup(), equalTo("db-vpc"));
    Interface vpc = leaf.getAllInterfaces().get("db-vpc");
    assertThat(vpc.getInterfaceType(), equalTo(InterfaceType.AGGREGATED));
    assertThat(vpc.getAllowedVlans(), equalTo(IntegerSpace.of(300)));
    // untagged binding on leaf-102 is an access port
    Interface eth12 = _configs.get("leaf-102").getAllInterfaces().get("eth1/2");
    assertThat(eth12.getSwitchportMode(), equalTo(SwitchportMode.ACCESS));
    assertThat(eth12.getAccessVlan(), equalTo(200));
  }

  @Test
  public void testBridgeDomains() {
    Configuration leaf = _configs.get("leaf-101");
    Interface svi = leaf.getAllInterfaces().get("vlan100");
    assertThat(svi.getVrfName(), equalTo("prod:main"));
    assertThat(svi.getConcreteAddress(), equalTo(ConcreteInterfaceAddress.parse("10.1.1.1/24")));
    assertThat(svi.getHmm(), equalTo(true));
    assertThat(svi.getIncomingFilter().getName(), equalTo("aci-zoning~prod:main"));
    // the db BD is on both vPC peers, with the same anycast gateway
    assertThat(leaf.getAllInterfaces(), hasKey("vlan300"));
    assertThat(_configs.get("leaf-102").getAllInterfaces(), hasKey("vlan300"));
    // web-bd is not deployed on leaf-102
    assertThat(_configs.get("leaf-102").getAllInterfaces(), not(hasKey("vlan100")));

    Layer2Vni vni = leaf.getVrfs().get("overlay-1").getLayer2Vnis().get(16351140);
    assertThat(vni.getVlan(), equalTo(100));
    assertThat(vni.getSourceAddress(), equalTo(Ip.parse("10.0.32.64")));
    assertThat(vni.getBumTransportMethod(), equalTo(BumTransportMethod.MULTICAST_GROUP));
    assertThat(vni.getBumTransportIps(), contains(Ip.parse("225.1.156.240")));
    assertThat(
        leaf.getVrfs().get("prod:main").getLayer3Vnis().get(2490368).getSrcVrf(),
        equalTo("overlay-1"));
  }

  @Test
  public void testEndpointRoutes() {
    assertThat(
        _configs.get("leaf-101").getVrfs().get("prod:main").getStaticRoutes().stream()
            .map(r -> r.getNetwork())
            .toList(),
        hasItem(Prefix.parse("10.1.1.10/32")));
  }

  @Test
  public void testFabricBgp() {
    BgpProcess leafBgp = _configs.get("leaf-101").getVrfs().get("overlay-1").getBgpProcess();
    assertThat(
        leafBgp.getActiveNeighbors().keySet(),
        containsInAnyOrder(Ip.parse("10.0.32.70"), Ip.parse("10.0.32.71")));
    BgpActivePeerConfig toSpine = leafBgp.getActiveNeighbors().get(Ip.parse("10.0.32.70"));
    assertThat(toSpine.getLocalAs(), equalTo(65001L));
    assertThat(toSpine.getEvpnAddressFamily().getL3VNIs().first().getVni(), equalTo(2490368));
    BgpProcess spineBgp = _configs.get("spine-201").getVrfs().get("overlay-1").getBgpProcess();
    assertThat(spineBgp.getActiveNeighbors().keySet(), hasItem(Ip.parse("10.0.32.66")));
    assertThat(
        spineBgp
            .getActiveNeighbors()
            .get(Ip.parse("10.0.32.66"))
            .getEvpnAddressFamily()
            .getRouteReflectorClient(),
        equalTo(true));
  }

  @Test
  public void testL3Out() {
    Configuration border = _configs.get("leaf-103");
    Interface uplink = border.getAllInterfaces().get("eth1/48");
    assertThat(uplink.getVrfName(), equalTo("prod:main"));
    assertThat(
        uplink.getConcreteAddress(), equalTo(ConcreteInterfaceAddress.parse("192.168.100.1/30")));
    BgpProcess bgp = border.getVrfs().get("prod:main").getBgpProcess();
    assertThat(bgp.getRouterId(), equalTo(Ip.parse("1.1.1.103")));
    BgpActivePeerConfig peer = bgp.getActiveNeighbors().get(Ip.parse("192.168.100.2"));
    assertThat(peer.getRemoteAsns(), equalTo(LongSpace.of(65100L)));
    assertThat(peer.getLocalIp(), equalTo(Ip.parse("192.168.100.1")));
    assertThat(
        peer.getIpv4UnicastAddressFamily().getExportPolicy(),
        equalTo("~aci~l3out~prod~wan~export~"));
  }
}
