package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A static EPG binding to a port, port-channel or vPC ({@code fvRsPathAtt}). */
public final class StaticPath implements Serializable {

  /** How the encapsulation is carried on the port ({@code mode}). */
  public enum Mode {
    /** Tagged with the encap VLAN ({@code regular}, the default). */
    TAGGED,
    /** Untagged frames belong to the EPG; the port may carry other EPGs tagged ({@code native}). */
    NATIVE,
    /** Untagged access port ({@code untagged}). */
    UNTAGGED;

    static @Nonnull Mode fromString(@Nullable String mode) {
      if (mode == null) {
        return TAGGED;
      }
      return switch (mode) {
        case "native" -> NATIVE;
        case "untagged" -> UNTAGGED;
        default -> TAGGED;
      };
    }
  }

  public StaticPath(PathRef path, @Nullable Integer encapVlan, Mode mode) {
    _path = path;
    _encapVlan = encapVlan;
    _mode = mode;
  }

  public @Nonnull PathRef getPath() {
    return _path;
  }

  /** The encap VLAN ({@code encap=vlan-N}), or {@code null} if absent or not a VLAN. */
  public @Nullable Integer getEncapVlan() {
    return _encapVlan;
  }

  public @Nonnull Mode getMode() {
    return _mode;
  }

  private final @Nonnull PathRef _path;
  private final @Nullable Integer _encapVlan;
  private final @Nonnull Mode _mode;
}
