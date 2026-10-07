package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** A taboo contract ({@code vzTaboo}): traffic matching its deny filters is always dropped. */
public final class TabooContract implements Serializable {

  public TabooContract(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _denyFilters = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/taboo-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/taboo-%s", _tenant, _name);
  }

  /** Deny filters of all taboo subjects ({@code vzRsDenyRule}). */
  public @Nonnull List<NamedRef> getDenyFilters() {
    return _denyFilters;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private final @Nonnull List<NamedRef> _denyFilters;
}
