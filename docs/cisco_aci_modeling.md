# Cisco ACI Modeling in Batfish

Batfish models a Cisco ACI fabric from an export of its APIC. One fabric becomes one Batfish node per
leaf and spine, built on the same vendor-independent features as an NX-OS VXLAN EVPN fabric, so
`routes`, `bgpSessionStatus`, `traceroute`, `reachability`, `searchFilters` and the other questions
work across ACI leaves and the routers and firewalls around them.

Class and attribute names below follow the APIC Management Information Model; the implementation
was checked against the model reference for every class it reads.

## Input

Put each fabric in its own folder under `aci_configs/`:

```
snapshot/
  aci_configs/
    dc1/            one folder per fabric; the folder name is the fabric name
      *.json        APIC REST API responses
  configs/          other devices (routers, firewalls), as usual
  batfish/layer1_topology.json   optional cabling between leaves and other devices
```

Every `.json` file in a fabric folder is read. A file can hold a query response
(`{"imdata": [...]}`), a configuration export (`{"polUni": {...}}`) or a list of objects. Files
can be split however is convenient; objects of the same fabric are merged.

| Data | APIC query | Needed for |
| --- | --- | --- |
| Tenants | `GET /api/class/fvTenant.json?rsp-subtree=full` | VRFs, bridge domains, EPGs, contracts, L3Outs (required) |
| Access policies | `GET /api/mo/uni/infra.json?rsp-subtree=full` | Ports, port-channels, vPCs, AAEP EPG deployment |
| Fabric policies | `GET /api/mo/uni/fabric.json?rsp-subtree=full` | Fabric BGP AS, route reflectors, vPC pairs |
| Node identities | `GET /api/mo/uni/controller.json?rsp-subtree=full` | Node names and roles |
| Nodes | `GET /api/class/fabricNode.json` | TEP addresses (required) |
| LLDP | `GET /api/class/lldpAdjEp.json` | Real leaf-spine links (optional) |
| Port-channels | `GET /api/class/pcAggrIf.json` | `po<N>` interface names (optional) |
| Endpoints | `GET /api/class/fvCEp.json?rsp-subtree=children` | Host routes and exact EPG classification (optional, recommended) |

Export with full properties: `rsp-prop-include=config-only` drops the VNIDs (`fvCtx.scope`,
`fvBD.seg`) and multicast groups (`fvBD.bcastP`) that APIC assigns. A single
`GET /api/mo/uni.json?rsp-subtree=full` also works in place of the four configuration queries.

`tools/export_aci_fabric.py` runs these queries with a read-only account and writes the folder:

```
APIC_PASSWORD=... python3 tools/export_aci_fabric.py \
    --apic https://apic.example.com --user readonly --out snapshot/aci_configs/dc1
```

## What is modeled

| ACI | Batfish |
| --- | --- |
| Leaf or spine (`fabricNode`, `fabricNodeIdentP`) | A node named after the switch, format `CISCO_ACI` |
| Infrastructure VRF | VRF `overlay-1`: TEP on `lo0`, IS-IS on fabric links, fabric BGP |
| Fabric links | From `lldpAdjEp`; otherwise every leaf links to every spine on interfaces `fabric-<peer node ID>`. Addresses come from `100.127.0.0/16`, since ACI fabric links are unnumbered |
| MP-BGP route distribution | iBGP EVPN sessions from each leaf to each route-reflector spine (`bgpRRNodePEp`), with EVPN type-5 routes standing in for VPNv4 |
| Tenant VRF (`fvCtx`) | VRF `<tenant>:<vrf>` with an L3 VNI (`fvCtx.scope`) on each leaf where it is deployed |
| Bridge domain (`fvBD`) | An L2 VNI (`fvBD.seg`, flooding on the `bcastP` group) and an anycast-gateway SVI `vlan<N>` with the BD's subnets on each leaf where an EPG of the BD is deployed |
| EPG static binding (`fvRsPathAtt`) and AAEP binding (`infraRsFuncToEpg`) | Trunk or access switchport on the port, port-channel or vPC |
| Access policies | Physical ports, port-channels and vPC aggregates from leaf and interface profiles |
| Endpoint (`fvCEp`) | A host route on the leaf where the endpoint was learned, distributed to the other leaves |
| L3Out | Routed ports, subinterfaces and SVIs (vPC side A/B addresses); router-ID loopbacks; static routes; BGP and OSPF in the tenant VRF |
| L3Out route control | Export: public subnets of associated BDs, and `export-rtctrl` external subnets for transit routes. Import: everything, or `import-rtctrl` subnets when import enforcement is on |
| Contracts, filters, taboo contracts, `vzAny`, preferred groups, ESGs | One zoning ACL per enforced VRF (below) |

### Contracts

Each enforced VRF gets an ACL named `aci-zoning~<tenant>:<vrf>`. It is the incoming filter of every
bridge-domain SVI and L3Out interface of the VRF on every leaf, so a flow is judged at the leaf
where it enters the fabric. Traffic to the leaf's own addresses is permitted. An unenforced VRF has
no filter.

ACI classifies packets into EPGs by port and VLAN; Batfish flows carry no such class, so EPG
membership becomes IP space, in this order of precedence:

1. ESG IP selectors, then EPG selectors of ESGs
2. Learned endpoint IPs
3. uSeg IP attributes
4. EPG subnets
5. Subnets of bridge domains with a single EPG
6. External EPG subnets with `import-security`, longest prefix first

Bridge-domain subnets shared by several EPGs, where endpoint data does not say which EPG an IP
belongs to, match only `vzAny` rules; conversion warns about each such bridge domain. Each class is a
named IP space (`aci~<dn>`), so traces show which EPG matched.

Rules follow the zoning-rule priorities of the APIC model (`actrlRule.prio`), deny before permit at
the same priority: intra-EPG permit or isolation; taboo contracts; EPG-to-EPG rules with specific
filters, then with match-all filters; `vzAny` rules; the preferred group; and the implicit deny.
Subjects apply filters consumer-to-provider and, with `revFltPorts`, provider-to-consumer with ports
reversed. Stateful entries require the ACK flag on provider-to-consumer TCP. Filter `priorityOverride`
is reported but not modeled.

## Limitations

- Multi-Pod, Multi-Site and remote leaves are not modeled.
- Service graphs and policy-based redirect are not modeled.
- Shared-services (inter-VRF) contracts and route leaking are not modeled.
- A bridge domain with several encap VLANs on one leaf bridges only one of them to its gateway,
  until per-port VLAN translation is added to the vendor-independent model; conversion warns.
- Bridged traffic between EPGs of one bridge domain is filtered only when traced from the bridge
  domain's SVI, because Batfish has no layer-2 forwarding step.
- FEX ports are ignored with a warning.
