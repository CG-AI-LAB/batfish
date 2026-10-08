package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** A filter ({@code vzFilter}): a list of entries, any of which matches. */
public final class AciFilter implements Serializable {

  public AciFilter(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _entries = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/flt-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/flt-%s", _tenant, _name);
  }

  public @Nonnull List<FilterEntry> getEntries() {
    return _entries;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private final @Nonnull List<FilterEntry> _entries;
}
