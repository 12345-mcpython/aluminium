"""Who declares 好活当赏 / 笑点, and who READS them?

The scope of the correction depends on this: if only Pearl's four files touch the two counters, the change is local;
if eight characters do, it is a mechanic. Reads matter more than declarations - a reader of a counter is a clause whose
number moves when the model changes.
"""
import json
import pathlib

CHARS = pathlib.Path("src/main/resources/characters")
OUT = pathlib.Path("tools/_elation_counter_scope.txt")


def main():
    declares = {}
    gains = {}
    spends = {}
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        resources = (data.get("resources") if isinstance(data, dict) else None) or []
        for entry in resources:
            if isinstance(entry, dict) and entry.get("id") in ("好活当赏", "笑点"):
                declares.setdefault(entry["id"], []).append(
                    f"{path.stem}({entry.get('scope') or 'SELF'},max={entry.get('max')})")
        rules = data["rules"] if isinstance(data, dict) else data
        for rule in rules:
            for effect in rule.get("do", []):
                for counter in ("好活当赏", "笑点"):
                    if effect.get("resource") == counter:
                        op = effect.get("op")
                        table = gains if op in ("GAIN_RESOURCE",) else (
                            spends if op in ("SPEND_RESOURCE", "REMOVE_RESOURCE") else None)
                        if table is not None:
                            table.setdefault(counter, []).append(f"{path.stem}:{rule['id']}")
                    # A SCALE that reads the counter is a reader whose number moves with the model.
                    if counter in str(effect.get("scale") or ""):
                        spends.setdefault(counter + " (read as a scale)", []).append(
                            f"{path.stem}:{rule['id']}")

    lines = []
    for label, table in (("declares", declares), ("gains", gains), ("spends/removes/reads", spends)):
        lines.append(f"== {label} ==")
        for counter, entries in sorted(table.items()):
            lines.append(f"  {counter}: {len(entries)} site(s)")
            for entry in entries:
                lines.append(f"      {entry}")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
