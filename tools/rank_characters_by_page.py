"""Rank the characters by their DOCUMENT page, since that is what "completely implemented" is measured against.

The pages live outside the repo (E:\\turnbasedgamedata\\aluminium_texts), which is why the earlier ranking found no pages:
it looked in the working tree.
"""
import json
import pathlib
import re
import sys

PAGES = pathlib.Path(r"E:\turnbasedgamedata\aluminium_texts")
CHARS = pathlib.Path("src/main/resources/characters")
OUT = pathlib.Path("tools/_character_doc_ranking.txt")


def main():
    if not PAGES.exists():
        print("document corpus not found at", PAGES)
        return 2
    pages = {}
    for path in PAGES.glob("*.md"):
        match = re.match(r"^(\d{4})", path.name)
        if match:
            pages.setdefault(match.group(1), []).append(path)

    rows = []
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        own = pages.get(path.stem, [])
        rows.append({
            "cid": path.stem,
            "rules": len(rules),
            "page": own[0].name if own else "-",
            "kb": sum(p.stat().st_size for p in own) / 1024,
        })

    have = [r for r in rows if r["page"] != "-"]
    have.sort(key=lambda r: -r["kb"])
    lines = [f"pages found: {len(pages)} ; characters with a page: {len(have)} of {len(rows)}", ""]
    lines.append(f"{'cid':>6} {'rules':>6} {'pageKB':>8}  page")
    for row in have[:20]:
        lines.append(f"{row['cid']:>6} {row['rules']:>6} {row['kb']:>8.0f}  {row['page']}")
    lines.append("")
    lines.append("=== rules per KB of page: the low ones have the most document per rule ===")
    have.sort(key=lambda r: (r["rules"] / r["kb"]) if r["kb"] else 0)
    for row in have[:14]:
        lines.append(f"  {row['cid']:>6}  rules {row['rules']:>3}  page {row['kb']:>6.0f} KB  "
                     f"={row['rules'] / row['kb']:.3f} rules/KB")
    lines.append("")
    lines.append("=== no page at all (cannot be read clause-by-clause) ===")
    lines.append("  " + ", ".join(r["cid"] for r in rows if r["page"] == "-"))

    text = "\n".join(lines)
    OUT.write_text(text, encoding="utf-8")
    # Note: written to a file rather than printed - the console here is GBK and cannot encode the page names, which
    # would fail the run for a reason that has nothing to do with the ranking. Read the file with the read tool.
    print(f"written {OUT} ({len(text)} chars)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
