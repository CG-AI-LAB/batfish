package org.batfish.vendor.cisco_aci.representation;

import static org.batfish.vendor.cisco_aci.representation.AciDn.parseIntOrNull;
import static org.batfish.vendor.cisco_aci.representation.AciDn.parseLongOrNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.vendor.cisco_aci.representation.AccessPolicies.AaepEpgBinding;
import org.batfish.vendor.cisco_aci.representation.AccessPolicies.BundleType;
import org.batfish.vendor.cisco_aci.representation.AccessPolicies.LeafProfile;
import org.batfish.vendor.cisco_aci.representation.AccessPolicies.PolicyGroup;
import org.batfish.vendor.cisco_aci.representation.AccessPolicies.PortSelector;

/**
 * Builds an {@link AciConfiguration} from {@link AciMo} trees.
 *
 * <p>Class and attribute names follow the APIC Management Information Model. Objects of classes the
 * model does not use are skipped silently below known containers; unknown top-level objects produce
 * a warning.
 */
public final class AciModelExtractor {

  public AciModelExtractor(AciConfiguration config, Consumer<String> warn) {
    _c = config;
    _warn = warn;
    _warnedClasses = new HashSet<>();
  }

  /** Whether {@code root} holds runtime state, extracted after all configuration. */
  public static boolean isRuntimeObject(AciMo root) {
    return RUNTIME_CLASSES.contains(root.getClassName());
  }

  /** Extracts one top-level object from a REST response or configuration export. */
  public void extract(AciMo root) {
    String dn = root.getAttribute("dn");
    switch (root.getClassName()) {
      case "polUni" -> root.getChildren().forEach(this::extractUniChild);
      case "fvTenant", "infraInfra", "fabricInst", "ctrlrInst" -> extractUniChild(root);
      case "fabricNode" -> extractFabricNode(root, dn);
      case "topSystem" -> extractTopSystem(root, dn);
      case "fabricNodeIdentP" -> extractNodeIdentity(root);
      case "fabricExplicitGEp" -> extractVpcPair(root);
      case "bgpInstPol" -> extractFabricBgp(root);
      case "lldpAdjEp" -> extractLldpAdjacency(root, dn);
      case "pcAggrIf" -> extractPortChannel(root, dn);
      case "fvCEp" -> extractTopLevelEndpoint(root, dn);
      case "actrlRule", "actrlFlt", "actrlEntry" -> {
        // Compiled zoning rules: used to validate contract conversion, not modeled.
      }
      case "error" ->
          _warn.accept(
              String.format(
                  "APIC returned an error instead of data: %s",
                  root.getAttribute("text", root.getAttributes().toString())));
      default -> {
        if (dn != null && dn.startsWith("uni/tn-")) {
          extractTopLevelTenantObject(root, dn);
        } else {
          warnUnrecognized(root.getClassName());
        }
      }
    }
  }

  private void warnUnrecognized(String className) {
    if (_warnedClasses.add(className)) {
      _warn.accept(String.format("Ignoring unsupported top-level APIC class %s", className));
    }
  }

  /** Handles tenant objects returned on their own by a class query, using their DN for context. */
  private void extractTopLevelTenantObject(AciMo mo, String dn) {
    String tenantName = AciDn.value(dn, "tn-");
    if (tenantName == null) {
      warnUnrecognized(mo.getClassName());
      return;
    }
    Tenant tenant = _c.getTenants().computeIfAbsent(tenantName, Tenant::new);
    switch (mo.getClassName()) {
      case "fvAEPg", "fvESg" -> {
        String ap = AciDn.value(dn, "ap-");
        if (ap == null) {
          warnUnrecognized(mo.getClassName());
        } else if (mo.getClassName().equals("fvAEPg")) {
          extractEpg(tenant, ap, mo);
        } else {
          extractEsg(tenant, ap, mo);
        }
      }
      case "l3extInstP" -> {
        String l3OutName = AciDn.value(dn, "out-");
        L3Out l3Out = l3OutName == null ? null : tenant.getL3Outs().get(l3OutName);
        if (l3Out == null) {
          _warn.accept(String.format("External EPG %s belongs to an unknown L3Out", dn));
        } else {
          l3Out.getExternalEpgs().add(extractExternalEpg(tenantName, l3OutName, mo));
        }
      }
      default -> {
        if (!extractTenantChild(tenant, mo)) {
          warnUnrecognized(mo.getClassName());
        }
      }
    }
  }

