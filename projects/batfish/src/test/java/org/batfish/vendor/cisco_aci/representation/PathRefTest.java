package org.batfish.vendor.cisco_aci.representation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class PathRefTest {

  @Test
  public void testPort() {
    PathRef path = PathRef.parse("topology/pod-1/paths-101/pathep-[eth1/5]");
    assertThat(path.getPodId(), equalTo(1));
    assertThat(path.getNodeIds(), contains(101));
    assertThat(path.getName(), equalTo("eth1/5"));
    assertThat(path.isPort(), equalTo(true));
    assertThat(path.isVpc(), equalTo(false));
    assertThat(path.toString(), equalTo("topology/pod-1/paths-101/pathep-[eth1/5]"));
  }

  @Test
  public void testPortChannel() {
    PathRef path = PathRef.parse("topology/pod-1/paths-101/pathep-[pc-pg]");
    assertThat(path.isPort(), equalTo(false));
    assertThat(path.isVpc(), equalTo(false));
    assertThat(path.getName(), equalTo("pc-pg"));
  }

  @Test
  public void testVpc() {
    PathRef path = PathRef.parse("topology/pod-1/protpaths-101-102/pathep-[vpc-pg]");
    assertThat(path.getNodeIds(), contains(101, 102));
    assertThat(path.isVpc(), equalTo(true));
    assertThat(path.isPort(), equalTo(false));
    assertThat(path.toString(), equalTo("topology/pod-1/protpaths-101-102/pathep-[vpc-pg]"));
  }

  @Test
  public void testUnsupported() {
    assertThat(PathRef.parse("topology/pod-1/paths-101/extpaths-111/pathep-[eth1/1]"), nullValue());
    assertThat(PathRef.parse("uni/tn-t"), nullValue());
    assertThat(PathRef.parse(null), nullValue());
  }
}
