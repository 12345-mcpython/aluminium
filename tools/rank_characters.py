"""Rank the shipped characters by how much there is to check - rules against their own document page.

Why: the coverage sweep proves every RULE is executed, but not that every CLAUSE was written. Pearl's three real gaps
(E1's save, the slot-9 branch, the technique's missing half) were found by reading her document against her rules, and
that pass has never been run for anyone else. So the useful next character is one with a lot of document to compare.
"""
import json
import pathlib
import re
import sys

RES = pathlib.Path("src/main/resources")
CHARS = RES / "characters"
OUT = pathlib.Path("tools/_character_ranking.txt")


def doc_paths():
    """Any .md page in the repo, by the number its name starts with."""
    found = {}
    for path in pathlib.Path(".").rglob("*.md"):
        match = re.match(r"^(\d{4})[_\s]", path.name)
        if match:
            found.setdefault(match.group(1), []).append(path)
    return found


def main():
    docs = doc_paths()
    rows = []
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        text = "\n".join(json.dumps(r, ensure_ascii=False) for r in rules)
        pages = docs.get(path.stem, [])
        doc_size = sum(p.stat().st_size for p in pages)
        # Rules that name where they came from: the measure of how traceable the file is.
        with_source = sum(1 for r in rules if isinstance(r, dict) and r.get("source"))
        rows.append({
            "cid": path.stem,
            "rules": len(rules),
            "with_source": with_source,
            "doc_pages": len(pages),
            "doc_bytes": doc_size,
            "notes": text.count('"note"'),
        })

    rows.sort(key=lambda r: (-r["rules"], r["cid"]))
    lines = [f"{'cid':>6} {'rules':>6} {'src':>4} {'docs':>5} {'docKB':>7} {'notes':>6}"]
    for row in rows[:24]:
        lines.append(f"{row['cid']:>6} {row['rules']:>6} {row['with_source']:>4} {row['doc_pages']:>5} "
                     f"{row['doc_bytes'] / 1024:>7.0f} {row['notes']:>6}")
    lines.append("")
    lines.append(f"characters: {len(rows)} ; with a document page: {sum(1 for r in rows if r['doc_pages'])}")
    lines.append(f"total rules: {sum(r['rules'] for r in rows)}")

    have_doc = [r for r in rows if r["doc_pages"]]
    lines.append("")
    lines.append("=== biggest by RULES, among those that HAVE a page to compare against ===")
    for row in have_doc[:12]:
        lines.append(f"  {row['cid']:>6}  rules {row['rules']:>3}  docs {row['doc_pages']}  "
                     f"{row['doc_bytes'] / 1024:.0f} KB")

    text = "\n".join(lines)
    OUT.write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    sys.exit(main())
