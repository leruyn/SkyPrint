#!/usr/bin/env python3
"""
update_node.py — create or update a single node in traceability.json.

Create a new node (ID auto-assigned):
    python3 update_node.py --type REQ --title "Checkout flow" \
        --status pending --spec-ref docs/features/REQ-004/req.md

Update an existing node (merge — omitted fields are left unchanged):
    python3 update_node.py --id REQ-004 --status done

Link nodes:
    python3 update_node.py --id DESIGN-004 --depends-on REQ-004 \
        --traces-to TEST-SIT-004

--spec-ref is only valid on REQ/DESIGN nodes. --code-ref is only valid on
CODE nodes. Both enforced (hard error), matching the Phase 7 checklist.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import (  # noqa: E402
    VALID_STATUS,
    VALID_TYPES,
    die,
    load_graph,
    next_id,
    node_type_of_id,
    now_iso,
    resolve_graph_path,
    save_graph,
)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default=None, help="Path to traceability.json")
    ap.add_argument("--id", default=None, help="Existing node ID to update")
    ap.add_argument("--type", choices=VALID_TYPES, help="Node type (for create)")
    ap.add_argument("--title", default=None)
    ap.add_argument("--status", default=None, choices=sorted(VALID_STATUS))
    ap.add_argument("--owner", default=None)
    ap.add_argument("--traces-to", nargs="*", default=None)
    ap.add_argument("--depends-on", nargs="*", default=None)
    ap.add_argument("--spec-ref", default=None)
    ap.add_argument("--code-ref", default=None)
    ap.add_argument("--module", default=None)
    ap.add_argument(
        "--note", default=None, help="Free-text note, e.g. why a catch-all exists"
    )
    args = ap.parse_args()

    graph_path = resolve_graph_path(args.file)
    data = load_graph(graph_path)

    if args.id:
        node_id = args.id
        node = data["nodes"].get(node_id)
        if node is None:
            die("no such node: %s (omit --id to create a new one)" % node_id)
        node_type = node["type"]
    else:
        if not args.type:
            die("--type is required when creating a new node (no --id given)")
        node_type = args.type
        node_id = next_id(data, node_type)
        node = {
            "type": node_type,
            "title": "",
            "status": "pending",
            "owner": "",
            "traces_to": [],
            "depends_on": [],
            "created_at": now_iso(),
        }
        data["nodes"][node_id] = node

    # enforce spec_ref / code_ref scoping
    if args.spec_ref is not None and node_type not in ("REQ", "DESIGN"):
        die("--spec-ref is only valid on REQ/DESIGN nodes (this is %s)" % node_type)
    if args.code_ref is not None and node_type != "CODE":
        die("--code-ref is only valid on CODE nodes (this is %s)" % node_type)

    if args.title is not None:
        node["title"] = args.title
    if args.status is not None:
        node["status"] = args.status
    if args.owner is not None:
        node["owner"] = args.owner
    if args.traces_to is not None:
        for t in args.traces_to:
            if node_type_of_id(t) is None:
                die("bad node ID in --traces-to: %s" % t)
        node["traces_to"] = sorted(set(node.get("traces_to", [])) | set(args.traces_to))
    if args.depends_on is not None:
        for t in args.depends_on:
            if node_type_of_id(t) is None:
                die("bad node ID in --depends-on: %s" % t)
        node["depends_on"] = sorted(
            set(node.get("depends_on", [])) | set(args.depends_on)
        )
    if args.spec_ref is not None:
        node["spec_ref"] = args.spec_ref
    if args.code_ref is not None:
        node["code_ref"] = args.code_ref
    if args.module is not None:
        node["module"] = args.module
    if args.note is not None:
        node["note"] = args.note

    node["updated_at"] = now_iso()
    save_graph(graph_path, data)
    print("OK: %s -> %s" % (node_id, node["status"]))


if __name__ == "__main__":
    main()
