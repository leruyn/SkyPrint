#!/usr/bin/env python3
"""
init_traceability.py — create an empty traceability.json + docs scaffold.

Usage:
    python3 init_traceability.py --project "My Project" [--target .] [--force]

Idempotent: refuses to overwrite an existing traceability.json unless
--force is passed.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import now_iso, save_graph, DEFAULT_FILE  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True, help="Project display name")
    ap.add_argument("--target", default=".", help="Target project root")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    target = os.path.abspath(args.target)
    os.makedirs(target, exist_ok=True)
    graph_path = os.path.join(target, DEFAULT_FILE)

    if os.path.exists(graph_path) and not args.force:
        print("SKIP: %s already exists (use --force to overwrite)" % graph_path)
        return

    data = {
        "project": args.project,
        "created_at": now_iso(),
        "updated_at": now_iso(),
        "nodes": {},
    }
    save_graph(graph_path, data)

    docs_dir = os.path.join(target, "docs", "features")
    os.makedirs(docs_dir, exist_ok=True)
    wbs_path = os.path.join(target, "docs", "wbs.md")
    if not os.path.exists(wbs_path):
        with open(wbs_path, "w", encoding="utf-8") as f:
            f.write(
                "# WBS\n\n> Generated file — run `generate_wbs.py` after adding "
                "nodes. Do not hand-edit.\n"
            )

    print("OK: initialized %s" % graph_path)
    print("OK: docs/features/ + docs/wbs.md scaffolded")


if __name__ == "__main__":
    main()
