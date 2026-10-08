package org.batfish.common.topology;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import java.util.Optional;
import org.batfish.datamodel.IntegerSpace;
import org.junit.Test;

/** Tests of {@link TrunkTagging}. */
public final class TrunkTaggingTest {

  @Test
  public void testWithoutTranslations() {
    TrunkTagging tagging =
        new TrunkTagging(IntegerSpace.of(Range.closed(4, 5)), 5, ImmutableMap.of());
    assertThat(tagging.receiveTagged(4), equalTo(Optional.of(4)));
    // Tagged native VLAN is rejected
    assertThat(tagging.receiveTagged(5), equalTo(Optional.empty()));
    assertThat(tagging.receiveTagged(6), equalTo(Optional.empty()));
    assertThat(tagging.sendTagged(4), equalTo(Optional.of(4)));
    assertThat(tagging.sendTagged(5), equalTo(Optional.empty()));
    assertThat(tagging.sendTagged(6), equalTo(Optional.empty()));
  }

  @Test
  public void testTranslations() {
    // Tag 100 carries VLAN 10, and tag 10 carries VLAN 3967.
    TrunkTagging tagging =
        new TrunkTagging(
            IntegerSpace.builder().including(10).including(20).including(3967).build(),
            null,
            ImmutableMap.of(100, 10, 10, 3967));
    assertThat(tagging.receiveTagged(100), equalTo(Optional.of(10)));
    assertThat(tagging.receiveTagged(10), equalTo(Optional.of(3967)));
    assertThat(tagging.receiveTagged(20), equalTo(Optional.of(20)));
    // VLAN 3967 is carried only under tag 10
    assertThat(tagging.receiveTagged(3967), equalTo(Optional.empty()));
    assertThat(tagging.sendTagged(10), equalTo(Optional.of(100)));
    assertThat(tagging.sendTagged(3967), equalTo(Optional.of(10)));
    assertThat(tagging.sendTagged(20), equalTo(Optional.of(20)));
    assertThat(tagging.getTranslatedTagsAndVlans(), containsInAnyOrder(10, 100, 3967));
  }

  @Test
  public void testUntranslatedVlanWithTranslatedNumber() {
    // VLAN 100 is allowed, but tag 100 carries VLAN 10, so VLAN 100 is not sent tagged.
    TrunkTagging tagging =
        new TrunkTagging(
            IntegerSpace.builder().including(10).including(100).build(),
            null,
            ImmutableMap.of(100, 10));
    assertThat(tagging.sendTagged(100), equalTo(Optional.empty()));
    assertThat(tagging.receiveTagged(100), equalTo(Optional.of(10)));
  }

  @Test
  public void testSymmetric() {
    TrunkTagging tagging =
        new TrunkTagging(
            IntegerSpace.of(Range.closed(1, 300)),
            1,
            ImmutableMap.of(100, 10, 10, 200, 250, 1, 4000, 300));
    for (int tag = 1; tag <= 4094; tag++) {
      Optional<Integer> vlan = tagging.receiveTagged(tag);
      if (vlan.isPresent()) {
        assertThat("tag " + tag, tagging.sendTagged(vlan.get()), equalTo(Optional.of(tag)));
      }
    }
    for (int vlan = 1; vlan <= 4094; vlan++) {
      Optional<Integer> tag = tagging.sendTagged(vlan);
      if (tag.isPresent()) {
        assertThat("vlan " + vlan, tagging.receiveTagged(tag.get()), equalTo(Optional.of(vlan)));
      }
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsSharedVlan() {
    new TrunkTagging(IntegerSpace.of(10), null, ImmutableMap.of(100, 10, 200, 10));
  }
}
