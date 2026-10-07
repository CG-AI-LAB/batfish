package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A filter entry ({@code vzEntry}). Unset fields ({@code unspecified} in APIC) match anything. */
public final class FilterEntry implements Serializable {

  /** The Ethernet type the entry matches ({@code etherT}). */
  public enum EtherType {
    /** {@code unspecified}: any frame. */
    ANY,
    /** {@code ip}: IPv4 or IPv6. */
    IP,
    IPV4,
    IPV6,
    ARP,
    /** Any other non-IP Ethernet type; never matches an IP packet. */
    OTHER;

    static @Nonnull EtherType fromString(@Nullable String etherType) {
      if (etherType == null) {
        return ANY;
      }
      return switch (etherType) {
        case "unspecified", "0" -> ANY;
        case "ip", "0xABCD" -> IP;
        case "ipv4", "0x0800", "0x800" -> IPV4;
        case "ipv6", "0x86DD" -> IPV6;
        case "arp", "0x806", "0x0806" -> ARP;
        default -> OTHER;
      };
    }
  }

  public FilterEntry(String name) {
    _name = name;
    _etherType = EtherType.ANY;
    _tcpRules = ImmutableSet.of();
  }

  public @Nonnull String getName() {
    return _name;
  }

  public @Nonnull EtherType getEtherType() {
    return _etherType;
  }

  public void setEtherType(EtherType etherType) {
    _etherType = etherType;
  }

  /** The IP protocol number ({@code prot}), or {@code null} for any. */
  public @Nullable Integer getIpProtocol() {
    return _ipProtocol;
  }

  public void setIpProtocol(@Nullable Integer ipProtocol) {
    _ipProtocol = ipProtocol;
  }

  /** Source port range start ({@code sFromPort}), or {@code null} for any. */
  public @Nullable Integer getSrcFromPort() {
    return _srcFromPort;
  }

  public void setSrcFromPort(@Nullable Integer srcFromPort) {
    _srcFromPort = srcFromPort;
  }

  /** Source port range end ({@code sToPort}), or {@code null} for any. */
  public @Nullable Integer getSrcToPort() {
    return _srcToPort;
  }

  public void setSrcToPort(@Nullable Integer srcToPort) {
    _srcToPort = srcToPort;
  }

  /** Destination port range start ({@code dFromPort}), or {@code null} for any. */
  public @Nullable Integer getDstFromPort() {
    return _dstFromPort;
  }

  public void setDstFromPort(@Nullable Integer dstFromPort) {
    _dstFromPort = dstFromPort;
  }

  /** Destination port range end ({@code dToPort}), or {@code null} for any. */
  public @Nullable Integer getDstToPort() {
    return _dstToPort;
  }

  public void setDstToPort(@Nullable Integer dstToPort) {
    _dstToPort = dstToPort;
  }

  /** The ICMPv4 type ({@code icmpv4T}), or {@code null} for any. */
  public @Nullable Integer getIcmpType() {
    return _icmpType;
  }

  public void setIcmpType(@Nullable Integer icmpType) {
    _icmpType = icmpType;
  }

  /** TCP flag rules ({@code tcpRules}): any of {@code syn, ack, fin, rst, est}. */
  public @Nonnull Set<String> getTcpRules() {
    return _tcpRules;
  }

  public void setTcpRules(Set<String> tcpRules) {
    _tcpRules = ImmutableSet.copyOf(tcpRules);
  }

  /** {@code stateful}: provider-to-consumer TCP traffic must have the ACK flag set. */
  public boolean isStateful() {
    return _stateful;
  }

  public void setStateful(boolean stateful) {
    _stateful = stateful;
  }

  private final @Nonnull String _name;
  private @Nonnull EtherType _etherType;
  private @Nullable Integer _ipProtocol;
  private @Nullable Integer _srcFromPort;
  private @Nullable Integer _srcToPort;
  private @Nullable Integer _dstFromPort;
  private @Nullable Integer _dstToPort;
  private @Nullable Integer _icmpType;
  private @Nonnull Set<String> _tcpRules;
  private boolean _stateful;
}
