package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A contract ({@code vzBrCP}). */
public final class Contract implements Serializable {

  /** Which provider/consumer pairs the contract connects ({@code scope}). */
  public enum Scope {
    APPLICATION_PROFILE,
    CONTEXT,
    TENANT,
    GLOBAL;

    static @Nonnull Scope fromString(@Nullable String scope) {
      if (scope == null) {
        return CONTEXT;
      }
      return switch (scope) {
        case "application-profile" -> APPLICATION_PROFILE;
        case "tenant" -> TENANT;
        case "global" -> GLOBAL;
        default -> CONTEXT;
      };
    }
  }

  public Contract(String tenant, String name) {
    _tenant = tenant;
    _name = name;
    _scope = Scope.CONTEXT;
    _subjects = new ArrayList<>();
  }

  public @Nonnull String getTenant() {
    return _tenant;
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code uni/tn-<tenant>/brc-<name>}. */
  public @Nonnull String getDn() {
    return String.format("uni/tn-%s/brc-%s", _tenant, _name);
  }

  public @Nonnull Scope getScope() {
    return _scope;
  }

  public void setScope(Scope scope) {
    _scope = scope;
  }

  public @Nonnull List<ContractSubject> getSubjects() {
    return _subjects;
  }

  private final @Nonnull String _tenant;
  private final @Nonnull String _name;
  private @Nonnull Scope _scope;
  private final @Nonnull List<ContractSubject> _subjects;
}
