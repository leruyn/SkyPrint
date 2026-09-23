#!/usr/bin/env python3
"""
confirm_import.py — commit a reviewed traceability.proposal.json into
traceability.json. This is the only script that turns an import_scan.py
proposal into real graph nodes — never done automatically, always a
separate, deliberate step (Phase 9 locked decision).

For every proposed REQ this creates the full 6-node set (REQ, DESIGN,
CODE, TEST-UAT, TEST-SIT, TEST-UNIT) wired together so
validate_traceability.py passes structurally right after import:
  - REQ:    spec_ref -> docs/features/<ID>/req.md (auto-scaffolded stub)
  - DESIGN: spec_ref -> docs/features/<ID>/design.md (auto-scaffolded
            stub, marked "needs real design review" — importing legacy
            code does not, by itself, mean a design was ever written)
  - CODE:   code_ref -> the real path found by import_scan.py
  - TEST-*: status stays "pending"; TEST-UNIT gets test_ref set if
            import_scan.py matched a real test file, otherwise empty
            (never marked pass/fail here — only sync_test_results.py may
            do that, from a real CI run)

All node titles/status start conservatively: "done" is NOT set here even
though code exists, because "done" means the full checklist (spec_ref +
test artifacts + validate clean), not just "file exists on disk".

Usage:
    python3 confirm_import.py --project "My Project" [--target .] \
        [--proposal traceability.proposal.json] [--yes]

Without --yes, prints what would be created and exits without writing —
this is the actual confirmation gate, not just a formality.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import load_graph, now_iso, save_graph  # noqa: E402

REQ_STUB = """# {title} (REQ)

> AUTO-IMPORTED by confirm_import.py on {date} from existing code at
> `{path}`. This stub was generated from the fact that this code exists,
> not from a real requirements conversation — every section below is a
> placeholder. Replace with the real use case (see
> scripts/traceability/templates/req.md.template for the format every
> other req.md follows) when known.

## Actor(s)

<!-- AUTO-IMPORTED — needs review: not knowable from code alone. -->

## Goal

<!-- AUTO-IMPORTED — needs review. -->

## Preconditions

<!-- AUTO-IMPORTED — needs review. -->

## Main flow

<!-- AUTO-IMPORTED — needs review: not knowable from code alone. -->

## Alternate / exception flows

<!-- AUTO-IMPORTED — needs review. -->

## Postconditions

<!-- AUTO-IMPORTED — needs review. -->

## Out of scope

<!-- AUTO-IMPORTED — needs review. -->

## Acceptance criteria (feeds TEST-UAT)

<!-- AUTO-IMPORTED — needs review. -->

## What this covers (evidence from import)

Code under `{path}` ({file_count} source file(s) found).
"""

DESIGN_STUB = """# {title} (DESIGN)

> AUTO-IMPORTED — NEEDS REVIEW. No real design doc was found for this
> code during import; every section below is a placeholder so spec_ref
> resolves to a real file. Replace with the actual design (see
> scripts/traceability/templates/design.md.template for the format every
> other design.md follows) once reviewed.

## Interfaces (keep narrow — SOLID / Interface Segregation)

<!-- AUTO-IMPORTED — needs review: not knowable from code alone. -->

## Components / classes

<!-- AUTO-IMPORTED — needs review. -->

## Data model

<!-- AUTO-IMPORTED — needs review. -->

## Dependencies

<!-- AUTO-IMPORTED — needs review. -->

## Risks / open questions

