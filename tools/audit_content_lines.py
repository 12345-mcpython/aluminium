"""What is actually left, per content line: shipped against the corpus's own master lists.

Prompted by finding the handoff's "next steps" stale in three places at once (relic 317 shipped, light cone 21025 shipped,
and the relic registry down to one entry), so "what is next" cannot be answered from the notes.

The masters are the corpus files themselves:
  characters  -> data/character_data.json
  light cones -> data/weapons.json
  relic sets  -> data/relic_sets.json

Reported per line: how many the master lists, how many are shipped, and WHICH are missing. A missing id is then either
"has data but no file" (work to do) or a known registry entry (blocked on a mechanism).
"""
import json
import pathlib
import sys

RES = pathlib.Path("src/main/resources")
DATA = RES / "data"
OUT = pathlib.Path("tools/_content_audit.txt")


def shipped(directory):
    return {p.stem for p in (RES / directory).glob("*.json") if not p.name.startswith("_")}


def master_ids(path, key_candidates=("id", "ID")):
    """Every id the corpus file lists, however it nests them."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    found = set()

    def walk(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key in key_candidates and isinstance(value, (int, str)):
                    found.add(str(value))
                walk(value)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    if isinstance(raw, dict):
        # The common shape: {id: {...}} at the top level.
        for key, value in raw.items():
            if key.isdigit() and isinstance(value, dict):
                found.add(key)
    walk(raw)
    return found


def registry(path):
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    lines = []
    lines.append("=== characters ===")
    chars = master_ids(DATA / "character_data.json")
    have_chars = shipped("characters")
    missing_chars = sorted(chars - have_chars, key=lambda s: int(s) if s.isdigit() else 0)
    lines.append(f"corpus {len(chars)} | shipped {len(have_chars)} | missing {len(missing_chars)}")
    lines.append("  missing: " + (", ".join(missing_chars) or "none"))

    lines.append("")
    lines.append("=== light cones ===")
    cones = master_ids(DATA / "weapons.json")
    have_cones = shipped("light_cones")
    missing_cones = sorted(cones - have_cones)
    cone_registry = registry(RES / "light_cones/_unmodelled.json")
    lines.append(f"corpus {len(cones)} | shipped {len(have_cones)} | missing {len(missing_cones)}")
    unresolved = [c for c in missing_cones if c not in cone_registry]
    lines.append(f"  missing AND not registered (real work): {', '.join(unresolved) or 'none'}")
    lines.append(f"  registered (blocked): {', '.join(sorted(cone_registry)) or 'none'}")

    lines.append("")
    lines.append("=== relic sets ===")
    relics = master_ids(DATA / "relic_sets.json")
    have_relics = shipped("relic_sets")
    missing_relics = sorted(relics - have_relics)
    relic_registry = registry(RES / "relic_sets/_unmodelled.json")
    lines.append(f"corpus {len(relics)} | shipped {len(have_relics)} | missing {len(missing_relics)}")
    unresolved = [r for r in missing_relics if r not in relic_registry]
    lines.append(f"  missing AND not registered (real work): {', '.join(unresolved) or 'none'}")
    lines.append(f"  registered (blocked): {', '.join(sorted(relic_registry)) or 'none'}")

    lines.append("")
    lines.append("=== memosprites ===")
    have_memo = shipped("memosprites")
    lines.append(f"shipped {len(have_memo)}: {', '.join(sorted(have_memo))}")

    text = "\n".join(lines)
    OUT.write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    sys.exit(main())
