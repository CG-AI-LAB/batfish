package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * A contract subject ({@code vzSubj}).
 *
 * <p>Filters attached directly to the subject ({@code vzRsSubjFiltAtt}) apply consumer-to-provider,
 * and also provider-to-consumer with ports reversed when {@code revFltPorts=yes}. Filters under
 * {@code vzInTerm} and {@code vzOutTerm} apply in one direction only.
 */
public final class ContractSubject implements Serializable {

  public ContractSubject(String name) {
    _name = name;
    _reverseFilterPorts = true;
    _filters = new ArrayList<>();
    _consumerToProviderFilters = new ArrayList<>();
    _providerToConsumerFilters = new ArrayList<>();
  }

  public @Nonnull String getName() {
    return _name;
  }

  /** {@code revFltPorts}: also permit the reverse direction with ports swapped. */
  public boolean isReverseFilterPorts() {
    return _reverseFilterPorts;
  }

  public void setReverseFilterPorts(boolean reverseFilterPorts) {
    _reverseFilterPorts = reverseFilterPorts;
  }

  /** Bidirectional filters ({@code vzRsSubjFiltAtt}). */
  public @Nonnull List<FilterRef> getFilters() {
    return _filters;
  }

  /** Filters applied to consumer-to-provider traffic ({@code vzInTerm}). */
  public @Nonnull List<FilterRef> getConsumerToProviderFilters() {
    return _consumerToProviderFilters;
  }

  /** Filters applied to provider-to-consumer traffic ({@code vzOutTerm}). */
  public @Nonnull List<FilterRef> getProviderToConsumerFilters() {
    return _providerToConsumerFilters;
  }

  private final @Nonnull String _name;
  private boolean _reverseFilterPorts;
  private final @Nonnull List<FilterRef> _filters;
  private final @Nonnull List<FilterRef> _consumerToProviderFilters;
  private final @Nonnull List<FilterRef> _providerToConsumerFilters;
}
