package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * The contract relations of an endpoint group of any kind: provided ({@code fvRsProv}), consumed
 * ({@code fvRsCons}), consumed through an imported contract interface ({@code fvRsConsIf}), and
 * taboo ({@code fvRsProtBy}).
 */
public final class ContractRelations implements Serializable {

  public ContractRelations() {
    _provided = new ArrayList<>();
    _consumed = new ArrayList<>();
    _consumedInterfaces = new ArrayList<>();
    _taboos = new ArrayList<>();
  }

  public @Nonnull List<NamedRef> getProvided() {
    return _provided;
  }

  public @Nonnull List<NamedRef> getConsumed() {
    return _consumed;
  }

  public @Nonnull List<NamedRef> getConsumedInterfaces() {
    return _consumedInterfaces;
  }

  public @Nonnull List<NamedRef> getTaboos() {
    return _taboos;
  }

  private final @Nonnull List<NamedRef> _provided;
  private final @Nonnull List<NamedRef> _consumed;
  private final @Nonnull List<NamedRef> _consumedInterfaces;
  private final @Nonnull List<NamedRef> _taboos;
}
