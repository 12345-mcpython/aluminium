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
    "\u26a0\u26a0 **2026-10-02 \u4e24\u6b21\u5c1d\u8bd5\u5747\u56de\u6eda \u2713\uff0c\u800c\u4e14\u7b2c\u4e00\u6b21\u7684\u201c\u963b\u65ad\u201d\u5df2\u88ab\u8ba4\u5b9a\u4e3a\u65e0\u6548\u6d4b\u91cf \u2717**\uff1a",
    "\u2b50\u2b50 **2026-10-02 \u7ec8\u4e8e\u7528\u4e00\u4e2a\u6709\u6548\u5bf9\u6bd4\u9489\u4f4f\u4e86\u5b83 \u2713**\uff1a")
rule["note"] += (
    "\u2b50 **\u6709\u6548\u5bf9\u6bd4\uff08\u540c\u5185\u5bb9\u3001\u540c\u8868\u3001\u540c\u7b49\u7ea7\u3001\u540c\u654c\u4eba\uff0c\u53ea\u5dee\u201c\u6709\u65e0\u6362\u88c5\u201d \u2713\uff09**\uff1a"
    "\u2b50 **\u6709\u6362\u88c5 = 767.585** \u2713\u3001**\u65e0\u6362\u88c5 = 628.024** \u2713 \u21d2 \u6362\u88c5**\u786e\u5b9e\u4f20\u5230\u4e86\u88ab\u547d\u4ee4\u7684\u65bd\u653e** \u2713\u3002"
    "\u26a0 \u5bff\u547d\u7528 `turns: 1` \u2713\uff08\u2757 `until: next_attack` \u4e5f\u91cf\u8fc7\u3001\u540c\u6837\u662f 767.585 \u2713\uff1b\u7528 `turns: 1` \u56e0\u4e3a\u5b83\u7684\u8fb9\u754c"
    "\u5b8c\u5168\u5728\u8fd9\u4e00\u56de\u5408\u5185 \u2713\uff09\u3002\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u5145\u80fd 150 \u90a3\u53e5\uff08\u5b9e\u6d4b\uff1a\u300c\u5145\u80fd \u2265 100\u300d\u90a3\u6761**\u6ca1\u6709\u3010\u8840\u4ec7\u3011\u95e8** \u2717"
    "\u21d2 \u53cd\u590d\u89e6\u53d1\u3001\u628a\u5145\u80fd\u62bd\u5e72 \u2717\u21d2 \u6512\u4e0d\u5230 150 \u2717\uff09\u3002")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the note records the valid comparison")

text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /**
     * \u2b50\u2b50 And the swap must REACH the cast -- read as a SAME-LEVEL comparison, which is what the first attempt got wrong.
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
        // \u26a0 AFTER startBattle, so the slot carries the level the convention gives it -- the trap the earlier attempt fell into.
        byHand.getSkills().put(SkillType.SKILL, new com.laosun.aluminium.models.skill.DefaultSkill(
                MYDEI, 9, byHand.getSkills().get(SkillType.SKILL).getLevel()));
        double otherBefore = other.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(other,
                byHand.getSkills().get(SkillType.SKILL), byHand, List.of(other.enemies.getFirst()));
        other.processRequests();
        double manual = otherBefore - other.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] commanded=" + commanded + " ; slot-9 by hand=" + manual);

        Assertions.assertEquals(manual, commanded, manual * 1e-9,
                "\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d-- the commanded cast runs the row the swap installed, not the slot's original one");
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