  private void extractUniChild(AciMo child) {
    switch (child.getClassName()) {
      case "fvTenant" -> extractTenant(child);
      case "infraInfra" -> extractInfra(child);
      case "fabricInst" -> extractFabricInst(child);
      case "ctrlrInst" -> extractControllerInst(child);
      default -> {
        // Other policy-universe content (AAA, monitoring, VMM, etc.) does not affect forwarding.
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Tenants

  private void extractTenant(AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      _warn.accept("Ignoring fvTenant without a name");
      return;
    }
    Tenant tenant = _c.getTenants().computeIfAbsent(name, Tenant::new);
    for (AciMo child : mo.getChildren()) {
      extractTenantChild(tenant, child);
    }
  }

  /** Extracts a direct child of a tenant. Returns whether the class is one this model uses. */
  private boolean extractTenantChild(Tenant tenant, AciMo child) {
    switch (child.getClassName()) {
      case "fvCtx" -> extractVrf(tenant, child);
      case "fvBD" -> extractBridgeDomain(tenant, child);
      case "fvAp" -> extractApplicationProfile(tenant, child);
      case "vzBrCP" -> extractContract(tenant, child);
      case "vzFilter" -> extractFilter(tenant, child);
      case "vzTaboo" -> extractTaboo(tenant, child);
      case "vzCPIf" -> extractContractInterface(tenant, child);
      case "l3extOut" -> extractL3Out(tenant, child);
      default -> {
        return false;
      }
    }
    return true;
  }

  private void extractVrf(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    AciVrf vrf = new AciVrf(tenant.getName(), name);
    Long vnid = parseLongOrNull(mo.getAttribute("scope"));
    if (vnid == null || vnid == 0) {
      vnid = parseLongOrNull(mo.getAttribute("seg"));
    }
    vrf.setVnid(vnid == null || vnid == 0 ? null : vnid);
    vrf.setEnforced(!"unenforced".equals(mo.getAttribute("pcEnfPref")));
    mo.getChild("vzAny")
        .ifPresent(
            any -> {
              vrf.setPreferredGroupEnabled("enabled".equals(any.getAttribute("prefGrMemb")));
              any.getChildren("vzRsAnyToProv")
                  .forEach(r -> addRef(vrf.getAnyProvided(), r, "tnVzBrCPName"));
              any.getChildren("vzRsAnyToCons")
                  .forEach(r -> addRef(vrf.getAnyConsumed(), r, "tnVzBrCPName"));
            });
    tenant.getVrfs().put(name, vrf);
  }

  private void extractBridgeDomain(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    BridgeDomain bd = new BridgeDomain(tenant.getName(), name);
    Long vnid = parseLongOrNull(mo.getAttribute("seg"));
    bd.setVnid(vnid == null || vnid == 0 ? null : vnid);
    bd.setMulticastGroup(parseIp(mo.getAttribute("bcastP"), "bcastP of BD " + bd.getDn()));
    bd.setUnicastRoute(!"no".equals(mo.getAttribute("unicastRoute")));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "fvRsCtx" -> bd.setVrf(toRef(child, "tnFvCtxName"));
        case "fvSubnet" -> {
          AciSubnet subnet = extractSubnet(child, bd.getDn());
          if (subnet != null) {
            bd.getSubnets().add(subnet);
          }
        }
        case "fvRsBDToOut" -> {
          String l3Out = child.getAttribute("tnL3extOutName");
          if (l3Out != null) {
            bd.getL3Outs().add(l3Out);
          }
        }
        default -> {}
      }
    }
    tenant.getBridgeDomains().put(name, bd);
  }

  private @Nullable AciSubnet extractSubnet(AciMo mo, String parentDn) {
    String ip = mo.getAttribute("ip");
    ConcreteInterfaceAddress gateway = parseInterfaceAddress(ip, "subnet of " + parentDn);
    if (gateway == null) {
      return null;
    }
    AciSubnet subnet = new AciSubnet(gateway);
    Set<String> scope = flags(mo.getAttribute("scope"));
    subnet.setPublic(scope.contains("public"));
    subnet.setShared(scope.contains("shared"));
    subnet.setPreferred("yes".equals(mo.getAttribute("preferred")));
    subnet.setNoDefaultGateway(flags(mo.getAttribute("ctrl")).contains("no-default-gateway"));
    return subnet;
  }

