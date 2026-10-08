package org.batfish.vendor.cisco_aci.representation;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.batfish.datamodel.bgp.LocalOriginationTypeTieBreaker.PREFER_NETWORK;
import static org.batfish.datamodel.bgp.NextHopIpTieBreaker.HIGHEST_NEXT_HOP_IP;
import static org.batfish.datamodel.bgp.NextHopIpTieBreaker.LOWEST_NEXT_HOP_IP;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.Warnings;
import org.batfish.common.topology.Layer1Edge;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpPassivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Bgpv4ToEvpnVrfLeakConfig;
import org.batfish.datamodel.BumTransportMethod;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.DeviceModel;
import org.batfish.datamodel.EvpnToBgpv4VrfLeakConfig;
import org.batfish.datamodel.IntegerSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Interface.Dependency;
import org.batfish.datamodel.Interface.DependencyType;
import org.batfish.datamodel.InterfaceType;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IsoAddress;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.LongSpace;
import org.batfish.datamodel.MultipathEquivalentAsPathMatchMode;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.PrefixSpace;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.SwitchportMode;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.VrfLeakConfig;
import org.batfish.datamodel.bgp.AddressFamilyCapabilities;
import org.batfish.datamodel.bgp.EvpnAddressFamily;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.bgp.Layer3VniConfig;
import org.batfish.datamodel.bgp.RouteDistinguisher;
import org.batfish.datamodel.bgp.community.ExtendedCommunity;
import org.batfish.datamodel.isis.IsisInterfaceLevelSettings;
import org.batfish.datamodel.isis.IsisInterfaceMode;
import org.batfish.datamodel.isis.IsisInterfaceSettings;
import org.batfish.datamodel.isis.IsisLevelSettings;
import org.batfish.datamodel.isis.IsisProcess;
import org.batfish.datamodel.ospf.NssaSettings;
import org.batfish.datamodel.ospf.OspfArea;
import org.batfish.datamodel.ospf.OspfInterfaceSettings;
import org.batfish.datamodel.ospf.OspfNetworkType;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.ospf.StubSettings;
import org.batfish.datamodel.route.nh.NextHopDiscard;
import org.batfish.datamodel.route.nh.NextHopInterface;
import org.batfish.datamodel.route.nh.NextHopIp;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.communities.CommunityIs;
import org.batfish.datamodel.routing_policy.communities.HasCommunity;
import org.batfish.datamodel.routing_policy.communities.InputCommunities;
import org.batfish.datamodel.routing_policy.communities.MatchCommunities;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.DestinationNetwork;
import org.batfish.datamodel.routing_policy.expr.ExplicitPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.vxlan.Layer2Vni;
import org.batfish.datamodel.vxlan.Layer3Vni;
import org.batfish.datamodel.vxlan.Vni;

/**
 * Converts an {@link AciConfiguration} into one {@link Configuration} per leaf and spine.
 *
 * <p>Each node gets:
 *
 * <ul>
 *   <li>VRF {@value #UNDERLAY_VRF_NAME}: the TEP loopback, fabric links running IS-IS, and the
 *       fabric BGP process carrying EVPN to the spine route reflectors. ACI distributes routes with
 *       MP-BGP VPNv4 and COOP; EVPN type-5 routes are the vendor-independent equivalent.
 *   <li>One VRF per deployed tenant VRF, named {@code <tenant>:<vrf>} as on the switch, with an L3
 *       VNI, bridge-domain SVIs (anycast gateways), L3Out interfaces and routing.
 *   <li>VRF {@code default}, holding the switched front-panel ports.
 * </ul>
 */
final class AciConversion {

  /** The ACI infrastructure VRF. */
  static final String UNDERLAY_VRF_NAME = "overlay-1";

  /** Synthetic addresses for fabric links, which ACI runs unnumbered. Only used in overlay-1. */
  @VisibleForTesting static final Prefix FABRIC_LINK_POOL = Prefix.parse("100.127.0.0/16");

  /** Used when the fabric BGP AS is not in the export. */
  @VisibleForTesting static final long DEFAULT_FABRIC_ASN = 65001L;

  private static final int DEFAULT_EBGP_ADMIN = 20;
  private static final int DEFAULT_IBGP_ADMIN = 200;
  private static final int DEFAULT_LOCAL_BGP_ADMIN = 220;
  private static final int DEFAULT_MTU = 9000;
  private static final double FABRIC_LINK_BANDWIDTH = 100E9;
  private static final double ACCESS_PORT_BANDWIDTH = 10E9;
  private static final long ISIS_COST = 1L;
  private static final String OSPF_PROCESS_ID = "default";

  /** NX-OS default OSPF reference bandwidth: 40 Gbps. */
  private static final double OSPF_REFERENCE_BANDWIDTH = 40E9;

  /** Internal BD VLANs come from here downward when a BD has no encap on a leaf. */
  private static final int FIRST_INTERNAL_VLAN = 3967;

  /** Conversion output. */
  static final class Result {
    Result(Map<String, Configuration> configurations, Set<Layer1Edge> layer1Edges) {
      _configurations = ImmutableMap.copyOf(configurations);
      _layer1Edges = ImmutableSet.copyOf(layer1Edges);
    }

    @Nonnull
    Map<String, Configuration> getConfigurations() {
      return _configurations;
    }

    @Nonnull
    Set<Layer1Edge> getLayer1Edges() {
      return _layer1Edges;
    }

    private final @Nonnull Map<String, Configuration> _configurations;
    private final @Nonnull Set<Layer1Edge> _layer1Edges;
  }

  /** An EPG deployed on an interface with an encap. */
  private static final class EpgBinding {
    private EpgBinding(Epg epg, BridgeDomain bd, int encap, StaticPath.Mode mode) {
      _epg = epg;
      _bd = bd;
      _encap = encap;
      _mode = mode;
    }

    private final Epg _epg;
    private final BridgeDomain _bd;
    private final int _encap;
    private final StaticPath.Mode _mode;
  }

  /** A BGP peer of an L3Out, created once the node's tenant BGP process exists. */
  private static final class PendingBgpPeer {
    private PendingBgpPeer(L3Out l3Out, BgpPeer peer, @Nullable Ip localIp) {
      _l3Out = l3Out;
      _peer = peer;
      _localIp = localIp;
    }

    private final L3Out _l3Out;
    private final BgpPeer _peer;
    private final @Nullable Ip _localIp;
  }

  /** Per-node conversion state. */
  private static final class NodeState {
    private NodeState(FabricNode node, String hostname, Configuration c, Ip tep) {
      _node = node;
      _hostname = hostname;
      _c = c;
      _tep = tep;
      _tenantVrfs = new LinkedHashMap<>();
      _l3OutRouterIds = new HashMap<>();
      _bindings = new TreeMap<>();
      _sviEncaps = new TreeMap<>();
      _l3Interfaces = new HashSet<>();
      _deployedBds = new LinkedHashSet<>();
      _bdVlans = new LinkedHashMap<>();
      _usedVlans = new HashSet<>();
      _pendingPeers = new LinkedHashMap<>();
      _ospfInterfaces = new LinkedHashMap<>();
    }

    private boolean isLeaf() {
      return _node.getRole() == FabricNode.Role.LEAF;
    }

    private boolean isSpine() {
      return _node.getRole() == FabricNode.Role.SPINE;
    }

    private final FabricNode _node;
    private final String _hostname;
    private final Configuration _c;
    private final Ip _tep;
    private final Map<AciVrf, Vrf> _tenantVrfs;
    private final Map<AciVrf, Ip> _l3OutRouterIds;

    /** Interface name to EPG bindings. */
    private final Map<String, List<EpgBinding>> _bindings;

    /** Interface name to L3Out SVI encaps. */
    private final Map<String, Set<Integer>> _sviEncaps;

    /** Interfaces used as routed ports or subinterface parents. */
    private final Set<String> _l3Interfaces;

    private final Set<BridgeDomain> _deployedBds;
    private final Map<BridgeDomain, Integer> _bdVlans;
    private final Set<Integer> _usedVlans;
    private final Map<AciVrf, List<PendingBgpPeer>> _pendingPeers;

    /** Per tenant VRF: L3Out to its OSPF interfaces on this node. */
    private final Map<AciVrf, Map<L3Out, List<String>>> _ospfInterfaces;

    private int _nextLoopback = 1;
  }

  AciConversion(AciConfiguration aci, @Nullable Warnings w) {
    _aci = aci;
    _w = w == null ? new Warnings() : w;
    _nodes = new TreeMap<>();
    _layer1Edges = new HashSet<>();
    _bdVrfs = new HashMap<>();
    _epgBds = new HashMap<>();
    _vrfVnis = new HashMap<>();
    _vrfIndexes = new HashMap<>();
    _bdVnis = new HashMap<>();
    _bdGroups = new HashMap<>();
    _epgsByDn = new HashMap<>();
  }

