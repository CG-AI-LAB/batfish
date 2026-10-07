package org.batfish.vendor.cisco_aci.representation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class AciDnTest {

  @Test
  public void testSplitKeepsBracketedSlashes() {
    assertThat(
        AciDn.split("topology/pod-1/paths-101/pathep-[eth1/5]"),
        contains("topology", "pod-1", "paths-101", "pathep-[eth1/5]"));
    assertThat(
        AciDn.split("uni/tn-t/ap-a/epg-e/rspathAtt-[topology/pod-1/paths-101/pathep-[eth1/5]]"),
        contains(
            "uni",
            "tn-t",
            "ap-a",
            "epg-e",
            "rspathAtt-[topology/pod-1/paths-101/pathep-[eth1/5]]"));
  }

  @Test
  public void testValue() {
    assertThat(AciDn.value("uni/tn-common/BD-default", "tn-"), equalTo("common"));
    assertThat(AciDn.value("uni/tn-common/BD-default", "BD-"), equalTo("default"));
    assertThat(AciDn.value("uni/tn-common/BD-default", "ctx-"), nullValue());
    assertThat(
        AciDn.interfaceName("topology/pod-1/node-101/sys/lldp/inst/if-[eth1/49]/adj-1"),
        equalTo("eth1/49"));
    assertThat(AciDn.nodeId("topology/pod-2/node-101/sys"), equalTo(101));
    assertThat(AciDn.podId("topology/pod-2/node-101/sys"), equalTo(2));
  }

  @Test
  public void testParent() {
    assertThat(
        AciDn.parent("uni/tn-t/ap-a/epg-e/cep-00:50:56:00:00:01"), equalTo("uni/tn-t/ap-a/epg-e"));
    assertThat(AciDn.parent("uni"), nullValue());
  }

  @Test
  public void testVlan() {
    assertThat(AciDn.vlan("vlan-100"), equalTo(100));
    assertThat(AciDn.vlan("unknown"), nullValue());
    assertThat(AciDn.vlan("vxlan-16777209"), nullValue());
    assertThat(AciDn.vlan("vlan-4095"), nullValue());
    assertThat(AciDn.vlan(null), nullValue());
  }
}
