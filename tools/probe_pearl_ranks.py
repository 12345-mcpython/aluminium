"""Pearl's Eidolon rows, so each 星魂 clause can be checked against its own parameters.

The corpus lists E2 [0.15, 1], E4 [1], E6 [0.2, 2.4]. This reads the dump's own row for each and resolves its text,
which is how the remaining Eidolon clauses can be told apart into "the number is here" and "the number is the one
thing missing".
"""
import json
import pathlib

TBGD = pathlib.Path(r"E:\turnbasedgamedata")
OUT = pathlib.Path("tools/_pearl_rank_rows.txt")
# RankIDList from her AvatarConfig row.
RANK_IDS = [150301, 150302, 150303, 150304, 150305, 150306]


def main():
    textmap = json.loads((TBGD / "TextMap" / "TextMapCHS.json").read_text(encoding="utf-8"))
    path = TBGD / "ExcelOutput" / "AvatarRankConfig.json"
    if not path.exists():
        print("no AvatarRankConfig.json in the dump")
        return 1
    rows = json.loads(path.read_text(encoding="utf-8"))
    lines = []
    for rank_id in RANK_IDS:
        mine = [r for r in rows if r.get("RankID") == rank_id]
        for row in mine:
            params = row.get("ParamList") or []
            desc = row.get("RankDesc") or {}
            text = textmap.get(str(desc.get("Hash"))) if desc.get("Hash") else None
            lines.append(f"== rank {rank_id} (E{(rank_id % 100)}) ==")
            lines.append(f"   params: {json.dumps(params, ensure_ascii=False)}")
            lines.append(f"   text  : {text}")
            lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
