#!/usr/bin/env python3
"""
validate_traceability.py — structural + reference validation for
traceability.json. This is the actual bar for "done" per the
per-requirement checklist in SKILL.md.

    python3 validate_traceability.py [--file traceability.json] \
        [--check-spec-refs] [--check-code-refs] [--strict]

Checks (always on, structural — these are hard errors, exit 1):
  - every traces_to / depends_on target ID exists in the graph
  - every REQ transitively reaches a TEST-UAT via traces_to
  - every DESIGN depends_on a REQ, and reaches a TEST-SIT
  - every CODE depends_on a DESIGN, and reaches a TEST-UNIT
  - depends_on graph is acyclic

Checks (opt-in via flags — reported as warnings unless --strict):
  --check-spec-refs   REQ/DESIGN nodes should have spec_ref resolving to a
                       real file on disk (relative to the traceability.json
                       directory).
  --check-code-refs   CODE nodes should have code_ref resolving to a real
                       file on disk.

Exit code: 0 only if there are zero errors, and (with --strict) zero
warnings either.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import (  # noqa: E402
    EXPECTED_DEPENDS_TARGET,
    EXPECTED_TRACE_TARGET,
    load_graph,
    node_type_of_id,
    resolve_graph_path,
)


def reaches(nodes, node_id, target_type, edge_key, seen=None):
    """BFS from node_id along edge_key looking for a node of target_type."""
    seen = seen or set()
    if node_id in seen:
        return False
    seen.add(node_id)
    node = nodes.get(node_id)
    if node is None:
        return False
    for nxt in node.get(edge_key, []):
        if node_type_of_id(nxt) == target_type:
            return True
        if reaches(nodes, nxt, target_type, edge_key, seen):
            return True
    return False


def find_cycle(nodes):
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {nid: WHITE for nid in nodes}
    path = []

    def visit(nid):
        color[nid] = GRAY
        path.append(nid)
        for nxt in nodes.get(nid, {}).get("depends_on", []):
            if nxt not in nodes:
                continue
            if color.get(nxt) == GRAY:
                return path[path.index(nxt):] + [nxt]
            if color.get(nxt) == WHITE:
                cyc = visit(nxt)
                if cyc:
                    return cyc
        path.pop()
        color[nid] = BLACK
        return None

    for nid in nodes:
        if color[nid] == WHITE:
            cyc = visit(nid)
            if cyc:
                return cyc
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default=None)
    ap.add_argument("--check-spec-refs", action="store_true")
    ap.add_argument("--check-code-refs", action="store_true")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    graph_path = resolve_graph_path(args.file)
    project_root = os.path.dirname(os.path.abspath(graph_path))
    data = load_graph(graph_path)
    nodes = data["nodes"]

    errors = []
    warnings = []

    # dangling ref check
    for nid, node in nodes.items():
        for edge_key in ("traces_to", "depends_on"):
            for target in node.get(edge_key, []):
                if target not in nodes:
                    errors.append(
                        "%s: %s -> %s points to a node that does not exist"
                        % (nid, edge_key, target)
                    )

    # cycle check
    cyc = find_cycle(nodes)
    if cyc:
        errors.append("depends_on cycle detected: " + " -> ".join(cyc))

    # V-model coverage checks
    for nid, node in nodes.items():
        ntype = node.get("type")
        target = EXPECTED_TRACE_TARGET.get(ntype)
        if target and not reaches(nodes, nid, target, "traces_to"):
            errors.append("%s (%s): does not reach any %s via traces_to" % (nid, ntype, target))
        dep_target = EXPECTED_DEPENDS_TARGET.get(ntype)
        if dep_target:
            has_dep = any(
                node_type_of_id(d) == dep_target for d in node.get("depends_on", [])
            )
            if not has_dep:
                errors.append(
                    "%s (%s): missing depends_on a %s node" % (nid, ntype, dep_target)
                )

    # spec_ref / code_ref checks
    if args.check_spec_refs:
        for nid, node in nodes.items():
            if node.get("type") in ("REQ", "DESIGN"):
                ref = node.get("spec_ref")
                if not ref:
                    warnings.append("%s: missing spec_ref" % nid)
                elif not os.path.exists(os.path.join(project_root, ref)):
                    warnings.append("%s: spec_ref does not resolve to a real file: %s" % (nid, ref))

    if args.check_code_refs:
        for nid, node in nodes.items():
            if node.get("type") == "CODE":
                ref = node.get("code_ref")
                if not ref:
                    warnings.append("%s: missing code_ref" % nid)
                elif not os.path.exists(os.path.join(project_root, ref)):
                    warnings.append("%s: code_ref does not resolve to a real file: %s" % (nid, ref))

    for w in warnings:
        print("WARNING: " + w)
    for e in errors:
        print("ERROR: " + e)

    print(
        "\n%d node(s), %d error(s), %d warning(s)"
        % (len(nodes), len(errors), len(warnings))
    )

    if errors or (args.strict and warnings):
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
