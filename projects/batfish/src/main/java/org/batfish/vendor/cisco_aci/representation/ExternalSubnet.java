package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.Set;
import javax.annotation.Nonnull;
import org.batfish.datamodel.Prefix;

/** An external EPG subnet ({@code l3extSubnet}). */
public final class ExternalSubnet implements Serializable {

  public static final String EXPORT_RTCTRL = "export-rtctrl";
  public static final String IMPORT_RTCTRL = "import-rtctrl";
  public static final String IMPORT_SECURITY = "import-security";
  public static final String SHARED_RTCTRL = "shared-rtctrl";
  public static final String SHARED_SECURITY = "shared-security";

  public ExternalSubnet(Prefix prefix, Set<String> scope, Set<String> aggregate) {
    _prefix = prefix;
    _scope = ImmutableSet.copyOf(scope);
    _aggregate = ImmutableSet.copyOf(aggregate);
  }

  public @Nonnull Prefix getPrefix() {
    return _prefix;
  }

  /** {@code scope} flags: {@code import-security} (the default), route-control flags, etc. */
  public @Nonnull Set<String> getScope() {
    return _scope;
  }

  /**
   * {@code aggregate} flags: a route-control scope listed here matches the prefix and anything
   * longer, not just the exact prefix.
   */
  public @Nonnull Set<String> getAggregate() {
    return _aggregate;
  }

  private final @Nonnull Prefix _prefix;
  private final @Nonnull Set<String> _scope;
  private final @Nonnull Set<String> _aggregate;
}