  private void extractApplicationProfile(Tenant tenant, AciMo mo) {
    String ap = mo.getAttribute("name");
    if (ap == null) {
      return;
    }
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "fvAEPg" -> extractEpg(tenant, ap, child);
        case "fvESg" -> extractEsg(tenant, ap, child);
        default -> {}
      }
    }
  }

  private void extractEpg(Tenant tenant, String ap, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    Epg epg = new Epg(tenant.getName(), ap, name);
    epg.setIntraEpgIsolation("enforced".equals(mo.getAttribute("pcEnfPref")));
    epg.setPreferredGroupMember("include".equals(mo.getAttribute("prefGrMemb")));
    for (AciMo child : mo.getChildren()) {
      if (extractContractRelation(epg.getContracts(), child)) {
        continue;
      }
      switch (child.getClassName()) {
        case "fvRsBd" -> epg.setBridgeDomain(toRef(child, "tnFvBDName"));
        case "fvRsPathAtt" -> {
          PathRef path = PathRef.parse(child.getAttribute("tDn"));
          if (path == null) {
            _warn.accept(
                String.format(
                    "Ignoring unsupported static path %s on EPG %s",
                    child.getAttribute("tDn"), epg.getDn()));
          } else {
            epg.getStaticPaths()
                .add(
                    new StaticPath(
                        path,
                        AciDn.vlan(child.getAttribute("encap")),
                        StaticPath.Mode.fromString(child.getAttribute("mode"))));
          }
        }
        case "fvSubnet" -> {
          AciSubnet subnet = extractSubnet(child, epg.getDn());
          if (subnet != null) {
            epg.getSubnets().add(subnet);
          }
        }
        case "fvCrtrn" -> extractIpAttributes(child, epg);
        case "fvCEp" -> {
          Endpoint endpoint = extractEndpoint(child);
          if (endpoint != null) {
            epg.getEndpoints().add(endpoint);
          }
        }
        default -> {}
      }
    }
    Epg previous = tenant.getEpgs().put(epg.getDn(), epg);
    if (previous != null) {
      // Endpoints may have been attached from a separate endpoint export; keep them.
      epg.getEndpoints().addAll(previous.getEndpoints());
    }
  }

  private void extractIpAttributes(AciMo criterion, Epg epg) {
    for (AciMo child : criterion.getChildren()) {
      if (child.getClassName().equals("fvIpAttr")) {
        Prefix prefix = parsePrefix(child.getAttribute("ip"), "IP attribute of " + epg.getDn());
        if (prefix != null) {
          epg.getIpAttributes().add(prefix);
        }
      } else if (child.getClassName().equals("fvSCrtrn")) {
        extractIpAttributes(child, epg);
      }
    }
  }

  private void extractEsg(Tenant tenant, String ap, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    Esg esg = new Esg(tenant.getName(), ap, name);
    esg.setPreferredGroupMember("include".equals(mo.getAttribute("prefGrMemb")));
    for (AciMo child : mo.getChildren()) {
      if (extractContractRelation(esg.getContracts(), child)) {
        continue;
      }
      switch (child.getClassName()) {
        case "fvRsScope" -> esg.setVrf(toRef(child, "tnFvCtxName"));
        case "fvEPSelector" -> {
          String expression = child.getAttribute("matchExpression");
          Prefix prefix = expression == null ? null : parseIpSelector(expression);
          if (prefix == null) {
            _warn.accept(
                String.format(
                    "Ignoring unsupported selector %s on ESG %s", expression, esg.getDn()));
          } else {
            esg.getIpSelectors().add(prefix);
          }
        }
        case "fvEPgSelector" -> {
          String epgDn = child.getAttribute("matchEpgDn");
          if (epgDn != null) {
            esg.getEpgSelectors().add(epgDn);
          }
        }
        default -> {}
      }
    }
    tenant.getEsgs().put(esg.getDn(), esg);
  }

  /** Parses an ESG IP selector such as {@code ip=='10.1.1.0/24'}. */
  @VisibleForTesting
  static @Nullable Prefix parseIpSelector(String expression) {
    Matcher m = IP_SELECTOR.matcher(expression.trim());
    if (!m.matches()) {
      return null;
    }
    String value = m.group(1).trim();
    try {
      return value.contains("/") ? Prefix.parse(value) : Ip.parse(value).toPrefix();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Extracts a contract relation of an EPG, ESG or external EPG. Returns false if not one. */
  private boolean extractContractRelation(ContractRelations relations, AciMo child) {
    switch (child.getClassName()) {
      case "fvRsProv" -> addRef(relations.getProvided(), child, "tnVzBrCPName");
      case "fvRsCons" -> addRef(relations.getConsumed(), child, "tnVzBrCPName");
      case "fvRsConsIf" -> addRef(relations.getConsumedInterfaces(), child, "tnVzCPIfName");
      case "fvRsProtBy" -> addRef(relations.getTaboos(), child, "tnVzTabooName");
      default -> {
        return false;
      }
    }
    return true;
  }

  private @Nullable Endpoint extractEndpoint(AciMo mo) {
    String mac = mo.getAttribute("mac");
    if (mac == null) {
      mac = mo.getAttribute("name");
    }
    if (mac == null) {
      return null;
    }
    Endpoint endpoint = new Endpoint(mac);
    endpoint.setEncapVlan(AciDn.vlan(mo.getAttribute("encap")));
    Ip ip = parseIp(mo.getAttribute("ip"), "endpoint " + mac);
    if (ip != null) {
      endpoint.getIps().add(ip);
    }
    PathRef fabricPath = PathRef.parse(mo.getAttribute("fabricPathDn"));
    if (fabricPath != null) {
      endpoint.getPaths().add(fabricPath);
    }
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "fvIp" -> {
          Ip addr = parseIp(child.getAttribute("addr"), "endpoint " + mac);
          if (addr != null && !endpoint.getIps().contains(addr)) {
            endpoint.getIps().add(addr);
          }
        }
        case "fvRsCEpToPathEp" -> {
          PathRef path = PathRef.parse(child.getAttribute("tDn"));
          if (path != null && !endpoint.getPaths().contains(path)) {
            endpoint.getPaths().add(path);
          }
        }
        default -> {}
      }
    }
    return endpoint;
  }

  private void extractTopLevelEndpoint(AciMo mo, @Nullable String dn) {
    String epgDn = dn == null ? null : AciDn.parent(dn);
    String tenantName = epgDn == null ? null : AciDn.value(epgDn, "tn-");
    String ap = epgDn == null ? null : AciDn.value(epgDn, "ap-");
    String epgName = epgDn == null ? null : AciDn.value(epgDn, "epg-");
    if (tenantName == null || ap == null || epgName == null) {
      // Endpoints of ESGs, L2Outs and service graphs are not modeled.
      return;
    }
    Endpoint endpoint = extractEndpoint(mo);
    if (endpoint == null) {
      return;
    }
    Tenant tenant = _c.getTenants().get(tenantName);
    Epg epg = tenant == null ? null : tenant.getEpgs().get(epgDn);
    if (epg == null) {
      _warn.accept(
          String.format("Ignoring endpoint %s of unknown EPG %s", endpoint.getMac(), epgDn));
      return;
    }
    epg.getEndpoints().add(endpoint);
  }

  // ---------------------------------------------------------------------------------------------
  // Contracts and filters

  private void extractContract(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    Contract contract = new Contract(tenant.getName(), name);
    contract.setScope(Contract.Scope.fromString(mo.getAttribute("scope")));
    for (AciMo subjectMo : mo.getChildren("vzSubj")) {
      String subjectName = subjectMo.getAttribute("name", "");
      ContractSubject subject = new ContractSubject(subjectName);
      subject.setReverseFilterPorts(!"no".equals(subjectMo.getAttribute("revFltPorts")));
      for (AciMo child : subjectMo.getChildren()) {
        switch (child.getClassName()) {
          case "vzRsSubjFiltAtt" -> addFilterRef(subject.getFilters(), child);
          case "vzInTerm" ->
              child
                  .getChildren("vzRsFiltAtt")
                  .forEach(f -> addFilterRef(subject.getConsumerToProviderFilters(), f));
          case "vzOutTerm" ->
              child
                  .getChildren("vzRsFiltAtt")
                  .forEach(f -> addFilterRef(subject.getProviderToConsumerFilters(), f));
          default -> {}
        }
      }
      contract.getSubjects().add(subject);
    }
    tenant.getContracts().put(name, contract);
  }

  private void addFilterRef(List<FilterRef> refs, AciMo mo) {
    NamedRef filter = toRef(mo, "tnVzFilterName");
    LineAction action =
        "deny".equals(mo.getAttribute("action")) ? LineAction.DENY : LineAction.PERMIT;
    refs.add(new FilterRef(filter, action, mo.getAttribute("priorityOverride", "default")));
  }

  private void extractFilter(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    AciFilter filter = new AciFilter(tenant.getName(), name);
    for (AciMo entryMo : mo.getChildren("vzEntry")) {
      filter.getEntries().add(extractFilterEntry(entryMo, filter.getDn()));
    }
    tenant.getFilters().put(name, filter);
  }

  private FilterEntry extractFilterEntry(AciMo mo, String filterDn) {
    FilterEntry entry = new FilterEntry(mo.getAttribute("name", ""));
    String context = "entry " + entry.getName() + " of " + filterDn;
    entry.setEtherType(FilterEntry.EtherType.fromString(mo.getAttribute("etherT")));
    entry.setIpProtocol(parseEnum(mo.getAttribute("prot"), PROTOCOLS, context));
    entry.setSrcFromPort(parseEnum(mo.getAttribute("sFromPort"), PORTS, context));
    entry.setSrcToPort(parseEnum(mo.getAttribute("sToPort"), PORTS, context));
    entry.setDstFromPort(parseEnum(mo.getAttribute("dFromPort"), PORTS, context));
    entry.setDstToPort(parseEnum(mo.getAttribute("dToPort"), PORTS, context));
    Integer icmpType = parseEnum(mo.getAttribute("icmpv4T"), ICMP_TYPES, context);
    // icmpv4T "unspecified" is stored as 255
    entry.setIcmpType(icmpType == null || icmpType == 255 ? null : icmpType);
    Set<String> tcpRules = new HashSet<>(flags(mo.getAttribute("tcpRules")));
    tcpRules.remove("unspecified");
    entry.setTcpRules(tcpRules);
    entry.setStateful("yes".equals(mo.getAttribute("stateful")));
    return entry;
  }

  /**
   * Parses an attribute that is either one of APIC's named values or a number. Returns {@code null}
   * for {@code unspecified} or {@code 0}, which APIC treats as "any".
   */
  private @Nullable Integer parseEnum(
      @Nullable String value, Map<String, Integer> names, String context) {
    if (value == null || value.equals("unspecified")) {
      return null;
    }
    Integer named = names.get(value);
    if (named != null) {
      return named;
    }
    Integer number = parseIntOrNull(value);
    if (number == null) {
      _warn.accept(String.format("Ignoring unrecognized value %s in %s", value, context));
      return null;
    }
    return number == 0 ? null : number;
  }

  private void extractTaboo(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    TabooContract taboo = new TabooContract(tenant.getName(), name);
    for (AciMo subject : mo.getChildren("vzTSubj")) {
      for (AciMo deny : subject.getChildren("vzRsDenyRule")) {
        taboo.getDenyFilters().add(toRef(deny, "tnVzFilterName"));
      }
    }
    tenant.getTaboos().put(name, taboo);
  }

  private void extractContractInterface(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    mo.getChild("vzRsIf")
        .map(r -> r.getAttribute("tDn"))
        .ifPresent(tDn -> tenant.getContractInterfaces().put(name, tDn));
  }

  // ---------------------------------------------------------------------------------------------
  // L3Outs

  private void extractL3Out(Tenant tenant, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    L3Out l3Out = new L3Out(tenant.getName(), name);
    l3Out.setEnforceImportRouteControl(flags(mo.getAttribute("enforceRtctrl")).contains("import"));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "l3extRsEctx" -> l3Out.setVrf(toRef(child, "tnFvCtxName"));
        case "bgpExtP" -> l3Out.setBgpEnabled(true);
        case "ospfExtP" -> {
          l3Out.setOspfAreaId(parseOspfArea(child.getAttribute("areaId", "1")));
          l3Out.setOspfAreaType(L3Out.OspfAreaType.fromString(child.getAttribute("areaType")));
        }
        case "l3extLNodeP" -> l3Out.getNodeProfiles().add(extractNodeProfile(child, l3Out));
        case "l3extInstP" -> {
          ExternalEpg epg = extractExternalEpg(tenant.getName(), name, child);
          l3Out.getExternalEpgs().add(epg);
        }
        default -> {}
      }
    }
    tenant.getL3Outs().put(name, l3Out);
  }

  private @Nullable Long parseOspfArea(String areaId) {
    if (areaId.equals("backbone")) {
      return 0L;
    }
    if (areaId.contains(".")) {
      Ip ip = parseIp(areaId, "OSPF area ID");
      return ip == null ? null : ip.asLong();
    }
    return parseLongOrNull(areaId);
  }

  private L3OutNodeProfile extractNodeProfile(AciMo mo, L3Out l3Out) {
    L3OutNodeProfile profile = new L3OutNodeProfile(mo.getAttribute("name", ""));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "l3extRsNodeL3OutAtt" -> {
          String tDn = child.getAttribute("tDn");
          Integer nodeId = tDn == null ? null : AciDn.nodeId(tDn);
          Integer podId = tDn == null ? null : AciDn.podId(tDn);
          if (nodeId == null) {
            _warn.accept(String.format("Ignoring L3Out node %s in %s", tDn, l3Out.getDn()));
            continue;
          }
          L3OutNode node = new L3OutNode(nodeId, podId == null ? 1 : podId);
          node.setRouterId(parseIp(child.getAttribute("rtrId"), "router ID in " + l3Out.getDn()));
          node.setRouterIdLoopback(!"no".equals(child.getAttribute("rtrIdLoopBack")));
          for (AciMo nodeChild : child.getChildren()) {
            switch (nodeChild.getClassName()) {
              case "l3extLoopBackIfP" -> {
                Ip loopback = parseLoopback(nodeChild.getAttribute("addr"), l3Out.getDn());
                if (loopback != null) {
                  node.getLoopbacks().add(loopback);
                }
              }
              case "ipRouteP" -> {
                L3OutStaticRoute route = extractStaticRoute(nodeChild, l3Out.getDn());
                if (route != null) {
                  node.getStaticRoutes().add(route);
                }
              }
              default -> {}
            }
          }
          profile.getNodes().add(node);
        }
        case "l3extLIfP" ->
            profile.getInterfaceProfiles().add(extractInterfaceProfile(child, l3Out));
        case "bgpPeerP" -> {
          BgpPeer peer = extractBgpPeer(child, l3Out.getDn());
          if (peer != null) {
            profile.getLoopbackBgpPeers().add(peer);
          }
        }
        default -> {}
      }
    }
    return profile;
  }

  private @Nullable Ip parseLoopback(@Nullable String addr, String context) {
    if (addr == null) {
      return null;
    }
    String ip = addr.contains("/") ? addr.substring(0, addr.indexOf('/')) : addr;
    return parseIp(ip, "loopback in " + context);
  }

  private @Nullable L3OutStaticRoute extractStaticRoute(AciMo mo, String context) {
    Prefix prefix = parsePrefix(mo.getAttribute("ip"), "static route in " + context);
    if (prefix == null) {
      return null;
    }
    Integer pref = parseIntOrNull(mo.getAttribute("pref"));
    L3OutStaticRoute route = new L3OutStaticRoute(prefix, pref == null ? 1 : pref);
    for (AciMo nh : mo.getChildren("ipNexthopP")) {
      Integer nhPref = parseIntOrNull(nh.getAttribute("pref"));
      if ("none".equals(nh.getAttribute("type"))) {
        route.getNextHops().add(new L3OutStaticRoute.NextHop(null, nhPref));
        continue;
      }
      String nhAddr = nh.getAttribute("nhAddr");
      if (nhAddr != null && nhAddr.contains("/")) {
        nhAddr = nhAddr.substring(0, nhAddr.indexOf('/'));
      }
      Ip ip = parseIp(nhAddr, "static route next hop in " + context);
      if (ip != null) {
        route.getNextHops().add(new L3OutStaticRoute.NextHop(ip, nhPref));
      }
    }
    return route;
  }

  private L3OutInterfaceProfile extractInterfaceProfile(AciMo mo, L3Out l3Out) {
    L3OutInterfaceProfile profile = new L3OutInterfaceProfile(mo.getAttribute("name", ""));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "ospfIfP" -> profile.setOspfEnabled(true);
        case "l3extRsPathL3OutAtt" -> {
          L3OutPath path = extractL3OutPath(child, l3Out);
          if (path != null) {
            profile.getPaths().add(path);
          }
        }
        default -> {}
      }
    }
    return profile;
  }

  private @Nullable L3OutPath extractL3OutPath(AciMo mo, L3Out l3Out) {
    String tDn = mo.getAttribute("tDn");
    PathRef pathRef = PathRef.parse(tDn);
    L3OutPath.Type type = L3OutPath.Type.fromString(mo.getAttribute("ifInstT"));
    if (pathRef == null || type == null) {
      _warn.accept(
          String.format(
              "Ignoring unsupported L3Out interface %s (%s) in %s",
              tDn, mo.getAttribute("ifInstT"), l3Out.getDn()));
      return null;
    }
    L3OutPath path = new L3OutPath(pathRef, type);
    String context = "interface " + tDn + " in " + l3Out.getDn();
    String addr = mo.getAttribute("addr");
    if (addr != null && !addr.equals("0.0.0.0")) {
      path.setAddress(parseInterfaceAddress(addr, context));
    }
    path.setEncapVlan(AciDn.vlan(mo.getAttribute("encap")));
    path.setMtu(parseIntOrNull(mo.getAttribute("mtu")));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "l3extIp" -> addSecondary(path.getSecondaryAddresses(), child, context);
        case "l3extMember" -> {
          String side = child.getAttribute("side", "A");
          String memberAddr = child.getAttribute("addr");
          if (memberAddr != null && !memberAddr.equals("0.0.0.0")) {
            ConcreteInterfaceAddress address = parseInterfaceAddress(memberAddr, context);
            if (address != null) {
              path.getMemberAddresses().put(side, address);
            }
          }
          // Floating secondary addresses shared by both vPC peers
          child
              .getChildren("l3extIp")
              .forEach(ip -> addSecondary(path.getSecondaryAddresses(), ip, context));
        }
        case "bgpPeerP" -> {
          BgpPeer peer = extractBgpPeer(child, l3Out.getDn());
          if (peer != null) {
            path.getBgpPeers().add(peer);
          }
        }
        default -> {}
      }
    }
    return path;
  }

  private void addSecondary(List<ConcreteInterfaceAddress> addresses, AciMo mo, String context) {
    ConcreteInterfaceAddress address = parseInterfaceAddress(mo.getAttribute("addr"), context);
    if (address != null && !addresses.contains(address)) {
      addresses.add(address);
    }
  }

  private @Nullable BgpPeer extractBgpPeer(AciMo mo, String context) {
    String addr = mo.getAttribute("addr");
    Prefix prefix =
        addr == null
            ? null
            : parsePrefix(addr.contains("/") ? addr : addr + "/32", "BGP peer in " + context);
    if (prefix == null) {
      return null;
    }
    BgpPeer peer = new BgpPeer(prefix);
    peer.setEnabled(!"disabled".equals(mo.getAttribute("adminSt")));
    Integer ttl = parseIntOrNull(mo.getAttribute("ttl"));
    peer.setTtl(ttl == null ? 1 : ttl);
    peer.setControls(flags(mo.getAttribute("ctrl")));
    mo.getChild("bgpAsP")
        .ifPresent(as -> peer.setRemoteAs(parseLongOrNull(as.getAttribute("asn"))));
    mo.getChild("bgpLocalAsnP")
        .ifPresent(as -> peer.setLocalAs(parseLongOrNull(as.getAttribute("localAsn"))));
    return peer;
  }

  private ExternalEpg extractExternalEpg(String tenant, String l3Out, AciMo mo) {
    ExternalEpg epg = new ExternalEpg(tenant, l3Out, mo.getAttribute("name", ""));
    epg.setPreferredGroupMember("include".equals(mo.getAttribute("prefGrMemb")));
    for (AciMo child : mo.getChildren()) {
      if (extractContractRelation(epg.getContracts(), child)) {
        continue;
      }
      if (child.getClassName().equals("l3extSubnet")) {
        Prefix prefix = parsePrefix(child.getAttribute("ip"), "subnet of " + epg.getDn());
        if (prefix != null) {
          Set<String> scope = flags(child.getAttribute("scope", ExternalSubnet.IMPORT_SECURITY));
          epg.getSubnets()
              .add(new ExternalSubnet(prefix, scope, flags(child.getAttribute("aggregate"))));
        }
      }
    }
    return epg;
  }

  // ---------------------------------------------------------------------------------------------
  // Access policies

  private void extractInfra(AciMo infra) {
    AccessPolicies policies = _c.getAccessPolicies();
    for (AciMo child : infra.getChildren()) {
      switch (child.getClassName()) {
        case "infraNodeP" -> extractLeafProfile(policies, child);
        case "infraAccPortP" -> extractInterfaceProfile(policies, child);
        case "infraFuncP" -> extractPolicyGroups(policies, child);
        case "infraAttEntityP" -> extractAaep(policies, child);
        default -> {}
      }
    }
  }

  private void extractLeafProfile(AccessPolicies policies, AciMo mo) {
    LeafProfile profile = new LeafProfile(mo.getAttribute("name", ""));
    for (AciMo child : mo.getChildren()) {
      switch (child.getClassName()) {
        case "infraLeafS" -> {
          if ("ALL".equals(child.getAttribute("type"))) {
            profile.setAllNodes(true);
          }
          for (AciMo block : child.getChildren("infraNodeBlk")) {
            Integer from = parseIntOrNull(block.getAttribute("from_"));
            Integer to = parseIntOrNull(block.getAttribute("to_"));
            if (from != null) {
              profile.getNodeRanges().add(new int[] {from, to == null ? from : to});
            }
          }
        }
        case "infraRsAccPortP" -> {
          String tDn = child.getAttribute("tDn");
          if (tDn != null) {
            profile.getInterfaceProfileDns().add(tDn);
          }
        }
        default -> {}
      }
    }
    policies.getLeafProfiles().add(profile);
  }

  private void extractInterfaceProfile(AccessPolicies policies, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    String dn = "uni/infra/accportprof-" + name;
    List<PortSelector> selectors = new ArrayList<>();
    for (AciMo selectorMo : mo.getChildren("infraHPortS")) {
      String policyGroup =
          selectorMo.getChild("infraRsAccBaseGrp").map(r -> r.getAttribute("tDn")).orElse(null);
      PortSelector selector = new PortSelector(selectorMo.getAttribute("name", ""), policyGroup);
      if ("ALL".equals(selectorMo.getAttribute("type"))) {
        _warn.accept(
            String.format(
                "Port selector %s in %s selects all ports; ports are modeled only where used",
                selector.getName(), dn));
      }
      for (AciMo block : selectorMo.getChildren("infraPortBlk")) {
        int fromCard = intAttr(block, "fromCard", 1);
        int toCard = intAttr(block, "toCard", fromCard);
        int fromPort = intAttr(block, "fromPort", 1);
        int toPort = intAttr(block, "toPort", fromPort);
        for (int card = fromCard; card <= toCard; card++) {
          for (int port = fromPort; port <= toPort; port++) {
            selector.getPorts().add(String.format("eth%d/%d", card, port));
          }
        }
      }
      selectors.add(selector);
    }
    policies.getInterfaceProfiles().put(dn, selectors);
  }

  private static int intAttr(AciMo mo, String name, int defaultValue) {
    Integer value = parseIntOrNull(mo.getAttribute(name));
    return value == null ? defaultValue : value;
  }

  private void extractPolicyGroups(AccessPolicies policies, AciMo funcProfile) {
    for (AciMo child : funcProfile.getChildren()) {
      String name = child.getAttribute("name");
      if (name == null) {
        continue;
      }
      String aaep = child.getChild("infraRsAttEntP").map(r -> r.getAttribute("tDn")).orElse(null);
      switch (child.getClassName()) {
        case "infraAccPortGrp" ->
            policies
                .getPolicyGroups()
                .put(
                    "uni/infra/funcprof/accportgrp-" + name,
                    new PolicyGroup(name, BundleType.NONE, aaep));
        case "infraAccBndlGrp" -> {
          BundleType type =
              "node".equals(child.getAttribute("lagT")) ? BundleType.VPC : BundleType.PORT_CHANNEL;
          policies
              .getPolicyGroups()
              .put("uni/infra/funcprof/accbundle-" + name, new PolicyGroup(name, type, aaep));
        }
        default -> {}
      }
    }
  }

  private void extractAaep(AccessPolicies policies, AciMo mo) {
    String name = mo.getAttribute("name");
    if (name == null) {
      return;
    }
    List<AaepEpgBinding> bindings = new ArrayList<>();
    for (AciMo generic : mo.getChildren("infraGeneric")) {
      for (AciMo binding : generic.getChildren("infraRsFuncToEpg")) {
        String epgDn = binding.getAttribute("tDn");
        if (epgDn != null) {
          bindings.add(
              new AaepEpgBinding(
                  epgDn,
                  AciDn.vlan(binding.getAttribute("encap")),
                  StaticPath.Mode.fromString(binding.getAttribute("mode"))));
        }
      }
    }
    policies.getAaepBindings().put("uni/infra/attentp-" + name, bindings);
  }

  // ---------------------------------------------------------------------------------------------
  // Fabric

  private void extractFabricInst(AciMo fabric) {
    for (AciMo child : fabric.getChildren()) {
      switch (child.getClassName()) {
        case "bgpInstPol" -> extractFabricBgp(child);
        case "fabricProtPol" ->
            child.getChildren("fabricExplicitGEp").forEach(this::extractVpcPair);
        default -> {}
      }
    }
  }

  private void extractFabricBgp(AciMo bgpInstPol) {
    bgpInstPol
        .getChild("bgpAsP")
        .map(as -> parseLongOrNull(as.getAttribute("asn")))
        .ifPresent(_c::setFabricAsn);
    bgpInstPol
        .getChild("bgpRRP")
        .ifPresent(
            rr ->
                rr.getChildren("bgpRRNodePEp").stream()
                    .map(n -> parseIntOrNull(n.getAttribute("id")))
                    .filter(id -> id != null)
                    .forEach(_c.getRouteReflectorNodeIds()::add));
  }

  private void extractVpcPair(AciMo mo) {
    List<Integer> nodeIds = new ArrayList<>();
    for (AciMo member : mo.getChildren("fabricNodePEp")) {
      Integer id = parseIntOrNull(member.getAttribute("id"));
      if (id != null) {
        nodeIds.add(id);
      }
    }
    Integer id = parseIntOrNull(mo.getAttribute("id"));
    if (nodeIds.size() != 2 || id == null) {
      _warn.accept(
          String.format("Ignoring vPC pair %s without exactly two nodes", mo.getAttribute("name")));
      return;
    }
    _c.getVpcPairs().add(new VpcPair(mo.getAttribute("name", ""), id, nodeIds));
  }

  private void extractControllerInst(AciMo controller) {
    for (AciMo child : controller.getChildren()) {
      switch (child.getClassName()) {
        case "fabricNodeIdentPol" ->
            child.getChildren("fabricNodeIdentP").forEach(this::extractNodeIdentity);
        case "fabricSetupPol" ->
            child.getChildren("fabricSetupP").stream()
                .map(s -> s.getAttribute("tepPool"))
                .filter(p -> p != null)
                .findFirst()
                .ifPresent(p -> _c.setTepPool(parsePrefix(p, "TEP pool")));
        default -> {}
      }
    }
  }

  private void extractNodeIdentity(AciMo mo) {
    Integer id = parseIntOrNull(mo.getAttribute("nodeId"));
    if (id == null || id == 0) {
      return;
    }
    FabricNode node = _c.getNodes().computeIfAbsent(id, FabricNode::new);
    if (node.getName() == null) {
      node.setName(mo.getAttribute("name"));
    }
    if (node.getRole() == FabricNode.Role.OTHER) {
      node.setRole(FabricNode.Role.fromString(mo.getAttribute("role")));
    }
    Integer pod = parseIntOrNull(mo.getAttribute("podId"));
    if (pod != null) {
      node.setPodId(pod);
    }
  }

  private void extractFabricNode(AciMo mo, @Nullable String dn) {
    Integer id = parseIntOrNull(mo.getAttribute("id"));
    if (id == null && dn != null) {
      id = AciDn.nodeId(dn);
    }
    if (id == null) {
      return;
    }
    updateNode(id, mo, dn);
  }

  private void extractTopSystem(AciMo mo, @Nullable String dn) {
    Integer id = parseIntOrNull(mo.getAttribute("id"));
    if (id == null && dn != null) {
      id = AciDn.nodeId(dn);
    }
    if (id == null) {
      return;
    }
    updateNode(id, mo, dn);
  }

  private void updateNode(int id, AciMo mo, @Nullable String dn) {
    FabricNode node = _c.getNodes().computeIfAbsent(id, FabricNode::new);
    String name = mo.getAttribute("name");
    if (name != null) {
      node.setName(name);
    }
    FabricNode.Role role = FabricNode.Role.fromString(mo.getAttribute("role"));
    if (role != FabricNode.Role.OTHER) {
      node.setRole(role);
    }
    Integer pod = parseIntOrNull(mo.getAttribute("podId"));
    if (pod == null && dn != null) {
      pod = AciDn.podId(dn);
    }
    if (pod != null) {
      node.setPodId(pod);
    }
    Ip tep = parseIp(mo.getAttribute("address"), "TEP address of node " + id);
    if (tep != null && !tep.equals(Ip.ZERO)) {
      node.setTepAddress(tep);
    }
  }

  private void extractLldpAdjacency(AciMo mo, @Nullable String dn) {
    Integer nodeId = dn == null ? null : AciDn.nodeId(dn);
    String localInterface = dn == null ? null : AciDn.interfaceName(dn);
    if (nodeId == null || localInterface == null) {
      return;
    }
    _c.getLldpAdjacencies()
        .add(
            new LldpAdjacency(
                nodeId, localInterface, mo.getAttribute("sysName"), mo.getAttribute("portIdV")));
  }

  private void extractPortChannel(AciMo mo, @Nullable String dn) {
    Integer nodeId = dn == null ? null : AciDn.nodeId(dn);
    String id = mo.getAttribute("id");
    String policyGroup = mo.getAttribute("name");
    if (nodeId == null || id == null || policyGroup == null) {
      return;
    }
    _c.getPortChannelIds().computeIfAbsent(nodeId, n -> new TreeMap<>()).put(policyGroup, id);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers

  private static NamedRef toRef(AciMo mo, String nameAttribute) {
    return new NamedRef(mo.getAttribute(nameAttribute, ""), mo.getAttribute("tDn"));
  }

  private static void addRef(List<NamedRef> refs, AciMo mo, String nameAttribute) {
    refs.add(toRef(mo, nameAttribute));
  }

  /** Splits a comma-separated APIC bitmask attribute. */
  private static Set<String> flags(@Nullable String value) {
    if (value == null) {
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<String> flags = ImmutableSet.builder();
    for (String flag : value.split(",")) {
      String trimmed = flag.trim();
      if (!trimmed.isEmpty()) {
        flags.add(trimmed);
      }
    }
    return flags.build();
  }

  private @Nullable Ip parseIp(@Nullable String value, String context) {
    if (value == null) {
      return null;
    }
    try {
      return Ip.parse(value.trim());
    } catch (IllegalArgumentException e) {
      _warn.accept(String.format("Ignoring invalid IP address %s for %s", value, context));
      return null;
    }
  }

  private @Nullable Prefix parsePrefix(@Nullable String value, String context) {
    if (value == null) {
      return null;
    }
    try {
      return Prefix.parse(value.trim());
    } catch (IllegalArgumentException e) {
      _warn.accept(String.format("Ignoring invalid prefix %s for %s", value, context));
      return null;
    }
  }

  private @Nullable ConcreteInterfaceAddress parseInterfaceAddress(
      @Nullable String value, String context) {
    if (value == null) {
      return null;
    }
    try {
      return ConcreteInterfaceAddress.parse(value.trim());
    } catch (IllegalArgumentException e) {
      _warn.accept(String.format("Ignoring invalid address %s for %s", value, context));
      return null;
    }
  }

  /** Classes holding runtime state rather than configuration. */
  private static final Set<String> RUNTIME_CLASSES =
      ImmutableSet.of("fabricNode", "topSystem", "lldpAdjEp", "pcAggrIf", "fvCEp");

  private static final Pattern IP_SELECTOR = Pattern.compile("ip\\s*==\\s*'([^']+)'");

  /** Named values of {@code vzEntry.prot}, from the APIC model. */
  private static final Map<String, Integer> PROTOCOLS =
      ImmutableMap.<String, Integer>builder()
          .put("icmp", 1)
          .put("igmp", 2)
          .put("tcp", 6)
          .put("egp", 8)
          .put("igp", 9)
          .put("udp", 17)
          .put("icmpv6", 58)
          .put("eigrp", 88)
          .put("ospfigp", 89)
          .put("pim", 103)
          .put("l2tp", 115)
          .build();

  /** Named values of {@code vzEntry} port attributes, from the APIC model. */
  private static final Map<String, Integer> PORTS =
      ImmutableMap.<String, Integer>builder()
          .put("ftpData", 20)
          .put("ssh", 22)
          .put("smtp", 25)
          .put("dns", 53)
          .put("http", 80)
          .put("pop3", 110)
          .put("https", 443)
          .put("rtsp", 554)
          .build();

  /** Named values of {@code vzEntry.icmpv4T}, from the APIC model. */
  private static final Map<String, Integer> ICMP_TYPES =
      ImmutableMap.of(
          "echo-rep", 0, "dst-unreach", 3, "src-quench", 4, "echo", 8, "time-exceeded", 11);

  private final @Nonnull AciConfiguration _c;
  private final @Nonnull Consumer<String> _warn;
  private final @Nonnull Set<String> _warnedClasses;
}
