package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fabric access policies ({@code uni/infra}): which leaf ports exist with which interface policy
 * group, and which EPGs attachable entity profiles carry.
 */
public final class AccessPolicies implements Serializable {

  /** A leaf profile ({@code infraNodeP}): leaf selectors and the interface profiles they use. */
  public static final class LeafProfile implements Serializable {
    public LeafProfile(String name) {
      _name = name;
      _nodeRanges = new ArrayList<>();
      _interfaceProfileDns = new ArrayList<>();
    }

    public @Nonnull String getName() {
      return _name;
    }

    /** Whether a selector of type {@code ALL} selects every leaf. */
    public boolean isAllNodes() {
      return _allNodes;
    }

    public void setAllNodes(boolean allNodes) {
      _allNodes = allNodes;
    }

    /** Inclusive node-ID ranges from {@code infraNodeBlk} ({@code from_}, {@code to_}). */
    public @Nonnull List<int[]> getNodeRanges() {
      return _nodeRanges;
    }

    /** Interface profiles applied to the selected leaves ({@code infraRsAccPortP}). */
    public @Nonnull List<String> getInterfaceProfileDns() {
      return _interfaceProfileDns;
    }

    /** Whether the profile selects {@code nodeId}. */
    public boolean selects(int nodeId) {
      return _allNodes || _nodeRanges.stream().anyMatch(r -> r[0] <= nodeId && nodeId <= r[1]);
    }

    private final @Nonnull String _name;
    private boolean _allNodes;
    private final @Nonnull List<int[]> _nodeRanges;
    private final @Nonnull List<String> _interfaceProfileDns;
  }

  /** An access port selector ({@code infraHPortS}) and its port blocks. */
  public static final class PortSelector implements Serializable {
    public PortSelector(String name, @Nullable String policyGroupDn) {
      _name = name;
      _policyGroupDn = policyGroupDn;
      _ports = new ArrayList<>();
    }

    public @Nonnull String getName() {
      return _name;
    }

    /** The interface policy group ({@code infraRsAccBaseGrp}). */
    public @Nullable String getPolicyGroupDn() {
      return _policyGroupDn;
    }

    /** The selected ports, expanded from {@code infraPortBlk}, as {@code eth<card>/<port>}. */
    public @Nonnull List<String> getPorts() {
      return _ports;
    }

    private final @Nonnull String _name;
    private final @Nullable String _policyGroupDn;
    private final @Nonnull List<String> _ports;
  }

  /** How an interface policy group bundles ports ({@code infraAccBndlGrp.lagT}). */
  public enum BundleType {
    /** {@code infraAccPortGrp}: individual ports. */
    NONE,
    /** {@code lagT=link}: a port-channel on one leaf. */
    PORT_CHANNEL,
    /** {@code lagT=node}: a vPC across a leaf pair. */
    VPC
  }

  /** An interface policy group ({@code infraAccPortGrp} or {@code infraAccBndlGrp}). */
  public static final class PolicyGroup implements Serializable {
    public PolicyGroup(String name, BundleType bundleType, @Nullable String aaepDn) {
      _name = name;
      _bundleType = bundleType;
      _aaepDn = aaepDn;
    }

    public @Nonnull String getName() {
      return _name;
    }

    public @Nonnull BundleType getBundleType() {
      return _bundleType;
    }

    /** The attachable entity profile ({@code infraRsAttEntP}). */
    public @Nullable String getAaepDn() {
      return _aaepDn;
    }

    private final @Nonnull String _name;
    private final @Nonnull BundleType _bundleType;
    private final @Nullable String _aaepDn;
  }

  /** An EPG deployed on every port of an attachable entity profile ({@code infraRsFuncToEpg}). */
  public static final class AaepEpgBinding implements Serializable {
    public AaepEpgBinding(String epgDn, @Nullable Integer encapVlan, StaticPath.Mode mode) {
      _epgDn = epgDn;
      _encapVlan = encapVlan;
      _mode = mode;
    }

    public @Nonnull String getEpgDn() {
      return _epgDn;
    }

    public @Nullable Integer getEncapVlan() {
      return _encapVlan;
    }

    public @Nonnull StaticPath.Mode getMode() {
      return _mode;
    }

    private final @Nonnull String _epgDn;
    private final @Nullable Integer _encapVlan;
    private final @Nonnull StaticPath.Mode _mode;
  }

  public AccessPolicies() {
    _leafProfiles = new ArrayList<>();
    _interfaceProfiles = new TreeMap<>();
    _policyGroups = new TreeMap<>();
    _aaepBindings = new TreeMap<>();
  }

  public @Nonnull List<LeafProfile> getLeafProfiles() {
    return _leafProfiles;
  }

  /** Interface profiles ({@code infraAccPortP}) by DN: their port selectors. */
  public @Nonnull Map<String, List<PortSelector>> getInterfaceProfiles() {
    return _interfaceProfiles;
  }

  /** Interface policy groups by DN. */
  public @Nonnull Map<String, PolicyGroup> getPolicyGroups() {
    return _policyGroups;
  }

  /** EPG bindings of attachable entity profiles ({@code infraAttEntityP}), by AAEP DN. */
  public @Nonnull Map<String, List<AaepEpgBinding>> getAaepBindings() {
    return _aaepBindings;
  }

  /** Returns the port selectors that apply to {@code nodeId}. */
  public @Nonnull List<PortSelector> portSelectorsFor(int nodeId) {
    ImmutableList.Builder<PortSelector> selectors = ImmutableList.builder();
    for (LeafProfile profile : _leafProfiles) {
      if (!profile.selects(nodeId)) {
        continue;
      }
      for (String interfaceProfileDn : profile.getInterfaceProfileDns()) {
        selectors.addAll(_interfaceProfiles.getOrDefault(interfaceProfileDn, ImmutableList.of()));
      }
    }
    return selectors.build();
  }

  private final @Nonnull List<LeafProfile> _leafProfiles;
  private final @Nonnull Map<String, List<PortSelector>> _interfaceProfiles;
  private final @Nonnull Map<String, PolicyGroup> _policyGroups;
  private final @Nonnull Map<String, List<AaepEpgBinding>> _aaepBindings;
}
