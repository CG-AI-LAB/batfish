package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Prefix;

/** An L3Out BGP peer ({@code bgpPeerP}). */
public final class BgpPeer implements Serializable {

  public BgpPeer(Prefix address) {
    _address = address;
    _enabled = true;
    _ttl = 1;
    _controls = ImmutableSet.of();
  }

  /** The peer address ({@code addr}); a prefix shorter than /32 is a dynamic neighbor range. */
  public @Nonnull Prefix getAddress() {
    return _address;
  }

  /** The peer's AS ({@code bgpAsP.asn}). */
  public @Nullable Long getRemoteAs() {
    return _remoteAs;
  }

  public void setRemoteAs(@Nullable Long remoteAs) {
    _remoteAs = remoteAs;
  }

  /** The local AS for this peer ({@code bgpLocalAsnP.localAsn}), overriding the fabric AS. */
  public @Nullable Long getLocalAs() {
    return _localAs;
  }

  public void setLocalAs(@Nullable Long localAs) {
    _localAs = localAs;
  }

  /** {@code adminSt=enabled}. */
  public boolean isEnabled() {
    return _enabled;
  }

  public void setEnabled(boolean enabled) {
    _enabled = enabled;
  }

  /** {@code ttl}: eBGP multihop TTL. */
  public int getTtl() {
    return _ttl;
  }

  public void setTtl(int ttl) {
    _ttl = ttl;
  }

  /** {@code ctrl} flags, such as {@code allow-self-as}, {@code as-override}, {@code send-com}. */
  public @Nonnull Set<String> getControls() {
    return _controls;
  }

  public void setControls(Set<String> controls) {
    _controls = ImmutableSet.copyOf(controls);
  }

  private final @Nonnull Prefix _address;
  private @Nullable Long _remoteAs;
  private @Nullable Long _localAs;
  private boolean _enabled;
  private int _ttl;
  private @Nonnull Set<String> _controls;
}
