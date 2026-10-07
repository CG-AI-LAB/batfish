package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.VendorConversionException;
import org.batfish.common.topology.Layer1Edge;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.Prefix;
import org.batfish.vendor.VendorConfiguration;

/**
 * One Cisco ACI fabric, as exported from its APIC. Converts into one {@link Configuration} per leaf
 * and spine.
 */
public final class AciConfiguration extends VendorConfiguration {

  public AciConfiguration(String fabricName) {
    _fabricName = fabricName;
    _nodes = new TreeMap<>();
    _vpcPairs = new ArrayList<>();
    _routeReflectorNodeIds = new HashSet<>();
    _tenants = new TreeMap<>();
    _accessPolicies = new AccessPolicies();
    _lldpAdjacencies = new ArrayList<>();
    _portChannelIds = new HashMap<>();
  }

  /** The fabric's folder name under {@code aci_configs/}. */
  public @Nonnull String getFabricName() {
    return _fabricName;
  }

  /** Fabric nodes by node ID. */
  public @Nonnull SortedMap<Integer, FabricNode> getNodes() {
    return _nodes;
  }

  public @Nonnull List<VpcPair> getVpcPairs() {
    return _vpcPairs;
  }

  /** The fabric BGP AS ({@code uni/fabric/bgpInstP-default/as}). */
  public @Nullable Long getFabricAsn() {
    return _fabricAsn;
  }

  public void setFabricAsn(@Nullable Long fabricAsn) {
    _fabricAsn = fabricAsn;
  }

  /** Spine route reflectors ({@code bgpRRNodePEp}). */
  public @Nonnull Set<Integer> getRouteReflectorNodeIds() {
    return _routeReflectorNodeIds;
  }

  /** The TEP pool ({@code fabricSetupP.tepPool}). */
  public @Nullable Prefix getTepPool() {
    return _tepPool;
  }

  public void setTepPool(@Nullable Prefix tepPool) {
    _tepPool = tepPool;
  }

  public @Nonnull SortedMap<String, Tenant> getTenants() {
    return _tenants;
  }

  public @Nonnull AccessPolicies getAccessPolicies() {
    return _accessPolicies;
  }

  public @Nonnull List<LldpAdjacency> getLldpAdjacencies() {
    return _lldpAdjacencies;
  }

  /**
   * Port-channel interface IDs from {@code pcAggrIf}: node ID, then interface policy group name,
   * then interface ID such as {@code po1}.
   */
  public @Nonnull Map<Integer, Map<String, String>> getPortChannelIds() {
    return _portChannelIds;
  }

  @Override
  public String getHostname() {
    return _fabricName;
  }

  @Override
  public void setHostname(String hostname) {
    throw new IllegalStateException("Setting the hostname is not allowed for ACI fabrics");
  }

  @Override
  public void setVendor(ConfigurationFormat format) {
    throw new IllegalStateException("Setting the format is not allowed for ACI fabrics");
  }

  @Override
  public @Nonnull List<Configuration> toVendorIndependentConfigurations()
      throws VendorConversionException {
    return ImmutableList.copyOf(convert().getConfigurations().values());
  }

  @Override
  public @Nonnull Set<Layer1Edge> getLayer1Edges() {
    return convert().getLayer1Edges();
  }

  private @Nonnull AciConversion.Result convert() {
    if (_converted == null) {
      _converted = new AciConversion(this, getWarnings()).convert();
    }
    return _converted;
  }

  private final @Nonnull String _fabricName;
  private final @Nonnull SortedMap<Integer, FabricNode> _nodes;
  private final @Nonnull List<VpcPair> _vpcPairs;
  private @Nullable Long _fabricAsn;
  private final @Nonnull Set<Integer> _routeReflectorNodeIds;
  private @Nullable Prefix _tepPool;
  private final @Nonnull SortedMap<String, Tenant> _tenants;
  private final @Nonnull AccessPolicies _accessPolicies;
  private final @Nonnull List<LldpAdjacency> _lldpAdjacencies;
  private final @Nonnull Map<Integer, Map<String, String>> _portChannelIds;

  /** Conversion output; never serialized. */
  private transient @Nullable AciConversion.Result _converted;
}
