package org.batfish.vendor.cisco_aci.representation;

import static org.batfish.datamodel.acl.AclLineMatchExprs.and;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchDst;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchSrc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
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
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.Warnings;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.ExprAclLine;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.InterfaceType;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpSpaceMetadata;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.TcpFlags;
import org.batfish.datamodel.TcpFlagsMatchConditions;
import org.batfish.datamodel.TraceElement;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.FalseExpr;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.acl.OrMatchExpr;
import org.batfish.datamodel.acl.TrueExpr;

/**
 * Compiles contracts into one zoning ACL per enforced VRF and applies it to traffic entering the
 * VRF's bridge-domain SVIs and L3Out interfaces on every leaf.
 *
 * <p>ACI classifies packets into EPGs ({@code pcTag}) by ingress port and VLAN, or by IP for
 * external and IP-based EPGs. Batfish has no packet class, so EPG membership is resolved to IP
 * space, in this order of precedence: ESG IP selectors, ESG EPG selectors, learned endpoints, uSeg
 * IP attributes, EPG subnets, subnets of single-EPG bridge domains, and external EPG subnets by
 * longest prefix. IPs inside other bridge-domain subnets are unclassified.
 *
 * <p>Rules are ordered by the zoning-rule priorities of the APIC model ({@code actrlRule.prio}),
 * deny before permit at the same priority.
 */
final class AciContracts {

  /** Zoning rule priorities from {@code actrlRule.prio}; lower applies first. */
  @VisibleForTesting
  enum Priority {
    CLASS_EQ_DENY(2),
    CLASS_EQ_ALLOW(3),
    BLACK_LIST(5),
    FULLY_QUALIFIED(7),
    SRC_DST_ANY(9),
    SRC_ANY_FILTER(13),
    ANY_DEST_FILTER(14),
    SRC_ANY_ANY(15),
    ANY_DEST_ANY(16),
    ANY_ANY_FILTER(17),
    PREFERRED_GROUP_PERMIT(20),
    ANY_ANY_ANY(21);

    Priority(int value) {
      _value = value;
    }

    int getValue() {
      return _value;
    }

    private final int _value;
  }

  /** An endpoint group of any kind, as a policy class. */
  private static final class PolicyClass {
    private PolicyClass(String dn, String type, String tenant, @Nullable String ap) {
      _dn = dn;
      _type = type;
      _tenant = tenant;
      _ap = ap;
      _ipSpaceName = "aci~" + dn;
      _prefixes = new ArrayList<>();
    }

    private final String _dn;
    private final String _type;
    private final String _tenant;
    private final @Nullable String _ap;
    private final String _ipSpaceName;

    /** (prefix, precedence rank) pairs classified into this class. */
    private final List<Map.Entry<Prefix, Integer>> _prefixes;

    private boolean _preferredGroupMember;
  }

  /** A zoning rule before ordering. */
  private static final class Rule {
    private Rule(
        Priority priority,
        LineAction action,
        @Nullable PolicyClass src,
        @Nullable PolicyClass dst,
        AclLineMatchExpr filter,
        String description) {
      _priority = priority;
      _action = action;
      _src = src;
      _dst = dst;
      _filter = filter;
      _description = description;
    }

    private final Priority _priority;
    private final LineAction _action;

    /** {@code null} means any. */
    private final @Nullable PolicyClass _src;

    private final @Nullable PolicyClass _dst;
    private final AclLineMatchExpr _filter;
    private final String _description;
  }

  // Classification precedence ranks; lower wins, then longest prefix.
  private static final int RANK_ESG_IP = 1;
  private static final int RANK_ESG_EPG = 2;
  private static final int RANK_ENDPOINT = 3;
  private static final int RANK_USEG = 4;
  private static final int RANK_EPG_SUBNET = 5;
  private static final int RANK_BD_SUBNET = 6;
  private static final int RANK_EXTERNAL = 7;

  AciContracts(AciConfiguration aci, Warnings w, AciConversion conversion) {
    _aci = aci;
    _w = w;
    _conversion = conversion;
  }

