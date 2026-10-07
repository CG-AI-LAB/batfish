package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;

/** A tenant ({@code fvTenant}) and the objects it contains. */
public final class Tenant implements Serializable {

  public static final String COMMON = "common";

  public Tenant(String name) {
    _name = name;
    _vrfs = new TreeMap<>();
    _bridgeDomains = new TreeMap<>();
    _epgs = new TreeMap<>();
    _esgs = new TreeMap<>();
    _contracts = new TreeMap<>();
    _contractInterfaces = new TreeMap<>();
    _filters = new TreeMap<>();
    _taboos = new TreeMap<>();
    _l3Outs = new TreeMap<>();
  }

  public @Nonnull String getName() {
    return _name;
  }

  public @Nonnull Map<String, AciVrf> getVrfs() {
    return _vrfs;
  }

  public @Nonnull Map<String, BridgeDomain> getBridgeDomains() {
    return _bridgeDomains;
  }

  /** EPGs keyed by DN. */
  public @Nonnull Map<String, Epg> getEpgs() {
    return _epgs;
  }

  /** ESGs keyed by DN. */
  public @Nonnull Map<String, Esg> getEsgs() {
    return _esgs;
  }

  public @Nonnull Map<String, Contract> getContracts() {
    return _contracts;
  }

  /**
   * Imported contract interfaces ({@code vzCPIf}) by name, mapped to the DN of the exported
   * contract ({@code vzRsIf.tDn}).
   */
  public @Nonnull Map<String, String> getContractInterfaces() {
    return _contractInterfaces;
  }

  public @Nonnull Map<String, AciFilter> getFilters() {
    return _filters;
  }

  public @Nonnull Map<String, TabooContract> getTaboos() {
    return _taboos;
  }

  public @Nonnull Map<String, L3Out> getL3Outs() {
    return _l3Outs;
  }

  private final @Nonnull String _name;
  private final @Nonnull Map<String, AciVrf> _vrfs;
  private final @Nonnull Map<String, BridgeDomain> _bridgeDomains;
  private final @Nonnull Map<String, Epg> _epgs;
  private final @Nonnull Map<String, Esg> _esgs;
  private final @Nonnull Map<String, Contract> _contracts;
  private final @Nonnull Map<String, String> _contractInterfaces;
  private final @Nonnull Map<String, AciFilter> _filters;
  private final @Nonnull Map<String, TabooContract> _taboos;
  private final @Nonnull Map<String, L3Out> _l3Outs;
}
