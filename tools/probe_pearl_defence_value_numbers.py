"""Find the numbers behind Pearl's 抵御值 mechanic.

The probe before this one established that status 10015034 carries `ModifierName =
MAvatar_Pearl_00_Skill03_Caster_BaseType` and `ReadParamList = ["MDF_PropertyValue"]` - i.e. it DOES have a parameter
row somewhere, which is the shape that has paid off repeatedly in this project ("a slot the prose does not reference
is often the mechanic"). This looks for it, in the places such a row lives:

  * `Config/ConfigAbility/**` - the ability scripts, which the corpus says are absent. Checked anyway, because the
    claim "they are not in the dump" is itself a claim (the last three times a note said something was missing, it
    was there);
  * ExcelOutput tables whose rows mention the modifier name or the status id;
  * the TextMap entry that carries the talent's own parameter list (#1..#5 are referenced by the prose).
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_defence_value_numbers.txt")
MODIFIER = "MAvatar_Pearl_00_Skill03_Caster_BaseType"
STATUS = "10015034"
TALENT_ROW = "150304"          # 天赋 吞纳沙砾，重塑生机


def config_tree(lines):
    lines.append(f"== any file under Config/ naming {MODIFIER} or {STATUS} ==")
    hits = 0
    for path in (TBGD / "Config").rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if MODIFIER in text or STATUS in text:
            hits += 1
            lines.append(f"  {path.relative_to(TBGD)}")
    if hits == 0:
        lines.append("  (none)")


def avatar_config(lines):
    lines.append("")
    lines.append("== AvatarConfig row for Pearl: where its ability script would live ==")
    path = TBGD / "ExcelOutput" / "AvatarConfig.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data if isinstance(data, list) else [data]
    for row in rows:
        if str(row.get("AvatarID")) == "1503" or "Pearl_00" in json.dumps(row):
            lines.append("  " + json.dumps(row, ensure_ascii=False)[:700])
            break


def status_config(lines):
    lines.append("")
    lines.append(f"== AvatarStatusConfig rows mentioning {MODIFIER} ==")
    path = TBGD / "ExcelOutput" / "AvatarStatusConfig.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data if isinstance(data, list) else [data]
    for row in rows:
        if MODIFIER in json.dumps(row):
            lines.append("  " + json.dumps(row, ensure_ascii=False))


def talent_params(lines):
    lines.append("")
    lines.append(f"== the talent's own parameter rows ({TALENT_ROW}) ==")
    for name in ("AvatarSkillConfig.json", "AvatarSkillTreeConfig.json"):
        path = TBGD / "ExcelOutput" / name
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        rows = data if isinstance(data, list) else [data]
        for row in rows:
            if TALENT_ROW in json.dumps(row):
                lines.append(f"  {name}: " + json.dumps(row, ensure_ascii=False)[:600])


if __name__ == "__main__":
    lines = []
    config_tree(lines)
    avatar_config(lines)
    status_config(lines)
    talent_params(lines)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
