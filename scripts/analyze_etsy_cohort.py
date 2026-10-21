#!/usr/bin/env python3
"""Replay saved real evidence through the packaged Java HTTP analysis service.
No external calls. Logs/databases are temporary; the result stays in ignored data/.
"""
import json
import os
import resource
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "etsy-cohort-1000"


def analyze():
    payload = (DATA / "input.json").read_bytes()
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    jar = ROOT / "services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar"
    with tempfile.TemporaryDirectory(prefix="cohort-analysis-") as directory:
        with (Path(directory) / "service.log").open("w") as log:
            service_started = time.perf_counter()
            cpu_before = resource.getrusage(resource.RUSAGE_CHILDREN)
            process = subprocess.Popen(["java", "-jar", str(jar),
                "--server.address=127.0.0.1", f"--server.port={port}",
                "--spring.datasource.url=jdbc:h2:mem:cohort"], cwd=directory,
                stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 25
                while True:
                    if process.poll() is not None:
                        raise SystemExit("Analysis service failed to start; no machine details emitted.")
                    try:
                        urllib.request.urlopen(base + "/v1/readiness-test", timeout=1)
                    except urllib.error.HTTPError:
                        break
                    except (OSError, urllib.error.URLError):
                        if time.monotonic() > deadline:
                            raise SystemExit("Analysis service startup timed out.")
                        time.sleep(.1)
                started = time.perf_counter()
                request = urllib.request.Request(base + "/v1/etsy-cohort-analyses", data=payload,
                    headers={"Content-Type": "application/json"}, method="POST")
                with urllib.request.urlopen(request, timeout=30) as response:
                    report = json.load(response)
                elapsed = time.perf_counter() - started
                assert report["received"] == 1000 and report["uniqueListings"] == 1000
                report["testRun"] = {"analysisHttpSeconds": round(elapsed,4), "llmCalls": 0,
                    "aiTokens": 0, "imageGenerationCalls": 0,
                    "operatingCost": "Not monetized: compute rate and human time valuation not configured."}
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            cpu_after = resource.getrusage(resource.RUSAGE_CHILDREN)
            report["testRun"]["serviceWallSeconds"] = round(time.perf_counter()-service_started,4)
            report["testRun"]["serviceCpuSeconds"] = round(
                cpu_after.ru_utime + cpu_after.ru_stime - cpu_before.ru_utime - cpu_before.ru_stime,4)
            os.umask(0o077)
            (DATA / "report.json").write_text(json.dumps(report, indent=2))
            print(json.dumps({key:value for key,value in report.items()
                              if key != "experimentCandidates"}, indent=2))


if __name__ == "__main__":
    analyze()
