package org.batfish.common.topology;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.IntegerSpace;
import org.batfish.datamodel.Interface;

/**
 * How a trunk switchport maps 802.1Q tags on the wire to VLANs on its device, given its allowed
 * VLANs, native VLAN and {@link Interface#getVlanTranslations() VLAN translations}.
 *
 * <p>The mapping is symmetric: a frame received with tag {@code t} belongs to VLAN {@code v} if and
 * only if frames of VLAN {@code v} are sent with tag {@code t}.
 */
@ParametersAreNonnullByDefault
public final class TrunkTagging {

  /** The tagging of trunk switchport {@code iface}. */
  public static @Nonnull TrunkTagging of(Interface iface) {
    return new TrunkTagging(
        iface.getAllowedVlans(), iface.getNativeVlan(), iface.getVlanTranslations());
  }

  /**
   * @param translations tag on the wire to VLAN on the device; no two tags may share a VLAN
   */
  public TrunkTagging(
      IntegerSpace allowedVlans, @Nullable Integer nativeVlan, Map<Integer, Integer> translations) {
    _allowedVlans = allowedVlans;
    _nativeVlan = nativeVlan;
    _vlansByTag = ImmutableMap.copyOf(translations);
    ImmutableMap.Builder<Integer, Integer> tagsByVlan = ImmutableMap.builder();
    translations.forEach((tag, vlan) -> tagsByVlan.put(vlan, tag));
    try {
      _tagsByVlan = tagsByVlan.buildOrThrow();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Two tags translate to the same VLAN: " + translations, e);
    }
  }

  /**
   * Returns the device VLAN of a frame received with tag {@code tag}, or {@link Optional#empty()}
   * if the trunk drops it.
   */
  public @Nonnull Optional<Integer> receiveTagged(int tag) {
    Integer vlan = _vlansByTag.get(tag);
    if (vlan == null) {
      if (_tagsByVlan.containsKey(tag)) {
        // That VLAN is carried only under the tag that translates to it.
        return Optional.empty();
      }
      vlan = tag;
    }
    if (Objects.equals(vlan, _nativeVlan)) {
      // Trunks send the native VLAN untagged, and reject frames tagged with it.
      return Optional.empty();
    }
    return Optional.of(vlan).filter(_allowedVlans::contains);
  }

  /**
   * Returns the tag with which frames of device VLAN {@code vlan} are sent, or {@link
   * Optional#empty()} if they are not sent tagged.
   */
  public @Nonnull Optional<Integer> sendTagged(int vlan) {
    if (!_allowedVlans.contains(vlan) || Objects.equals(vlan, _nativeVlan)) {
      return Optional.empty();
    }
    Integer tag = _tagsByVlan.get(vlan);
    if (tag != null) {
      return Optional.of(tag);
    }
    if (_vlansByTag.containsKey(vlan)) {
      // The tag with this VLAN's number belongs to another VLAN.
      return Optional.empty();
    }
    return Optional.of(vlan);
  }

  /**
   * Tags and VLANs whose tag differs from their VLAN number: the tags translated and the VLANs they
   * translate to. Every other allowed VLAN is carried under its own number.
   */
  public @Nonnull Set<Integer> getTranslatedTagsAndVlans() {
    return ImmutableSet.<Integer>builder()
        .addAll(_vlansByTag.keySet())
        .addAll(_tagsByVlan.keySet())
        .build();
  }

  public boolean hasTranslations() {
    return !_vlansByTag.isEmpty();
  }

  private final @Nonnull IntegerSpace _allowedVlans;
  private final @Nullable Integer _nativeVlan;
  private final @Nonnull Map<Integer, Integer> _vlansByTag;
  private final @Nonnull Map<Integer, Integer> _tagsByVlan;
}
