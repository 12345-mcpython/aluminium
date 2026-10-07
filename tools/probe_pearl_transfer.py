"""Can a clause be written for "switching the active character"? Measure it against the machinery and against the
ability scripts that ARE in the dump.

The clause: 1503's Technique says "使用秘技后获得【美学底本】，切换行动角色时【美学底本】转移至当前行动角色" -
after using the Technique she gains the marker, and when the active character is switched the marker MOVES to
whoever is now active. Pearl's own ability script is not in the dump (measured), so this looks at what the engine can
already observe and at how OTHER characters' scripts express the same idea.

Three cheap questions, in order:
  1. does the engine have any event or notion of "the active character changed"? (grep the events and Battle)
  2. what does the ability-script vocabulary call it - i.e. is there a trigger name in the dump's own scripts?
  3. if both answer no, is there an EXISTING event that coincides with a character switch in this engine (a turn
     boundary), which would make the clause expressible without new vocabulary - and if so, what is the honest
     difference?
"""
import json
import pathlib
import re

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
AVATAR_DIR = TBGD / "Config" / "ConfigAbility" / "Avatar"
OUT = pathlib.Path("tools/_pearl_technique_transfer.txt")
ENGINE = pathlib.Path("src/main/java/com/laosun/aluminium")


def engine_events(lines):
    text = (ENGINE / "enums" / "TriggerEvent.java").read_text(encoding="utf-8")
    names = re.findall(r'^\s+([A-Z][A-Z_0-9]+)\("', text, re.M)
    lines.append(f"== engine trigger events ({len(names)}) ==")
    for name in names:
        flag = ""
        if any(word in name for word in ("TURN", "WAVE", "BATTLE", "ACTIVE", "SWITCH", "ENTER")):
            flag = "   <-- boundary-ish"
        lines.append(f"  {name}{flag}")
    lines.append("")
    lines.append("== does the engine mention an active-character switch anywhere? ==")
    hits = []
    for path in ENGINE.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r".{0,80}(active character|switch|switching|activeCharacter).{0,80}", text):
            hits.append(f"  {path.name}: {match.group(0).strip()}")
    lines.extend(hits[:20] if hits else ["  (none)"])


def script_vocabulary(lines):
    """Which trigger names do the dump's own ability scripts use? That is the game's vocabulary, not ours."""
    lines.append("")
    lines.append("== trigger/ability names used by the dump's scripts (sampled) ==")
    counts = {}
    sampled = 0
    for path in sorted(AVATAR_DIR.glob("*_Ability.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:                              # noqa: BLE001 - a probe reports
            continue
        sampled += 1
        blob = json.dumps(data, ensure_ascii=False)
        for key in ("OnSwitch", "Switch", "ActiveCharacter", "AvatarChange", "OnAvatarChange"):
            if key in blob:
                counts[key] = counts.get(key, 0) + 1
        if sampled > 60:
            break
    lines.append(f"  sampled {sampled} scripts")
    lines.append(f"  switch-flavoured keys found: {counts if counts else 'none'}")


def turn_boundary_in_engine(lines):
    lines.append("")
    lines.append("== what the engine fires at a turn boundary, and whether a switch coincides with it ==")
    text = (ENGINE / "Battle.java").read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(text):
        if "TURN_START" in line or "TURN_END" in line:
            lines.append(f"  Battle.java:{i + 1}: {line.strip()}")


if __name__ == "__main__":
    lines = []
    engine_events(lines)
    script_vocabulary(lines)
    turn_boundary_in_engine(lines)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
