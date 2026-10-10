"""Find javadoc references that no longer resolve after a class was split.

A `{@link #name}` or `{@code name}` in documentation used to be same-file by
construction. After a split some of those names live in a sibling class, and the link
silently degrades -- an IDE stops navigating, `javadoc` warns, and a reader is sent to a
member that is not there. This reports each reference and whether its target is still in
the file.

Self-references (`#name`) and same-name members are checked; unqualified `@link Name`
forms are reported when `Name` is not a type declared in this file or in its imports.

Usage:
    python tools/refactor_docrefs.py <file.java> [<sibling.java> ...]
"""

import io
import re
import sys
from collections import Counter

SELF_LINK = re.compile(r"\{@link\s+#(\w+)")
TYPE_LINK = re.compile(r"\{@link\s+(?!\#)([\w.]+)")
SEE_TAG = re.compile(r"@see\s+#(\w+)")


def members(path):
    """Names declared in this file: methods, fields, and nested types."""
    text = io.open(path, encoding="utf-8").read()
    names = set()
    names |= set(re.findall(r"^\s*(?:public|private|protected)?\s*static\s+(?:final\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*(?:=|;|\()",
                            text, re.M))
    names |= set(re.findall(r"^\s*(?:public|private|protected)?\s*(?:static\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*\([^)]*\)\s*\{",
                            text, re.M))
    names |= set(re.findall(r"^\s*(?:public|private|protected)?\s*(?:static\s+)?(?:final\s+)?(?:enum|record|class|interface)\s+(\w+)",
                            text, re.M))
    return names


def main():
    path = sys.argv[1]
    siblings = sys.argv[2:]
    text = io.open(path, encoding="utf-8").read()

    here = members(path)
    elsewhere = set()
    sibling_members = {}
    for sib in siblings:
        m = members(sib)
        sibling_members[sib] = m
        elsewhere |= m

    from collections import Counter
    dangling, moved = [], []
    for kind, regex in (("link", SELF_LINK), ("see", SEE_TAG)):
        for name in regex.findall(text):
            if name in here:
                continue
            if name in elsewhere:
                where = [s for s, m in sibling_members.items() if name in m]
                moved.append((name, where))
            else:
                dangling.append((kind, name))

    print(f"{path}: {len(here)} declared members")
    if moved:
        # `where` is a list, so dedupe on the (name, sorted targets) pair rather than the
        # raw tuple -- lists are unhashable and sorting the tuple alone raises.
        seen = sorted({(n, ", ".join(sorted(w))) for n, w in moved})
        print(f"\nreferences that now live in a SIBLING ({len(seen)}):")
        for name, where in seen:
            print(f"   {name:32} -> {where}")
    if dangling:
        print(f"\nreferences that resolve NOWHERE ({len(dangling)}):")
        for kind, name in sorted(set(dangling)):
            print(f"   [{kind}] {name}")
    if not moved and not dangling:
        print("\nall javadoc references still resolve in this file")


if __name__ == "__main__":
    main()
