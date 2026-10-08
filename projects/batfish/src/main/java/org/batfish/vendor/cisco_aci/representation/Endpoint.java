package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;

/** A learned endpoint ({@code fvCEp}) and its IP addresses ({@code fvIp}). */
public final class Endpoint implements Serializable {

  public Endpoint(String mac) {
    _mac = mac;
    _ips = new ArrayList<>();
    _paths = new ArrayList<>();
  }

  public @Nonnull String getMac() {
    return _mac;
  }

  /** The endpoint's IP addresses. */
  public @Nonnull List<Ip> getIps() {
    return _ips;
  }

  /** Where the endpoint was learned ({@code fvRsCEpToPathEp} or {@code fabricPathDn}). */
  public @Nonnull List<PathRef> getPaths() {
    return _paths;
  }

  /** The VLAN the endpoint was learned on ({@code encap}). */
  public @Nullable Integer getEncapVlan() {
    return _encapVlan;
  }

  public void setEncapVlan(@Nullable Integer encapVlan) {
    _encapVlan = encapVlan;
  }

  private final @Nonnull String _mac;
  private final @Nonnull List<Ip> _ips;
  private final @Nonnull List<PathRef> _paths;
  private @Nullable Integer _encapVlan;
}
