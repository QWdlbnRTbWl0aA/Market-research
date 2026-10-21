"""Offline tests for live-discovery request guards; never sends network requests."""
import io
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import urllib.error

import etsy_probe as probe


class Response(io.BytesIO):
    headers = {"x-limit-per-second": "5", "x-remaining-today": "99"}
    def __init__(self):
        super().__init__(b'{"count":0,"results":[]}')


class BudgetTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.previous = probe.DATA
        probe.DATA = Path(self.directory.name)

    def tearDown(self):
        probe.DATA = self.previous
        self.directory.cleanup()

    def ledger(self, value):
        (probe.DATA / "budget.json").write_text(json.dumps(value))

    def test_unknown_quota_blocks_search_before_network(self):
        with patch.object(probe.urllib.request, "urlopen") as send:
            with self.assertRaises(SystemExit):
                probe.request("listings/active", "search-0", "test-only")
            send.assert_not_called()

    def test_rolling_budget_blocks_even_probe(self):
        self.ledger({"calls": [time.time()] * 6})
        with patch.object(probe.urllib.request, "urlopen") as send:
            with self.assertRaises(SystemExit):
                probe.request("openapi-ping", "quota-probe", "test-only")
            send.assert_not_called()

    def test_daily_reserve_blocks_search(self):
        self.ledger({"calls": [], "quota": {"x-limit-per-second": "5", "x-remaining-today": "10"}})
        with patch.object(probe.urllib.request, "urlopen") as send:
            with self.assertRaises(SystemExit):
                probe.request("listings/active", "search-0", "test-only")
            send.assert_not_called()

    def test_reservation_survives_transport_failure(self):
        with patch.object(probe.urllib.request, "urlopen", side_effect=OSError("test failure")):
            with self.assertRaises(SystemExit):
                probe.request("openapi-ping", "quota-probe", "test-only")
        ledger = json.loads((probe.DATA / "budget.json").read_text())
        self.assertEqual(1, len(ledger["calls"]))

    def test_rate_headers_and_minimum_spacing(self):
        self.ledger({"calls": [999], "quota": {"x-limit-per-second": "5", "x-remaining-today": "100"}})
        with patch.object(probe.time, "time", return_value=1000), patch.object(probe.time, "sleep") as sleep:
            with patch.object(probe.urllib.request, "urlopen", return_value=Response()):
                _, headers = probe.request("listings/active", "search-0", "test-only")
        sleep.assert_called_once_with(1)
        self.assertEqual("99", headers["x-remaining-today"])
        self.assertEqual(2, len(json.loads((probe.DATA / "budget.json").read_text())["calls"]))

    def test_429_persists_cooldown_and_does_not_retry(self):
        error = urllib.error.HTTPError("https://example.invalid", 429, "limited", {"retry-after": "120"}, None)
        with patch.object(probe.urllib.request, "urlopen", side_effect=error) as send:
            with self.assertRaises(SystemExit):
                probe.request("openapi-ping", "quota-probe", "test-only")
            self.assertEqual(1, send.call_count)
        self.assertGreater(json.loads((probe.DATA / "budget.json").read_text())["blocked_until"], time.time() + 100)
        with patch.object(probe.urllib.request, "urlopen") as send:
            with self.assertRaises(SystemExit):
                probe.request("openapi-ping", "quota-probe", "test-only")
            send.assert_not_called()


if __name__ == "__main__":
    unittest.main()
