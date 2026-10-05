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
    "③ **旧报的 `CAST_SKILL{SKILL}` 放的是普通战技** ✗（槽 2 ✓）。",
    "③ **旧报的 `CAST_SKILL{SKILL}` 放的是普通战技** ✗（槽 2 ✓）。"
    "⭐ **2026-10-02 第二次尝试：换装的寿命改成 `turns: 1`** ✓（❗ 第一次用 `until: next_attack` ✗，"
    "而它**在攻击开始时就还原** ✗ ⇒ 伤害仍用旧行算 ✗；这正是 `1301` 注记里写着“**尚未钉住**”的那件事 ✓）。")

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the swap is back, with turns: 1")

# the reading: the commanded cast must deal what a direct cast of slot 9 deals
text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /**
     * ⭐ And the swap must REACH the cast: the commanded cast has to deal what casting 槽 9 directly deals.
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
                "「自动施放【弑王成王】」-- the commanded cast runs the row the swap installed, not the slot's original one");
    }
}
'''
text = text.rstrip()
if "theSwapReachesTheCast" in text:
    sys.exit("REFUSING: the reading is already there")
text = text[:-1].rstrip() + "\n" + ADD
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the swap-reaches-the-cast reading is written")
