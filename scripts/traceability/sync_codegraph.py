#!/usr/bin/env python3
"""
sync_codegraph.py — diff CODE-* node `code_ref` fields against the real
codebase. Never auto-creates nodes from what it finds — reporting only.

Two modes:
  1. Full mode (default): if the optional `cgc` CLI (CodeGraphContext,
     `pip install codegraphcontext`) is installed AND the Python runtime is
     3.12+, runs `cgc update` to refresh the indexed code graph first.
     CodeGraphContext is a fast-churning small OSS project, so if its CLI
     surface has changed and `cgc update` fails, this script logs a note
     and falls back to mode 2 rather than hard-failing.
  2. File-existence mode (fallback, always available, zero dependencies):
     checks each CODE-* node's code_ref against the filesystem directly,
     and walks common source directories for files no CODE-* node
     references.

Locked decision (per Phase 9 discussion): if Python < 3.12 or `cgc` isn't
on PATH, skip the cgc-specific step SILENTLY and just note it in the final
summary line — never block, never prompt.

Usage:
    python3 sync_codegraph.py [--file traceability.json] [--no-index] \
        [--src-dir .]

Reports (never fails the exit code by itself — this is informational,
`validate_traceability.py --check-code-refs` is the actual gate):
  - BROKEN code_ref: node has code_ref, file doesn't exist
  - NO code_ref: CODE-* node missing code_ref entirely
  - UNTRACKED: real source file under --src-dir not referenced by any
    code_ref (best-effort, common extensions only)
"""
import argparse
import os
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _lib import load_graph, resolve_graph_path  # noqa: E402

SOURCE_EXTS = {
    ".kt", ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".dart",
    ".swift", ".rb", ".rs", ".c", ".cpp", ".h", ".cs",
}
IGNORE_DIR_NAMES = {
    ".git", "node_modules", "build", "dist", "target", ".gradle", ".idea",
    "venv", ".venv", "__pycache__", "docs", ".claude", ".cursor", ".agents",
}


def cgc_available():
    return sys.version_info >= (3, 12) and shutil.which("cgc") is not None


def try_cgc_update(project_root):
    try:
        result = subprocess.run(
            ["cgc", "update"], cwd=project_root, capture_output=True,
            text=True, timeout=120,
        )
        return result.returncode == 0
    except Exception as e:  # cgc CLI surface can change without notice
        print("NOTE: `cgc update` failed (%s) — falling back to file-existence mode" % e)
        return False


def walk_source_files(src_dir):
    found = []
    for root, dirs, files in os.walk(src_dir):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIR_NAMES and not d.startswith(".")]
        for fn in files:
            if os.path.splitext(fn)[1] in SOURCE_EXTS:
                found.append(os.path.relpath(os.path.join(root, fn), src_dir))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default=None)
    ap.add_argument("--src-dir", default=None)
    ap.add_argument("--no-index", action="store_true", help="Skip `cgc update`, assume already indexed")
    args = ap.parse_args()

    graph_path = resolve_graph_path(args.file)
    project_root = os.path.dirname(os.path.abspath(graph_path))
    src_dir = args.src_dir or project_root
    data = load_graph(graph_path)
    nodes = data["nodes"]

    used_cgc = False
    if cgc_available():
        if args.no_index or try_cgc_update(project_root):
            used_cgc = True
    else:
        reason = "Python < 3.12" if sys.version_info < (3, 12) else "`cgc` not on PATH"
        print("NOTE: codegraph sync skipped cgc-native step (%s) — using file-existence mode" % reason)

    broken, missing, referenced = [], [], set()
    for nid, node in nodes.items():
        if node.get("type") != "CODE":
            continue
        ref = node.get("code_ref")
        if not ref:
            missing.append(nid)
            continue
        referenced.add(os.path.normpath(ref))
        if not os.path.exists(os.path.join(project_root, ref)):
            broken.append((nid, ref))

    untracked = []
    for f in walk_source_files(src_dir):
        if os.path.normpath(f) not in referenced:
            untracked.append(f)

    for nid, ref in broken:
        print("BROKEN code_ref: %s -> %s" % (nid, ref))
    for nid in missing:
        print("NO code_ref: %s" % nid)
    for f in untracked[:200]:
        print("UNTRACKED: %s" % f)
    if len(untracked) > 200:
        print("... and %d more untracked file(s)" % (len(untracked) - 200))

    mode = "cgc-native" if used_cgc else "file-existence fallback"
    print(
        "\nOK: mode=%s, %d broken, %d missing code_ref, %d untracked file(s)"
        % (mode, len(broken), len(missing), len(untracked))
    )


if __name__ == "__main__":
    main()
