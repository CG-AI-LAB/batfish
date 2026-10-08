package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** An L3Out logical interface profile ({@code l3extLIfP}). */
public final class L3OutInterfaceProfile implements Serializable {

  public L3OutInterfaceProfile(String name) {
    _name = name;
    _paths = new ArrayList<>();
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** Whether OSPF runs on the profile's interfaces ({@code ospfIfP} present). */
  public boolean isOspfEnabled() {
    return _ospfEnabled;
  }

  public void setOspfEnabled(boolean ospfEnabled) {
    _ospfEnabled = ospfEnabled;
  }

  public @Nonnull List<L3OutPath> getPaths() {
    return _paths;
  }

  private final @Nonnull String _name;
  private boolean _ospfEnabled;
  private final @Nonnull List<L3OutPath> _paths;
}
