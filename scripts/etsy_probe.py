#!/usr/bin/env python3
"""Read-only discovery; stdlib only. Private responses stay in ignored data/.

Usage: python3 scripts/etsy_probe.py /path/to/etsy.env
Uses a persisted rolling-day budget, process lock, and server-header reconciliation.
Never emits credentials, request headers, or raw error bodies.
"""
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "etsy-discovery"
DATA.mkdir(parents=True, exist_ok=True, mode=0o700)
os.umask(0o077)
KEYS = ("x-limit-per-second", "x-limit-per-day", "x-remaining-today",
        "x-remaining-this-second", "x-remaining-this-secon", "retry-after")


def read_credentials(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip().removeprefix("export ")
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"'")
    return values["ETSY_API_KEY"] + ":" + values["ETSY_API_SECRET"]


def request(path, label, credential):
    ledger_path = DATA / "budget.json"
    ledger = json.loads(ledger_path.read_text()) if ledger_path.exists() else {"calls": []}
    now = time.time()
    ledger["calls"] = [stamp for stamp in ledger["calls"] if stamp > now - 86400]
    if len(ledger["calls"]) >= 6:
        raise SystemExit("Local six-request rolling-day budget exhausted; no request sent.")
    if ledger.get("blocked_until", 0) > now:
        raise SystemExit("Provider cooldown is active; no request sent.")
    if label != "quota-probe":
        quota = ledger.get("quota", {})
        if int(quota.get("x-remaining-today", "0")) <= 10 or int(quota.get("x-limit-per-second", "0")) < 1:
            raise SystemExit("Quota unavailable or below reserve; no search sent.")
    if ledger["calls"]:
        time.sleep(max(0, ledger["calls"][-1] + 2 - time.time()))
    # Reserve before network I/O; a failed/uncertain attempt still consumes our budget.
    ledger["calls"].append(time.time())
    ledger_path.write_text(json.dumps(ledger, indent=2))
    req = urllib.request.Request("https://openapi.etsy.com/v3/application/" + path,
                                 headers={"x-api-key": credential, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            headers = {key: response.headers[key] for key in KEYS if response.headers.get(key)}
            body = json.load(response)
    except urllib.error.HTTPError as error:
        ledger["quota"] = {key: error.headers[key] for key in KEYS if error.headers.get(key)}
        if error.code == 429:
            try:
                wait = max(60, int(error.headers.get("retry-after", "60")))
            except ValueError:
                wait = 3600
            ledger["blocked_until"] = time.time() + wait
        ledger_path.write_text(json.dumps(ledger, indent=2))
        raise SystemExit(f"Etsy returned HTTP {error.code}; stopped without retry.") from None
    except Exception:
        raise SystemExit("Etsy transport or decoding failed; stopped without retry.") from None
    ledger["quota"] = headers
    ledger_path.write_text(json.dumps(ledger, indent=2))
    (DATA / (label + ".json")).write_text(json.dumps(body, indent=2))
    return body, headers


def main():
    credential = read_credentials(sys.argv[1])
    with (DATA / "budget.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        ledger_path = DATA / "budget.json"
        prior = json.loads(ledger_path.read_text()) if ledger_path.exists() else {"calls": []}
        if sum(stamp > time.time() - 86400 for stamp in prior["calls"]) + 5 > 6:
            raise SystemExit("Insufficient local budget for discovery batch; no request sent.")
        observations = []
        body, quota = request("openapi-ping", "quota-probe", credential)
        observations.append({"operation": "quota-probe", "quota": quota})
        for index, phrase in enumerate(("halloween printable", "halloween scavenger hunt printable",
                                        "halloween classroom games printable", "halloween svg")):
            query = urllib.parse.urlencode({"keywords": phrase, "limit": 1, "sort_on": "score"})
            body, quota = request("listings/active?" + query, f"search-{index}", credential)
            results = body.get("results", [])
            sample = results[0] if results else {}
            observations.append({"query": phrase, "count": body.get("count"), "returned": len(results),
                "quota": quota, "fields": sorted(sample),
                "field_types": {key: type(value).__name__ for key, value in sample.items()},
                "sample": {key: sample.get(key) for key in ("listing_id", "title", "listing_type", "is_digital",
                    "num_favorers", "views", "price", "tags", "original_creation_timestamp", "last_modified_timestamp")}})
        report = {"checked_at": dt.datetime.now(dt.timezone.utc).isoformat(), "requests_this_session": 5,
                  "observations": observations}
        (DATA / "observations.json").write_text(json.dumps(report, indent=2))
        # Only public marketplace observations and quota counters are printed.
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
