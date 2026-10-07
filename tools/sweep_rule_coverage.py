"""Which shipped rule has NO judge watching it? Sweeps every character, one rule at a time.

Why the method has to be this: judges read BEHAVIOUR, not rule ids (measured on Pearl - only 2 of her 51 ids appear in
any test file), so "is this rule covered" cannot be answered by grepping. The only honest answer is to remove the rule
and see whether anything goes red.

Why the FULL suite per removal rather than the character's own tests: a rule can be guarded by another character's
judge (shared mechanics like the Elation counters do exactly that), and a filter that only ran one file would report
those as unguarded.

It is resumable: results append to a JSON file, so a run that is interrupted picks up where it stopped, and already
answered rules are skipped.

Usage:
    python tools/sweep_rule_coverage.py                 # continue, or start
    python tools/sweep_rule_coverage.py --list          # just report progress
    python tools/sweep_rule_coverage.py 1503            # one character only
"""
import json
import pathlib
import subprocess
import sys
import time

CHARS = pathlib.Path("src/main/resources/characters")
RESULTS = pathlib.Path("tools/_rule_coverage.json")
REPORT = pathlib.Path("tools/_rule_coverage_report.txt")


def load_results():
    if RESULTS.exists():
        return json.loads(RESULTS.read_text(encoding="utf-8"))
    return {}


def save_results(results):
    RESULTS.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")


def rules_of(path):
    """The rules as (key, index) pairs.

    <p>Note: 127 of the shipped rules carry no `id` at all (measured), so a key cannot be the id alone - those rules are
    addressed by their POSITION within the file, which is stable for the duration of a sweep because the file is restored
    byte for byte after every removal.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    rules = data["rules"] if isinstance(data, dict) else data
    out = []
    for index, rule in enumerate(rules):
        if isinstance(rule, dict) and rule.get("id"):
            out.append((str(rule["id"]), index))
        else:
            on = rule.get("on") if isinstance(rule, dict) else "?"
            out.append((f"#{index}:{on}", index))
    return out


def without(path, index):
    """The file's text with the rule at that index removed."""
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        data["rules"] = [r for i, r in enumerate(data["rules"]) if i != index]
    else:
        data = [r for i, r in enumerate(data) if i != index]
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def suite_is_red():
    result = subprocess.run([".\\gradlew.bat", "test", "--console=plain", "-q"],
                            capture_output=True, text=True)
    return result.returncode != 0


def report(results):
    by_character = {}
    for key, guarded in results.items():
        cid, _, rule = key.partition(":")
        by_character.setdefault(cid, []).append((rule, guarded))
    lines = []
    unguarded_total = 0
    for cid in sorted(by_character):
        pairs = by_character[cid]
        bad = [rule for rule, guarded in pairs if not guarded]
        unguarded_total += len(bad)
        lines.append(f"{cid}: {len(pairs)} checked, {len(bad)} unguarded")
        for rule in bad:
            lines.append(f"    UNGUARDED {rule}")
    lines.append("")
    lines.append(f"total checked {len(results)} ; unguarded {unguarded_total}")
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines[-6:]))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    results = load_results()

    if "--list" in sys.argv:
        report(results)
        return 0

    wanted = set(args)
    files = sorted(CHARS.glob("*.json"))
    if wanted:
        files = [f for f in files if f.stem in wanted]

    planned = []
    for path in files:
        for key_part, index in rules_of(path):
            key = f"{path.stem}:{key_part}"
            if key not in results:
                planned.append((path, index, key))
    print(f"to check: {len(planned)} rule(s) across {len(files)} file(s)", flush=True)

    started = time.time()
    for position, (path, index, key) in enumerate(planned, start=1):
        original = path.read_text(encoding="utf-8")
        try:
            path.write_text(without(path, index), encoding="utf-8")
            guarded = suite_is_red()
        finally:
            path.write_text(original, encoding="utf-8")
        results[key] = guarded
        save_results(results)
        elapsed = time.time() - started
        print(f"[{position}/{len(planned)}] {'GUARDED  ' if guarded else 'UNGUARDED'} "
              f"{key}  ({elapsed:.0f}s elapsed)", flush=True)

    report(results)
    return 0


if __name__ == "__main__":
    sys.exit(main())
