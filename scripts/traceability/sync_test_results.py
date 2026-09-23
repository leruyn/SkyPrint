#!/usr/bin/env python3
"""
sync_test_results.py — the ONLY script allowed to set a TEST-* node's
status to pass/fail (locked decision — never set by hand, never by
update_node.py). Everything else stays "pending" until a real CI run
produces a JUnit XML report.

Works off the standard JUnit XML schema, which Gradle/Maven (Java/Kotlin),
pytest (`pytest --junitxml=...`), and JS (`jest-junit`) all emit — so this
one script covers every stack without per-tool special-casing.

Matching a <testcase> in a report to a TEST-* node uses a `test_ref` field
on the node (set via update_node.py, e.g.
`update_node.py --id TEST-UNIT-001 ...` then hand-edit or a future
`--test-ref` flag) — a substring that should appear in
"<classname>.<name>" of the real test. Nodes without a test_ref are
reported as skipped, not touched.

Usage:
    python3 sync_test_results.py [--file traceability.json] \
        [--junit-dir path/to/reports]

If --junit-dir is omitted, auto-searches common conventions under the
project root: build/test-results (Gradle), target/surefire-reports
(Maven), test-results/ and reports/junit/ (pytest/jest defaults).
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import load_graph, resolve_graph_path, save_graph  # noqa: E402

COMMON_DIRS = [
    "build/test-results",
    "target/surefire-reports",
    "test-results",
    "reports/junit",
    "junit-reports",
]


def find_reports(project_root, junit_dir):
    if junit_dir:
        search_roots = [junit_dir]
    else:
        search_roots = [
            os.path.join(project_root, d)
            for d in COMMON_DIRS
            if os.path.isdir(os.path.join(project_root, d))
        ]
    xml_files = []
    for root in search_roots:
        xml_files.extend(glob.glob(os.path.join(root, "**", "*.xml"), recursive=True))
    return sorted(set(xml_files))


def parse_testcases(xml_path):
    """Yield (identifier, status) for each <testcase> in a JUnit XML file."""
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError:
        return
    root = tree.getroot()
    suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
    if root.tag != "testsuite" and not suites:
        suites = [root]
    for suite in suites:
        for tc in suite.findall("testcase"):
            classname = tc.get("classname", "")
            name = tc.get("name", "")
            identifier = "%s.%s" % (classname, name) if classname else name
            if tc.find("failure") is not None or tc.find("error") is not None:
                status = "fail"
            elif tc.find("skipped") is not None:
                status = "skipped"
            else:
                status = "pass"
            yield identifier, status


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default=None)
    ap.add_argument("--junit-dir", default=None)
    args = ap.parse_args()

    graph_path = resolve_graph_path(args.file)
    project_root = os.path.dirname(os.path.abspath(graph_path))
    data = load_graph(graph_path)
    nodes = data["nodes"]

    reports = find_reports(project_root, args.junit_dir)
    if not reports:
        print("WARNING: no JUnit XML reports found (checked: %s)" % (args.junit_dir or ", ".join(COMMON_DIRS)))

    all_cases = []
    for r in reports:
        all_cases.extend(parse_testcases(r))

    updated, skipped, unmatched = 0, 0, 0
    for nid, node in nodes.items():
        if node["type"] not in ("TEST-UAT", "TEST-SIT", "TEST-UNIT"):
            continue
        test_ref = node.get("test_ref")
        if not test_ref:
            skipped += 1
            continue
        matches = [status for ident, status in all_cases if test_ref in ident]
        if not matches:
            unmatched += 1
            print("NOTE: %s: no report matched test_ref=%r" % (nid, test_ref))
            continue
        new_status = "fail" if "fail" in matches else ("pass" if "pass" in matches else "pending")
        if new_status != node["status"]:
            node["status"] = new_status
            updated += 1

    save_graph(graph_path, data)
    print(
        "OK: %d report file(s), %d node(s) updated, %d skipped (no test_ref), %d unmatched"
        % (len(reports), updated, skipped, unmatched)
    )


if __name__ == "__main__":
    main()
