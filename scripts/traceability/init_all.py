#!/usr/bin/env python3
"""
init_all.py — the `/tn-init <source>` entrypoint. One command, one
positional argument, works no matter where the project comes from:

    python3 init_all.py https://github.com/org/repo.git   # clones, then inits
    python3 init_all.py git@github.com:org/repo.git
    python3 init_all.py ./already-cloned-project           # existing local path
    python3 init_all.py my-new-project                     # doesn't exist yet -> new project

What it does, always in this order:
  1. Resolve <source> to a target directory (cloning first if it's a git URL).
  2. Copy the base kit (AGENTS.md, CLAUDE.md, .cursor/, .agents/, .claude/,
     scripts/traceability/) into target — never overwrites files already
     there.
  3. Count real source files in target (ignoring the base kit itself and
     .git) — more than 5 real source files means "this already has code".
       - No code yet -> run init_traceability.py (clean bootstrap, Phase 8
         behavior, unchanged).
       - Has code -> run import_scan.py, which writes
         traceability.proposal.json and STOPS. It never writes to
         traceability.json directly (locked decision) — review the
         proposal, then run confirm_import.py --yes yourself.
  4. Try to wire the git hook via `git config core.hooksPath
     scripts/traceability/hooks` (only if target is already a git repo) —
     this avoids ever needing to write into .git/hooks/ directly.
  5. For the clean-bootstrap path only, immediately runs generate_wbs.py +
     validate_traceability.py so you start from a known-good state.
  6. Optional --with-codegraph: best-effort CodeGraphContext setup: skips
     silently (one summary line) if Python < 3.12 or `cgc` isn't
     installable here — never blocks, never prompts (Phase 9 decision).

Flags (all optional — the common case is just `init_all.py <source>`):
    --project NAME       display name (default: target directory's basename)
    --target PATH        override where a git URL gets cloned to
    --with-codegraph      best-effort CodeGraphContext setup
    --force               re-run steps even if target/base-kit files exist
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))          # .../scripts/traceability
KIT_ROOT = os.path.dirname(os.path.dirname(SCRIPTS_DIR))           # kit root (parent of scripts/)

sys.path.insert(0, SCRIPTS_DIR)
from import_scan import SOURCE_EXTS, IGNORE_DIRS  # noqa: E402

BASE_KIT_ENTRIES = ["AGENTS.md", "CLAUDE.md", ".cursor", ".agents", ".claude", "scripts"]
GIT_URL_RE = re.compile(r"^(https?://|git@|ssh://)")
HAS_CODE_THRESHOLD = 5


def classify_source(source):
    if GIT_URL_RE.match(source) or source.endswith(".git"):
        return "git"
    if os.path.isdir(source):
        return "existing-path"
    return "new-name"


def derive_repo_name(url):
    name = url.rstrip("/").split("/")[-1].split(":")[-1]
    if name.endswith(".git"):
        name = name[:-4]
    return name or "project"


def run(cmd, cwd=None, check=True):
    print("$ " + " ".join(cmd))
    result = subprocess.run(cmd, cwd=cwd)
    if check and result.returncode != 0:
        sys.exit(result.returncode)
    return result.returncode


def count_source_files(target):
    total = 0
    for root, dirs, files in os.walk(target):
        dirs[:] = [
            d for d in dirs
            if d not in IGNORE_DIRS and not d.startswith(".")
        ]
        total += sum(1 for f in files if os.path.splitext(f)[1] in SOURCE_EXTS)
    return total


def copy_if_missing(src, dst):
    if os.path.isdir(src):
        os.makedirs(dst, exist_ok=True)
        for entry in os.listdir(src):
            copy_if_missing(os.path.join(src, entry), os.path.join(dst, entry))
    elif os.path.isfile(src) and not os.path.exists(dst):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)


def copy_base_kit(target):
    copied_any = False
    for entry in BASE_KIT_ENTRIES:
        src = os.path.join(KIT_ROOT, entry)
        if not os.path.exists(src):
            continue
        dst = os.path.join(target, entry)
        before = set()
        if os.path.exists(dst):
            for root, _, files in os.walk(dst):
                before.update(os.path.join(root, f) for f in files)
        copy_if_missing(src, dst)
        copied_any = True
    return copied_any


def try_wire_git_hook(target):
    if not os.path.isdir(os.path.join(target, ".git")):
        print("NOTE: %s is not a git repo (yet) — skipping git hook wiring. "
              "Run `git init` then `git config core.hooksPath scripts/traceability/hooks` yourself." % target)
        return False
    hook_dir = os.path.join(target, "scripts", "traceability", "hooks")
    if not os.path.isdir(hook_dir):
        print("NOTE: no scripts/traceability/hooks/ found to wire up.")
        return False
    for f in os.listdir(hook_dir):
        os.chmod(os.path.join(hook_dir, f), 0o755)
    rc = run(["git", "config", "core.hooksPath", "scripts/traceability/hooks"], cwd=target, check=False)
    if rc == 0:
        print("OK: git hook wired via core.hooksPath (no .git/hooks/ write needed)")
        return True
    print("NOTE: could not set core.hooksPath automatically — run it yourself in %s" % target)
    return False


def try_codegraph(target):
    if sys.version_info < (3, 12):
        print("NOTE: --with-codegraph skipped (Python < 3.12 on this runtime)")
        return
    if shutil.which("cgc") is None:
        rc = run([sys.executable, "-m", "pip", "install", "--quiet", "codegraphcontext"], check=False)
        if rc != 0 or shutil.which("cgc") is None:
            print("NOTE: --with-codegraph skipped (could not install/find `cgc`)")
            return
    run([sys.executable, os.path.join(SCRIPTS_DIR, "sync_codegraph.py"), "--file",
         os.path.join(target, "traceability.json"), "--src-dir", target], check=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source", help="git URL, existing local path, or a new project name")
    ap.add_argument("--project", default=None)
    ap.add_argument("--target", default=None)
    ap.add_argument("--with-codegraph", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    kind = classify_source(args.source)

    if kind == "git":
        target = os.path.abspath(args.target or derive_repo_name(args.source))
        if os.path.exists(target) and os.listdir(target):
            print("NOTE: %s already exists and is non-empty — assuming already cloned, skipping git clone" % target)
        else:
            run(["git", "clone", args.source, target])
    elif kind == "existing-path":
        target = os.path.abspath(args.target or args.source)
    else:  # new-name
        target = os.path.abspath(args.target or args.source)
        os.makedirs(target, exist_ok=True)

    project_name = args.project or os.path.basename(target.rstrip(os.sep)) or "project"

    print("\n=== target: %s (source kind: %s) ===" % (target, kind))

    # A third state, checked BEFORE the empty/has-code decision: this
    # project might already be tracked (a traceability.json already
    # exists here — e.g. re-running init_all.py to pick up a newer base
    # kit on a project set up by an older version of it). Re-running the
    # import flow in that case would call import_scan.py again and
    # propose the whole codebase as brand-new REQs, ignoring everything
    # already recorded — never do that automatically.
    existing_graph = os.path.join(target, "traceability.json")
    if os.path.exists(existing_graph) and not args.force:
        with open(existing_graph, "r", encoding="utf-8") as f:
            existing_node_count = len(json.load(f).get("nodes", {}))
        print("Found existing traceability.json (%d node(s)) -> ALREADY-TRACKED flow "
              "(refreshing base kit only, not re-scanning code)" % existing_node_count)
        copy_base_kit(target)
        print("OK: base kit refreshed in %s (existing files left untouched, new scripts/commands added)" % target)
        wired = try_wire_git_hook(target)
        if args.with_codegraph:
            try_codegraph(target)
        run([sys.executable, os.path.join(SCRIPTS_DIR, "generate_wbs.py"), "--file", existing_graph], check=False)
        run([sys.executable, os.path.join(SCRIPTS_DIR, "validate_traceability.py"), "--file", existing_graph], check=False)
        print("\nOK: %s already tracked (%d node(s)), base kit refreshed, git hook re-wired if possible." % (target, existing_node_count))
        print("To find code added since the last audit, run: python3 %s --target %s "
              "(review the proposal before confirm_import.py, as always)." %
              (os.path.join(SCRIPTS_DIR, "import_scan.py"), target))
        print("\n=== summary ===")
        print("target:        %s" % target)
        print("flow:          already-tracked (refresh only)")
        print("git hook:      %s" % ("wired via core.hooksPath" if wired else "NOT wired (see note above)"))
        print("codegraph:     %s" % ("attempted" if args.with_codegraph else "not requested (--with-codegraph)"))
        return

    # Count BEFORE copying the base kit in — the kit's own scripts/*.py
    # would otherwise inflate the count and misclassify an empty project.
    n_files = count_source_files(target)
    has_code = n_files > HAS_CODE_THRESHOLD
    print("Found %d real source file(s) -> %s" % (n_files, "IMPORT flow (has code)" if has_code else "BOOTSTRAP flow (new/empty)"))

    copy_base_kit(target)
    print("OK: base kit copied into %s (existing files left untouched)" % target)

    if has_code:
        run([sys.executable, os.path.join(SCRIPTS_DIR, "import_scan.py"), "--target", target])
        wired = try_wire_git_hook(target)
        if args.with_codegraph:
            try_codegraph(target)
        print("\n=== NEXT STEP (required — nothing was written to traceability.json) ===")
        print("1. Review %s" % os.path.join(target, "traceability.proposal.json"))
        print("2. python3 %s --project \"%s\" --target %s --yes" %
              (os.path.join(SCRIPTS_DIR, "confirm_import.py"), project_name, target))
        print("3. python3 %s --file %s --check-spec-refs --check-code-refs" %
              (os.path.join(SCRIPTS_DIR, "validate_traceability.py"), os.path.join(target, "traceability.json")))
    else:
        run([sys.executable, os.path.join(SCRIPTS_DIR, "init_traceability.py"), "--project", project_name,
             "--target", target] + (["--force"] if args.force else []))
        wired = try_wire_git_hook(target)
        if args.with_codegraph:
            try_codegraph(target)
        run([sys.executable, os.path.join(SCRIPTS_DIR, "generate_wbs.py"), "--file",
             os.path.join(target, "traceability.json")], check=False)
        run([sys.executable, os.path.join(SCRIPTS_DIR, "validate_traceability.py"), "--file",
             os.path.join(target, "traceability.json")], check=False)
        print("\nOK: %s bootstrapped and validated clean. Add your first REQ with update_node.py." % target)

    print("\n=== summary ===")
    print("target:        %s" % target)
    print("flow:          %s" % ("import (proposal pending review)" if has_code else "bootstrap (clean)"))
    print("git hook:      %s" % ("wired via core.hooksPath" if wired else "NOT wired (see note above)"))
    print("codegraph:     %s" % ("attempted" if args.with_codegraph else "not requested (--with-codegraph)"))


if __name__ == "__main__":
    main()
