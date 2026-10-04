"""Retry the swap with a lifetime that does not end at the attack's start (round 6 of the goal).

Measured last round: `REPLACE_SKILL{until: next_attack}` installs the row into the map (`getSkillSlot() == 9`) but the cast that
follows in the same rule deals 767.585, while casting slot 9 directly deals 383.793. 1301's own note records the open question --
「生命周期语义（"用掉后还原"与"攻击时还原"的先后）尚未用行为用例钉住」 -- and this is that bug: if the swap is restored at the START of
the attack, the damage is computed from the row that was there before it.

So the lifetime becomes `turns: 1`, which lasts past the cast and expires with his turn -- and the reading becomes a real
comparison: the content's commanded cast must deal what a DIRECT slot-9 cast deals.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if any(effect.get("op") == "REPLACE_SKILL" for effect in rule["do"]):
    sys.exit("REFUSING: the swap is already there")
rule["do"].insert(0, {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "turns": 1, "target": "self"})
rule["note"] = rule["note"].replace(
    "\u2462 **\u65e7\u62a5\u7684 `CAST_SKILL{SKILL}` \u653e\u7684\u662f\u666e\u901a\u6218\u6280** \u2717\uff08\u69fd 2 \u2713\uff09\u3002",
    "\u2462 **\u65e7\u62a5\u7684 `CAST_SKILL{SKILL}` \u653e\u7684\u662f\u666e\u901a\u6218\u6280** \u2717\uff08\u69fd 2 \u2713\uff09\u3002"
    "\u2b50 **2026-10-02 \u7b2c\u4e8c\u6b21\u5c1d\u8bd5\uff1a\u6362\u88c5\u7684\u5bff\u547d\u6539\u6210 `turns: 1`** \u2713\uff08\u2757 \u7b2c\u4e00\u6b21\u7528 `until: next_attack` \u2717\uff0c"
    "\u800c\u5b83**\u5728\u653b\u51fb\u5f00\u59cb\u65f6\u5c31\u8fd8\u539f** \u2717 \u21d2 \u4f24\u5bb3\u4ecd\u7528\u65e7\u884c\u7b97 \u2717\uff1b\u8fd9\u6b63\u662f `1301` \u6ce8\u8bb0\u91cc\u5199\u7740\u201c**\u5c1a\u672a\u9489\u4f4f**\u201d\u7684\u90a3\u4ef6\u4e8b \u2713\uff09\u3002")

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the swap is back, with turns: 1")

# the reading: the commanded cast must deal what a direct cast of slot 9 deals
text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /**
     * \u2b50 And the swap must REACH the cast: the commanded cast has to deal what casting \u69fd 9 directly deals.
     *
     * <p>This is the reading that was missing. The first attempt used `until: next_attack` and the swap was restored at the START of
     * the attack, so the damage came from the old row (767.585 against 383.793) -- visible only because the two numbers were
     * compared in the same scene.
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
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double commanded = enemyBefore - battle.enemies.getFirst().getCurrentHp();

        Character him2 = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        EffectSpec swap = new EffectSpec();
        TriggerSpecs.set(swap, "op", "REPLACE_SKILL");
        TriggerSpecs.set(swap, "skill", "SKILL");
        TriggerSpecs.set(swap, "skillId", 9);
        TriggerSpecs.set(swap, "permanent", Boolean.TRUE);
        TriggerSpecs.set(swap, "target", "self");
        him2.setTriggerTable(new TriggerTable(MYDEI,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), swap)), List.of()));
        Battle battle2 = new Battle(List.of(him2),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle2.startBattle();
        battle2.processRequests();
        double otherBefore = battle2.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle2,
                him2.getSkills().get(SkillType.SKILL), him2, List.of(battle2.enemies.getFirst()));
        battle2.processRequests();
        double direct = otherBefore - battle2.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] commanded=" + commanded + " direct slot 9=" + direct);

        Assertions.assertEquals(direct, commanded, direct * 1e-6,
                "\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d-- the commanded cast runs the row the swap installed, not the slot's original one");
    }
}
'''
text = text.rstrip()
if "theSwapReachesTheCast" in text:
    sys.exit("REFUSING: the reading is already there")
text = text[:-1].rstrip() + "\n" + ADD
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the swap-reaches-the-cast reading is written")