  void apply() {
    Set<AciVrf> vrfs = new LinkedHashSet<>();
    _aci.getTenants().values().forEach(t -> vrfs.addAll(t.getVrfs().values()));
    for (AciVrf vrf : vrfs) {
      Map<Configuration, Vrf> deployed = _conversion.deployedVrfs(vrf);
      if (deployed.isEmpty() || !vrf.isEnforced()) {
        continue;
      }
      compileVrf(vrf, deployed);
    }
  }

  /** The name of the zoning ACL of a VRF. */
  static @Nonnull String zoningAclName(AciVrf vrf) {
    return "aci-zoning~" + AciConversion.vrfName(vrf);
  }

  private void compileVrf(AciVrf vrf, Map<Configuration, Vrf> deployed) {
    Map<Object, PolicyClass> classes = classify(vrf);
    List<Rule> rules = new ArrayList<>();
    addIntraClassRules(classes, rules);
    addTabooRules(classes, rules);
    addContractRules(vrf, classes, rules);
    if (vrf.isPreferredGroupEnabled()) {
      for (PolicyClass src : classes.values()) {
        for (PolicyClass dst : classes.values()) {
          if (src._preferredGroupMember && dst._preferredGroupMember && src != dst) {
            rules.add(
                new Rule(
                    Priority.PREFERRED_GROUP_PERMIT,
                    LineAction.PERMIT,
                    src,
                    dst,
                    TrueExpr.INSTANCE,
                    "preferred group"));
          }
        }
      }
    }
    // Stable sort: priority, then deny before permit.
    rules.sort(
        Comparator.comparing((Rule r) -> r._priority.getValue())
            .thenComparing(r -> r._action == LineAction.PERMIT));

    // Class IP spaces are the same on every leaf; compute them once.
    List<PolicyClass> all = new ArrayList<>(classes.values());
    if (_unclassified != null) {
      all.add(_unclassified);
    }
    Map<PolicyClass, IpSpace> spaces = new LinkedHashMap<>();
    classes.values().forEach(pc -> spaces.put(pc, classIpSpace(pc, all)));
    for (Map.Entry<Configuration, Vrf> e : deployed.entrySet()) {
      installAcl(e.getKey(), e.getValue(), vrf, spaces, rules);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Classification

  /** Returns the policy classes of {@code vrf}, keyed by the model object. */
  private @Nonnull Map<Object, PolicyClass> classify(AciVrf vrf) {
    Map<Object, PolicyClass> classes = new LinkedHashMap<>();
    // EPGs per bridge domain, to find single-EPG bridge domains
    Map<BridgeDomain, List<Epg>> epgsByBd = new HashMap<>();
    for (Tenant tenant : _aci.getTenants().values()) {
      for (Epg epg : tenant.getEpgs().values()) {
        BridgeDomain bd = _conversion.bridgeDomainOf(epg);
        if (bd == null || _conversion.vrfOf(bd) != vrf) {
          continue;
        }
        epgsByBd.computeIfAbsent(bd, b -> new ArrayList<>()).add(epg);
        PolicyClass c =
            new PolicyClass(epg.getDn(), "EPG", epg.getTenant(), epg.getApplicationProfile());
        c._preferredGroupMember = epg.isPreferredGroupMember();
        epg.getEndpoints()
            .forEach(
                ep ->
                    ep.getIps()
                        .forEach(ip -> c._prefixes.add(entry(ip.toPrefix(), RANK_ENDPOINT))));
        epg.getIpAttributes().forEach(p -> c._prefixes.add(entry(p, RANK_USEG)));
        epg.getSubnets().forEach(s -> c._prefixes.add(entry(s.getPrefix(), RANK_EPG_SUBNET)));
        classes.put(epg, c);
      }
    }
    List<String> unclassifiedBds = new ArrayList<>();
    epgsByBd.forEach(
        (bd, epgs) -> {
          if (epgs.size() == 1) {
            PolicyClass c = classes.get(epgs.get(0));
            bd.getSubnets().forEach(s -> c._prefixes.add(entry(s.getPrefix(), RANK_BD_SUBNET)));
          } else if (epgs.stream().anyMatch(epg -> epg.getEndpoints().isEmpty())) {
            unclassifiedBds.add(bd.getDn());
          }
        });
    if (!unclassifiedBds.isEmpty()) {
      _w.redFlagf(
          "VRF %s: bridge domains %s have several EPGs and some lack endpoint data; IPs not"
              + " classified by endpoints or EPG subnets match only vzAny rules",
          AciConversion.vrfName(vrf), unclassifiedBds);
    }
    // ESGs scoped to this VRF take endpoints away from their EPGs.
    for (Tenant tenant : _aci.getTenants().values()) {
      for (Esg esg : tenant.getEsgs().values()) {
        if (_conversion.resolveVrf(esg.getTenant(), esg.getVrf()) != vrf) {
          continue;
        }
        PolicyClass c =
            new PolicyClass(esg.getDn(), "ESG", esg.getTenant(), esg.getApplicationProfile());
        c._preferredGroupMember = esg.isPreferredGroupMember();
        esg.getIpSelectors().forEach(p -> c._prefixes.add(entry(p, RANK_ESG_IP)));
        for (String epgDn : esg.getEpgSelectors()) {
          PolicyClass epgClass = classes.get(_conversion.epgByDn(epgDn));
          if (epgClass != null) {
            epgClass._prefixes.forEach(p -> c._prefixes.add(entry(p.getKey(), RANK_ESG_EPG)));
          }
        }
        classes.put(esg, c);
      }
      for (L3Out l3Out : tenant.getL3Outs().values()) {
        if (_conversion.resolveVrf(l3Out.getTenant(), l3Out.getVrf()) != vrf) {
          continue;
        }
        for (ExternalEpg epg : l3Out.getExternalEpgs()) {
          PolicyClass c = new PolicyClass(epg.getDn(), "External EPG", epg.getTenant(), null);
          c._preferredGroupMember = epg.isPreferredGroupMember();
          epg.getSubnets().stream()
              .filter(s -> s.getScope().contains(ExternalSubnet.IMPORT_SECURITY))
              .forEach(s -> c._prefixes.add(entry(s.getPrefix(), RANK_EXTERNAL)));
          classes.put(epg, c);
        }
      }
    }
    // Bridge-domain subnets with several EPGs belong to no external EPG.
    PolicyClass unclassified =
        new PolicyClass("unclassified", "unclassified", vrf.getTenant(), null);
    epgsByBd.forEach(
        (bd, epgs) -> {
          if (epgs.size() > 1) {
            bd.getSubnets()
                .forEach(s -> unclassified._prefixes.add(entry(s.getPrefix(), RANK_BD_SUBNET)));
          }
        });
    _unclassified = unclassified;
    return classes;
  }

  private static Map.Entry<Prefix, Integer> entry(Prefix prefix, int rank) {
    return Map.entry(prefix, rank);
  }

  /**
   * The IP space of {@code c}: an ordered first-match list over every classification entry that
   * overlaps one of {@code c}'s, by rank and then longest prefix.
   */
  private @Nonnull IpSpace classIpSpace(PolicyClass c, Iterable<PolicyClass> all) {
    if (c._prefixes.isEmpty()) {
      return EmptyIpSpace.INSTANCE;
    }
    List<Map.Entry<Map.Entry<Prefix, Integer>, Boolean>> entries = new ArrayList<>();
    c._prefixes.forEach(p -> entries.add(Map.entry(p, true)));
    for (PolicyClass other : all) {
      if (other == c) {
        continue;
      }
      for (Map.Entry<Prefix, Integer> p : other._prefixes) {
        if (c._prefixes.stream().anyMatch(mine -> overlapsBefore(p, mine))) {
          entries.add(Map.entry(p, false));
        }
      }
    }
    entries.sort(
        Comparator.comparing(
                (Map.Entry<Map.Entry<Prefix, Integer>, Boolean> e) -> e.getKey().getValue())
            .thenComparing(e -> -e.getKey().getKey().getPrefixLength())
            // at an exact tie, the other class wins only if it sorts first; prefer ours
            .thenComparing(e -> !e.getValue()));
    AclIpSpace.Builder space = AclIpSpace.builder();
    Set<Map.Entry<Prefix, Integer>> seen = new HashSet<>();
    for (Map.Entry<Map.Entry<Prefix, Integer>, Boolean> e : entries) {
      if (!seen.add(e.getKey())) {
        continue;
      }
      space.thenAction(
          e.getValue() ? LineAction.PERMIT : LineAction.DENY, e.getKey().getKey().toIpSpace());
    }
    return space.build();
  }

  /** Whether {@code other} overlaps {@code mine} and takes precedence over it. */
  private static boolean overlapsBefore(
      Map.Entry<Prefix, Integer> other, Map.Entry<Prefix, Integer> mine) {
    Prefix o = other.getKey();
    Prefix m = mine.getKey();
    boolean overlaps = o.containsPrefix(m) || m.containsPrefix(o);
    if (!overlaps) {
      return false;
    }
    if (!other.getValue().equals(mine.getValue())) {
      return other.getValue() < mine.getValue();
    }
    return o.getPrefixLength() >= m.getPrefixLength();
  }

  // ---------------------------------------------------------------------------------------------
  // Rules

  private static void addIntraClassRules(Map<Object, PolicyClass> classes, List<Rule> rules) {
    for (Map.Entry<Object, PolicyClass> e : classes.entrySet()) {
      PolicyClass c = e.getValue();
      boolean isolated = e.getKey() instanceof Epg epg && epg.isIntraEpgIsolation();
      rules.add(
          new Rule(
              isolated ? Priority.CLASS_EQ_DENY : Priority.CLASS_EQ_ALLOW,
              isolated ? LineAction.DENY : LineAction.PERMIT,
              c,
              c,
              TrueExpr.INSTANCE,
              isolated ? "intra-EPG isolation" : "intra-EPG traffic"));
    }
  }

  private void addTabooRules(Map<Object, PolicyClass> classes, List<Rule> rules) {
    for (Map.Entry<Object, PolicyClass> e : classes.entrySet()) {
      ContractRelations relations = relationsOf(e.getKey());
      if (relations == null) {
        continue;
      }
      PolicyClass c = e.getValue();
      for (NamedRef ref : relations.getTaboos()) {
        TabooContract taboo = _conversion.resolveTaboo(c._tenant, ref);
        if (taboo == null) {
          _w.redFlagf("%s %s references unknown taboo contract %s", c._type, c._dn, ref);
          continue;
        }
        for (NamedRef filterRef : taboo.getDenyFilters()) {
          AclLineMatchExpr filter = filterExpr(taboo.getTenant(), filterRef, false, false);
          String description = "taboo " + taboo.getName() + " filter " + filterRef.getName();
          rules.add(new Rule(Priority.BLACK_LIST, LineAction.DENY, null, c, filter, description));
          rules.add(new Rule(Priority.BLACK_LIST, LineAction.DENY, c, null, filter, description));
        }
      }
    }
  }

  private static @Nullable ContractRelations relationsOf(Object group) {
    if (group instanceof Epg epg) {
      return epg.getContracts();
    } else if (group instanceof Esg esg) {
      return esg.getContracts();
    } else if (group instanceof ExternalEpg epg) {
      return epg.getContracts();
    }
    return null;
  }

  private void addContractRules(AciVrf vrf, Map<Object, PolicyClass> classes, List<Rule> rules) {
    // contract -> providers and consumers; null stands for vzAny
    Map<Contract, Set<PolicyClass>> providers = new LinkedHashMap<>();
    Map<Contract, Set<PolicyClass>> consumers = new LinkedHashMap<>();
    Set<Contract> anyProvided = new HashSet<>();
    Set<Contract> anyConsumed = new HashSet<>();
    for (Map.Entry<Object, PolicyClass> e : classes.entrySet()) {
      ContractRelations relations = relationsOf(e.getKey());
      PolicyClass c = e.getValue();
      if (relations == null) {
        continue;
      }
      for (NamedRef ref : relations.getProvided()) {
        Contract contract = resolveOrWarn(c, ref);
        if (contract != null) {
          providers.computeIfAbsent(contract, k -> new LinkedHashSet<>()).add(c);
        }
      }
      for (NamedRef ref : relations.getConsumed()) {
        Contract contract = resolveOrWarn(c, ref);
        if (contract != null) {
          consumers.computeIfAbsent(contract, k -> new LinkedHashSet<>()).add(c);
        }
      }
      for (NamedRef ref : relations.getConsumedInterfaces()) {
        Contract contract = resolveContractInterface(c._tenant, ref);
        if (contract == null) {
          _w.redFlagf("%s %s consumes unknown contract interface %s", c._type, c._dn, ref);
        } else {
          consumers.computeIfAbsent(contract, k -> new LinkedHashSet<>()).add(c);
        }
      }
    }
    for (NamedRef ref : vrf.getAnyProvided()) {
      Contract contract = _conversion.resolveContract(vrf.getTenant(), ref);
      if (contract != null) {
        anyProvided.add(contract);
      }
    }
    for (NamedRef ref : vrf.getAnyConsumed()) {
      Contract contract = _conversion.resolveContract(vrf.getTenant(), ref);
      if (contract != null) {
        anyConsumed.add(contract);
      }
    }
    Set<Contract> contracts = new LinkedHashSet<>();
    contracts.addAll(providers.keySet());
    contracts.addAll(consumers.keySet());
    contracts.addAll(anyProvided);
    contracts.addAll(anyConsumed);
    for (Contract contract : contracts) {
      List<PolicyClass> provs = new ArrayList<>(providers.getOrDefault(contract, Set.of()));
      List<PolicyClass> cons = new ArrayList<>(consumers.getOrDefault(contract, Set.of()));
      if (anyProvided.contains(contract)) {
        provs.add(null);
      }
      if (anyConsumed.contains(contract)) {
        cons.add(null);
      }
      for (PolicyClass consumer : cons) {
        for (PolicyClass provider : provs) {
          if (consumer != null && provider != null && !inScope(contract, consumer, provider)) {
            continue;
          }
          addSubjectRules(contract, consumer, provider, rules);
        }
      }
    }
  }

  private @Nullable Contract resolveOrWarn(PolicyClass c, NamedRef ref) {
    Contract contract = _conversion.resolveContract(c._tenant, ref);
    if (contract == null) {
      _w.redFlagf("%s %s references unknown contract %s", c._type, c._dn, ref);
    }
    return contract;
  }

  private @Nullable Contract resolveContractInterface(String tenant, NamedRef ref) {
    Tenant t = _aci.getTenants().get(tenant);
    String contractDn = t == null ? null : t.getContractInterfaces().get(ref.getName());
    if (contractDn == null) {
      Tenant common = _aci.getTenants().get(Tenant.COMMON);
      contractDn = common == null ? null : common.getContractInterfaces().get(ref.getName());
    }
    return contractDn == null
        ? null
        : _conversion.resolveContract(tenant, new NamedRef("", contractDn));
  }

  /** Whether a contract's scope connects the consumer and provider. */
  private static boolean inScope(Contract contract, PolicyClass consumer, PolicyClass provider) {
    return switch (contract.getScope()) {
      case APPLICATION_PROFILE ->
          consumer._tenant.equals(provider._tenant)
              && (consumer._ap == null
                  || provider._ap == null
                  || Objects.equals(consumer._ap, provider._ap));
      case TENANT -> consumer._tenant.equals(provider._tenant);
      case CONTEXT, GLOBAL -> true; // both are in this VRF
    };
  }

  private void addSubjectRules(
      Contract contract,
      @Nullable PolicyClass consumer,
      @Nullable PolicyClass provider,
      List<Rule> rules) {
    for (ContractSubject subject : contract.getSubjects()) {
      String base = "contract " + contract.getName() + " subject " + subject.getName();
      for (FilterRef f : subject.getFilters()) {
        // consumer to provider as written
        addFilterRule(contract, f, consumer, provider, false, false, base, rules);
        // provider to consumer: reversed ports when revFltPorts=yes; stateful entries need ACK
        addFilterRule(
            contract,
            f,
            provider,
            consumer,
            subject.isReverseFilterPorts(),
            subject.isReverseFilterPorts(),
            base,
            rules);
      }
      for (FilterRef f : subject.getConsumerToProviderFilters()) {
        addFilterRule(contract, f, consumer, provider, false, false, base, rules);
      }
      for (FilterRef f : subject.getProviderToConsumerFilters()) {
        addFilterRule(contract, f, provider, consumer, false, true, base, rules);
      }
    }
  }

  private void addFilterRule(
      Contract contract,
      FilterRef filterRef,
      @Nullable PolicyClass src,
      @Nullable PolicyClass dst,
      boolean reversePorts,
      boolean returnTraffic,
      String base,
      List<Rule> rules) {
    AciFilter filter = _conversion.resolveFilter(contract.getTenant(), filterRef.getFilter());
    if (filter == null) {
      _w.redFlagf(
          "Contract %s references unknown filter %s", contract.getDn(), filterRef.getFilter());
      return;
    }
    if (!filterRef.getPriorityOverride().equals("default")) {
      _w.redFlagf(
          "Priority override %s on filter %s of contract %s is not modeled",
          filterRef.getPriorityOverride(), filter.getName(), contract.getDn());
    }
    boolean anyFilter = filter.getEntries().stream().anyMatch(AciContracts::matchesEverything);
    Priority priority;
    if (src != null && dst != null) {
      priority = anyFilter ? Priority.SRC_DST_ANY : Priority.FULLY_QUALIFIED;
    } else if (src != null) {
      priority = anyFilter ? Priority.SRC_ANY_ANY : Priority.SRC_ANY_FILTER;
    } else if (dst != null) {
      priority = anyFilter ? Priority.ANY_DEST_ANY : Priority.ANY_DEST_FILTER;
    } else {
      priority = anyFilter ? Priority.ANY_ANY_ANY : Priority.ANY_ANY_FILTER;
    }
    String description =
        String.format(
            "%s filter %s (%s to %s)",
            base, filter.getName(), src == null ? "any" : src._dn, dst == null ? "any" : dst._dn);
    rules.add(
        new Rule(
            priority,
            filterRef.getAction(),
            src,
            dst,
            filterEntriesExpr(filter, reversePorts, returnTraffic),
            description));
  }

  private @Nonnull AclLineMatchExpr filterExpr(
      String tenant, NamedRef filterRef, boolean reversePorts, boolean returnTraffic) {
    AciFilter filter = _conversion.resolveFilter(tenant, filterRef);
    if (filter == null) {
      _w.redFlagf("Taboo contract references unknown filter %s", filterRef);
      return FalseExpr.INSTANCE;
    }
    return filterEntriesExpr(filter, reversePorts, returnTraffic);
  }

  private static boolean matchesEverything(FilterEntry entry) {
    return (entry.getEtherType() == FilterEntry.EtherType.ANY)
        && entry.getIpProtocol() == null
        && entry.getSrcFromPort() == null
        && entry.getDstFromPort() == null
        && entry.getIcmpType() == null
        && entry.getTcpRules().isEmpty();
  }

  /** An expression matching any entry of {@code filter}. */
  @VisibleForTesting
  static @Nonnull AclLineMatchExpr filterEntriesExpr(
      AciFilter filter, boolean reversePorts, boolean returnTraffic) {
    List<AclLineMatchExpr> entries = new ArrayList<>();
    for (FilterEntry entry : filter.getEntries()) {
      AclLineMatchExpr expr = entryExpr(entry, reversePorts, returnTraffic);
      if (expr != FalseExpr.INSTANCE) {
        entries.add(expr);
      }
    }
    if (entries.isEmpty()) {
      return FalseExpr.INSTANCE;
    }
    return entries.size() == 1
        ? entries.get(0)
        : new OrMatchExpr(entries, TraceElement.of("filter " + filter.getName()));
  }

  /** An expression matching IPv4 packets that a filter entry matches. */
  @VisibleForTesting
  static @Nonnull AclLineMatchExpr entryExpr(
      FilterEntry entry, boolean reversePorts, boolean returnTraffic) {
    switch (entry.getEtherType()) {
      case ANY, IP, IPV4 -> {}
      default -> {
        return FalseExpr.INSTANCE; // IPv6, ARP and other Ethernet types never match IPv4 flows
      }
    }
    HeaderSpace.Builder hs = HeaderSpace.builder();
    Integer protocol = entry.getIpProtocol();
    if (protocol != null) {
      hs.setIpProtocols(IpProtocol.fromNumber(protocol));
    }
    SubRange srcPorts = portRange(entry.getSrcFromPort(), entry.getSrcToPort());
    SubRange dstPorts = portRange(entry.getDstFromPort(), entry.getDstToPort());
    if (reversePorts) {
      SubRange tmp = srcPorts;
      srcPorts = dstPorts;
      dstPorts = tmp;
    }
    if (srcPorts != null) {
      hs.setSrcPorts(srcPorts);
    }
    if (dstPorts != null) {
      hs.setDstPorts(dstPorts);
    }
    if (entry.getIcmpType() != null) {
      hs.setIcmpTypes(entry.getIcmpType());
    }
    List<TcpFlagsMatchConditions> flags = tcpFlagConditions(entry, returnTraffic);
    if (!flags.isEmpty()) {
      hs.setTcpFlags(flags);
    }
    return new MatchHeaderSpace(hs.build(), TraceElement.of("filter entry " + entry.getName()));
  }

  private static @Nullable SubRange portRange(@Nullable Integer from, @Nullable Integer to) {
    if (from == null && to == null) {
      return null;
    }
    int start = from != null ? from : to;
    int end = to != null ? to : start;
    return new SubRange(Math.min(start, end), Math.max(start, end));
  }

  private static @Nonnull List<TcpFlagsMatchConditions> tcpFlagConditions(
      FilterEntry entry, boolean returnTraffic) {
    ImmutableList.Builder<TcpFlagsMatchConditions> conditions = ImmutableList.builder();
    Set<String> rules = new TreeSet<>(entry.getTcpRules());
    if (returnTraffic && entry.isStateful()) {
      // Stateful entries permit provider-to-consumer TCP only with ACK set.
      rules.add("ack");
    }
    if (rules.contains("est")) {
      // "established": ACK or RST set
      conditions.add(flagSet(TcpFlags.builder().setAck(true).build(), true, false, false, false));
      conditions.add(flagSet(TcpFlags.builder().setRst(true).build(), false, true, false, false));
      rules.remove("est");
      if (rules.isEmpty()) {
        return conditions.build();
      }
    }
    if (rules.isEmpty()) {
      return ImmutableList.of();
    }
    TcpFlags.Builder flags = TcpFlags.builder();
    boolean ack = rules.contains("ack");
    boolean rst = rules.contains("rst");
    boolean syn = rules.contains("syn");
    boolean fin = rules.contains("fin");
    flags.setAck(ack).setRst(rst).setSyn(syn).setFin(fin);
    return ImmutableList.of(flagSet(flags.build(), ack, rst, syn, fin));
  }

  private static TcpFlagsMatchConditions flagSet(
      TcpFlags flags, boolean useAck, boolean useRst, boolean useSyn, boolean useFin) {
    return TcpFlagsMatchConditions.builder()
        .setTcpFlags(flags)
        .setUseAck(useAck)
        .setUseRst(useRst)
        .setUseSyn(useSyn)
        .setUseFin(useFin)
        .build();
  }

  // ---------------------------------------------------------------------------------------------
  // Installation

  private static void installAcl(
      Configuration c, Vrf viVrf, AciVrf vrf, Map<PolicyClass, IpSpace> spaces, List<Rule> rules) {
    // Named IP spaces, so traces read "permitted by EPG uni/tn-x/ap-y/epg-z"
    spaces.forEach(
        (pc, space) -> {
          c.getIpSpaces().put(pc._ipSpaceName, space);
          c.getIpSpaceMetadata().put(pc._ipSpaceName, new IpSpaceMetadata(pc._dn, pc._type, null));
        });
    ImmutableList.Builder<AclLine> lines = ImmutableList.builder();
    // Traffic to the leaf itself (routing protocols, pings to gateways) is not subject to
    // contracts.
    List<IpSpace> owned = new ArrayList<>();
    for (Interface iface : c.getAllInterfaces(viVrf.getName()).values()) {
      iface.getAllConcreteAddresses().forEach(a -> owned.add(a.getIp().toIpSpace()));
    }
    if (!owned.isEmpty()) {
      lines.add(
          ExprAclLine.builder()
              .setAction(LineAction.PERMIT)
              .setMatchCondition(matchDst(AclIpSpace.union(owned)))
              .setName("traffic to the leaf")
              .setTraceElement(TraceElement.of("traffic to the leaf itself"))
              .build());
    }
    for (Rule rule : rules) {
      List<AclLineMatchExpr> conjuncts = new ArrayList<>();
      if (rule._src != null) {
        conjuncts.add(matchSrc(classRef(rule._src)));
      }
      if (rule._dst != null) {
        conjuncts.add(matchDst(classRef(rule._dst)));
      }
      if (rule._filter != TrueExpr.INSTANCE) {
        conjuncts.add(rule._filter);
      }
      AclLineMatchExpr match =
          conjuncts.isEmpty()
              ? TrueExpr.INSTANCE
              : conjuncts.size() == 1 ? conjuncts.get(0) : and(conjuncts);
      lines.add(
          ExprAclLine.builder()
              .setAction(rule._action)
              .setMatchCondition(match)
              .setName(rule._description)
              .setTraceElement(
                  TraceElement.of(
                      String.format(
                          "%s by %s (priority %s)",
                          rule._action == LineAction.PERMIT ? "Permitted" : "Denied",
                          rule._description,
                          rule._priority.name().toLowerCase().replace('_', '-'))))
              .build());
    }
    lines.add(
        ExprAclLine.builder()
            .setAction(LineAction.DENY)
            .setMatchCondition(TrueExpr.INSTANCE)
            .setName("implicit deny")
            .setTraceElement(TraceElement.of("Denied by the VRF's implicit deny"))
            .build());
    IpAccessList acl =
        IpAccessList.builder()
            .setOwner(c)
            .setName(zoningAclName(vrf))
            .setLines(lines.build())
            .build();
    for (Interface iface : c.getAllInterfaces(viVrf.getName()).values()) {
      if (iface.getInterfaceType() == InterfaceType.VLAN
          || iface.getInterfaceType() == InterfaceType.PHYSICAL
          || iface.getInterfaceType() == InterfaceType.LOGICAL
          || iface.getInterfaceType() == InterfaceType.AGGREGATED) {
        iface.setIncomingFilter(acl);
      }
    }
  }

  private static @Nonnull IpSpace classRef(PolicyClass c) {
    return new IpSpaceReference(c._ipSpaceName, c._type + " " + c._dn);
  }

  private final @Nonnull AciConfiguration _aci;
  private final @Nonnull Warnings _w;
  private final @Nonnull AciConversion _conversion;

  /** Bridge-domain subnets of the VRF being compiled that no class claims. */
  private @Nullable PolicyClass _unclassified;
}
