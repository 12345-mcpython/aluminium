"""Ship 1404's two enhanced-skill clauses (round 4 of the goal).

Measured, not guessed:
  * `REPLACE_SKILL{skill: <SkillType slot>, skill_id: <SLOT NUMBER>, until: ...}` is the shipped spelling (1301's 酒花奔涌, slot 8);
    its note warns the number is a SLOT, not a row id, and `EnhancedSkillDataProbeTest` pins that;
  * the data says 1404 has TWO BPSKILL slots: **9 = toughness 60/30 = 弑王成王** and **11 = toughness 90/60 = 弑神登神** (the corpus
    states exactly those toughnesses), so the register row I dismissed last round was RIGHT;
  * `EXTRA_TURN` is wired ("optional `target`") and already ships in 1102 and 1309;
  * so `1404.json` gets both halves: the turn-start autocast now gates on 【血仇】 and swaps in slot 9 (it used to cast the plain
    skill, i.e. the file claimed 弑王成王 while executing something else), and the 150-charge clause -- spend, extra turn, swap in
    slot 11, cast -- ships as its own rule.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
TURN_START = "turn_start_autocasts_skill"
NEW_ID = "bloodfeud_godslayer_at_a_hundred_and_fifty"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

# ---- 1. the turn-start rule: gate it, and swap the enhanced skill in first
rule = next(entry for entry in rules if isinstance(entry, dict) and entry.get("id") == TURN_START)
if "self has_state \u8840\u4ec7" in rule["when"]:
    sys.exit("REFUSING: the bloodfeud gate is already there")
rule["when"] = ["actor == self", "self has_state \u8840\u4ec7"]
rule["do"] = [
    {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "until": "next_attack", "target": "self"},
    {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
]
rule["source"] = ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404)\uff1a\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6"
                  "\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d")
rule["note"] = (
    "\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d\u21d2 `TURN_START` \uff0b `self has_state \u8840\u4ec7` \u21d2 "
    "**\u5148\u6362\u518d\u653e** \u2713\uff1a`REPLACE_SKILL{skill: SKILL, skill_id: **9**, until: next_attack}` \u21d2 `CAST_SKILL{skill: SKILL}` \u2713\u3002"
    "\u26a0\u26a0 **\u672c\u8f6e\u8ba2\u6b63\u4e24\u5904\uff08\u5747\u5b9e\u6d4b \u2713\uff09**\uff1a\u2460 \u539f\u6765\u6ca1\u6709 `self has_state \u8840\u4ec7` \u95e8 \u2717"
    "\uff08\u53e5\u5b50\u660e\u5199\u5728\u3010\u8840\u4ec7\u3011\u8bed\u5883\u91cc \u2713\uff09\uff1b\u2461 \u539f\u6765\u76f4\u63a5 `CAST_SKILL{SKILL}` \u2717 \u2014\u2014 \u90a3\u653e\u7684\u662f**\u666e\u901a\u6218\u6280**\uff08\u69fd 2\uff09"
    "\u800c\u4e0d\u662f\u3010\u5f11\u738b\u6210\u738b\u3011\uff08**\u69fd 9** \u2713\uff09\u21d2 \u5373**\u58f0\u79f0\u4e0e\u6267\u884c\u4e0d\u4e00\u81f4** \u2717\u3002\u26a0 \u69fd\u53f7\u6765\u81ea\u6570\u636e\uff1a"
    "`SkillData.init(1404, 9)` \u7684\u7834\u97e7\u4e3a **60/30** \u2713\uff0c\u4e0e\u8bed\u6599\u7ed9\u3010\u5f11\u738b\u6210\u738b\u3011\u7684\u7834\u97e7\u9010\u5b57\u76f8\u7b26 \u2713"
    "\uff08\u800c\u3010\u5f11\u795e\u767b\u795e\u3011\u662f **90/60** \u21d2 \u69fd 11 \u2713\uff09\u3002"
)

# ---- 2. the 150-charge clause
if any(isinstance(entry, dict) and entry.get("id") == NEW_ID for entry in rules):
    sys.exit("REFUSING: the 150-charge rule is already there")
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": NEW_ID,
    "when": ["resource_changed:\u5929\u8d4b\u5145\u80fd", "self has_state \u8840\u4ec7", "self_resource:\u5929\u8d4b\u5145\u80fd >= 150"],
    "do": [
        {"op": "SPEND_RESOURCE", "resource": "\u5929\u8d4b\u5145\u80fd", "amount": 150},
        {"op": "EXTRA_TURN", "target": "self"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "until": "next_attack", "target": "self"},
        {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
    ],
    "source": ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404)\uff1a\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u5145\u80fd\u8fbe\u5230 **150** \u70b9\u65f6\uff0c"
               "\u4e07\u654c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d"),
    "note": (
        "\u300c\u5145\u80fd\u8fbe\u5230 **150** \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a**\u989d\u5916\u56de\u5408**\u5e76**\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011**\u300d\u21d2 \u56db\u4e2a\u5df2\u6709\u8bcd \u2713\uff1a"
        "`SPEND_RESOURCE{150}` \u2713\uff08\u3010\u5f11\u795e\u767b\u795e\u3011\u81ea\u5df1\u90a3\u4e00\u884c\u5c31\u5199\u7740\u300c\u6d88\u8017 150 \u70b9\u5145\u80fd\u300d \u2713\uff09\uff1b`EXTRA_TURN` \u2713"
        "\uff08\u5df2\u51fa\u8d27\uff1a`1102` \u5e0c\u513f\u3001`1309` \u77e5\u66f4\u9e1f \u2713\uff09\uff1b`REPLACE_SKILL{skill_id: **11**, until: next_attack}` \u2713 \u2014\u2014 "
        "\u69fd 11 \u7684\u7834\u97e7 **90/60** \u4e0e\u8bed\u6599\u7ed9\u3010\u5f11\u795e\u767b\u795e\u3011\u7684\u9010\u5b57\u76f8\u7b26 \u2713\uff1b`CAST_SKILL{SKILL}` \u2713\u3002"
        "\u26a0 \u4e0e `turn_start_autocasts_skill` \u540c\u7528 `SKILL` \u69fd \u2713 \u2014\u2014 \u26a0 **\u4e0d\u51b2\u7a81** \u2713\uff1a\u4e24\u8005\u90fd\u662f"
        "\u201c**\u5148\u6362\u518d\u653e\u3001\u653e\u5b8c\u8fd8\u539f**\u201d\u7684\u77ac\u65f6\u6362\u88c5 \u2713\uff08`until: next_attack` \u2713\uff09\uff0c\u800c\u5b83\u4eec\u5404\u81ea\u6362\u6210\u4e0d\u540c\u7684\u884c \u2713\u3002"
        "\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u300c\u4e0b\u4e00\u6b21\u3010\u5f11\u795e\u767b\u795e\u3011**\u4f18\u5148\u653b\u51fb\u6307\u5b9a\u654c\u65b9\u5355\u4f53**\u300d\uff08\u7ec8\u7ed3\u6280\u6807\u8bb0\u7684\u76ee\u6807 \u2717\uff09"
        "\u4e0e `1415` \u7684\u5fc6\u7075\u6280\u80fd 8\u300c\u4f7f\u5176\u81ea\u52a8\u65bd\u653e1\u6b21**\u4e0d\u6d88\u8017\u5145\u80fd**\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d\u2717\u3002"),
})

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404: the turn-start cast is gated and swapped, and the 150-charge clause is in")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1404 \u4e07\u654c\uff1a\u4e24\u4e2a**\u5f3a\u5316\u6218\u6280**\u7684\u81ea\u52a8\u65bd\u653e (2026-10-02).
 *
 * <p>Measured: the data has TWO BPSKILL slots for him -- 9 (\u7834\u97e7 60/30 = \u3010\u5f11\u738b\u6210\u738b\u3011) and 11 (90/60 = \u3010\u5f11\u795e\u767b\u795e\u3011) -- and
 * `REPLACE_SKILL` addresses a SLOT, so both sentences are writable. The discriminator below is the enhanced skill's own cost:
 * \u3010\u5f11\u738b\u6210\u738b\u3011 \u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c\u5f53\u524d\u751f\u547d\u503c 35% \u7684\u751f\u547d\u503c\u300d, which the plain \u6218\u6280 does not pay.
 */
public class MydeiBloodfeudSkillsTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "\\u8840\\u4ec7";
    private static final String CHARGE = "\\u5929\\u8d4b\\u5145\\u80fd";

    /** \u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d. */
    @Test
    public void theTurnStartCastsTheEnhancedSkill() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        double hpBefore = him.getCurrentHp();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double hpAfter = him.getCurrentHp();
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] turn start: own hp " + hpBefore + " -> " + hpAfter
                + " ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertTrue(hpAfter < hpBefore,
                "\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c\u5f53\u524d\u751f\u547d\u503c 35% \u7684\u751f\u547d\u503c\u300d-- \u3010\u5f11\u738b\u6210\u738b\u3011 is the skill being cast");
        Assertions.assertTrue(enemyAfter < enemyBefore, "and it lands");
    }

    /** \u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d. */
    @Test
    public void theHundredAndFiftyChargeCastsGodslayer() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, 150);
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        int chargeBefore = him.getResources().value(CHARGE);
        // \u26a0 The op is what raises RESOURCE_CHANGED (`Cone20024Test` records the same wiring), so the event is fired here the way
        // that judge does it rather than relying on a direct gain.
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 150);
        battle.processRequests();
        int chargeAfter = him.getResources().value(CHARGE);
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] 150 charge: charge " + chargeBefore + " -> " + chargeAfter
                + " ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertEquals(0, chargeAfter, "\u300c\u6d88\u8017 150 \u70b9\u5145\u80fd\u300d-- the resource was there to spend");
        Assertions.assertTrue(enemyAfter < enemyBefore, "\u3010\u5f11\u795e\u767b\u795e\u3011 was cast");
    }

    /** \u26a0 Half a turn is `beforeMove()` alone; expiry and turn-start work land across both halves (see `ArlanEidolonFourTest`). */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
''')
print("ok   the judge is written")