  @Nonnull
  Result convert() {
    initNodes();
    resolveTenantObjects();
    createAccessPorts();
    deployEpgs();
    createL3Outs();
    createBridgeDomains();
    createSwitchports();
    createTenantRouting();
    createUnderlay();
    createFabricBgp();
    new AciContracts(_aci, _w, this).apply();
    Map<String, Configuration> configs = new TreeMap<>();
    _nodes.values().forEach(n -> configs.put(n._hostname, n._c));
    return new Result(configs, _layer1Edges);
  }

  // ---------------------------------------------------------------------------------------------
  // Accessors for AciContracts

  /** The VI VRFs of {@code vrf}, by hostname, on every node where it is deployed. */
  @Nonnull
  Map<Configuration, Vrf> deployedVrfs(AciVrf vrf) {
    Map<Configuration, Vrf> deployed = new LinkedHashMap<>();
    for (NodeState n : _nodes.values()) {
      Vrf viVrf = n._tenantVrfs.get(vrf);
      if (viVrf != null) {
        deployed.put(n._c, viVrf);
      }
    }
    return deployed;
  }

  /** The VRF of a bridge domain, if resolved. */
  @Nullable
  AciVrf vrfOf(BridgeDomain bd) {
    return _bdVrfs.get(bd);
  }

  /** The bridge domain of an EPG, if resolved. */
  @Nullable
  BridgeDomain bridgeDomainOf(Epg epg) {
    return _epgBds.get(epg);
  }

  /** The EPG with the given DN. */
  @Nullable
  Epg epgByDn(String dn) {
    return _epgsByDn.get(dn);
  }

  /** Resolves a relation to a VRF. */
  @Nullable
  AciVrf resolveVrf(String tenant, @Nullable NamedRef ref) {
    return resolve(tenant, ref, "ctx-", Tenant::getVrfs);
  }

  /** Resolves a relation to a contract. */
  @Nullable
  Contract resolveContract(String tenant, NamedRef ref) {
    return resolve(tenant, ref, "brc-", Tenant::getContracts);
  }

  /** Resolves a relation to a filter. */
  @Nullable
  AciFilter resolveFilter(String tenant, NamedRef ref) {
    return resolve(tenant, ref, "flt-", Tenant::getFilters);
  }

  /** Resolves a relation to a taboo contract. */
  @Nullable
  TabooContract resolveTaboo(String tenant, NamedRef ref) {
    return resolve(tenant, ref, "taboo-", Tenant::getTaboos);
  }

  /**
   * Resolves a named relation the way APIC does: the target DN if the export has it, else the name
   * in the source tenant, then in tenant common. An empty name means {@code default}.
   */
  private <T> @Nullable T resolve(
      String tenant,
      @Nullable NamedRef ref,
      String rnPrefix,
      Function<Tenant, Map<String, T>> objects) {
    if (ref == null) {
      return null;
    }
    String dn = ref.getTargetDn();
    if (dn != null) {
      String targetTenant = AciDn.value(dn, "tn-");
      String targetName = AciDn.value(dn, rnPrefix);
      if (targetTenant != null && targetName != null) {
        Tenant t = _aci.getTenants().get(targetTenant);
        T target = t == null ? null : objects.apply(t).get(targetName);
        if (target != null) {
          return target;
        }
      }
    }
    String name = ref.getName().isEmpty() ? "default" : ref.getName();
    Tenant own = _aci.getTenants().get(tenant);
    T target = own == null ? null : objects.apply(own).get(name);
    if (target == null) {
      Tenant common = _aci.getTenants().get(Tenant.COMMON);
      target = common == null ? null : objects.apply(common).get(name);
    }
    return target;
  }

  @Nullable
  BridgeDomain resolveBridgeDomain(String tenant, @Nullable NamedRef ref) {
    return resolve(tenant, ref, "BD-", Tenant::getBridgeDomains);
  }

  /** The VI name of a tenant VRF, as {@code show vrf} prints it on a leaf. */
  static @Nonnull String vrfName(AciVrf vrf) {
    return vrf.getTenant() + ":" + vrf.getName();
  }

  // ---------------------------------------------------------------------------------------------
  // Nodes

  private void initNodes() {
    Set<Integer> pods = new TreeSet<>();
    for (Map.Entry<Integer, String> entry : _aci.getHostnames().entrySet()) {
      FabricNode node = _aci.getNodes().get(entry.getKey());
      pods.add(node.getPodId());
      String hostname = entry.getValue();
      String baseHostname = AciConfiguration.baseHostname(node);
      if (!hostname.equals(baseHostname)) {
        _w.redFlagf(
            "Duplicate node name %s; naming node %d %s", baseHostname, node.getId(), hostname);
      }
      Ip tep = node.getTepAddress();
      if (tep == null) {
        tep = Ip.create(SYNTHETIC_TEP_BASE.asLong() + node.getId());
        _w.redFlagf(
            "TEP address of node %s is not in the export; using %s. Export the fabricNode class"
                + " to model real TEPs.",
            hostname, tep);
      }
      Configuration c =
          Configuration.builder()
              .setHostname(hostname)
              .setConfigurationFormat(ConfigurationFormat.CISCO_ACI)
              .setDeviceModel(DeviceModel.CISCO_UNSPECIFIED)
              .setDefaultCrossZoneAction(LineAction.PERMIT)
              .setDefaultInboundAction(LineAction.PERMIT)
              .build();
      Vrf.builder().setOwner(c).setName(Configuration.DEFAULT_VRF_NAME).build();
      Vrf underlay = Vrf.builder().setOwner(c).setName(UNDERLAY_VRF_NAME).build();
      Interface.builder()
          .setOwner(c)
          .setVrf(underlay)
          .setName("lo0")
          .setType(InterfaceType.LOOPBACK)
          .setAddress(ConcreteInterfaceAddress.create(tep, Prefix.MAX_PREFIX_LENGTH))
          .setDescription("TEP")
          .build();
      _nodes.put(node.getId(), new NodeState(node, hostname, c, tep));
    }
    if (pods.size() > 1) {
      _w.redFlagf(
          "Fabric has nodes in pods %s; Multi-Pod connectivity between pods is not modeled", pods);
    }
    Long asn = _aci.getFabricAsn();
    if (asn == null) {
      _w.redFlagf(
          "Fabric BGP AS (uni/fabric/bgpInstP-default/as) is not in the export; using %d",
          DEFAULT_FABRIC_ASN);
      asn = DEFAULT_FABRIC_ASN;
    }
    _fabricAsn = asn;
  }

  private @Nullable NodeState leafOrWarn(int nodeId, String context) {
    NodeState n = _nodes.get(nodeId);
    if (n == null) {
      _w.redFlagf("%s references node %d, which is not a known leaf", context, nodeId);
      return null;
    }
    if (!n.isLeaf()) {
      _w.redFlagf("%s references node %s, which is not a leaf", context, n._hostname);
      return null;
    }
    return n;
  }

  // ---------------------------------------------------------------------------------------------
  // Tenant object resolution

