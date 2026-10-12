"""Count, per file, how many scanned declarations do not start on a real signature line.

A span whose first line is not a signature is the failure that matters most, because every
consumer slices the body from that line: the body then includes whatever precedes the
declaration (its javadoc, or another method's tail).

Usage:
    python tools/refactor_check_spans.py <file.java> [...]
"""

import importlib.util
import sys

SPEC = importlib.util.spec_from_file_location("cm", "tools/refactor_callmap.py")
cm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cm)

SIG = ("public", "private", "protected", "static", "final", "abstract", "synchronized",
       "native", "strictfp", "default")


def main():
    worst = 0
    for path in sys.argv[1:]:
        raw = open(path, encoding="utf-8").read()
        raw_lines = raw.split("\n")
        methods, _fields, _sites = cm.scan(path)

        bad = []
        for name, info in methods.items():
            for d in info["decls"]:
                line = raw_lines[d["line"] - 1]
                stripped = line.strip()
                is_sig = "(" in line and (stripped.startswith(SIG) or stripped.startswith("<")
                                          or (stripped and stripped[0].isupper()))
                if not is_sig:
                    bad.append((name, d["line"], stripped[:72]))
        print(f"=== {path}")
        print(f"    {len(methods)} names, "
              f"{sum(len(i['decls']) for i in methods.values())} declarations, "
              f"{len(bad)} starting off a signature")
        for name, line, text in bad:
            print(f"        {name:30} line {line:5} | {text}")
        worst += len(bad)
    print(f"\nTOTAL declarations starting off a signature: {worst}")


if __name__ == "__main__":
    main()
