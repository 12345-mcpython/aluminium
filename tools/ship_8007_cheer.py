"""8007: 【迷迷的声援】-- the half that is fully expressible (2026-10-02).

Document, verbatim (8007_开拓者.html:143):
「使指定我方单体<b>行动提前 100%</b>并附上【<b>迷迷的声援</b>】，持续 <b>3</b> 回合。
持有【迷迷的声援】的目标每造成 1 次伤害，都会再额外造成 1 次等同于原伤害 <b>28%</b> 的真实伤害。」

Two halves, and only one is expressible today:
  * ⭐ SHIPPED HERE: the advance and the state. `ADVANCE` is wired (`percent` 0.0-1.0 = the fraction of the way to the next
    turn) and `APPLY_BUFF` + `turns` is the ordinary timed state; the document gives both numbers (100%, 3).
  * ⛔ REGISTERED, measured twice, NOT approximated: the 28% rider. `DAMAGE` states its multiplier in exactly two ways (a skill
    row via `skill` + `damage_param`, or a literal ratio -- TriggerInterpreter:432), and neither can mean "28% of the damage
    that was just dealt". And 「真实伤害」 has no `DamageType` member (measured: NORMAL, ULTRA, BREAK, DOT, TECHNIQUE, ELATION)
    while the glossary defines it as an effect-proof, element-less hit that does not count as an attack (:144).

ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/8007.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MimiCheerTest.java"
RULE = "skill_advance_and_cheer"
CHEER = "\u8ff7\u8ff7\u7684\u58f0\u63f4"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "SKILL_CAST",
    "id": RULE,
    "when": ["actor == self"],
    "do": [
        {"op": "ADVANCE", "percent": 1.0, "target": "target"},
        {"op": "APPLY_BUFF", "buff": CHEER, "turns": 3, "target": "target"},
    ],
    "source": ("8007 \u5f00\u62d3\u8005 \u6218\u6280\uff08\u6587\u6863 `:143`\uff09\uff1a"
               "\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53**\u884c\u52a8\u63d0\u524d 100%**\u5e76\u9644\u4e0a\u3010**\u8ff7\u8ff7\u7684\u58f0\u63f4**\u3011\uff0c"
               "\u6301\u7eed **3** \u56de\u5408\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u53ea\u5199**\u80fd\u5b8c\u6574\u8868\u8fbe**\u7684\u90a3\u534a \u2713\uff08\u63d0\u524d + \u72b6\u6001 \u2713\uff0c\u4e24\u4e2a\u6570\u90fd\u7531\u6587\u6863\u7ed9\u5b9a \u2713\uff09\u3002"
             "\u26d4 **\u5df2\u767b\u8bb0\u3001\u4e0d\u8fd1\u4f3c**\uff1a\u540c\u53e5\u7684\u201c**\u7b49\u540c\u4e8e\u539f\u4f24\u5bb3 28% \u7684\u771f\u5b9e\u4f24\u5bb3**\u201d\u2717 \u2014\u2014 "
             "`DAMAGE` \u53ea\u6709\u4e24\u79cd\u4e58\u6570\u5199\u6cd5\uff08\u6280\u80fd\u884c\u53c2\u6570 \u2713\uff0f\u5b57\u9762\u6bd4\u4f8b \u2713\uff09\uff0c\u90fd\u8868\u8fbe\u4e0d\u4e86"
             "\u201c\u521a\u624d\u90a3\u6b21\u4f24\u5bb3\u7684 28%\u201d \u2717\uff1b\u800c\u201c\u771f\u5b9e\u4f24\u5bb3\u201d\u5728 `DamageType` \u91cc\u6ca1\u6709\u6210\u5458 \u2717\u3002"),
})

if is_dict:
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   8007.json: the advance and the cheer")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8007\uff1a\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u884c\u52a8\u63d0\u524d 100% \u5e76\u9644\u4e0a\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\uff0c\u6301\u7eed 3 \u56de\u5408\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN. The two halves of that sentence are both observable: the state appears on the chosen ally, and the state
 * is what the 28% rider (registered separately) would hang on.
 */
public class MimiCheerTest {
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CHEER = "\u8ff7\u8ff7\u7684\u58f0\u63f4";

    /** \u2b50 Casting the skill lays the cheer on the ally it was aimed at. */
    @Test
    public void theSkillLaysTheCheerOnItsTarget() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(ally.getBuffManager().hasState(CHEER), "precondition: no cheer yet");
        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: he has a skill");
        SkillExecutor.execute(battle, skill, owner, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ally.getBuffManager().hasState(CHEER),
                "\u300c\u9644\u4e0a\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\u300d");
    }
}
''')
print("ok   judge written")
