"""Offline pagination tests; mocked transport does not consume Etsy quota."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.parse
import collect_etsy_cohort as collector


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.previous = collector.probe.DATA
        collector.probe.DATA = self.root
        self.quota = {"x-remaining-today":"4999","x-limit-per-second":"5"}
        self.calls = []
        self.print_patch = patch("collect_etsy_cohort.print", create=True)
        self.print_patch.start()

    def tearDown(self):
        self.print_patch.stop()
        collector.probe.DATA = self.previous
        self.directory.cleanup()

    def fake_request(self, path, label, *args):
        self.calls.append(path)
        if label == "quota-probe":
            return {}, self.quota
        offset = int(urllib.parse.parse_qs(path.split("?")[1])["offset"][0])
        return {"count": 10000, "results": [{"listing_id": i+1} for i in range(offset,offset+100)]}, self.quota

    def test_collects_1000_real_shaped_rows_in_ten_pages_without_padding(self):
        with patch.object(collector.probe,"request",side_effect=self.fake_request):
            manifest = collector.collect("test-only",self.root / "cohort")
        payload = json.loads((self.root / "cohort/input.json").read_text())
        self.assertEqual(1000,len(payload["listings"]))
        self.assertEqual(11,manifest["requests"])
        self.assertEqual(11,len(self.calls))

    def test_short_result_stops_without_synthetic_padding(self):
        with patch.object(collector.probe,"request",return_value=({"results":[]},self.quota)):
            with self.assertRaises(SystemExit):
                collector.collect("test-only",self.root / "cohort")
        self.assertFalse((self.root / "cohort/input.json").exists())

    def test_overlapping_pages_are_deduplicated_and_extra_page_is_bounded(self):
        def overlapping(path, label, *args):
            body, quota = self.fake_request(path,label,*args)
            if label != "quota-probe":
                offset = int(urllib.parse.parse_qs(path.split("?")[1])["offset"][0])
                start = max(0, offset - 50)
                body["results"] = [{"listing_id": i+1} for i in range(start,start+100)]
            return body,quota
        with patch.object(collector.probe,"request",side_effect=overlapping):
            manifest = collector.collect("test-only",self.root / "cohort")
        self.assertEqual(1000,manifest["unique_selected"])
        self.assertEqual(1100,manifest["fetched_rows"])
        self.assertEqual(12,manifest["requests"])

    def test_insufficient_quota_stops_before_search(self):
        with patch.object(collector.probe,"request",return_value=({},{"x-remaining-today":"10"})) as request:
            with self.assertRaises(SystemExit):
                collector.collect("test-only",self.root / "cohort")
            self.assertEqual(1,request.call_count)

    def test_existing_cohort_does_not_trigger_recollection(self):
        target = self.root / "cohort"
        target.mkdir()
        (target / "input.json").write_text("{}")
        with patch.object(collector.probe,"request") as request:
            with self.assertRaises(SystemExit):
                collector.collect("test-only",target)
            request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
