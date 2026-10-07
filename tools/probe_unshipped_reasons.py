"""For each corpus character that has no shipped file, find its registered reason.

The project's discipline is that every unshipped character carries a named reason in the registry, so this prints the
first passage around each id in the two registry files. Output goes to a file (the console is not UTF-8 here).
"""
import pathlib
import re

OUT = pathlib.Path("tools/_unshipped_reasons.txt")
CORPUS = pathlib.Path(r"E:\code\blog\hsr")
CHARS = pathlib.Path("src/main/resources/characters")
REGISTRIES = ["GAPS.md", "HANDOFF.md"]


def main():
    pages = {p.stem.split("_")[0]: p.stem for p in CORPUS.glob("*.md") if re.match(r"^\d{4}_", p.name)}
    files = {p.stem for p in CHARS.glob("*.json")}
    missing = sorted(set(pages) - files)

    lines = []
    for cid in missing:
        lines.append(f"== {cid}  {pages.get(cid, '(no page)')} ==")
        found = False
        for name in REGISTRIES:
            text = pathlib.Path(name).read_text(encoding="utf-8")
            for match in re.finditer(cid, text):
                start = max(0, match.start() - 400)
                segment = text[start:match.start() + 400].replace("\n", " ")
                lines.append(f"   [{name}] …{segment}…")
                found = True
                break
        if not found:
            lines.append("   (no passage in either registry names this id)")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
    print("unshipped:", missing)


if __name__ == "__main__":
    main()
