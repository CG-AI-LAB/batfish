package org.batfish.vendor.cisco_aci.representation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class AciMoReaderTest {

  @Test
  public void testQueryResponse() throws IOException {
    List<AciMo> mos =
        AciMoReader.read(
            "{\"totalCount\":\"1\",\"imdata\":[{\"fvTenant\":{\"attributes\":{\"name\":\"t1\"},"
                + "\"children\":[{\"fvCtx\":{\"attributes\":{\"name\":\"v1\",\"descr\":\"\"}}}]}}]}");
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
        AciMoReader.read("{\"polUni\":{\"attributes\":{}}}").get(0).getClassName(),
        equalTo("polUni"));
    List<AciMo> mos =
        AciMoReader.read(
            "[{\"fabricNode\":{\"attributes\":{\"id\":101}}},{\"topSystem\":{\"attributes\":{}}}]");
    assertThat(mos.stream().map(AciMo::getClassName).toList(), contains("fabricNode", "topSystem"));
    // non-string values are read as text
    assertThat(mos.get(0).getAttribute("id"), equalTo("101"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsNonApicJson() throws IOException {
    AciMoReader.read("{\"a\":1,\"b\":2}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsTwoClassNames() throws IOException {
    AciMoReader.read("{\"fvTenant\":{},\"fvCtx\":{}}");
  }

  @Test
  public void testLines() throws IOException {
    List<AciMo> mos =
        AciMoReader.read(
            String.join(
                "\n",
                "{\"imdata\": [",
                " {",
                "  \"fvTenant\": {",
                "   \"attributes\": {\"name\": \"t1\"},",
                "   \"children\": [",
                "    {\"fvCtx\": {\"attributes\": {\"name\": \"v1\"}}}",
                "   ]",
                "  }",
                " }",
                "]}"));
    AciMo tenant = mos.get(0);
    assertThat(tenant.getLine(), equalTo(2));
    assertThat(tenant.getLastLine(), equalTo(9));
    AciMo ctx = tenant.getChild("fvCtx").get();
    assertThat(ctx.getLine(), equalTo(6));
    assertThat(ctx.getLastLine(), equalTo(6));
  }
}
