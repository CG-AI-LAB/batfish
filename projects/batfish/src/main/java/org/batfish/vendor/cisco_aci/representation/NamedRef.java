package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A named relation from one APIC object to another, such as {@code fvRsBd} naming a bridge domain.
 *
 * <p>APIC resolves the name in the source object's tenant first, then in tenant {@code common}. A
 * full-property export also carries the resolved target DN ({@code tDn}), which takes precedence.
 */
public final class NamedRef implements Serializable {

  public NamedRef(String name, @Nullable String targetDn) {
    _name = name;
    _targetDn = targetDn;
  }

  /** The target name; empty means APIC's default object of that class. */
  public @Nonnull String getName() {
    return _name;
  }

  /** The resolved target DN when the export includes it. */
  public @Nullable String getTargetDn() {
    return _targetDn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NamedRef)) {
      return false;
    }
    NamedRef that = (NamedRef) o;
    return _name.equals(that._name) && Objects.equals(_targetDn, that._targetDn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_name, _targetDn);
  }

  @Override
  public String toString() {
    return _targetDn != null ? _targetDn : _name;
  }

  private final @Nonnull String _name;
  private final @Nullable String _targetDn;
}
