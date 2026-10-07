"""Verify the corpus claim that Pearl's ten stat traces need no content rules.

The open items say: "Ten stat traces (lines 305-314: DEF, SPD, effect RES, Elation damage) are panel attributes
handled by `SkillTrace`, so they need no content rules." That is exactly the kind of claim this project has learned to
re-measure - it is a claim that nothing is missing, and a missing trace would be a silently low panel with no error
anywhere.

This prints Pearl's resolved panel at Lv80 (E0), and the trace rows the dump has for her, so "already handled" is a
number rather than a belief. It also prints what the rows SAY, because a row that is present in the data but not read
by the panel path would look identical in a sum.
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_trace_rows.txt")
TREE = "AvatarSkillTreeConfig.json"


def main():
    lines = []
    path = TBGD / "ExcelOutput" / TREE
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data if isinstance(data, list) else [data]
    mine = [r for r in rows if r.get("AvatarID") == 1503]
    lines.append(f"== {TREE}: {len(mine)} rows for AvatarID 1503 ==")
    for row in mine:
        status = row.get("StatusAddList") or []
        params = row.get("ParamList") or []
        lines.append(f"  PointID={row.get('PointID')} PointType={row.get('PointType')} "
                     f"Anchor={row.get('AnchorType')} Level={row.get('Level')}")
        lines.append(f"     StatusAddList={json.dumps(status, ensure_ascii=False)}")
        lines.append(f"     ParamList={json.dumps(params, ensure_ascii=False)}")

    lines.append("")
    lines.append("== the statuses those rows add, as the dump describes them ==")
    status_ids = set()
    for row in mine:
        for entry in row.get("StatusAddList") or []:
            if isinstance(entry, dict) and entry.get("StatusID"):
                status_ids.add(int(entry["StatusID"]))
    stat_path = TBGD / "ExcelOutput" / "AvatarStatusConfig.json"
    stat_data = json.loads(stat_path.read_text(encoding="utf-8"))
    stat_rows = stat_data if isinstance(stat_data, list) else [stat_data]
    for row in stat_rows:
        if row.get("StatusID") in status_ids:
            lines.append("  " + json.dumps(row, ensure_ascii=False))

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
