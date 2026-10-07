package org.batfish.vendor.cisco_aci.representation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import org.batfish.common.util.BatfishObjectMapper;
import org.junit.Test;

public class AciMoReaderTest {

  private static JsonNode json(String text) throws IOException {
    return BatfishObjectMapper.mapper().readTree(text);
  }

  @Test
  public void testQueryResponse() throws IOException {
    List<AciMo> mos =
        AciMoReader.read(
            json(
                "{\"totalCount\":\"1\",\"imdata\":[{\"fvTenant\":{\"attributes\":{\"name\":\"t1\"},"
                    + "\"children\":[{\"fvCtx\":{\"attributes\":{\"name\":\"v1\",\"descr\":\"\"}}}]}}]}"));
    assertThat(mos, hasSize(1));
    AciMo tenant = mos.get(0);
    assertThat(tenant.getClassName(), equalTo("fvTenant"));
    assertThat(tenant.getAttribute("name"), equalTo("t1"));
    AciMo ctx = tenant.getChild("fvCtx").get();
    assertThat(ctx.getAttribute("name"), equalTo("v1"));
    // empty attributes read as absent
    assertThat(ctx.getAttribute("descr"), nullValue());
    assertThat(ctx.getAttribute("descr", "none"), equalTo("none"));
  }

  @Test
  public void testConfigExportAndList() throws IOException {
    assertThat(
        AciMoReader.read(json("{\"polUni\":{\"attributes\":{}}}")).get(0).getClassName(),
        equalTo("polUni"));
    List<AciMo> mos =
        AciMoReader.read(
            json(
                "[{\"fabricNode\":{\"attributes\":{\"id\":101}}},{\"topSystem\":{\"attributes\":{}}}]"));
    assertThat(mos.stream().map(AciMo::getClassName).toList(), contains("fabricNode", "topSystem"));
    // non-string values are read as text
    assertThat(mos.get(0).getAttribute("id"), equalTo("101"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsNonApicJson() throws IOException {
    AciMoReader.read(json("{\"a\":1,\"b\":2}"));
  }
}
