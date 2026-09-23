"""
_lib.py — shared helpers for the traceability-network script set.

Single source of truth is traceability.json, sitting at the project root
(or wherever --file points). Schema:

{
  "project": "<name>",
  "created_at": "<iso8601>",
  "updated_at": "<iso8601>",
  "nodes": {
    "REQ-001": {
      "type": "REQ",
      "title": "...",
      "status": "pending|in_progress|done|pass|fail",
      "owner": "...",
      "traces_to": ["TEST-UAT-001"],
      "depends_on": [],
      "spec_ref": "docs/features/REQ-001/req.md",   # REQ/DESIGN only
      "code_ref": "src/foo/Bar.kt",                  # CODE only
      "module": "backend",                           # optional, monorepo
      "created_at": "...",
      "updated_at": "..."
    },
    ...
  }
}

No third-party dependencies — must run with a bare `python3`, nothing else,
so any agent with a terminal (Claude Code, Cursor, Antigravity, plain CI) can
call these scripts the same way.
"""
import json
import os
import re
import sys
from datetime import datetime, timezone

DEFAULT_FILE = "traceability.json"

TYPE_PREFIX = {
    "REQ": "REQ",
    "DESIGN": "DESIGN",
    "CODE": "CODE",
    "TEST-UAT": "TEST-UAT",
    "TEST-SIT": "TEST-SIT",
    "TEST-UNIT": "TEST-UNIT",
}

VALID_TYPES = list(TYPE_PREFIX.keys())

# V-model edges this node type is expected to eventually reach/depend on.
EXPECTED_TRACE_TARGET = {
    "REQ": "TEST-UAT",
    "DESIGN": "TEST-SIT",
    "CODE": "TEST-UNIT",
}
EXPECTED_DEPENDS_TARGET = {
    "DESIGN": "REQ",
    "CODE": "DESIGN",
}

VALID_STATUS = {"pending", "in_progress", "done", "pass", "fail", "blocked"}

ID_RE = re.compile(r"^(REQ|DESIGN|CODE|TEST-UAT|TEST-SIT|TEST-UNIT)-(\d+)$")


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def node_type_of_id(node_id):
    m = ID_RE.match(node_id)
    if not m:
        return None
    return m.group(1)


def die(msg, code=1):
    sys.stderr.write("ERROR: " + msg + "\n")
    sys.exit(code)


def load_graph(path):
    if not os.path.exists(path):
        die(
            "traceability file not found: %s "
            "(run init_traceability.py first)" % path
        )
    with open(path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError as e:
            die("invalid JSON in %s: %s" % (path, e))
    data.setdefault("nodes", {})
    return data


def save_graph(path, data):
    data["updated_at"] = now_iso()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False, sort_keys=True)
        f.write("\n")
    os.replace(tmp, path)


def next_id(data, node_type):
    """Return the next free ID for node_type, e.g. REQ-007."""
    prefix = TYPE_PREFIX[node_type]
    max_n = 0
    for nid in data["nodes"]:
        t = node_type_of_id(nid)
        if t == node_type:
            n = int(nid.rsplit("-", 1)[-1])
            max_n = max(max_n, n)
    return "%s-%03d" % (prefix, max_n + 1)


def find_file(start_dir, filename):
    """Walk upward from start_dir looking for filename. Returns path or None."""
    cur = os.path.abspath(start_dir)
    while True:
        candidate = os.path.join(cur, filename)
        if os.path.exists(candidate):
            return candidate
        parent = os.path.dirname(cur)
        if parent == cur:
            return None
        cur = parent


def resolve_graph_path(explicit_path=None):
    if explicit_path:
        return explicit_path
    found = find_file(os.getcwd(), DEFAULT_FILE)
    return found or os.path.join(os.getcwd(), DEFAULT_FILE)