  private void resolveTenantObjects() {
    int nextVrfIndex = 1;
    List<AciVrf> missingVnid = new ArrayList<>();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (AciVrf vrf : tenant.getVrfs().values()) {
        _vrfIndexes.put(vrf, nextVrfIndex);
        if (vrf.getVnid() != null) {
          _vrfVnis.put(vrf, vrf.getVnid().intValue());
        } else {
          _vrfVnis.put(vrf, SYNTHETIC_VRF_VNI_BASE + nextVrfIndex);
          missingVnid.add(vrf);
        }
        nextVrfIndex++;
      }
    }
    int bdIndex = 1;
    List<BridgeDomain> missingBdIds = new ArrayList<>();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (BridgeDomain bd : tenant.getBridgeDomains().values()) {
        AciVrf vrf = resolveVrf(bd.getTenant(), bd.getVrf());
        if (vrf == null) {
          _w.redFlagf("Bridge domain %s has no resolvable VRF (%s)", bd.getDn(), bd.getVrf());
        } else {
          _bdVrfs.put(bd, vrf);
        }
        if (bd.getVnid() != null && bd.getMulticastGroup() != null) {
          _bdVnis.put(bd, bd.getVnid().intValue());
          _bdGroups.put(bd, bd.getMulticastGroup());
        } else {
          _bdVnis.put(
              bd, bd.getVnid() != null ? bd.getVnid().intValue() : SYNTHETIC_BD_VNI_BASE + bdIndex);
          _bdGroups.put(
              bd,
              bd.getMulticastGroup() != null
                  ? bd.getMulticastGroup()
                  : Ip.create(SYNTHETIC_GIPO_BASE.asLong() + 16L * bdIndex));
          missingBdIds.add(bd);
        }
        bdIndex++;
      }
      for (Epg epg : tenant.getEpgs().values()) {
        _epgsByDn.put(epg.getDn(), epg);
        BridgeDomain bd = resolveBridgeDomain(epg.getTenant(), epg.getBridgeDomain());
        if (bd == null) {
          _w.redFlagf(
              "EPG %s has no resolvable bridge domain (%s)", epg.getDn(), epg.getBridgeDomain());
        } else {
          _epgBds.put(epg, bd);
        }
      }
    }
    if (!missingVnid.isEmpty()) {
      _w.redFlagf(
          "VNIDs of %d VRFs are not in the export (config-only export?); using synthetic VNIs",
          missingVnid.size());
    }
    if (!missingBdIds.isEmpty()) {
      _w.redFlagf(
          "VNIDs or multicast groups of %d bridge domains are not in the export (config-only"
              + " export?); using synthetic values",
          missingBdIds.size());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Interfaces

  private @Nonnull Interface getOrCreatePhysical(NodeState n, String name) {
    Interface iface = n._c.getAllInterfaces().get(name);
    if (iface != null) {
      return iface;
    }
    return Interface.builder()
        .setOwner(n._c)
        .setVrf(n._c.getDefaultVrf())
        .setName(name)
        .setType(InterfaceType.PHYSICAL)
        .setBandwidth(ACCESS_PORT_BANDWIDTH)
        .setMtu(DEFAULT_MTU)
        .build();
  }

  private @Nonnull Interface getOrCreateAggregate(NodeState n, String name, String policyGroup) {
    Interface iface = n._c.getAllInterfaces().get(name);
    if (iface != null) {
      return iface;
    }
    return Interface.builder()
        .setOwner(n._c)
        .setVrf(n._c.getDefaultVrf())
        .setName(name)
        .setType(InterfaceType.AGGREGATED)
        .setDescription(policyGroup)
        .setMtu(DEFAULT_MTU)
        .build();
  }

  /**
   * The name of a port-channel or vPC interface: the switch's {@code po<N>} when {@code pcAggrIf}
   * is exported, else the interface policy group name.
   */
  private @Nonnull String aggregateName(int nodeId, String policyGroup) {
    return _aci.getPortChannelIds()
        .getOrDefault(nodeId, ImmutableMap.of())
        .getOrDefault(policyGroup, policyGroup);
  }

  /** The interfaces a path names: one per leaf, two for a vPC. */
  private @Nonnull List<Map.Entry<NodeState, Interface>> resolvePath(PathRef path, String context) {
    ImmutableList.Builder<Map.Entry<NodeState, Interface>> interfaces = ImmutableList.builder();
    for (int nodeId : path.getNodeIds()) {
      NodeState n = leafOrWarn(nodeId, context);
      if (n == null) {
        continue;
      }
      Interface iface =
          path.isPort()
              ? getOrCreatePhysical(n, path.getName())
              : getOrCreateAggregate(n, aggregateName(nodeId, path.getName()), path.getName());
      interfaces.add(Map.entry(n, iface));
    }
    return interfaces.build();
  }

  private void createAccessPorts() {
    AccessPolicies policies = _aci.getAccessPolicies();
    for (NodeState n : _nodes.values()) {
      if (!n.isLeaf()) {
        continue;
      }
      int nodeId = n._node.getId();
      for (AccessPolicies.PortSelector selector : policies.portSelectorsFor(nodeId)) {
        String pgDn = selector.getPolicyGroupDn();
        AccessPolicies.PolicyGroup pg = pgDn == null ? null : policies.getPolicyGroups().get(pgDn);
        if (pgDn != null && pg == null) {
          _w.redFlagf(
              "Port selector %s uses unknown interface policy group %s", selector.getName(), pgDn);
        }
        for (String port : selector.getPorts()) {
          Interface physical = getOrCreatePhysical(n, port);
          if (pg == null) {
            continue;
          }
          physical.setDescription(pg.getName());
          Interface bindTo = physical;
          if (pg.getBundleType() != AccessPolicies.BundleType.NONE) {
            if (pg.getBundleType() == AccessPolicies.BundleType.VPC && vpcPeerOf(nodeId) == null) {
              _w.redFlagf(
                  "vPC policy group %s is used on %s, which is not in a vPC pair",
                  pg.getName(), n._hostname);
            }
            Interface aggregate =
                getOrCreateAggregate(n, aggregateName(nodeId, pg.getName()), pg.getName());
            physical.setChannelGroup(aggregate.getName());
            Set<Dependency> deps = new LinkedHashSet<>(aggregate.getDependencies());
            deps.add(new Dependency(physical.getName(), DependencyType.AGGREGATE));
            aggregate.setDependencies(deps);
            bindTo = aggregate;
          }
          // EPGs deployed on every port of the attachable entity profile
          for (AccessPolicies.AaepEpgBinding binding :
              policies.getAaepBindings().getOrDefault(pg.getAaepDn(), ImmutableList.of())) {
            Epg epg = _epgsByDn.get(binding.getEpgDn());
            if (epg == null) {
              _w.redFlagf(
                  "Attachable entity profile %s deploys unknown EPG %s",
                  pg.getAaepDn(), binding.getEpgDn());
              continue;
            }
            addBinding(n, bindTo, epg, binding.getEncapVlan(), binding.getMode());
          }
        }
      }
    }
  }

  private @Nullable Integer vpcPeerOf(int nodeId) {
    for (VpcPair pair : _aci.getVpcPairs()) {
      if (pair.getNodeIds().get(0) == nodeId) {
        return pair.getNodeIds().get(1);
      }
      if (pair.getNodeIds().get(1) == nodeId) {
        return pair.getNodeIds().get(0);
      }
    }
    return null;
  }

  private void addBinding(
      NodeState n, Interface iface, Epg epg, @Nullable Integer encap, StaticPath.Mode mode) {
    BridgeDomain bd = _epgBds.get(epg);
    if (bd == null || !_bdVrfs.containsKey(bd)) {
      return; // warned during resolution
    }
    if (encap == null) {
      _w.redFlagf(
          "EPG %s binding on %s %s has no VLAN encap; ignoring it",
          epg.getDn(), n._hostname, iface.getName());
      return;
    }
    List<EpgBinding> bindings =
        n._bindings.computeIfAbsent(iface.getName(), k -> new ArrayList<>());
    if (bindings.stream().noneMatch(b -> b._epg == epg && b._encap == encap)) {
      bindings.add(new EpgBinding(epg, bd, encap, mode));
    }
    n._deployedBds.add(bd);
  }

  private void deployEpgs() {
    for (Tenant tenant : _aci.getTenants().values()) {
      for (Epg epg : tenant.getEpgs().values()) {
        BridgeDomain bd = _epgBds.get(epg);
        if (bd == null || !_bdVrfs.containsKey(bd)) {
          continue;
        }
        for (StaticPath staticPath : epg.getStaticPaths()) {
          for (Map.Entry<NodeState, Interface> e :
              resolvePath(staticPath.getPath(), "Static path of EPG " + epg.getDn())) {
            addBinding(
                e.getKey(), e.getValue(), epg, staticPath.getEncapVlan(), staticPath.getMode());
          }
        }
        // Endpoints learned through other means (VMM domains, AAEPs) still deploy the BD.
        for (Endpoint endpoint : epg.getEndpoints()) {
          for (PathRef path : endpoint.getPaths()) {
            for (int nodeId : path.getNodeIds()) {
              NodeState n = _nodes.get(nodeId);
              if (n != null && n.isLeaf()) {
                n._deployedBds.add(bd);
              }
            }
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Tenant VRFs on nodes

  private @Nonnull Vrf getOrCreateTenantVrf(NodeState n, AciVrf vrf) {
    return n._tenantVrfs.computeIfAbsent(
        vrf, v -> Vrf.builder().setOwner(n._c).setName(vrfName(v)).build());
  }

  private @Nonnull Interface createLoopback(NodeState n, Vrf vrf, Ip ip, String description) {
    String name = "lo" + n._nextLoopback++;
    return Interface.builder()
        .setOwner(n._c)
        .setVrf(vrf)
        .setName(name)
        .setType(InterfaceType.LOOPBACK)
        .setAddress(ConcreteInterfaceAddress.create(ip, Prefix.MAX_PREFIX_LENGTH))
        .setDescription(description)
        .build();
  }

  // ---------------------------------------------------------------------------------------------
  // L3Outs

  private void createL3Outs() {
    for (Tenant tenant : _aci.getTenants().values()) {
      for (L3Out l3Out : tenant.getL3Outs().values()) {
        AciVrf vrf = resolveVrf(tenant.getName(), l3Out.getVrf());
        if (vrf == null) {
          _w.redFlagf("L3Out %s has no resolvable VRF (%s)", l3Out.getDn(), l3Out.getVrf());
          continue;
        }
        for (L3OutNodeProfile profile : l3Out.getNodeProfiles()) {
          createL3OutNodeProfile(l3Out, vrf, profile);
        }
      }
    }
  }

  private void createL3OutNodeProfile(L3Out l3Out, AciVrf vrf, L3OutNodeProfile profile) {
    String context = "L3Out " + l3Out.getDn();
    Map<Integer, Ip> loopbackPeeringIps = new HashMap<>();
    for (L3OutNode node : profile.getNodes()) {
      NodeState n = leafOrWarn(node.getNodeId(), context);
      if (n == null) {
        continue;
      }
      Vrf viVrf = getOrCreateTenantVrf(n, vrf);
      Ip routerId = node.getRouterId();
      if (routerId != null) {
        Ip existing = n._l3OutRouterIds.putIfAbsent(vrf, routerId);
        if (existing != null && !existing.equals(routerId)) {
          _w.redFlagf(
              "%s sets router ID %s on %s, which already uses %s in VRF %s",
              context, routerId, n._hostname, existing, vrfName(vrf));
        }
        if (node.isRouterIdLoopback()) {
          createLoopback(n, viVrf, routerId, l3Out.getName() + " router ID");
          loopbackPeeringIps.put(node.getNodeId(), routerId);
        }
      }
      for (Ip loopback : node.getLoopbacks()) {
        createLoopback(n, viVrf, loopback, l3Out.getName() + " loopback");
        loopbackPeeringIps.putIfAbsent(node.getNodeId(), loopback);
      }
      for (L3OutStaticRoute route : node.getStaticRoutes()) {
        addStaticRoute(viVrf, route);
      }
    }
    for (L3OutInterfaceProfile interfaceProfile : profile.getInterfaceProfiles()) {
      for (L3OutPath path : interfaceProfile.getPaths()) {
        createL3OutPath(l3Out, vrf, interfaceProfile, path);
      }
    }
    for (BgpPeer peer : profile.getLoopbackBgpPeers()) {
      if (!l3Out.isBgpEnabled()) {
        _w.redFlagf("%s has BGP peer %s but BGP is not enabled", context, peer.getAddress());
        continue;
      }
      for (L3OutNode node : profile.getNodes()) {
        NodeState n = _nodes.get(node.getNodeId());
        if (n == null || !n.isLeaf()) {
          continue;
        }
        Ip localIp = loopbackPeeringIps.get(node.getNodeId());
        if (localIp == null) {
          _w.redFlagf(
              "%s peers with %s over a loopback, but node %s has no L3Out loopback",
              context, peer.getAddress(), n._hostname);
        }
        n._pendingPeers
            .computeIfAbsent(vrf, v -> new ArrayList<>())
            .add(new PendingBgpPeer(l3Out, peer, localIp));
      }
    }
  }

  private static void addStaticRoute(Vrf vrf, L3OutStaticRoute route) {
    SortedSet<StaticRoute> routes = new TreeSet<>(vrf.getStaticRoutes());
    if (route.getNextHops().isEmpty()) {
      // No next hop: APIC installs the route to Null0
      routes.add(
          StaticRoute.builder()
              .setNetwork(route.getPrefix())
              .setNextHop(NextHopDiscard.instance())
              .setAdmin(route.getPreference())
              .build());
    }
    for (L3OutStaticRoute.NextHop nh : route.getNextHops()) {
      routes.add(
          StaticRoute.builder()
              .setNetwork(route.getPrefix())
              .setNextHop(
                  nh.getAddress() == null
                      ? NextHopDiscard.instance()
                      : NextHopIp.of(nh.getAddress()))
              .setAdmin(firstNonNull(nh.getPreference(), route.getPreference()))
              .build());
    }
    vrf.setStaticRoutes(ImmutableSortedSet.copyOf(routes));
  }

  private void createL3OutPath(
      L3Out l3Out, AciVrf vrf, L3OutInterfaceProfile interfaceProfile, L3OutPath path) {
    String context = "L3Out " + l3Out.getDn() + " interface " + path.getPath();
    List<Integer> nodeIds = path.getPath().getNodeIds();
    for (int i = 0; i < nodeIds.size(); i++) {
      NodeState n = leafOrWarn(nodeIds.get(i), context);
      if (n == null) {
        continue;
      }
      ConcreteInterfaceAddress address =
          path.getPath().isVpc()
              ? path.getMemberAddresses().get(i == 0 ? "A" : "B")
              : path.getAddress();
      Vrf viVrf = getOrCreateTenantVrf(n, vrf);
      Interface parent =
          path.getPath().isPort()
              ? getOrCreatePhysical(n, path.getPath().getName())
              : getOrCreateAggregate(
                  n,
                  aggregateName(n._node.getId(), path.getPath().getName()),
                  path.getPath().getName());
      Interface l3Interface = createL3OutInterface(n, viVrf, parent, path, context);
      if (l3Interface == null) {
        continue;
      }
      l3Interface.setDescription(l3Out.getName());
      if (path.getMtu() != null) {
        l3Interface.setMtu(path.getMtu());
      }
      if (address != null) {
        ImmutableList.Builder<ConcreteInterfaceAddress> all = ImmutableList.builder();
        all.add(address);
        path.getSecondaryAddresses().stream().filter(a -> !a.equals(address)).forEach(all::add);
        l3Interface.setAllAddresses(all.build());
        l3Interface.setAddress(address);
      } else {
        _w.redFlagf("%s on %s has no address", context, n._hostname);
      }
      if (interfaceProfile.isOspfEnabled() && l3Out.getOspfAreaId() != null) {
        n._ospfInterfaces
            .computeIfAbsent(vrf, v -> new LinkedHashMap<>())
            .computeIfAbsent(l3Out, l -> new ArrayList<>())
            .add(l3Interface.getName());
      }
      for (BgpPeer peer : path.getBgpPeers()) {
        if (!l3Out.isBgpEnabled()) {
          _w.redFlagf("%s has BGP peer %s but BGP is not enabled", context, peer.getAddress());
          continue;
        }
        n._pendingPeers
            .computeIfAbsent(vrf, v -> new ArrayList<>())
            .add(new PendingBgpPeer(l3Out, peer, address == null ? null : address.getIp()));
      }
    }
  }

  /** Creates the L3 interface of an L3Out path on one node; null if it cannot be modeled. */
  private @Nullable Interface createL3OutInterface(
      NodeState n, Vrf viVrf, Interface parent, L3OutPath path, String context) {
    Integer encap = path.getEncapVlan();
    return switch (path.getType()) {
      case ROUTED -> {
        n._l3Interfaces.add(parent.getName());
        parent.setVrf(viVrf);
        yield parent;
      }
      case SUB_INTERFACE -> {
        if (encap == null) {
          _w.redFlagf("%s is a subinterface without a VLAN encap", context);
          yield null;
        }
        n._l3Interfaces.add(parent.getName());
        yield Interface.builder()
            .setOwner(n._c)
            .setVrf(viVrf)
            .setName(parent.getName() + "." + encap)
            .setType(InterfaceType.LOGICAL)
            .setEncapsulationVlan(encap)
            .setDependencies(
                ImmutableList.of(new Dependency(parent.getName(), DependencyType.BIND)))
            .build();
      }
      case SVI -> {
        if (encap == null) {
          _w.redFlagf("%s is an SVI without a VLAN encap", context);
          yield null;
        }
        n._sviEncaps.computeIfAbsent(parent.getName(), k -> new TreeSet<>()).add(encap);
        n._usedVlans.add(encap);
        String sviName = "vlan" + encap;
        Interface svi = n._c.getAllInterfaces().get(sviName);
        yield svi != null
            ? svi
            : Interface.builder()
                .setOwner(n._c)
                .setVrf(viVrf)
                .setName(sviName)
                .setType(InterfaceType.VLAN)
                .setVlan(encap)
                .build();
      }
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Bridge domains

  private void createBridgeDomains() {
    for (NodeState n : _nodes.values()) {
      if (!n.isLeaf()) {
        continue;
      }
      // Encaps of each BD on this leaf, in a stable order
      Map<BridgeDomain, SortedSet<Integer>> encaps = new LinkedHashMap<>();
      n._deployedBds.forEach(bd -> encaps.put(bd, new TreeSet<>()));
      n._bindings.values().forEach(bs -> bs.forEach(b -> encaps.get(b._bd).add(b._encap)));
      for (Map.Entry<BridgeDomain, SortedSet<Integer>> e : encaps.entrySet()) {
        BridgeDomain bd = e.getKey();
        Integer vlan =
            e.getValue().stream().filter(v -> !n._usedVlans.contains(v)).findFirst().orElse(null);
        if (vlan == null) {
          vlan = allocateInternalVlan(n);
        }
        if (e.getValue().size() > 1) {
          _w.redFlagf(
              "Bridge domain %s uses encaps %s on %s; only VLAN %d is bridged to its gateway"
                  + " until per-port VLAN translation is modeled",
              bd.getDn(), e.getValue(), n._hostname, vlan);
        } else if (!e.getValue().isEmpty() && !e.getValue().contains(vlan)) {
          _w.redFlagf(
              "Encap VLAN %s of bridge domain %s on %s is already used; its ports are not"
                  + " bridged to the gateway",
              e.getValue(), bd.getDn(), n._hostname);
        }
        n._usedVlans.add(vlan);
        n._bdVlans.put(bd, vlan);
        createBridgeDomainOnNode(n, bd, vlan);
      }
    }
  }

  private static int allocateInternalVlan(NodeState n) {
    int vlan = FIRST_INTERNAL_VLAN;
    while (n._usedVlans.contains(vlan)) {
      vlan--;
    }
    return vlan;
  }

  private void createBridgeDomainOnNode(NodeState n, BridgeDomain bd, int vlan) {
    AciVrf vrf = _bdVrfs.get(bd);
    assert vrf != null;
    Vrf viVrf = getOrCreateTenantVrf(n, vrf);
    List<AciSubnet> gateways =
        bd.getSubnets().stream()
            .filter(s -> !s.isNoDefaultGateway())
            .sorted(Comparator.comparing(s -> !s.isPreferred()))
            .collect(ImmutableList.toImmutableList());
    if (bd.isUnicastRoute() && !gateways.isEmpty()) {
      Interface svi =
          Interface.builder()
              .setOwner(n._c)
              .setVrf(viVrf)
              .setName("vlan" + vlan)
              .setType(InterfaceType.VLAN)
              .setVlan(vlan)
              .setDescription(bd.getTenant() + ":" + bd.getName())
              .setMtu(DEFAULT_MTU)
              .setProxyArp(false)
              // The BD gateway is an anycast address on every leaf where the BD is deployed.
              .setHmm(true)
              .setAddress(gateways.get(0).getGateway())
              .build();
      svi.setAllAddresses(
          gateways.stream().map(AciSubnet::getGateway).collect(ImmutableList.toImmutableList()));
      svi.setAddress(gateways.get(0).getGateway());
    }
    Vrf underlay = n._c.getVrfs().get(UNDERLAY_VRF_NAME);
    Ip group = _bdGroups.get(bd);
    underlay.addLayer2Vni(
        Layer2Vni.builder()
            .setVni(_bdVnis.get(bd))
            .setVlan(vlan)
            .setSourceAddress(n._tep)
            .setSrcVrf(UNDERLAY_VRF_NAME)
            .setUdpPort(Vni.DEFAULT_UDP_PORT)
            .setBumTransportMethod(BumTransportMethod.MULTICAST_GROUP)
            .setBumTransportIps(ImmutableSortedSet.of(group))
            .build());
  }

  private void createSwitchports() {
    for (NodeState n : _nodes.values()) {
      Set<String> names = new TreeSet<>(n._bindings.keySet());
      names.addAll(n._sviEncaps.keySet());
      for (String name : names) {
        Interface iface = n._c.getAllInterfaces().get(name);
        List<EpgBinding> bindings = n._bindings.getOrDefault(name, ImmutableList.of());
        Set<Integer> sviEncaps = n._sviEncaps.getOrDefault(name, ImmutableSet.of());
        if (n._l3Interfaces.contains(name)) {
          _w.redFlagf(
              "Interface %s on %s is a routed L3Out interface and also carries EPGs or SVIs;"
                  + " ignoring the switched configuration",
              name, n._hostname);
          continue;
        }
        Set<Integer> allowed = new TreeSet<>(sviEncaps);
        Integer untagged = null;
        for (EpgBinding b : bindings) {
          allowed.add(b._encap);
          if (b._mode != StaticPath.Mode.TAGGED) {
            untagged = b._encap;
          }
        }
        iface.setSwitchport(true);
        boolean onlyAccess =
            allowed.size() == 1
                && bindings.stream().allMatch(b -> b._mode == StaticPath.Mode.UNTAGGED)
                && !bindings.isEmpty();
        if (onlyAccess) {
          iface.setSwitchportMode(SwitchportMode.ACCESS);
          iface.setAccessVlan(allowed.iterator().next());
        } else {
          iface.setSwitchportMode(SwitchportMode.TRUNK);
          iface.setAllowedVlans(
              IntegerSpace.unionOfSubRanges(
                  allowed.stream()
                      .map(v -> new SubRange(v, v))
                      .collect(ImmutableList.toImmutableList())));
          iface.setNativeVlan(untagged);
        }
        // Members of an aggregate inherit its switching; Batfish reads it from the aggregate.
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Tenant routing: L3 VNIs, BGP, OSPF, EVPN leaking, endpoint routes

  private void createTenantRouting() {
    for (NodeState n : _nodes.values()) {
      if (!n.isLeaf()) {
        continue;
      }
      for (Map.Entry<AciVrf, Vrf> e : ImmutableList.copyOf(n._tenantVrfs.entrySet())) {
        AciVrf vrf = e.getKey();
        Vrf viVrf = e.getValue();
        int vni = _vrfVnis.get(vrf);
        viVrf.addLayer3Vni(
            Layer3Vni.builder()
                .setVni(vni)
                .setSourceAddress(n._tep)
                .setSrcVrf(UNDERLAY_VRF_NAME)
                .setUdpPort(Vni.DEFAULT_UDP_PORT)
                .build());
        BgpProcess bgp = createTenantBgpProcess(n, vrf, viVrf);
        createEvpnLeaking(n, vrf, viVrf, bgp, vni);
        for (PendingBgpPeer peer : n._pendingPeers.getOrDefault(vrf, ImmutableList.of())) {
          createL3OutBgpPeer(n, vrf, bgp, peer);
        }
        Map<L3Out, List<String>> ospf = n._ospfInterfaces.get(vrf);
        if (ospf != null) {
          createOspf(n, vrf, viVrf, ospf);
        }
        addEndpointRoutes(n, vrf, viVrf);
      }
    }
  }

  private @Nonnull BgpProcess createTenantBgpProcess(NodeState n, AciVrf vrf, Vrf viVrf) {
    Ip routerId = n._l3OutRouterIds.getOrDefault(vrf, n._tep);
    String redistributionPolicy = "~aci~vrf~" + vrfName(vrf) + "~redistribute~";
    PrefixSpace bdSubnets = new PrefixSpace();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (BridgeDomain bd : tenant.getBridgeDomains().values()) {
        if (_bdVrfs.get(bd) == vrf) {
          bd.getSubnets().forEach(s -> bdSubnets.addPrefix(s.getPrefix()));
        }
      }
    }
    RoutingPolicy.builder()
        .setOwner(n._c)
        .setName(redistributionPolicy)
        .addStatement(
            new If(
                "Redistribute bridge domain subnets",
                new org.batfish.datamodel.routing_policy.expr.Conjunction(
                    ImmutableList.of(
                        new MatchProtocol(RoutingProtocol.CONNECTED),
                        new MatchPrefixSet(
                            DestinationNetwork.instance(), new ExplicitPrefixSet(bdSubnets)))),
                ImmutableList.of(Statements.ExitAccept.toStaticStatement())))
        .addStatement(
            new If(
                "Redistribute L3Out static routes and endpoint host routes",
                new MatchProtocol(RoutingProtocol.STATIC),
                ImmutableList.of(Statements.ExitAccept.toStaticStatement())))
        .addStatement(
            new If(
                "Redistribute L3Out OSPF routes",
                new MatchProtocol(
                    RoutingProtocol.OSPF,
                    RoutingProtocol.OSPF_IA,
                    RoutingProtocol.OSPF_E1,
                    RoutingProtocol.OSPF_E2),
                ImmutableList.of(Statements.ExitAccept.toStaticStatement())))
        .addStatement(Statements.ExitReject.toStaticStatement())
        .build();
    BgpProcess bgp =
        BgpProcess.builder()
            .setVrf(viVrf)
            .setRouterId(routerId)
            .setEbgpAdminCost(DEFAULT_EBGP_ADMIN)
            .setIbgpAdminCost(DEFAULT_IBGP_ADMIN)
            .setLocalAdminCost(DEFAULT_LOCAL_BGP_ADMIN)
            .setLocalOriginationTypeTieBreaker(PREFER_NETWORK)
            .setNetworkNextHopIpTieBreaker(LOWEST_NEXT_HOP_IP)
            .setRedistributeNextHopIpTieBreaker(HIGHEST_NEXT_HOP_IP)
            .setRedistributionPolicy(redistributionPolicy)
            .build();
    bgp.setMultipathEbgp(true);
    bgp.setMultipathIbgp(true);
    bgp.setMultipathEquivalentAsPathMatchMode(MultipathEquivalentAsPathMatchMode.EXACT_PATH);
    return bgp;
  }

  private @Nonnull ExtendedCommunity routeTarget(int vni) {
    return ExtendedCommunity.target(_fabricAsn & 0xFFFFL, vni);
  }

  private @Nonnull RouteDistinguisher routeDistinguisher(NodeState n, AciVrf vrf) {
    return RouteDistinguisher.from(n._tep, _vrfIndexes.get(vrf));
  }

  private void createEvpnLeaking(NodeState n, AciVrf vrf, Vrf viVrf, BgpProcess bgp, int vni) {
    ExtendedCommunity rt = routeTarget(vni);
    // The VRF's own RD lets the dataplane skip re-importing routes this VRF exported.
    viVrf.setRouteDistinguisher(routeDistinguisher(n, vrf));
    Vrf underlay = n._c.getVrfs().get(UNDERLAY_VRF_NAME);
    getOrInitVrfLeakConfig(underlay)
        .addBgpv4ToEvpnVrfLeakConfig(
            Bgpv4ToEvpnVrfLeakConfig.builder()
                .setImportFromVrf(viVrf.getName())
                .setSrcVrfRouteDistinguisher(routeDistinguisher(n, vrf))
                .setAttachRouteTargets(rt)
                .build());
    RoutingPolicy importPolicy =
        RoutingPolicy.builder()
            .setOwner(n._c)
            .setName("~aci~vrf~" + vrfName(vrf) + "~evpn~import~")
            .addStatement(
                new If(
                    new MatchCommunities(
                        InputCommunities.instance(), new HasCommunity(new CommunityIs(rt))),
                    ImmutableList.of(Statements.ReturnTrue.toStaticStatement())))
            .addStatement(Statements.ReturnFalse.toStaticStatement())
            .build();
    getOrInitVrfLeakConfig(viVrf)
        .addEvpnToBgpv4VrfLeakConfig(
            EvpnToBgpv4VrfLeakConfig.builder()
                .setImportFromVrf(UNDERLAY_VRF_NAME)
                .setImportPolicy(importPolicy.getName())
                .build());
    assert bgp.getRouterId() != null;
  }

  private static @Nonnull VrfLeakConfig getOrInitVrfLeakConfig(Vrf vrf) {
    if (vrf.getVrfLeakConfig() == null) {
      vrf.setVrfLeakConfig(new VrfLeakConfig(true));
    }
    return vrf.getVrfLeakConfig();
  }

  /** Prefixes the fabric owns in {@code vrf}: BD subnets and endpoint host routes. */
  private @Nonnull PrefixSpace internalPrefixes(AciVrf vrf) {
    PrefixSpace internal = new PrefixSpace();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (BridgeDomain bd : tenant.getBridgeDomains().values()) {
        if (_bdVrfs.get(bd) == vrf) {
          bd.getSubnets().forEach(s -> internal.addPrefix(s.getPrefix()));
        }
      }
      for (Epg epg : tenant.getEpgs().values()) {
        BridgeDomain bd = _epgBds.get(epg);
        if (bd != null && _bdVrfs.get(bd) == vrf) {
          epg.getEndpoints()
              .forEach(ep -> ep.getIps().forEach(ip -> internal.addPrefix(ip.toPrefix())));
        }
      }
    }
    return internal;
  }

  /**
   * The export policy of an L3Out. Fabric-owned prefixes leave only as public subnets of bridge
   * domains associated with the L3Out; other routes (transit) leave when they match an {@code
   * export-rtctrl} subnet of one of its external EPGs.
   */
  private @Nonnull String l3OutExportPolicy(NodeState n, AciVrf vrf, L3Out l3Out) {
    String name = "~aci~l3out~" + l3Out.getTenant() + "~" + l3Out.getName() + "~export~";
    if (n._c.getRoutingPolicies().containsKey(name)) {
      return name;
    }
    PrefixSpace publicSubnets = new PrefixSpace();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (BridgeDomain bd : tenant.getBridgeDomains().values()) {
        if (_bdVrfs.get(bd) == vrf && bd.getL3Outs().contains(l3Out.getName())) {
          bd.getSubnets().stream()
              .filter(AciSubnet::isPublic)
              .forEach(s -> publicSubnets.addPrefix(s.getPrefix()));
        }
      }
    }
    PrefixSpace transit = routeControlSpace(l3Out, ExternalSubnet.EXPORT_RTCTRL);
    RoutingPolicy.builder()
        .setOwner(n._c)
        .setName(name)
        .addStatement(
            new If(
                "Fabric subnets: only public subnets of associated bridge domains",
                matchNetwork(internalPrefixes(vrf)),
                ImmutableList.of(
                    new If(
                        matchNetwork(publicSubnets),
                        ImmutableList.of(Statements.ExitAccept.toStaticStatement()),
                        ImmutableList.of(Statements.ExitReject.toStaticStatement())))))
        .addStatement(
            new If(
                "Transit routes matching export route control subnets",
                matchNetwork(transit),
                ImmutableList.of(Statements.ExitAccept.toStaticStatement())))
        .addStatement(Statements.ExitReject.toStaticStatement())
        .build();
    return name;
  }

  /** The import policy of an L3Out: everything, or only import route-control subnets. */
  private static @Nonnull String l3OutImportPolicy(NodeState n, L3Out l3Out) {
    String name = "~aci~l3out~" + l3Out.getTenant() + "~" + l3Out.getName() + "~import~";
    if (n._c.getRoutingPolicies().containsKey(name)) {
      return name;
    }
    List<Statement> statements = new ArrayList<>();
    if (l3Out.isEnforceImportRouteControl()) {
      statements.add(
          new If(
              "Import route control subnets",
              matchNetwork(routeControlSpace(l3Out, ExternalSubnet.IMPORT_RTCTRL)),
              ImmutableList.of(Statements.ExitAccept.toStaticStatement())));
      statements.add(Statements.ExitReject.toStaticStatement());
    } else {
      statements.add(Statements.ExitAccept.toStaticStatement());
    }
    RoutingPolicy.builder().setOwner(n._c).setName(name).setStatements(statements).build();
    return name;
  }

  /** Prefixes of an L3Out's external EPG subnets with the given route-control scope. */
  private static @Nonnull PrefixSpace routeControlSpace(L3Out l3Out, String scope) {
    PrefixSpace space = new PrefixSpace();
    for (ExternalEpg epg : l3Out.getExternalEpgs()) {
      for (ExternalSubnet subnet : epg.getSubnets()) {
        if (!subnet.getScope().contains(scope)) {
          continue;
        }
        Prefix prefix = subnet.getPrefix();
        space.addPrefixRange(
            subnet.getAggregate().contains(scope)
                ? PrefixRange.sameAsOrMoreSpecificThan(prefix)
                : PrefixRange.fromPrefix(prefix));
      }
    }
    return space;
  }

  private static @Nonnull BooleanExpr matchNetwork(PrefixSpace space) {
    return new MatchPrefixSet(DestinationNetwork.instance(), new ExplicitPrefixSet(space));
  }

  private void createL3OutBgpPeer(NodeState n, AciVrf vrf, BgpProcess bgp, PendingBgpPeer pending) {
    BgpPeer peer = pending._peer;
    L3Out l3Out = pending._l3Out;
    if (!peer.isEnabled()) {
      return;
    }
    if (peer.getRemoteAs() == null) {
      _w.redFlagf(
          "BGP peer %s of L3Out %s has no remote AS; ignoring it",
          peer.getAddress(), l3Out.getDn());
      return;
    }
    long localAs = firstNonNull(peer.getLocalAs(), _fabricAsn);
    Set<String> controls = peer.getControls();
    Ipv4UnicastAddressFamily af =
        Ipv4UnicastAddressFamily.builder()
            .setAddressFamilyCapabilities(
                AddressFamilyCapabilities.builder()
                    .setSendCommunity(controls.contains("send-com"))
                    .setSendExtendedCommunity(controls.contains("send-ext-com"))
                    .setAllowLocalAsIn(controls.contains("allow-self-as"))
                    .build())
            .setImportPolicy(l3OutImportPolicy(n, l3Out))
            .setExportPolicy(l3OutExportPolicy(n, vrf, l3Out))
            .build();
    String description = l3Out.getName();
    if (peer.getAddress().getPrefixLength() == Prefix.MAX_PREFIX_LENGTH) {
      BgpActivePeerConfig.builder()
          .setBgpProcess(bgp)
          .setPeerAddress(peer.getAddress().getStartIp())
          .setLocalIp(pending._localIp)
          .setLocalAs(localAs)
          .setRemoteAs(peer.getRemoteAs())
          .setEbgpMultihop(peer.getTtl() > 1)
          .setDescription(description)
          .setIpv4UnicastAddressFamily(af)
          .build();
    } else {
      BgpPassivePeerConfig.builder()
          .setBgpProcess(bgp)
          .setPeerPrefix(peer.getAddress())
          .setLocalIp(pending._localIp)
          .setLocalAs(localAs)
          .setRemoteAsns(LongSpace.of(peer.getRemoteAs()))
          .setEbgpMultihop(peer.getTtl() > 1)
          .setDescription(description)
          .setIpv4UnicastAddressFamily(af)
          .build();
    }
  }

  private void createOspf(
      NodeState n, AciVrf vrf, Vrf viVrf, Map<L3Out, List<String>> interfacesByL3Out) {
    Ip routerId = n._l3OutRouterIds.getOrDefault(vrf, n._tep);
    Map<Long, OspfArea.Builder> areas = new TreeMap<>();
    Set<L3Out> l3Outs = interfacesByL3Out.keySet();
    for (Map.Entry<L3Out, List<String>> e : interfacesByL3Out.entrySet()) {
      L3Out l3Out = e.getKey();
      long areaId = Objects.requireNonNull(l3Out.getOspfAreaId());
      OspfArea.Builder area = areas.computeIfAbsent(areaId, a -> OspfArea.builder().setNumber(a));
      L3Out.OspfAreaType type = firstNonNull(l3Out.getOspfAreaType(), L3Out.OspfAreaType.NSSA);
      if (areaId != 0) {
        switch (type) {
          case NSSA -> area.setNssa(NssaSettings.builder().build());
          case STUB -> area.setStub(StubSettings.builder().build());
          case REGULAR -> area.setNonStub();
        }
      }
      area.addInterfaces(e.getValue());
      for (String ifaceName : e.getValue()) {
        Interface iface = n._c.getAllInterfaces().get(ifaceName);
        iface.setOspfSettings(
            OspfInterfaceSettings.defaultSettingsBuilder()
                .setProcess(OSPF_PROCESS_ID)
                .setAreaName(areaId)
                .setEnabled(true)
                .setPassive(false)
                .setNetworkType(
                    iface.getInterfaceType() == InterfaceType.VLAN
                        ? OspfNetworkType.BROADCAST
                        : OspfNetworkType.POINT_TO_POINT)
                .build());
      }
    }
    // Export what the L3Outs export: one policy permitting a route if any L3Out would.
    RoutingPolicy.Builder export =
        RoutingPolicy.builder()
            .setOwner(n._c)
            .setName("~aci~vrf~" + vrfName(vrf) + "~ospf~export~");
    for (L3Out l3Out : l3Outs) {
      export.addStatement(
          new If(
              new org.batfish.datamodel.routing_policy.expr.Conjunction(
                  ImmutableList.of(
                      new org.batfish.datamodel.routing_policy.expr.Not(
                          new MatchProtocol(
                              RoutingProtocol.OSPF,
                              RoutingProtocol.OSPF_IA,
                              RoutingProtocol.OSPF_E1,
                              RoutingProtocol.OSPF_E2)),
                      new org.batfish.datamodel.routing_policy.expr.CallExpr(
                          l3OutExportPolicy(n, vrf, l3Out)))),
              ImmutableList.of(Statements.ExitAccept.toStaticStatement())));
    }
    export.addStatement(Statements.ExitReject.toStaticStatement());
    RoutingPolicy exportPolicy = export.build();
    ImmutableMap.Builder<Long, OspfArea> builtAreas = ImmutableMap.builder();
    areas.forEach((id, b) -> builtAreas.put(id, b.build()));
    OspfProcess.builder()
        .setVrf(viVrf)
        .setProcessId(OSPF_PROCESS_ID)
        .setRouterId(routerId)
        .setReferenceBandwidth(OSPF_REFERENCE_BANDWIDTH)
        .setAdminCosts(OspfProcess.computeDefaultAdminCosts(ConfigurationFormat.CISCO_NX))
        .setAreas(builtAreas.build())
        .setExportPolicy(exportPolicy)
        .build();
  }

  /**
   * Installs a host route on the leaf where each endpoint was learned, so other leaves forward
   * straight to it as the spine proxy would.
   */
  private void addEndpointRoutes(NodeState n, AciVrf vrf, Vrf viVrf) {
    SortedSet<StaticRoute> routes = new TreeSet<>(viVrf.getStaticRoutes());
    int nodeId = n._node.getId();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (Epg epg : tenant.getEpgs().values()) {
        BridgeDomain bd = _epgBds.get(epg);
        Integer vlan = bd == null ? null : n._bdVlans.get(bd);
        if (bd == null || vlan == null || _bdVrfs.get(bd) != vrf) {
          continue;
        }
        String svi = "vlan" + vlan;
        if (!n._c.getAllInterfaces().containsKey(svi)) {
          continue;
        }
        for (Endpoint endpoint : epg.getEndpoints()) {
          if (endpoint.getPaths().stream().noneMatch(p -> p.getNodeIds().contains(nodeId))) {
            continue;
          }
          for (Ip ip : endpoint.getIps()) {
            if (bd.getSubnets().stream().noneMatch(s -> s.getPrefix().containsIp(ip))) {
              continue;
            }
            routes.add(
                StaticRoute.builder()
                    .setNetwork(ip.toPrefix())
                    // Interface-only next hop: the leaf ARPs for the endpoint, as for the
                    // connected subnet, rather than forwarding to a next-hop device.
                    .setNextHop(NextHopInterface.of(svi))
                    .setAdmin(1)
                    .setTag(ENDPOINT_ROUTE_TAG)
                    .build());
          }
        }
      }
    }
    viVrf.setStaticRoutes(ImmutableSortedSet.copyOf(routes));
  }

  // ---------------------------------------------------------------------------------------------
  // Underlay

  private void createUnderlay() {
    for (NodeState n : _nodes.values()) {
      Vrf underlay = n._c.getVrfs().get(UNDERLAY_VRF_NAME);
      underlay.setIsisProcess(
          IsisProcess.builder()
              .setNetAddress(
                  new IsoAddress(String.format("49.0001.0000.0000.%04d.00", n._node.getId())))
              .setLevel1(IsisLevelSettings.builder().build())
              .build());
      n._c.getAllInterfaces().get("lo0").setIsis(isisSettings(IsisInterfaceMode.PASSIVE, false));
    }
    List<Map.Entry<Map.Entry<NodeState, String>, Map.Entry<NodeState, String>>> links =
        fabricLinks();
    long next = FABRIC_LINK_POOL.getStartIp().asLong();
    for (Map.Entry<Map.Entry<NodeState, String>, Map.Entry<NodeState, String>> link : links) {
      if (next + 1 > FABRIC_LINK_POOL.getEndIp().asLong()) {
        _w.redFlag("Too many fabric links to address");
        break;
      }
      Ip a = Ip.create(next);
      Ip b = Ip.create(next + 1);
      next += 2;
      createFabricInterface(link.getKey(), a, link.getValue().getKey()._hostname);
      createFabricInterface(link.getValue(), b, link.getKey().getKey()._hostname);
      Layer1Edge edge =
          new Layer1Edge(
              link.getKey().getKey()._hostname,
              link.getKey().getValue(),
              link.getValue().getKey()._hostname,
              link.getValue().getValue());
      _layer1Edges.add(edge);
      _layer1Edges.add(edge.reverse());
    }
  }

  /**
   * Leaf-spine links from LLDP when exported; otherwise a full mesh between every leaf and spine,
   * on interfaces named {@code fabric-<peer node ID>}.
   */
  private @Nonnull List<Map.Entry<Map.Entry<NodeState, String>, Map.Entry<NodeState, String>>>
      fabricLinks() {
    Map<String, NodeState> byHostname = new HashMap<>();
    _nodes.values().forEach(n -> byHostname.put(n._hostname, n));
    // Deduplicate links seen from both ends; key on the lexically smaller endpoint.
    Map<String, Map.Entry<Map.Entry<NodeState, String>, Map.Entry<NodeState, String>>> links =
        new TreeMap<>();
    for (LldpAdjacency adj : _aci.getLldpAdjacencies()) {
      NodeState local = _nodes.get(adj.getNodeId());
      String remoteName = adj.getRemoteSystemName();
      NodeState remote = remoteName == null ? null : byHostname.get(remoteName.toLowerCase());
      String remotePort = adj.getRemotePortId();
      if (local == null || remote == null || remotePort == null) {
        continue;
      }
      if (local.isLeaf() == remote.isLeaf()) {
        continue; // only leaf-spine links carry the underlay
      }
      String localPort = adj.getLocalInterface().toLowerCase();
      String remotePortName = remotePort.toLowerCase();
      String keyA = local._hostname + "|" + localPort;
      String keyB = remote._hostname + "|" + remotePortName;
      String key = keyA.compareTo(keyB) < 0 ? keyA + "~" + keyB : keyB + "~" + keyA;
      links.putIfAbsent(
          key, Map.entry(Map.entry(local, localPort), Map.entry(remote, remotePortName)));
    }
    if (!links.isEmpty()) {
      return ImmutableList.copyOf(links.values());
    }
    ImmutableList.Builder<Map.Entry<Map.Entry<NodeState, String>, Map.Entry<NodeState, String>>>
        mesh = ImmutableList.builder();
    for (NodeState leaf : _nodes.values()) {
      if (!leaf.isLeaf()) {
        continue;
      }
      for (NodeState spine : _nodes.values()) {
        if (spine.isSpine()) {
          mesh.add(
              Map.entry(
                  Map.entry(leaf, "fabric-" + spine._node.getId()),
                  Map.entry(spine, "fabric-" + leaf._node.getId())));
        }
      }
    }
    return mesh.build();
  }

  private void createFabricInterface(Map.Entry<NodeState, String> end, Ip ip, String peer) {
    NodeState n = end.getKey();
    Vrf underlay = n._c.getVrfs().get(UNDERLAY_VRF_NAME);
    Interface iface = n._c.getAllInterfaces().get(end.getValue());
    ConcreteInterfaceAddress address = ConcreteInterfaceAddress.create(ip, 31);
    if (iface == null) {
      iface =
          Interface.builder()
              .setOwner(n._c)
              .setVrf(underlay)
              .setName(end.getValue())
              .setType(InterfaceType.PHYSICAL)
              .build();
    } else {
      iface.setVrf(underlay);
    }
    iface.setAddress(address);
    iface.setAllAddresses(ImmutableList.of(address));
    iface.setBandwidth(FABRIC_LINK_BANDWIDTH);
    iface.setMtu(DEFAULT_MTU);
    iface.setDescription("fabric link to " + peer);
    iface.setIsis(isisSettings(IsisInterfaceMode.ACTIVE, true));
  }

  private static @Nonnull IsisInterfaceSettings isisSettings(
      IsisInterfaceMode mode, boolean pointToPoint) {
    return IsisInterfaceSettings.builder()
        .setPointToPoint(pointToPoint)
        .setLevel1(IsisInterfaceLevelSettings.builder().setCost(ISIS_COST).setMode(mode).build())
        .build();
  }

  // ---------------------------------------------------------------------------------------------
  // Fabric BGP

  private void createFabricBgp() {
    Set<Integer> reflectors = new TreeSet<>();
    for (int id : _aci.getRouteReflectorNodeIds()) {
      NodeState n = _nodes.get(id);
      if (n != null && n.isSpine()) {
        reflectors.add(id);
      }
    }
    if (reflectors.isEmpty()) {
      _nodes.values().stream()
          .filter(NodeState::isSpine)
          .forEach(n -> reflectors.add(n._node.getId()));
      if (!reflectors.isEmpty() && !_aci.getTenants().isEmpty()) {
        _w.redFlag(
            "No BGP route reflectors in the export (uni/fabric/bgpInstP-default/rr); using every"
                + " spine");
      }
    }
    for (NodeState n : _nodes.values()) {
      boolean reflector = reflectors.contains(n._node.getId());
      if (!n.isLeaf() && !reflector) {
        continue;
      }
      Vrf underlay = n._c.getVrfs().get(UNDERLAY_VRF_NAME);
      BgpProcess bgp =
          BgpProcess.builder()
              .setVrf(underlay)
              .setRouterId(n._tep)
              .setEbgpAdminCost(DEFAULT_EBGP_ADMIN)
              .setIbgpAdminCost(DEFAULT_IBGP_ADMIN)
              .setLocalAdminCost(DEFAULT_LOCAL_BGP_ADMIN)
              .setLocalOriginationTypeTieBreaker(PREFER_NETWORK)
              .setNetworkNextHopIpTieBreaker(LOWEST_NEXT_HOP_IP)
              .setRedistributeNextHopIpTieBreaker(HIGHEST_NEXT_HOP_IP)
              .build();
      bgp.setMultipathIbgp(true);
      bgp.setMultipathEquivalentAsPathMatchMode(MultipathEquivalentAsPathMatchMode.EXACT_PATH);
      String exportPolicy = "~aci~fabric~evpn~export~";
      RoutingPolicy.builder()
          .setOwner(n._c)
          .setName(exportPolicy)
          .addStatement(Statements.ExitAccept.toStaticStatement())
          .build();
      List<NodeState> peers = new ArrayList<>();
      if (n.isLeaf()) {
        reflectors.forEach(id -> peers.add(_nodes.get(id)));
      } else {
        _nodes.values().stream().filter(NodeState::isLeaf).forEach(peers::add);
      }
      SortedSet<Layer3VniConfig> l3Vnis = layer3VniConfigs(n);
      for (NodeState peer : peers) {
        BgpActivePeerConfig.builder()
            .setBgpProcess(bgp)
            .setPeerAddress(peer._tep)
            .setLocalIp(n._tep)
            .setLocalAs(_fabricAsn)
            .setRemoteAs(_fabricAsn)
            .setDescription("fabric " + peer._hostname)
            .setEvpnAddressFamily(
                EvpnAddressFamily.builder()
                    .setAddressFamilyCapabilities(
                        AddressFamilyCapabilities.builder()
                            .setSendCommunity(true)
                            .setSendExtendedCommunity(true)
                            .build())
                    .setL2Vnis(ImmutableSet.of())
                    .setL3Vnis(l3Vnis)
                    .setNveIp(n._tep)
                    // Route reflectors keep routes for VRFs they do not have.
                    .setPropagateUnmatched(!n.isLeaf())
                    .setRouteReflectorClient(!n.isLeaf())
                    .setExportPolicy(exportPolicy)
                    .build())
            .build();
      }
    }
  }

  private @Nonnull SortedSet<Layer3VniConfig> layer3VniConfigs(NodeState n) {
    ImmutableSortedSet.Builder<Layer3VniConfig> configs = ImmutableSortedSet.naturalOrder();
    for (AciVrf vrf : n._tenantVrfs.keySet()) {
      int vni = _vrfVnis.get(vrf);
      ExtendedCommunity rt = routeTarget(vni);
      configs.add(
          Layer3VniConfig.builder()
              .setVni(vni)
              .setVrf(vrfName(vrf))
              .setRouteDistinguisher(routeDistinguisher(n, vrf))
              .setRouteTarget(rt)
              .setImportRouteTarget(rt.matchString())
              .setAdvertiseV4Unicast(true)
              .build());
    }
    return configs.build();
  }

  /** Tag on endpoint host routes. */
  static final long ENDPOINT_ROUTE_TAG = 0xACEL;

  private static final Ip SYNTHETIC_TEP_BASE = Ip.parse("10.255.0.0");
  private static final Ip SYNTHETIC_GIPO_BASE = Ip.parse("225.127.0.0");
  private static final int SYNTHETIC_VRF_VNI_BASE = 2_000_000;
  private static final int SYNTHETIC_BD_VNI_BASE = 15_000_000;

  private final @Nonnull AciConfiguration _aci;
  private final @Nonnull Warnings _w;
  private final @Nonnull Map<Integer, NodeState> _nodes;
  private final @Nonnull Set<Layer1Edge> _layer1Edges;
  private final @Nonnull Map<BridgeDomain, AciVrf> _bdVrfs;
  private final @Nonnull Map<Epg, BridgeDomain> _epgBds;
  private final @Nonnull Map<AciVrf, Integer> _vrfVnis;
  private final @Nonnull Map<AciVrf, Integer> _vrfIndexes;
  private final @Nonnull Map<BridgeDomain, Integer> _bdVnis;
  private final @Nonnull Map<BridgeDomain, Ip> _bdGroups;
  private final @Nonnull Map<String, Epg> _epgsByDn;
  private long _fabricAsn;
}
