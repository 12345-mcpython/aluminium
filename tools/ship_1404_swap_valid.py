"""Ship the swap, with a VALID reading (round 6 of the goal).

The valid comparison (same content, same table, same levels, same enemy, only the swap differs):
    with the swap:    767.585
    without it:       628.024        -> the swap DOES reach the commanded cast.
(An earlier "proof" of the opposite compared a scene whose table had been replaced, where `level_convention` never ran.)

So the swap ships, and the reading is a same-level comparison inside one judge: the content's commanded cast must deal what the
same slot-9 row deals when installed BY HAND after the levels convention has run -- which is what removes the level trap from the
reading itself.
"""
import io
import json
import sys

JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
PROBE = "src/test/java/com/laosun/aluminium/test/SwapDamageProbeTest.java"
CHAR = "src/main/resources/characters/1404.json"

import os
if os.path.exists(PROBE):
    os.remove(PROBE)
    print("ok   the probe is gone (it answered)")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if not any(effect.get("op") == "REPLACE_SKILL" for effect in rule["do"]):
    sys.exit("REFUSING: the swap is not in the rule")
rule["note"] = rule["note"].replace(
    "⚠⚠ **2026-10-02 两次尝试均回滚 ✓，而且第一次的“阻断”已被认定为无效测量 ✗**：",
    "⭐⭐ **2026-10-02 终于用一个有效对比钉住了它 ✓**：")
rule["note"] += (
    "⭐ **有效对比（同内容、同表、同等级、同敌人，只差“有无换装” ✓）**："
    "⭐ **有换装 = 767.585** ✓、**无换装 = 628.024** ✓ ⇒ 换装**确实传到了被命令的施放** ✓。"
    "⚠ 寿命用 `turns: 1` ✓（❗ `until: next_attack` 也量过、同样是 767.585 ✓；用 `turns: 1` 因为它的边界"
    "完全在这一回合内 ✓）。⚠ 仍登记 ✓：充能 150 那句（实测：「充能 ≥ 100」那条**没有【血仇】门** ✗"
    "⇒ 反复触发、把充能抽干 ✗⇒ 攒不到 150 ✗）。")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the note records the valid comparison")

text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /**
     * ⭐⭐ And the swap must REACH the cast -- read as a SAME-LEVEL comparison, which is what the first attempt got wrong.
     *
     * <p>The earlier "proof" compared against a scene whose trigger table had been REPLACED, so `level_convention` never ran there
     * and the two numbers came from different levels. This one installs the same row by hand AFTER the battle has started (so the
     * levels convention has already run), casts it directly, and requires the content's commanded cast to deal the same.
     */
    @Test
    public void theSwapReachesTheCast() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();
        double before = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double commanded = before - battle.enemies.getFirst().getCurrentHp();

        Character byHand = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle other = new Battle(List.of(byHand),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        other.startBattle();
        other.processRequests();
        // ⚠ AFTER startBattle, so the slot carries the level the convention gives it -- the trap the earlier attempt fell into.
        byHand.getSkills().put(SkillType.SKILL, new com.laosun.aluminium.models.skill.DefaultSkill(
                MYDEI, 9, byHand.getSkills().get(SkillType.SKILL).getLevel()));
        double otherBefore = other.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(other,
                byHand.getSkills().get(SkillType.SKILL), byHand, List.of(other.enemies.getFirst()));
        other.processRequests();
        double manual = otherBefore - other.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] commanded=" + commanded + " ; slot-9 by hand=" + manual);

        Assertions.assertEquals(manual, commanded, manual * 1e-9,
                "「自动施放【弑王成王】」-- the commanded cast runs the row the swap installed, not the slot's original one");
    }
}
'''
if "theSwapReachesTheCast" in text:
    sys.exit("REFUSING: the reading is already there")
text = text.rstrip()
if not text.endswith("}"):
    sys.exit("REFUSING: unexpected judge tail")
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text[:-1].rstrip() + "\n" + ADD)
print("ok   the same-level reading is written")
