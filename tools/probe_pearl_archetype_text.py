"""Second pass on the 【美学底本】 question: get the status texts out with the right keys and the right encoding.

The first pass was wrong twice, and both mistakes are worth recording:
  * it matched the id as a raw SUBSTRING, so `10015037` hit the hash key `9151612769010015037` - a false positive;
  * it printed to a console whose code page mangled the Chinese, so nothing could be read anyway.

This one looks the ids up by key (and by the "key contains the id as its tail" rule, which is how this dump
keys status texts), writes the answers to a UTF-8 file, and then says where to read them.
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_archetype_probe.txt")
IDS = ["10015032", "10015035", "10015037"]
NAME_HINTS = ["美学底本", "深度学习", "行笔，幻造星月", "行笔，绘制末浪"]


def main():
    text = TBGD / "TextMap" / "TextMapCHS.json"
    data = json.loads(text.read_text(encoding="utf-8"))
    lines = [f"TextMapCHS entries: {len(data)}", ""]

    lines.append("== keys whose TEXT contains one of the names ==")
    for hint in NAME_HINTS:
        hits = [(k, v) for k, v in data.items() if hint in str(v)]
        lines.append(f"-- {hint}: {len(hits)} hit(s)")
        for k, v in hits[:8]:
            lines.append(f"   key={k} text={v}")

    lines.append("")
    lines.append("== keys that END with each status id (how this dump keys status rows) ==")
    for i in IDS:
        hits = [(k, v) for k, v in data.items() if str(k).endswith(i)]
        lines.append(f"-- {i}: {len(hits)} hit(s)")
        for k, v in hits[:8]:
            lines.append(f"   key={k} text={v}")

    lines.append("")
    lines.append("== exact key == id ==")
    for i in IDS:
        lines.append(f"-- {i}: {data.get(i, 'NOT PRESENT')}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
