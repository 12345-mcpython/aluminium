"""Measure each unshipped corpus character instead of quoting the stale registry.

The registry's list was written on 2026-10-02 and still names 1503 - which shipped. So rather than quote it, this checks
each unshipped id against the data the loader actually needs:

  * a `character_data` row (otherwise `CharacterFactory.create` throws "not found" - a hard limit);
  * the corpus page's own skills/stats, to say what is missing in words.

Output: tools/_unshipped_measured.txt
"""
import json
import pathlib
import re

OUT = pathlib.Path("tools/_unshipped_measured.txt")
CORPUS = pathlib.Path(r"E:\code\blog\hsr")
CHARS = pathlib.Path("src/main/resources/characters")
GENERATED = pathlib.Path("src/main/resources/data")


def main():
    pages = {p.stem.split("_")[0]: p for p in CORPUS.glob("*.md") if re.match(r"^\d{4}_", p.name)}
    shipped = {p.stem for p in CHARS.glob("*.json")}
    missing = sorted(set(pages) - shipped)

    # What the generated data folder holds, since that is what CharacterFactory reads.
    generated = sorted(p.name for p in GENERATED.glob("*")) if GENERATED.exists() else []
    lines = [f"generated data files: {generated}", ""]

    for cid in missing:
        page = pages[cid]
        text = page.read_text(encoding="utf-8")
        name = page.stem.split("_", 1)[1] if "_" in page.stem else page.stem
        lines.append(f"== {cid} {name} ==")
        # Is there a data row for it?
        row = None
        for candidate in generated:
            path = GENERATED / candidate
            if path.is_file() and path.suffix == ".json":
                try:
                    blob = json.loads(path.read_text(encoding="utf-8"))
                except Exception:                                  # noqa: BLE001 - a probe reports
                    continue
                if isinstance(blob, list) and any(
                        isinstance(entry, dict) and str(entry.get("id") or entry.get("ID") or "") == cid
                        for entry in blob):
                    row = candidate
                    break
        lines.append(f"   data row: {row if row else 'NONE found in ' + str(generated)}")
        # The page's own headline facts.
        for pattern, label in ((r"- 命途: `([^`]+)`", "path"),
                               (r"- 元素: `([^`]+)`", "element"),
                               (r"- 最大能量: (\d+)", "max energy")):
            match = re.search(pattern, text)
            if match:
                lines.append(f"   {label}: {match.group(1)}")
        sections = re.findall(r"^### (.+)$", text, re.M)
        lines.append(f"   page sections: {len(sections)} -> {sections[:6]}")
        lines.append(f"   page bytes: {len(text.encode('utf-8'))}")
        lines.append("")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT)
    print("unshipped:", missing)


if __name__ == "__main__":
    main()
