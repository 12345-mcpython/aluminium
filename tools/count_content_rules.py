"""Count rules in the content files, per content type, by structure rather than by guesswork.

The content directories do not share a shape: a character file is a flat list of rules, while other
content may nest them. A count that assumes one shape silently under-reports (the first version of
this counted 69 of 94 character files), so each file is walked and every object carrying an `on`
key is counted wherever it sits.

Usage:
    python tools/count_content_rules.py
"""

import json
import os
import glob
from collections import defaultdict


def count_rules(node):
    """Every dict with an `on` key, at any depth."""
    if isinstance(node, dict):
        n = 1 if "on" in node else 0
        return n + sum(count_rules(v) for v in node.values())
    if isinstance(node, list):
        return sum(count_rules(v) for v in node)
    return 0


def main():
    per_kind = defaultdict(lambda: [0, 0, 0])  # files with rules, files total, rules
    for path in sorted(glob.glob("src/main/resources/**/*.json", recursive=True)):
        if f"{os.sep}data{os.sep}" in path:
            continue
        kind = path.replace("\\", "/").split("/")[-2]
        per_kind[kind][1] += 1
        try:
            data = json.load(open(path, encoding="utf-8"))
        except Exception as exc:
            print(f"  UNREADABLE {path}: {exc}")
            continue
        n = count_rules(data)
        if n:
            per_kind[kind][0] += 1
            per_kind[kind][2] += n

    print(f"{'content type':16} {'files':>6} {'with rules':>11} {'rules':>7} {'avg':>6}")
    total_rules = 0
    for kind in sorted(per_kind):
        with_rules, files, rules = per_kind[kind]
        total_rules += rules
        avg = f"{rules / with_rules:.1f}" if with_rules else "-"
        print(f"{kind:16} {files:>6} {with_rules:>11} {rules:>7} {avg:>6}")
    print(f"{'TOTAL':16} {'':>6} {'':>11} {total_rules:>7}")


if __name__ == "__main__":
    main()
