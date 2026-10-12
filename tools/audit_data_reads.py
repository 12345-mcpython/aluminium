"""Enumerate every JSON file the Java source loads, so documentation claims can be checked.

The docs register "the engine reads N of the generated files"; that number drifts as content is
added, and a wrong count in a README is the kind of error a reader has no way to catch. This
scans the source for the literal filenames passed to the JSON loader and reports the distinct set,
grouped by whether the file is generated (present in data/) or a content file.

Usage:
    python tools/audit_data_reads.py
"""

import os
import re
import glob
from collections import defaultdict

SRC = "src/main/java"
DATA = "src/main/resources/data"

# Any string literal that ENDS in `.json`. Matching the filename at the start of the literal is
# not enough: `RelicSets` declares `"/data/relic_sets.json"`, and a pattern anchored on the quote
# therefore reported that file as never read -- a false negative in the direction that makes a
# live file look dead. The optional path prefix is normalised away below.
LITERAL = re.compile(r'"([^"\n]*?([a-z0-9_]+\.json))"')


def main():
    per_file = defaultdict(set)
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        text = open(path, encoding="utf-8").read()
        for _whole, name in LITERAL.findall(text):
            per_file[os.path.relpath(path, SRC)].add(name)

    all_names = set()
    for names in per_file.values():
        all_names |= names

    on_disk = {os.path.basename(p) for p in glob.glob(os.path.join(DATA, "*.json"))}
    generated = sorted(n for n in all_names if n in on_disk)
    missing = sorted(n for n in all_names if n not in on_disk and n != "data_path.txt")

    print(f"distinct .json literals in source: {len(all_names)}")
    print(f"  resolved into {DATA}: {len(generated)}")
    for n in generated:
        print(f"    {n}")
    print(f"\n  literals with no file in {DATA} ({len(missing)}):")
    for n in missing:
        where = ", ".join(sorted(f for f, s in per_file.items() if n in s))
        print(f"    {n:38} referenced by {where}")

    print(f"\nfiles present in {DATA} but never named in source: "
          f"{len(on_disk - set(generated))}")
    for n in sorted(on_disk - set(generated)):
        print(f"    {n}")


if __name__ == "__main__":
    main()
