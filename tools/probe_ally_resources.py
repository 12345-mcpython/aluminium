"""Which allies declare which counters - so a fixture can pick a recipient whose slate is clean.

Needed because `Resource.gain` clamps to the counter's cap, and a counter that arrives with a cap of 0 swallows the
payment while saying nothing. Knowing who declares what turns that from a mystery into a choice.
"""
import json
import pathlib

OUT = pathlib.Path("tools/_ally_resources.txt")
CIDS = ["1101", "1102", "1103", "1204", "1502", "1503"]


def main():
    lines = []
    for cid in CIDS:
        path = pathlib.Path(f"src/main/resources/characters/{cid}.json")
        if not path.exists():
            lines.append(f"{cid}: no file")
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        # Note: A character file is either {resources, rules} or a bare list of rules.
        declared = (data.get("resources") if isinstance(data, dict) else None) or []
        summary = []
        for entry in declared:
            if isinstance(entry, dict):
                summary.append(f"{entry.get('id')}(max={entry.get('max')},scope={entry.get('scope') or 'SELF'})")
            else:
                summary.append(repr(entry))
        lines.append(f"{cid}: {summary if summary else '(none)'}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
