package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;

/** A static route on a border leaf ({@code ipRouteP}) and its next hops ({@code ipNexthopP}). */
public final class L3OutStaticRoute implements Serializable {

  /** A static route next hop. */
  public static final class NextHop implements Serializable {

    /** A next-hop address, or {@code null} with {@code type=none} for a null route. */
    public NextHop(@Nullable Ip address, @Nullable Integer preference) {
      _address = address;
      _preference = preference;
    }

    /** {@code nhAddr}; {@code null} means the route is discarded ({@code type=none}). */
    public @Nullable Ip getAddress() {
      return _address;
    }

    /** {@code pref}, or {@code null} for {@code unspecified} (the route's preference applies). */
    public @Nullable Integer getPreference() {
      return _preference;
    }

    private final @Nullable Ip _address;
    private final @Nullable Integer _preference;
  }

  public L3OutStaticRoute(Prefix prefix, int preference) {
    _prefix = prefix;
    _preference = preference;
    _nextHops = new ArrayList<>();
  }

  public @Nonnull Prefix getPrefix() {
    return _prefix;
  }

  /** {@code pref}: the administrative distance. Defaults to 1. */
  public int getPreference() {
    return _preference;
  }

  public @Nonnull List<NextHop> getNextHops() {
    return _nextHops;
  }

  private final @Nonnull Prefix _prefix;
  private final int _preference;
  private final @Nonnull List<NextHop> _nextHops;
}
