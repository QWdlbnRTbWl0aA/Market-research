#!/usr/bin/env python3
"""Explicit read-only live collection: 1000 unique Halloween search listings.
A maximum of 15 new requests (1 quota probe + up to 14 pages), 30 rolling-day
attempts across probe tools, >=2 seconds between requests, and a daily reserve.
Previous fetched research is not reused. Credentials/machine details are not logged.
"""
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import sys
import time
import urllib.parse

import etsy_probe as probe

DESTINATION = probe.ROOT / "data" / "etsy-cohort-1000"
QUERY = "halloween printable"
TARGET = 1000
MAX_PAGES = 14
ROLLING_CAP = 30


def collect(credential, destination=DESTINATION):
    destination.mkdir(parents=True, exist_ok=True, mode=0o700)
    if (destination / "input.json").exists():
        raise SystemExit("A cohort already exists; replay it instead of silently recollecting.")
    with (probe.DATA / "budget.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        ledger_path = probe.DATA / "budget.json"
        ledger = json.loads(ledger_path.read_text()) if ledger_path.exists() else {"calls": []}
        previous = sum(x > time.time() - 86400 for x in ledger["calls"])
        if previous + 1 + MAX_PAGES > ROLLING_CAP:
            raise SystemExit("Insufficient local rolling budget; no request sent.")
        started = time.time()
        _, quota = probe.request("openapi-ping", "quota-probe", credential, ROLLING_CAP, destination)
        remaining = int(quota.get("x-remaining-today", "0"))
        if remaining < MAX_PAGES + 10:
            raise SystemExit("Insufficient observed Etsy quota for batch and reserve.")
        manifest = {"query": QUERY, "sort": "score", "limit": 100, "target_unique": TARGET,
                    "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                    "before_search_quota": quota, "prior_local_24h_attempts": previous,
                    "rolling_local_cap": ROLLING_CAP, "max_new_requests": 1 + MAX_PAGES,
                    "pages": [], "selection": "ranked convenience sample, not a demand estimate"}
        unique = {}
        fetched = 0
        for page in range(MAX_PAGES):
            params = urllib.parse.urlencode({"keywords": QUERY, "sort_on": "score", "limit": 100, "offset": page * 100})
            body, quota = probe.request("listings/active?" + params, f"page-{page:02d}", credential, ROLLING_CAP, destination)
            results = body.get("results")
            if not isinstance(results, list):
                raise SystemExit("Unexpected results shape; no retry.")
            fetched += len(results)
            manifest["pages"].append({"offset": page * 100, "returned": len(results),
                                       "count": body.get("count"), "quota": quota})
            for row in results:
                identifier = row.get("listing_id") if isinstance(row, dict) else None
                if isinstance(identifier, int) and not isinstance(identifier, bool) and identifier > 0:
                    if identifier not in unique and len(unique) < TARGET:
                        unique[identifier] = row
            manifest.update({"fetched_rows": fetched, "unique_selected": len(unique),
                "requests": 1 + len(manifest["pages"]), "after_quota": quota,
                "elapsed_seconds": round(time.time()-started, 3)})
            (destination / "manifest.json").write_text(json.dumps(manifest, indent=2))
            if len(unique) == TARGET:
                break
            if not results:
                break
        if len(unique) != TARGET:
            raise SystemExit(f"Only {len(unique)} unique listings collected within budget; no synthetic padding.")
        observed = dt.datetime.now(dt.timezone.utc).isoformat()
        payload = {"query": QUERY, "observedAt": observed, "listings": list(unique.values())}
        (destination / "input.json").write_text(json.dumps(payload))
        # Safe aggregate-only output.
        print(json.dumps({"unique_listings": len(unique), "fetched_rows": fetched,
                          "requests": manifest["requests"], "after_quota": quota}, indent=2))
        return manifest


if __name__ == "__main__":
    os.umask(0o077)
    collect(probe.read_credentials(sys.argv[1]))
