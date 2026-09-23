#!/usr/bin/env python3
"""
import_scan.py — scan an EXISTING codebase and propose REQ/DESIGN/CODE/TEST
nodes, WITHOUT ever writing to traceability.json directly.

Locked decision (Phase 9): importing a project with real code always stops
at a review step. This script only ever writes `traceability.proposal.json`
— run `confirm_import.py` afterwards (after a human/agent reviews the
proposal) to actually commit it.

Granularity heuristic (deliberately one generic rule, not per-framework
rules — simpler, trades some precision for not needing a rule per stack):
  1. Find the project's main source root: first of
     ["src", "app", "lib", "source", "Sources", "cmd", "internal"] that
     exists, else the project root itself.
  2. Collapse "pass-through" directories — any directory holding exactly
     one child directory and no files of its own (this is what naturally
     unwraps a Java/Kotlin package path like src/main/java/com/acme/app
     down to the first directory that actually branches, without any
     Java-specific rule).
  3. Each direct child directory of that point becomes one proposed REQ,
     as long as it contains at least one real source file somewhere under
     it. Loose files directly at that level (not in any subdirectory)
     become a single catch-all REQ, flagged `judgment-call`.

Monorepo detection: top-level directories that each contain their own
project marker (package.json, build.gradle*, pyproject.toml, pom.xml,
go.mod, pubspec.yaml, Package.swift) are treated as separate modules; the
above scan runs once per module and every proposed node gets `module` set.

Existing docs (any *.md under the repo besides README/LICENSE/CHANGELOG)
and existing test files (name matches *Test.*, test_*.py, *.spec.*,
*.test.*, *Tests.*) are searched for and attached as evidence where the
path plausibly matches a candidate (candidate dir name appears in the
doc/test path); everything else is reported under "unmatched_docs" /
"unmatched_tests" for manual linking later.

Usage:
    python3 import_scan.py --target /path/to/project [--out traceability.proposal.json]
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import now_iso  # noqa: E402

SOURCE_EXTS = {
    ".kt", ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".dart",
    ".swift", ".rb", ".rs", ".c", ".cpp", ".h", ".cs",
}
IGNORE_DIRS = {
    ".git", "node_modules", "build", "dist", "target", ".gradle", ".idea",
    "venv", ".venv", "__pycache__", ".claude", ".cursor", ".agents",
    "docs", ".dart_tool", "Pods", "vendor",
}
MAIN_SRC_CANDIDATES = ["src", "app", "lib", "source", "Sources", "cmd", "internal"]
# Generic (not framework-specific) convention for "this holds tests, not
# features" — excluded from candidate-root branching so e.g. Maven/Gradle's
# src/main vs src/test doesn't produce a bogus "test" REQ. Actual test
# *files* are still found and linked via find_tests()/matched_tests below,
# regardless of which directory they live in.
TEST_DIR_NAMES = {
    "test", "tests", "androidtest", "androidtestdebug", "spec", "specs",
    "__tests__", "e2e", "e2e-tests", "instrumented-test",
}
MODULE_MARKERS = {
    "package.json", "build.gradle", "build.gradle.kts", "pyproject.toml",
    "requirements.txt", "pom.xml", "go.mod", "pubspec.yaml", "Package.swift",
}
DOC_EXCLUDE = {"readme.md", "license.md", "changelog.md", "contributing.md"}
TEST_NAME_HINTS = ("test_", "_test", "test.", "spec.", "tests.")

# Above a certain size, one folder is probably not "one requirement" — flag
# it as a judgment call instead of asserting mechanical confidence.
LARGE_CANDIDATE_FILE_COUNT = 30


def is_source_file(fn):
    return os.path.splitext(fn)[1] in SOURCE_EXTS


def count_source_files(path):
    total = 0
    for root, dirs, files in os.walk(path):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith(".")]
        total += sum(1 for f in files if is_source_file(f))
    return total


def find_modules(target):
    """Return list of (module_name_or_None, module_path)."""
    top_level_with_marker = []
    for entry in sorted(os.listdir(target)):
        p = os.path.join(target, entry)
        if not os.path.isdir(p) or entry in IGNORE_DIRS or entry.startswith("."):
            continue
        if any(os.path.exists(os.path.join(p, m)) for m in MODULE_MARKERS):
            top_level_with_marker.append((entry, p))
    if len(top_level_with_marker) >= 2:
        return top_level_with_marker
    return [(None, target)]


def find_main_src(module_path):
    for cand in MAIN_SRC_CANDIDATES:
        p = os.path.join(module_path, cand)
        if os.path.isdir(p):
            return p
    return module_path


def collapse_passthrough(path):
    """Descend through directories that have exactly 1 subdir and 0 files."""
    cur = path
    while True:
        try:
            entries = os.listdir(cur)
        except OSError:
            return cur
        entries = [e for e in entries if e not in IGNORE_DIRS and not e.startswith(".")]
        subdirs = [
            e for e in entries
            if os.path.isdir(os.path.join(cur, e)) and e.lower() not in TEST_DIR_NAMES
        ]
        files = [e for e in entries if os.path.isfile(os.path.join(cur, e))]
        if len(subdirs) == 1 and len(files) == 0:
            cur = os.path.join(cur, subdirs[0])
        else:
            return cur


def find_docs(target):
    docs = []
    for root, dirs, files in os.walk(target):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith(".")]
        for f in files:
            if f.lower().endswith(".md") and f.lower() not in DOC_EXCLUDE:
                docs.append(os.path.relpath(os.path.join(root, f), target))
    return docs


def find_tests(target):
    tests = []
    for root, dirs, files in os.walk(target):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith(".")]
        for f in files:
            low = f.lower()
            if is_source_file(f) and any(h in low for h in TEST_NAME_HINTS):
                tests.append(os.path.relpath(os.path.join(root, f), target))
    return tests


def scan_module(target, module_name, module_path, docs, tests):
    main_src = find_main_src(module_path)
    base = collapse_passthrough(main_src)
    try:
        entries = os.listdir(base)
    except OSError:
        entries = []
    entries = [e for e in entries if e not in IGNORE_DIRS and not e.startswith(".")]
    subdirs = sorted(
        e for e in entries
        if os.path.isdir(os.path.join(base, e)) and e.lower() not in TEST_DIR_NAMES
    )
    loose_files = [e for e in entries if os.path.isfile(os.path.join(base, e)) and is_source_file(e)]

    candidates = []
    for d in subdirs:
        full = os.path.join(base, d)
        n_files = count_source_files(full)
        if n_files == 0:
            continue
        rel = os.path.relpath(full, target)
        matched_docs = [doc for doc in docs if d.lower() in doc.lower()]
        matched_tests = [t for t in tests if d.lower() in t.lower()]
        candidates.append({
            "title": d,
            "path": rel,
            "module": module_name,
            "file_count": n_files,
            "confidence": "judgment-call" if n_files > LARGE_CANDIDATE_FILE_COUNT else "mechanical",
            "matched_docs": matched_docs,
            "matched_tests": matched_tests,
        })

    if loose_files:
        rel = os.path.relpath(base, target)
        candidates.append({
            "title": "%s (catch-all)" % os.path.basename(base.rstrip(os.sep)),
            "path": rel,
            "module": module_name,
            "file_count": len(loose_files),
            "confidence": "judgment-call",
            "note": "loose files directly under %s not in any subdirectory — catch-all, split by hand if needed" % rel,
            "matched_docs": [],
            "matched_tests": [],
        })

    return candidates


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", required=True)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    target = os.path.abspath(args.target)
    out_path = args.out or os.path.join(target, "traceability.proposal.json")

    docs = find_docs(target)
    tests = find_tests(target)
    modules = find_modules(target)

    all_candidates = []
    for module_name, module_path in modules:
        all_candidates.extend(scan_module(target, module_name, module_path, docs, tests))

    proposed_nodes = []
    for i, c in enumerate(all_candidates, start=1):
        proposed_nodes.append({
            "suggested_id": "REQ-%03d" % i,
            "title": c["title"],
            "path": c["path"],
            "module": c["module"],
            "confidence": c["confidence"],
            "file_count": c["file_count"],
            "matched_docs": c["matched_docs"],
            "matched_tests": c["matched_tests"],
            "note": c.get("note"),
        })

    matched_doc_set = {d for c in all_candidates for d in c["matched_docs"]}
    matched_test_set = {t for c in all_candidates for t in c["matched_tests"]}

    proposal = {
        "generated_at": now_iso(),
        "target": target,
        "modules_detected": [m for m, _ in modules],
        "heuristic": "one REQ per top-level directory under the collapsed main source root (generic, not per-framework)",
        "proposed_nodes": proposed_nodes,
        "unmatched_docs": sorted(set(docs) - matched_doc_set),
        "unmatched_tests": sorted(set(tests) - matched_test_set),
    }

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(proposal, f, indent=2, ensure_ascii=False)
        f.write("\n")

    n_judgment = sum(1 for n in proposed_nodes if n["confidence"] == "judgment-call")
    print(
        "OK: wrote %s — %d proposed REQ (%d mechanical, %d judgment-call), "
        "%d unmatched doc(s), %d unmatched test(s)"
        % (out_path, len(proposed_nodes), len(proposed_nodes) - n_judgment, n_judgment,
           len(proposal["unmatched_docs"]), len(proposal["unmatched_tests"]))
    )
    print("Review the proposal, edit it if needed, then run confirm_import.py.")


if __name__ == "__main__":
    main()
