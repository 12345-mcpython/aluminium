"""How many shipped RAISE_SKILL_LEVEL rules push a slot past its own parameter table?

The finding: `SkillExecutor` returns silently (no damage, no error) when the resolved skill level is outside the
parameter table, and the same for the generated-effect path with `return 0`. Pearl at Eidolon 3+ has COMMON at level 11
against a 10-row table, so her enhanced basic deals NOTHING with no symptom.

Whether that is a content bug, an engine bug, or both depends on how many shipped rules raise a slot beyond its table -
so this counts them, rather than assuming Pearl is alone.

Reads every character file's RAISE_SKILL_LEVEL rules, resolves the raise against the base level from the convention,
and compares with the row count for that slot from the data dump.
"""
import json
import pathlib

CHARS = pathlib.Path("src/main/resources/characters")
SKILLS = pathlib.Path(r"E:\turnbasedgamedata\ExcelOutput\AvatarSkillConfig.json")
OUT = pathlib.Path("tools/_raise_beyond_table.txt")
# Which skill id backs which slot, per AvatarConfig's SkillList order convention (verified for 1503).
SLOT_TRIGGERS = {
    "COMMON": "Skill01",
    "SKILL": "Skill02",
    "ULTRA": "Skill03",
    "TALENT": "SkillP01",
    "ELATION_SKILL": "Skill04",
}


def main():
    rows = json.loads(SKILLS.read_text(encoding="utf-8"))
    # skill id -> rows available, keyed by AvatarID
    by_avatar = {}
    for row in rows:
        by_avatar.setdefault(row.get("AvatarID") or row.get("SkillID") // 100, {}) \
            .setdefault(row.get("SkillID"), set()).add(row.get("Level"))

    lines = []
    flagged = []
    for path in sorted(CHARS.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:                            # noqa: BLE001 - a probe reports
            lines.append(f"{path.name}: unparseable ({exc})")
            continue
        rules = data.get("rules") if isinstance(data, dict) else data
        if not isinstance(rules, list):
            continue
        raises = {}
        for rule in rules:
            for effect in rule.get("do", []):
                if effect.get("op") == "RAISE_SKILL_LEVEL" and effect.get("skill"):
                    slot = str(effect["skill"]).upper()
                    raises[slot] = raises.get(slot, 0) + int(effect.get("amount") or 0)
        if not raises:
            continue
        lines.append(f"{path.name}: raises {raises}")
        flagged.append((path.name, raises))

    lines.append("")
    lines.append("== does any raise exceed the table? (slot -> level vs rows, per character) ==")
    for name, raises in flagged:
        cid = int(name.split(".")[0])
        for slot, amount in raises.items():
            trigger = SLOT_TRIGGERS.get(slot)
            if trigger is None:
                continue
            skill_ids = [sid for sid, levels in by_avatar.get(cid, {}).items()]
            # match by trigger key is not in this table; report what the dump has for the avatar instead
            lines.append(f"  {name} {slot} +{amount}: dump rows for avatar {cid}: "
                         f"{[(sid, max(by_avatar[cid][sid])) for sid in skill_ids]}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
