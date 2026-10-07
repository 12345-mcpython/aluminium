"""Find duplicate `### ` headings in the registry, and sections whose body is missing.

The registry is edited by scripts that replace a heading plus its body, so it is worth checking for the two shapes that
edit can leave behind: a heading repeated, or a heading immediately followed by another.

Output: tools/_gaps_health.txt
"""
import pathlib

OUT = pathlib.Path("tools/_gaps_health.txt")
TEXT = pathlib.Path("GAPS.md")


def main():
    lines = TEXT.read_text(encoding="utf-8").splitlines()
    headings = [(i + 1, line) for i, line in enumerate(lines) if line.startswith("### ")]

    counts = {}
    for _, line in headings:
        counts[line] = counts.get(line, 0) + 1

    report = []
    report.append(f"headings: {len(headings)}  unique: {len(counts)}")
    for line, count in counts.items():
        if count > 1:
            report.append(f"  DUPLICATE x{count}: {line}")

    for index, line in headings:
        following = next((x.strip() for x in lines[index:index + 3] if x.strip()), "")
        if following.startswith("### ") or not following:
            report.append(f"  EMPTY BODY at line {index}: {line}")

    OUT.write_text("\n".join(report), encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    main()
