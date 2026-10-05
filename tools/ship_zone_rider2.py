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
ZONE = "\u7ed3\u754c"

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
        "scale": "owner_max_hp",                             # \u26a0 the spelling the DAMAGE op reads (self_attr:HEALTH settled 1.0, measured)
        "percent_from_skill_param": "ULTRA:2",               # \u00d7 #3 of HIS OWN ultimate, at its own level
        "element": "Quantum",
        "target": "highest_hp_attack_hit",                   # \u300c\u88ab\u653b\u51fb\u76ee\u6807\u4e2d\u5f53\u524d\u751f\u547d\u503c\u6700\u9ad8\u7684\u76ee\u6807\u300d
    }],
    "source": ("1403 \u7f07\u5b9d \u7ec8\u7ed3\u6280 (140303)\uff1a\u300c\u53d7\u5230\u6211\u65b9\u76ee\u6807\u653b\u51fb\u540e\uff0c**\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb**\uff0c"
               "\u4f1a\u5bf9**\u88ab\u653b\u51fb\u76ee\u6807\u4e2d\u5f53\u524d\u751f\u547d\u503c\u6700\u9ad8\u7684\u76ee\u6807**\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650**\u7684"
               "\u91cf\u5b50\u5c5e\u6027\u9644\u52a0\u4f24\u5bb3\u3002\u300d"),
    "note": ("\u2b50 **\u4e3a\u4ec0\u4e48\u6302 `ATTACK_FINISHED`**\uff1a\u5b83\u81ea\u5df1\u7684\u6ce8\u91ca\u5199\u7740 "
             "\u201csettlement complete, **hit set frozen**\u201d \u2014\u2014 \u6b63\u662f\u672c\u53e5\u8981\u7684\u201c\u88ab\u653b\u51fb\u76ee\u6807\u201d\u96c6\u5408\uff0c"
             "\u800c\u4e14\u5b83**\u5b58\u5728**\uff08\u6302 `DAMAGE_SETTLED` \u65f6\u4f1a\u5728\u6ca1\u6709\u547d\u4e2d\u96c6\u7684\u573a\u5408\u629b\u9519\uff0c**\u5b9e\u6d4b**\uff1aTrinnonZoneTest \u56e0\u6b64\u53d8\u7ea2\uff09\u3002"
             "\u2b50 **\u4e14 `scale` \u5fc5\u987b\u662f `owner_max_hp`**\uff08**\u5b9e\u6d4b**\uff1a\u5148\u5199\u7684 `self_attr:HEALTH` \u4e0d\u5728\u90a3\u4e2a\u96c6\u91cc\uff0c\u9759\u9ed8\u5730\u7b97\u6210 1.0\uff09\u3002"),
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
