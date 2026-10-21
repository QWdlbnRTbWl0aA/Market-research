#!/usr/bin/env python3
"""Cross-process HTTP acceptance tests; no external services or credentials.
Run after mvn verify. Uses temporary databases/logs and removes them on exit.
"""
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SERVICES = {"orchestrator": "research-orchestrator", "analysis": "analysis-service",
            "archive": "research-archive-service"}


def port():
    with socket.socket() as server:
        server.bind(("127.0.0.1", 0))
        return server.getsockname()[1]


class Harness:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.ports = {name: port() for name in SERVICES}
        self.processes = {}
        self.logs = []

    def call(self, service, method, path, body=None, key=None):
        headers = {"Content-Type": "application/json"}
        if key:
            headers["Idempotency-Key"] = key
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.ports[service]}" + path,
            data=None if body is None else json.dumps(body).encode(),
            headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                text = response.read()
                return response.status, json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            return error.code, None

    def start(self, name):
        service = SERVICES[name]
        log = (self.directory / f"{name}-{len(self.logs)}.log").open("w")
        self.logs.append(log)
        environment = dict(os.environ)
        environment.update({
            "SERVER_ADDRESS": "127.0.0.1",
            "SERVER_PORT": str(self.ports[name]),
            f"{name.upper()}_DB_URL": f"jdbc:h2:file:{self.directory / name};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
            "ANALYSIS_BASE_URL": f"http://127.0.0.1:{self.ports['analysis']}",
            "ARCHIVE_BASE_URL": f"http://127.0.0.1:{self.ports['archive']}"})
        jar = ROOT / "services" / service / "target" / f"{service}-0.1.0-SNAPSHOT.jar"
        self.processes[name] = subprocess.Popen(["java", "-jar", str(jar)], cwd=self.directory,
            env=environment, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            if self.processes[name].poll() is not None:
                raise AssertionError(f"{name} exited; private test log retained until test cleanup")
            try:
                # A 404 confirms HTTP readiness without creating work.
                self.call(name, "GET", "/v1/readiness-test")
                return
            except (OSError, urllib.error.URLError):
                time.sleep(.1)
        raise AssertionError(f"{name} failed to start")

    def stop(self, name, crash=False):
        process = self.processes.pop(name)
        process.kill() if crash else process.terminate()
        process.wait(timeout=10)

    def submit(self, scenario="FAST", key=None):
        key = key or str(uuid.uuid4())
        request = {"query": "halloween scavenger hunt printable", "scenario": scenario}
        status, run = self.call("orchestrator", "POST", "/v1/research-runs", request, key)
        assert status == 202, status
        return key, request, run

    def wait(self, run, states, timeout=15):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            status, view = self.call("orchestrator", "GET", "/v1/research-runs/" + run["id"])
            assert status == 200, f"Run lookup failed with HTTP {status}"
            if view["status"] in states:
                return view
            if view["status"] in {"FAILED", "CANCELLED", "SUCCEEDED"}:
                raise AssertionError(f"Unexpected terminal state: {view['status']} ({view['errorCode']})")
            time.sleep(.15)
        raise AssertionError("Workflow did not reach expected state")

    def close(self):
        for name in list(self.processes):
            self.stop(name)
        for log in self.logs:
            log.close()


def main():
    with tempfile.TemporaryDirectory(prefix="market-research-acceptance-") as directory:
        harness = Harness(directory)
        try:
            for service in ("analysis", "archive", "orchestrator"):
                harness.start(service)
            key, request, run = harness.submit()
            code, duplicate = harness.call("orchestrator", "POST", "/v1/research-runs", request, key)
            assert code == 202 and duplicate["id"] == run["id"]
            code, _ = harness.call("orchestrator", "POST", "/v1/research-runs",
                                   {**request, "query": "different"}, key)
            assert code == 409
            harness.wait(run, {"SUCCEEDED"})
            code, report = harness.call("orchestrator", "GET", "/v1/research-runs/" + run["id"] + "/report")
            assert code == 200 and report["source"] == "SYNTHETIC_FIXTURE"
            assert report["digitalListings"] == 3 and report["distinctShops"] == 3
            assert report["medianPrices"] == {"EUR": 4.0, "USD": 4.0}
            assert report["opportunityDecision"] == "INSUFFICIENT_EVIDENCE"
            # Business-action archive replay is idempotent; conflicting input is rejected.
            assert harness.call("archive", "POST", "/v1/research-reports", report)[0] == 201
            assert harness.call("archive", "POST", "/v1/research-reports",
                                {**report, "source": "DIFFERENT"})[0] == 409
            print("PASS: end-to-end report, run idempotency/conflict, archive idempotency/conflict")

            _, _, failed = harness.submit("FAIL")
            failure = harness.wait(failed, {"FAILED"})
            assert failure["errorCode"] == "ANALYSIS_FAILED"
            _, _, cancelled = harness.submit("SLOW")
            harness.wait(cancelled, {"ANALYZING"})
            assert harness.call("orchestrator", "DELETE", "/v1/research-runs/" + cancelled["id"])[0] == 202
            harness.wait(cancelled, {"CANCELLED"})
            print("PASS: explicit worker failure and cancellation")

            key, request, slow = harness.submit("SLOW")
            slow = harness.wait(slow, {"ANALYZING"})
            job_id = slow["analysisJobId"]
            harness.stop("orchestrator", crash=True)
            harness.stop("analysis", crash=True)
            harness.start("analysis")
            harness.start("orchestrator")
            harness.wait(slow, {"SUCCEEDED"}, timeout=40)
            code, recovered = harness.call("orchestrator", "POST", "/v1/research-runs", request, key)
            assert code == 202 and recovered["id"] == slow["id"] and recovered["analysisJobId"] == job_id
            harness.stop("archive", crash=True)
            harness.start("archive")
            assert harness.call("orchestrator", "GET", "/v1/research-runs/" + slow["id"] + "/report")[0] == 200
            print("PASS: orchestrator/worker crash recovery, stable job IDs, archive restart persistence")

            harness.stop("analysis")
            _, _, retrying = harness.submit()
            time.sleep(2)
            code, retry_state = harness.call("orchestrator", "GET", "/v1/research-runs/" + retrying["id"])
            assert code == 200 and retry_state["retryCount"] > 0
            harness.start("analysis")
            harness.wait(retrying, {"SUCCEEDED"}, timeout=20)
            print("PASS: persisted dependency backoff and recovery")
        finally:
            harness.close()


if __name__ == "__main__":
    main()
