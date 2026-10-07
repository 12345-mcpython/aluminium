"""Pull out the `Switch` occurrences the dump's own ability scripts contain.

The previous probe found `Switch` in 7 of 61 sampled scripts. That is the game's own vocabulary for something, and it
is worth reading before deciding whether "切换行动角色时" needs new engine vocabulary or maps onto an existing
boundary. This prints the surrounding JSON so the answer is the game's own words rather than my paraphrase.
"""
import json
import pathlib

AVATAR_DIR = pathlib.Path(r"E:\turnbasedgamedata\Config\ConfigAbility\Avatar")
OUT = pathlib.Path("tools/_switch_vocabulary.txt")


def walk(node, path, out):
    """Collect every (json-path, value) whose KEY contains Switch."""
    if isinstance(node, dict):
        for key, value in node.items():
            here = f"{path}.{key}" if path else key
            if "switch" in key.lower():
                out.append((here, value))
            walk(value, here, out)
    elif isinstance(node, list):
        for i, value in enumerate(node):
            walk(value, f"{path}[{i}]", out)


def main():
    lines = []
    for path in sorted(AVATAR_DIR.glob("*_Ability.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:                              # noqa: BLE001 - a probe reports
            continue
        found = []
        walk(data, "", found)
        if found:
            lines.append(f"== {path.name} ==")
            for where, value in found[:8]:
                lines.append(f"  {where} = {json.dumps(value, ensure_ascii=False)[:400]}")
            lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
