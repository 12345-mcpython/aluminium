"""`literalBase` reads the share through `shareOf`, and the zone rider lands with a table-preserving judge (2026-10-02).

⚠ Two measured facts drive this round.

① `literalBase`'s first line was `double share = effect.getPercent() == null ? 0.0 : effect.getPercent();` -- the DAMAGE op's literal path did not know the newer share
spellings, exactly like the line `derivedMagnitude` had (fixed last round, same family). A rider stating `percent_from_skill_param` therefore settled a share of 0.0
instead of #3 of his ultimate.

② The judge must NOT replace a character's table to isolate one rule. `literalBase`'s own comment records what that costs: "my first probes **replaced the character's
table** ... which also dropped **`level_convention`**, so the cast ran at the Lv1 row ... and rolled a CORRECT implementation back three times (M-32's own trap, sprung by
the judge)." So the rider-off scene keeps his loaded rules and removes exactly one.
"""
import io
import json
import sys

INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"
ZONE = "\u7ed3\u754c"

# ---------- ① the engine line ----------
interp = io.open(INT, encoding="utf-8").read()
OLD = "        double share = effect.getPercent() == null ? 0.0 : effect.getPercent();"
NEW = ("        // \u26a0 `shareOf`, not the raw field (2026-10-02): a share may also come from one of the owner's own skills (`percent_from_skill_param`),\n"
       "        // and `derivedMagnitude` was fixed for exactly this one round earlier. Same family, same line.\n"
       "        double share = shareOf(effect, ctx);")
if interp.count(NEW) == 1:
    print("ok   literalBase already reads the share through shareOf")
elif interp.count(OLD) == 1:
    io.open(INT, "w", encoding="utf-8", newline="\n").write(interp.replace(OLD, NEW))
    print("ok   literalBase now reads the share through shareOf")
else:
    sys.exit("REFUSING: neither shape is unique (%d / %d)" % (interp.count(OLD), interp.count(NEW)))

# ---------- the content ----------
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
        "scale": "owner_max_hp",
        "percent_from_skill_param": "ULTRA:2",
        "element": "Quantum",
        "target": "highest_hp_attack_hit",
    }],
    "source": ("1403 \u7f07\u5b9d \u7ec8\u7ed3\u6280 (140303)\uff1a\u300c\u53d7\u5230\u6211\u65b9\u76ee\u6807\u653b\u51fb\u540e\uff0c**\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb**\uff0c"
               "\u4f1a\u5bf9**\u88ab\u653b\u51fb\u76ee\u6807\u4e2d\u5f53\u524d\u751f\u547d\u503c\u6700\u9ad8\u7684\u76ee\u6807**\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650**\u7684"
               "\u91cf\u5b50\u5c5e\u6027\u9644\u52a0\u4f24\u5bb3\u3002\u300d"),
    "note": ("\u2b50 \u6302 `ATTACK_FINISHED`\uff1a\u5b83\u7684\u6ce8\u91ca\u5199\u7740 \u201csettlement complete, **hit set frozen**\u201d \u2014\u2014 "
             "\u6b63\u662f\u672c\u53e5\u8981\u7684\u201c\u88ab\u653b\u51fb\u76ee\u6807\u201d\u96c6\u5408\uff0c\u800c\u4e14\u5b83**\u5b58\u5728**\u3002\u2b50 `scale` \u662f `owner_max_hp`"
             "\uff08`self_attr:HEALTH` \u4e0d\u5728 DAMAGE \u5b57\u9762\u91cf\u8def\u5f84\u8ba4\u7684\u96c6\u91cc\uff0c\u9759\u9ed8\u7b97\u6210 1.0\uff0c**\u5b9e\u6d4b**\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))

# ---------- the judge ----------
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
 * <p>The two scenes differ by EXACTLY one rule, and the zone is open in both. \u26a0 An earlier version replaced his table with an EMPTY one to remove that rule, which also
 * dropped `level_convention` -- the trap `literalBase`\\u2019s own comment records being sprung by a judge three times. This one keeps every loaded rule and filters out
 * exactly the rider.
 */
public class ZoneAdditionalDamageTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ATTACKER = 1402;
    private static final int MONSTER = 1002011;
    private static final String RIDER = "ult_zone_additional_damage";

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
                "and of the same order as the share -- not the 0.0 a share spelling the op ignores would settle to");
        Assertions.assertTrue(rawAt80 > rawAtLow, "#3 x Max HP runs with level, which is what percent_from_skill_param reads");
    }

    /** `#3 \\u00d7 Max HP` at a level, read the way the engine reads it -- at the skill level that character actually has. */
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

        if (!withRider) {
            // keep EVERY loaded rule (level_convention included) and drop exactly the rider
            var kept = new java.util.ArrayList<>(tribbie.getTriggerTable().getRules());
            kept.removeIf(r -> RIDER.equals(r.getId()));
            tribbie.setTriggerTable(new TriggerTable(TRIBBIE, kept));
        }

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies.getFirst())),
                "precondition: his ultimate opens the zone");
        battle.processRequests();

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
print("ok   judge written (table-preserving)")
