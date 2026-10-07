"""Add the rule 1506's batch was missing: her Ultimate swaps her Basic for the ENHANCED one.

「【无敌玩家】状态下…拥有强化普攻和强化欢愉技」 - so the state grants a different Basic, which is a REPLACE_SKILL on the
same event her Ultimate already fires on. Without it the enhanced basic is never installed, the slot-8 rules never fire,
and the 强化普攻次数 counter never runs down.

It also fixes a fixture bug in the new judge: the E1 case passed 0 where an ally cid belongs.
"""
import json
import pathlib

LIVE = pathlib.Path("src/main/resources/characters/1506.json")
SCRIPT = pathlib.Path("tools/ship_1506.py")
JUDGE = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/SilverWolfLV999KitTest.java")

RULE = {
    "on": "ULT_CAST",
    "id": "p1506_ult_installs_the_enhanced_basic",
    "when": ["actor == self"],
    "do": [
        {
            "op": "REPLACE_SKILL",
            "skill": "COMMON",
            "skill_id": 8,
            "turns": 3,
            "target": "self"
        }
    ],
    "source": "1506 银狼LV.999 天赋: 「【无敌玩家】状态下…拥有强化普攻和强化欢愉技，完整施放 3 次强化普攻后退出【无敌玩家】状态」"
              "—— 强化普攻是槽位 8（`skills.json` 1506 槽位 8 = 奖励关：「狼尊时刻」）。",
    "note": "⚠⚠ **这一条是我第一版漏掉的** ✓ —— 而漏掉它的**症状是沉默的** ✓：槽 8 的两条规则（消耗次数 ✓、"
            "满 3 次退出 ✓）**永远不会触发** ✓，判据只看到「状态一直在」✓。⇒ ⭐ 这正是「**从句对着规则读**」"
            "存在的理由 ✓：规则各自都对 ✓，**缺的是把它们连起来的那一句** ✓。"
            "⚠ `turns: 3` 与【无敌玩家】的寿命一致 ✓（3 次强化普攻 ✓）；⚠ 真正的退出仍由计数驱动 ✓，"
            "`turns` 只是替换的**外层时限** ✓。",
}


def main():
    data = json.loads(LIVE.read_text(encoding="utf-8"))
    ids = {r["id"] for r in data["rules"]}
    if RULE["id"] in ids:
        print("already present")
    else:
        data["rules"].append(RULE)
        LIVE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        check = json.loads(LIVE.read_text(encoding="utf-8"))
        landed = next((r for r in check["rules"] if r["id"] == RULE["id"]), None)
        if landed is None or landed["do"][0].get("skill_id") != 8:
            print("WRITE CHECK FAILED")
            return 4
        print(f"rules: {len(ids)} -> {len(check['rules'])}")

    # Keep the shipping script in step, so a re-ship produces the same file.
    text = SCRIPT.read_text(encoding="utf-8")
    marker = '    {\n        "on": "BASIC_ATTACK",\n        "id": "p1506_godmode_basic_spends_a_charge",'
    block = (
        '    {\n'
        '        "on": "ULT_CAST",\n'
        '        "id": "p1506_ult_installs_the_enhanced_basic",\n'
        '        "when": ["actor == self"],\n'
        '        "do": [{"op": "REPLACE_SKILL", "skill": "COMMON", "skill_id": 8, "turns": 3, "target": "self"}],\n'
        '        "source": "1506 银狼LV.999 天赋: 「【无敌玩家】状态下…拥有强化普攻和强化欢愉技」—— 强化普攻是槽位 8。",\n'
        '        "note": "⚠ 这一条是补的：没有它，【无敌玩家】装不上强化普攻，槽 8 的消耗与退出规则永远不会触发（症状是沉默的）。",\n'
        '    },\n')
    if "p1506_ult_installs_the_enhanced_basic" not in text and marker in text:
        text = text.replace(marker, block + marker, 1)
        SCRIPT.write_text(text, encoding="utf-8")
        print("script updated")

    # The judge's E1 fixture passed 0 where an ally cid belongs.
    body = JUDGE.read_text(encoding="utf-8")
    if "newBattle(1, 0)" in body:
        body = body.replace("newBattle(1, 0)", "newBattle(1, ALLY)")
        JUDGE.write_text(body, encoding="utf-8")
        print("judge fixture fixed: ally cid was 0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
