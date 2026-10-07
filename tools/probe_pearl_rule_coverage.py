"""Which of Pearl's rules has NO judge watching it?

Removing a rule and running her judges is the only honest way to ask: judges read BEHAVIOUR, not rule ids (measured - only
2 of her 51 ids appear in any test file), so "is this rule covered" cannot be answered by grepping names.

Each rule is removed in turn, the suite is run, and the outcome recorded. A rule whose removal changes nothing is a rule
nothing is guarding.

Usage:
    python tools/probe_pearl_rule_coverage.py            # all rules
    python tools/probe_pearl_rule_coverage.py <rule-id>  # just one
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
OUT = pathlib.Path("tools/_pearl_rule_coverage.txt")


def rules():
    return json.loads(CONTENT.read_text(encoding="utf-8"))["rules"]


def write(kept):
    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    data["rules"] = kept
    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    original = rules()
    targets = sys.argv[1:] or [r["id"] for r in original]
    lines = []
    for rule_id in targets:
        kept = [r for r in original if r["id"] != rule_id]
        if len(kept) == len(original):
            lines.append(f"?? {rule_id}: not found")
            continue
        write(kept)
        try:
            result = subprocess.run([".\\gradlew.bat", "test", "--tests", "*Pearl*", "--console=plain"],
                                    capture_output=True, text=True)
            caught = result.returncode != 0
        finally:
            write(original)
        status = "GUARDED  " if caught else "UNGUARDED"
        lines.append(f"{status} {rule_id}")
        print(lines[-1], flush=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    unguarded = [l for l in lines if l.startswith("UNGUARDED")]
    print(f"\nunguarded: {len(unguarded)} of {len(targets)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
