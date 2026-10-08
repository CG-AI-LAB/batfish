#!/usr/bin/env python3
"""Exports a Cisco ACI fabric from its APIC for analysis by Batfish.

Writes APIC REST API responses into a folder that goes under a snapshot's
aci_configs/ directory, one folder per fabric:

    snapshot/aci_configs/<fabric>/*.json

Only read-only queries are made. The account needs read access to the tenants,
fabric and access policies; a read-only "admin" or "ops" role is enough.

Usage:

    APIC_PASSWORD=... python3 tools/export_aci_fabric.py \\
        --apic https://apic.example.com --user readonly --out snapshot/aci_configs/dc1

The password is read from APIC_PASSWORD, or prompted for when unset. It is never
written to disk. Use --insecure only for an APIC with a self-signed certificate.
"""

import argparse
import getpass
import json
import os
import ssl
import sys
import urllib.parse
import urllib.request

# (output file, REST path, query parameters, paged, required)
QUERIES = [
    # Configuration: tenants, access policies, fabric policies, node identities.
    # Full properties (not config-only), so VNIDs, class IDs and multicast groups are included.
    ("tenants.json", "/api/class/fvTenant.json", {"rsp-subtree": "full"}, False, True),
    ("infra.json", "/api/mo/uni/infra.json", {"rsp-subtree": "full"}, False, True),
    ("fabric.json", "/api/mo/uni/fabric.json", {"rsp-subtree": "full"}, False, True),
    ("controller.json", "/api/mo/uni/controller.json", {"rsp-subtree": "full"}, False, True),
    # Runtime state: nodes and their TEP addresses, fabric links, port-channel IDs, endpoints.
    ("nodes.json", "/api/class/fabricNode.json", {}, True, True),
    ("lldp.json", "/api/class/lldpAdjEp.json", {}, True, False),
    ("port-channels.json", "/api/class/pcAggrIf.json", {}, True, False),
    (
        "endpoints.json",
        "/api/class/fvCEp.json",
        {"rsp-subtree": "children", "rsp-subtree-class": "fvIp,fvRsCEpToPathEp"},
        True,
        False,
    ),
]

PAGE_SIZE = 10000


class Apic:
    def __init__(self, base_url, insecure):
        self._base = base_url.rstrip("/")
        self._context = ssl.create_default_context()
        if insecure:
            self._context.check_hostname = False
            self._context.verify_mode = ssl.CERT_NONE
        self._cookie = None

    def _request(self, path, params=None, body=None):
        url = self._base + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(url, data=data, method="POST" if data else "GET")
        request.add_header("Content-Type", "application/json")
        if self._cookie:
            request.add_header("Cookie", "APIC-cookie=" + self._cookie)
        with urllib.request.urlopen(request, context=self._context, timeout=600) as response:
            return json.load(response)

    def login(self, user, password):
        reply = self._request(
            "/api/aaaLogin.json",
            body={"aaaUser": {"attributes": {"name": user, "pwd": password}}},
        )
        try:
            self._cookie = reply["imdata"][0]["aaaLogin"]["attributes"]["token"]
        except (KeyError, IndexError):
            raise SystemExit("APIC login failed: %s" % json.dumps(reply)[:300])

    def logout(self):
        try:
            self._request("/api/aaaLogout.json", body={"aaaUser": {"attributes": {}}})
        except OSError:
            pass

    def query(self, path, params, paged):
        if not paged:
            return self._request(path, params)
        imdata = []
        page = 0
        while True:
            reply = self._request(path, dict(params, **{"page-size": PAGE_SIZE, "page": page}))
            items = reply.get("imdata", [])
            imdata.extend(items)
            if len(items) < PAGE_SIZE:
                break
            page += 1
        return {"totalCount": str(len(imdata)), "imdata": imdata}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--apic", required=True, help="APIC base URL, e.g. https://apic1")
    parser.add_argument("--user", required=True, help="read-only APIC user")
    parser.add_argument("--out", required=True, help="output folder, snapshot/aci_configs/<fabric>")
    parser.add_argument(
        "--insecure", action="store_true", help="skip TLS certificate verification"
    )
    parser.add_argument(
        "--skip-endpoints",
        action="store_true",
        help="do not export learned endpoints (contracts then use subnets only)",
    )
    args = parser.parse_args()

    password = os.environ.get("APIC_PASSWORD") or getpass.getpass("APIC password: ")
    os.makedirs(args.out, exist_ok=True)
    apic = Apic(args.apic, args.insecure)
    apic.login(args.user, password)
    try:
        for filename, path, params, paged, required in QUERIES:
            if args.skip_endpoints and filename == "endpoints.json":
                continue
            try:
                data = apic.query(path, params, paged)
            except OSError as e:
                if required:
                    raise
                print("skipping %s: %s" % (filename, e), file=sys.stderr)
                continue
            with open(os.path.join(args.out, filename), "w") as f:
                json.dump(data, f, indent=1)
                f.write("\n")
            print("wrote %s (%s objects)" % (filename, data.get("totalCount", "?")))
    finally:
        apic.logout()


if __name__ == "__main__":
    main()
