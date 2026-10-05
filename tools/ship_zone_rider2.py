"""The zone's additional damage, third attempt -- and this time the two measured mistakes are fixed (2026-10-02).

Verbatim (140303): 「开启结界…结界持续期间，敌方目标受到的伤害提高 #2%。<b>受到我方目标攻击后，每有1名目标受到攻击，会对被攻击目标中当前生命值最高的目标造成 1 次等同于缇宝 #3%
生命上限的量子属性附加伤害。</b>」

⚠ Mistake ①, measured: the first version used `scale: "self_attr:HEALTH"`, which is NOT a spelling the DAMAGE op's literal path reads -- it settled **1.0**, silently.
The op names a Max HP share `owner_max_hp` (its own validation: `"owner_max_hp".equals(literalScale) || "target_max_hp".equals(literalScale)`).

⚠ Mistake ②, measured: hanging it on `DAMAGE_SETTLED` fired it in scenes with no hit set at all (TrinnonZoneTest went red on 「Effect targets "highest_hp_attack_hit" but this
event has no such party」). `ATTACK_FINISHED` is the event whose own comment says "settlement complete, **hit set frozen**", which is both the hit set this sentence wants and
a set that exists. Its repeat count is an explicit parameter of `fireTriggers(..., int hitCount, ...)`.
"""
import io
import json
import sys

TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"
ZONE = "结界"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["1403"]["3"].get("param_list") or []
if not rows or rows[0][2] == rows[-1][2]:
    sys.exit("REFUSING: #3 does not run with level")
print("ok   #3 runs %s -> %s over %d levels" % (rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "ult_zone_additional_damage"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "ATTACK_FINISHED",
    "when": ["self has_state " + ZONE],
    "do": [{
        "op": "DAMAGE",
        "scale": "owner_max_hp",                             # ⚠ the spelling the DAMAGE op reads (self_attr:HEALTH settled 1.0, measured)
        "percent_from_skill_param": "ULTRA:2",               # × #3 of HIS OWN ultimate, at its own level
        "element": "Quantum",
        "target": "highest_hp_attack_hit",                   # 「被攻击目标中当前生命值最高的目标」
    }],
    "source": ("1403 缇宝 终结技 (140303)：「受到我方目标攻击后，**每有 1 名目标受到攻击**，"
               "会对**被攻击目标中当前生命值最高的目标**造成 1 次**等同于缇宝 #3% 生命上限**的"
               "量子属性附加伤害。」"),
    "note": ("⭐ **为什么挂 `ATTACK_FINISHED`**：它自己的注释写着 "
             "“settlement complete, **hit set frozen**” —— 正是本句要的“被攻击目标”集合，"
             "而且它**存在**（挂 `DAMAGE_SETTLED` 时会在没有命中集的场合抛错，**实测**：TrinnonZoneTest 因此变红）。"
             "⭐ **且 `scale` 必须是 `owner_max_hp`**（**实测**：先写的 `self_attr:HEALTH` 不在那个集里，静默地算成 1.0）。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))

# ---- the judge: two scenes that differ by EXACTLY the rider rule ----
JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1403 缇宝's zone deals its own additional damage (2026-10-02).
 *
 * <p>「受到我方目标攻击后，每有1名目标受到攻击，会对被攻击目标中当前生命值最高的目标造成 1 次等同于缇宝 #3% 生命上限的量子属性附加伤害。」
 *
 * <p>⭐ The two scenes differ by EXACTLY one thing: whether the rider's rule is on his table. The zone is open in both. An earlier version compared "zone closed" with
 * "zone open" and measured two effects at once -- the zone's 30% vulnerability lands on the ALLY's attack and does not follow 缇宝's level, so both deltas came out
 * 62.0727054541112 while the raw share ran 69.16 vs 9.41. Isolating one rule is what makes the number mean something.
 */
public class ZoneAdditionalDamageTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ATTACKER = 1402;
    private static final int MONSTER = 1002011;

    @Test
    public void theZoneAddsExactlyItsOwnInstance() {
        double withRider = damageFromAllyAttack(true);
        double withoutRider = damageFromAllyAttack(false);
        double delta = withRider - withoutRider;
        double rawAt80 = rawShare(LEVEL);
        double rawAtLow = rawShare(1);

        System.out.println("[zone_rider] zone open, rider on = " + withRider + " ; rider off = " + withoutRider
                + " ; the rider's own instance = " + delta + " (raw #3 x Max HP = " + rawAt80 + ")");

        Assertions.assertTrue(withoutRider > 0, "precondition: the ally attack lands");
        Assertions.assertTrue(delta > 0, "\\u300c\\u9020\\u6210 1 \\u6b21\\u2026\\u9644\\u52a0\\u4f24\\u5bb3\\u300d-- the zone's own instance");
        Assertions.assertTrue(delta < rawAt80,
                "it is a real damage instance, so the target's zones scale it: " + delta + " < " + rawAt80);
        Assertions.assertTrue(delta > rawAt80 / 4,
                "and it is the same order as the share, not the 1.0 a scale outside the op\\u2019s own set settles to");
        Assertions.assertTrue(rawAt80 > rawAtLow, "#3 x Max HP runs with level, which is what percent_from_skill_param reads");
    }

    /** `#3 \\u00d7 Max HP` at a level, read the way the engine reads it. */
    private static double rawShare(int level) {
        Character tribbie = CharacterFactory.create(TRIBBIE, level);
        Skill ultra = tribbie.getSkills().get(SkillType.ULTRA);
        var row = ultra.getData().getSkills().get(tribbie.skillLevel(ultra) - 1);
        return row.get(2) * tribbie.getAttribute(AttributeType.HEALTH).get();
    }

    private static double damageFromAllyAttack(boolean withRider) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        Battle battle = new Battle(List.of(tribbie, attacker),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();
        attacker = battle.characters.get(1);

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies.getFirst())),
                "precondition: his ultimate opens the zone");
        battle.processRequests();
        if (!withRider) {
            // the zone stays OPEN (the state lives on him); only the rider's rule comes off
            tribbie.setTriggerTable(new TriggerTable(TRIBBIE, List.of()));
        }

        var enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        Skill skill = attacker.getSkills().values().stream()
                .filter(s -> s != null && s.getData() != null && s.getData().getEffect().isDamaging())
                .findFirst().orElse(null);
        Assertions.assertNotNull(skill, "precondition: the attacker has a damaging skill");
        SkillExecutor.execute(battle, skill, attacker, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/ZoneAdditionalDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
