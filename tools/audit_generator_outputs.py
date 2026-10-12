"""Count the files the data generator writes, so README's "emits N files" claim can be checked.

The README states a number that changes whenever the generator gains an export, and a reader has
no way to catch it being stale. This counts the distinct paths passed to the generator's own
out_path() helper, which is where every emitted file is named.

Usage:
    python tools/audit_generator_outputs.py [path/to/generate_data.py]
"""

import re
import sys
from pathlib import Path

DEFAULT = r"E:\code\python\generate_data.py"


def main():
    path = Path(sys.argv[1] if len(sys.argv) > 1 else DEFAULT)
    if not path.exists():
        print(f"generator not found: {path}")
        return 1
    src = path.read_text(encoding="utf-8")

    names = sorted(set(re.findall(r'out_path\(\s*"([a-z0-9_]+\.json)"\s*\)', src)))
    print(f"{path}")
    print(f"files written via out_path(): {len(names)}")
    for n in names:
        print(f"    {n}")

    # A file the engine reads but the generator does not write must be hand-maintained.
    print(f"\ndump_data.json written: {'dump_data' in src}")
    other = sorted(set(re.findall(r'open\(\s*"([a-z0-9_]+\.json)"', src)))
    if other:
        print(f"other literal .json opens in the script: {other}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
