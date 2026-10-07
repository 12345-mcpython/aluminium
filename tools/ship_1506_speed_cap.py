"""1506's speed trace, completed: the per-point term with its ceiling.

「速度大于等于 160 时，使自身欢愉度提高 50%，之后每超过 1 点速度使自身欢愉度提高 2%，最多计入 100 点超出的速度。」

Until now only the first half shipped. The second half needs a CEILING ON THE PER-POINT POINTS, which no spelling could
state - so it was registered rather than approximated (writing it unbounded gives 280% at Speed 300 where the document says
200%, a silent wrong number). `EffectSpec.maxPoints` now states it, on the `self_attr_above` scale.
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "p1506_trace_speed_becomes_elation"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup")
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored")
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written")

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rule = next((r for r in data["rules"] if r["id"] == RULE_ID), None)
    if rule is None:
        print("rule not found:", RULE_ID)
        return 2

    rule["do"] = [
        {
            "op": "MODIFY_ATTR",
            "attribute": "ELATION_DAMAGE_BOOST",
            "amount": 0.5,
            "permanent": True,
            "target": "self"
        },
        {
            "op": "MODIFY_ATTR",
            "attribute": "ELATION_DAMAGE_BOOST",
            "scale": "self_attr_above:speed:160",
            "percent": 0.02,
            "max_points": 100,
            "coexist": True,
            "permanent": True,
            "target": "self"
        },
    ]
    rule["note"] = (
        "⭐⭐ **整条写出，两半都在** ✓：「速度 ≥ 160 ⇒ 欢愉度 +50%」✓ **＋** 「之后每超过 1 点 +2%，"
        "**最多计入 100 点**」✓ —— 写成**两个效果** ✓（一个平的 ✓、一个按超出的速度缩放 ✓），"
        "两者相加 ✓。"
        "⚠⚠ **上限是这一条此前写不出来的唯一原因** ✓：没有它就只能**二选一** —— "
        "要么丢掉上界 ✓（速度 300 时给出 **280%** ✓，而原文是 **200%** ✓ ⇒ **一个安静的错数** ✓），"
        "要么近似 ✓。⇒ 引擎为此新增 **`EffectSpec.maxPoints`**（`max_points` ✓），"
        "⚠ **它限制的是「点数」、不是最终份额** ✓ —— 但在这里**两者等价** ✓（份额 = 点数 × 正常数 ✓），"
        "而**写在点数上是原文的写法** ✓ ⇒ 读者可以拿字段对着句子核 ✓。"
        "⚠ `self_attr_above:<属性>:<阈值>` 这个拼法**本来就存在** ✓（每个词汇都是先有读者才建的 ✓），"
        "缺的只是给它一个上限 ✓。")
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next(r for r in check["rules"] if r["id"] == RULE_ID)
    if len(landed["do"]) != 2 or landed["do"][1].get("max_points") != 100:
        print("WRITE CHECK FAILED")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("completed:", json.dumps([e.get("scale") or e.get("amount") for e in landed["do"]], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
