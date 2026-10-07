"""Which shipped rules have no `id`? They cannot be swept by removal-and-report without one.

Also reports each character file's shape, since the sweep assumed every file is {resources, rules}.
"""
import json
import pathlib

CHARS = pathlib.Path("src/main/resources/characters")
OUT = pathlib.Path("tools/_rule_shape_report.txt")


def main():
    lines = []
    total = 0
    no_id = 0
    shapes = {}
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        shapes[type(data).__name__] = shapes.get(type(data).__name__, 0) + 1
        missing = [r for r in rules if not isinstance(r, dict) or "id" not in r]
        total += len(rules)
        no_id += len(missing)
        if missing:
            lines.append(f"{path.stem}: {len(missing)} of {len(rules)} rule(s) with no id")
            for rule in missing[:4]:
                lines.append("     " + json.dumps(rule, ensure_ascii=False)[:150])
    lines.insert(0, f"rules total {total} ; without an id {no_id}")
    lines.insert(1, f"file shapes: {shapes}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines[:14]))


if __name__ == "__main__":
    main()