<!-- AUTO-IMPORTED — needs review. -->
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True)
    ap.add_argument("--target", default=".")
    ap.add_argument("--proposal", default=None)
    ap.add_argument("--yes", action="store_true", help="Actually write; omit to dry-run")
    args = ap.parse_args()

    target = os.path.abspath(args.target)
    proposal_path = args.proposal or os.path.join(target, "traceability.proposal.json")
    if not os.path.exists(proposal_path):
        sys.stderr.write("ERROR: proposal not found: %s (run import_scan.py first)\n" % proposal_path)
        sys.exit(1)
    with open(proposal_path, "r", encoding="utf-8") as f:
        proposal = json.load(f)

    graph_path = os.path.join(target, "traceability.json")
    if os.path.exists(graph_path):
        data = load_graph(graph_path)
    else:
        data = {"project": args.project, "created_at": now_iso(), "updated_at": now_iso(), "nodes": {}}

    existing_req_n = sum(1 for nid in data["nodes"] if nid.startswith("REQ-"))
    to_create = proposal["proposed_nodes"]

    print("Proposal: %d REQ candidate(s) from %s" % (len(to_create), proposal.get("target")))
    for n in to_create:
        flag = " [%s]" % n["note"] if n.get("note") else ""
        print("  - %s  (%s, %d files, module=%s)%s" % (n["title"], n["confidence"], n["file_count"], n.get("module"), flag))

    if not args.yes:
        print("\nDRY RUN — nothing written. Re-run with --yes after reviewing the list above")
        print("(edit %s by hand first if any of it looks wrong)." % proposal_path)
        return

    created = []
    for i, n in enumerate(to_create, start=1):
        req_n = existing_req_n + i
        req_id = "REQ-%03d" % req_n
        design_id = "DESIGN-%03d" % req_n
        code_id = "CODE-%03d" % req_n
        uat_id = "TEST-UAT-%03d" % req_n
        sit_id = "TEST-SIT-%03d" % req_n
        unit_id = "TEST-UNIT-%03d" % req_n

        feature_dir = os.path.join(target, "docs", "features", req_id)
        os.makedirs(feature_dir, exist_ok=True)
        req_md = os.path.join(feature_dir, "req.md")
        design_md = os.path.join(feature_dir, "design.md")
        if not os.path.exists(req_md):
            with open(req_md, "w", encoding="utf-8") as f:
                f.write(REQ_STUB.format(title=n["title"], date=now_iso(), path=n["path"], file_count=n["file_count"]))
        if not os.path.exists(design_md):
            with open(design_md, "w", encoding="utf-8") as f:
                f.write(DESIGN_STUB.format(title=n["title"]))

        now = now_iso()
        test_ref = n["matched_tests"][0] if n.get("matched_tests") else None

        def base_node(ntype, title):
            return {
                "type": ntype, "title": title, "status": "pending", "owner": "",
                "traces_to": [], "depends_on": [], "module": n.get("module"),
                "created_at": now, "updated_at": now,
            }

        req_node = base_node("REQ", n["title"])
        req_node["spec_ref"] = "docs/features/%s/req.md" % req_id
        req_node["traces_to"] = [uat_id]
        if n.get("note"):
            req_node["note"] = n["note"]

        design_node = base_node("DESIGN", n["title"] + " design")
        design_node["spec_ref"] = "docs/features/%s/design.md" % req_id
        design_node["depends_on"] = [req_id]
        design_node["traces_to"] = [sit_id]

        code_node = base_node("CODE", n["title"])
        code_node["code_ref"] = n["path"]
        code_node["depends_on"] = [design_id]
        code_node["traces_to"] = [unit_id]

        uat_node = base_node("TEST-UAT", "UAT — " + n["title"])
        sit_node = base_node("TEST-SIT", "SIT — " + n["title"])
        unit_node = base_node("TEST-UNIT", "Unit — " + n["title"])
        if test_ref:
            unit_node["test_ref"] = test_ref

        data["nodes"].update({
            req_id: req_node, design_id: design_node, code_id: code_node,
            uat_id: uat_node, sit_id: sit_node, unit_id: unit_node,
        })
        created.append(req_id)

    save_graph(graph_path, data)
    print("\nOK: created %d REQ (+5 linked nodes each) = %d nodes total in %s" % (len(created), len(created) * 6, graph_path))
    print("Next: python3 validate_traceability.py --check-spec-refs --check-code-refs, then generate_wbs.py")


if __name__ == "__main__":
    main()
