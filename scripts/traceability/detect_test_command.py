#!/usr/bin/env python3
"""
detect_test_command.py — figure out how to run this project's tests by
looking at marker files at the root, so CI templates don't need per-repo
editing for the common stacks.

    python3 detect_test_command.py --target .          # just print what it found
    python3 detect_test_command.py --target . --run     # actually run the tests

Detection order (first match wins):
  build.gradle(.kts)   -> ./gradlew test               (JUnit XML: build/test-results/**)
  pom.xml              -> mvn test                      (JUnit XML: target/surefire-reports/**)
  package.json         -> npm test                      (expects a "test" script; JUnit XML
                                                           wherever jest-junit/etc is configured
                                                           to write — reports/junit/ by default
                                                           convention this kit's
                                                           sync_test_results.py already checks)
  pyproject.toml /
  requirements.txt     -> pytest --junitxml=test-results/results.xml
  go.mod               -> go test ./...                 (no JUnit XML without extra tooling —
                                                           flagged as a limitation)

If nothing matches, exits with a clear message instead of guessing.
"""
import argparse
import json
import os
import subprocess
import sys

DETECTORS = [
    ("gradle", ["build.gradle", "build.gradle.kts"], ["./gradlew", "test"], "build/test-results"),
    ("maven", ["pom.xml"], ["mvn", "test"], "target/surefire-reports"),
    ("npm", ["package.json"], ["npm", "test"], "reports/junit"),
    ("pytest", ["pyproject.toml", "requirements.txt", "setup.py"],
     ["pytest", "--junitxml=test-results/results.xml"], "test-results"),
    ("go", ["go.mod"], ["go", "test", "./..."], None),
]


def detect(target):
    for stack, markers, cmd, junit_dir in DETECTORS:
        if any(os.path.exists(os.path.join(target, m)) for m in markers):
            return stack, cmd, junit_dir
    return None, None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default=".")
    ap.add_argument("--run", action="store_true")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    target = os.path.abspath(args.target)
    stack, cmd, junit_dir = detect(target)

    if not stack:
        sys.stderr.write("ERROR: no known test stack detected under %s "
                          "(looked for build.gradle*, pom.xml, package.json, "
                          "pyproject.toml/requirements.txt, go.mod)\n" % target)
        sys.exit(2)

    if args.json:
        print(json.dumps({"stack": stack, "command": cmd, "junit_dir": junit_dir}))
        return

    print("Detected stack: %s" % stack)
    print("Test command:   %s" % " ".join(cmd))
    print("JUnit XML dir:  %s" % (junit_dir or "(none — go test needs extra tooling for JUnit XML)"))

    if args.run:
        rc = subprocess.run(cmd, cwd=target).returncode
        print("\nTest command exited with code %d" % rc)
        sys.exit(rc)


if __name__ == "__main__":
    main()
