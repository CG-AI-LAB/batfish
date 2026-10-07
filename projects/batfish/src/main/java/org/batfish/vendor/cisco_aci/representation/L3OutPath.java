package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.ConcreteInterfaceAddress;

/** An L3Out interface on a path ({@code l3extRsPathL3OutAtt}). */
public final class L3OutPath implements Serializable {

  /** The kind of interface ({@code ifInstT}). */
  public enum Type {
    /** {@code l3-port}: a routed port. */
    ROUTED,
    /** {@code sub-interface}: a routed 802.1Q subinterface. */
    SUB_INTERFACE,
    /** {@code ext-svi}: an SVI on a switched port, port-channel or vPC. */
    SVI;

    static @Nullable Type fromString(@Nullable String type) {
      if (type == null) {
        return null;
      }
      return switch (type) {
        case "l3-port" -> ROUTED;
        case "sub-interface" -> SUB_INTERFACE;
        case "ext-svi" -> SVI;
        default -> null;
      };
    }
  }

  public L3OutPath(PathRef path, Type type) {
    _path = path;
    _type = type;
    _secondaryAddresses = new ArrayList<>();
    _memberAddresses = new HashMap<>();
    _bgpPeers = new ArrayList<>();
  }

  public @Nonnull PathRef getPath() {
    return _path;
  }

  public @Nonnull Type getType() {
    return _type;
  }

  /** The interface address ({@code addr}); unset on vPC SVIs, which use member addresses. */
  public @Nullable ConcreteInterfaceAddress getAddress() {
    return _address;
  }

  public void setAddress(@Nullable ConcreteInterfaceAddress address) {
    _address = address;
  }

  /** Secondary and floating addresses ({@code l3extIp}). */
  public @Nonnull List<ConcreteInterfaceAddress> getSecondaryAddresses() {
    return _secondaryAddresses;
  }

  /**
   * Per-side addresses of a vPC SVI ({@code l3extMember}), keyed by side {@code A} or {@code B}.
   */
  public @Nonnull Map<String, ConcreteInterfaceAddress> getMemberAddresses() {
    return _memberAddresses;
  }

  /** The encap VLAN for subinterfaces and SVIs ({@code encap}). */
  public @Nullable Integer getEncapVlan() {
    return _encapVlan;
  }

  public void setEncapVlan(@Nullable Integer encapVlan) {
    _encapVlan = encapVlan;
  }

  /** {@code mtu}, or {@code null} for {@code inherit}. */
  public @Nullable Integer getMtu() {
    return _mtu;
  }

  public void setMtu(@Nullable Integer mtu) {
    _mtu = mtu;
  }

  /** BGP peers reached over this interface ({@code bgpPeerP} under the path). */
  public @Nonnull List<BgpPeer> getBgpPeers() {
    return _bgpPeers;
  }

  private final @Nonnull PathRef _path;
  private final @Nonnull Type _type;
  private @Nullable ConcreteInterfaceAddress _address;
  private final @Nonnull List<ConcreteInterfaceAddress> _secondaryAddresses;
  private final @Nonnull Map<String, ConcreteInterfaceAddress> _memberAddresses;
  private @Nullable Integer _encapVlan;
  private @Nullable Integer _mtu;
  private final @Nonnull List<BgpPeer> _bgpPeers;
}
