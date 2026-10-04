"""Ship 1404's turn-start clause and the share it needed (round 5 of the goal).

What the last round measured, and what it changes:
  * the failure had TWO independent causes: the content never stated a cost at all, and `REPLACE_SKILL`'s effect on the cast was
    unproven. The damage reading (767.59) matches the PLAIN skill, so the swap is the part still to pin -- with
    `getSkillSlot()`, which `DefaultSkill` documents as returning the slot it was built with;
  * the cost vocabulary has no CURRENT-HP share (`target_max_hp`, `target_lost_hp`, `owner_max_hp`, `owner_def`, `owner_attack`),
    while 万敌's sentence is 「消耗等同于万敌**当前生命值** 35% 的生命值」 -- 35% of MAX would be a plausible-looking wrong number, so it
    gets its own name: `target_current_hp`;
  * `CONSUME_HP`'s own validation does not check `scale` at all (it checks percent / duration / stacks), so one case in
    `grantAmount` is the whole consumption point for that half.

Shipped: the share, the gated turn-start rule (swap slot 9 -> pay the cost -> cast), and two readings.
Registered: the 150-charge clause, whose drive is blocked by a measured defect (the >=100 rule drains the charge and has no
【血仇】 gate, so it can never reach 150).
"""
import io
import json
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


# ---- 1. the missing share
patch(
    INTERP,
    """            case "owner_max_hp" -> ownerAttributeOf(ctx, "owner_max_hp", AttributeType.HEALTH) * share + flat;""",
    """            case "owner_max_hp" -> ownerAttributeOf(ctx, "owner_max_hp", AttributeType.HEALTH) * share + flat;
            // \u2b50 「\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c**\u5f53\u524d\u751f\u547d\u503c** 35% \u7684\u751f\u547d\u503c」 (2026-10-02, reader: 1404's two enhanced skills). The
            // vocabulary had max-HP shares and a lost-HP share, and neither says "a share of what is left": 35% of MAX on a
            // wounded unit is a plausible-looking wrong number, which is exactly the kind this engine refuses to guess.
            // \u26a0 `target` is the unit being resolved (for CONSUME_HP, the spender), matching the `target_*` family.
            case "target_current_hp" -> target.getCurrentHp() * share + flat;""",
    "the current-HP share",
)

# ---- 2. the content
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
rule = next(entry for entry in rules if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if "self has_state \u8840\u4ec7" in rule["when"]:
    sys.exit("REFUSING: the gate is already there")
rule["when"] = ["actor == self", "self has_state \u8840\u4ec7"]
rule["do"] = [
    {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "until": "next_attack", "target": "self"},
    {"op": "CONSUME_HP", "scale": "target_current_hp", "percent": 0.35, "target": "self"},
    {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
]
rule.pop("source", None)
rule["source"] = ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404) \u3010\u8840\u4ec7\u3011\u671f\u95f4\uff1a\u300c\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6"
                  "\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d\uff1b\u3010\u5f11\u738b\u6210\u738b\u3011\u81ea\u5df1\u90a3\u4e00\u884c\uff1a\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c"
                  "**\u5f53\u524d\u751f\u547d\u503c 35%** \u7684\u751f\u547d\u503c\uff0c\u5bf9\u654c\u65b9\u5355\u4f53\u9020\u6210\u7b49\u540c\u4e8e\u4e07\u654c 110% \u751f\u547d\u4e0a\u9650\u7684\u865a\u6570\u5c5e\u6027\u4f24\u5bb3\u300d")
rule["note"] = (
    "\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d\u21d2 `TURN_START` \uff0b `self has_state \u8840\u4ec7` \u21d2 "
    "**\u5148\u6362\u3001\u518d\u4ed8\u4ee3\u4ef7\u3001\u540e\u653e** \u2713\uff1a`REPLACE_SKILL{skill: SKILL, skill_id: **9**, until: next_attack}` \u2713 "
    "\u21d2 `CONSUME_HP{scale: target_current_hp, percent: 0.35}` \u2713 \u21d2 `CAST_SKILL{skill: SKILL}` \u2713\u3002"
    "\u26a0\u26a0 **2026-10-02 \u4e24\u5904\u8ba2\u6b63\uff08\u5747\u5b9e\u6d4b \u2713\uff09**\uff1a\u2460 \u539f\u6765**\u6ca1\u6709 `self has_state \u8840\u4ec7` \u95e8** \u2717"
    "\uff08\u53e5\u5b50\u660e\u5199\u5728\u3010\u8840\u4ec7\u3011\u8bed\u5883\u91cc \u2713\uff09\uff1b\u2461 \u539f\u6765\u76f4\u63a5 `CAST_SKILL{SKILL}` \u2717 \u2014\u2014 \u90a3\u653e\u7684\u662f**\u666e\u901a\u6218\u6280**\uff08\u69fd 2\uff09"
    "\u800c\u4e0d\u662f\u3010\u5f11\u738b\u6210\u738b\u3011\uff08**\u69fd 9** \u2713\uff0c\u7834\u97e7 60/30 \u4e0e\u8bed\u6599\u9010\u5b57\u76f8\u7b26 \u2713\uff09\uff0c\u4e5f**\u6ca1\u6709\u4ed8\u4ee3\u4ef7** \u2717\u3002"
    "\u26a0 **\u4ee3\u4ef7\u4e3a\u4f55\u5199\u5728\u89c4\u5219\u91cc** \u2713\uff1a`CONSUME_HP` \u662f\u672c\u9879\u76ee\u5bf9\u201c\u6280\u80fd\u81ea\u8eab\u4ee3\u4ef7\u201d\u7684**\u5df2\u6709\u5199\u6cd5** \u2713"
    "\uff08`1205` \u5203\u7684\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u5203\u751f\u547d\u4e0a\u9650 30%\u300d\u5c31\u662f\u8fd9\u4e48\u5199\u7684 \u2713\uff09\uff1b\u800c\u672c\u53e5\u662f\u300c**\u5f53\u524d**\u751f\u547d\u503c\u300d\u2717\uff0c"
    "\u65e7\u8bcd\u6c47\u53ea\u6709\u6700\u5927\u503c/\u5df2\u635f\u5931\u4e24\u79cd\u4efd\u989d \u2717 \u21d2 \u672c\u8f6e\u65b0\u589e\u4e00\u4e2a**\u5f53\u524d\u751f\u547d\u503c**\u4efd\u989d `target_current_hp` \u2713\u3002"
    "\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u5145\u80fd 150 \u90a3\u53e5\uff08\u2757 \u5b9e\u6d4b\uff1a\u300c\u5145\u80fd \u2265 100\u300d\u90a3\u6761**\u6ca1\u6709\u3010\u8840\u4ec7\u3011\u95e8** \u2717 \u21d2 \u5b83\u4f1a**\u53cd\u590d**\u89e6\u53d1\u5e76\u628a\u5145\u80fd\u62bd\u5e72 \u2717"
    "\uff0c\u5145\u80fd\u56e0\u6b64\u6512\u4e0d\u5230 150 \u2717\uff09\u3002")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404's turn-start rule gates, swaps, pays and casts")

# ---- 3. the readings
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1404 \u4e07\u654c\uff1a\u3010\u5f11\u738b\u6210\u738b\u3011\u7684\u81ea\u52a8\u65bd\u653e (2026-10-02).
 *
 * <p>Two readings, kept apart on purpose, because the earlier attempt failed and the failure had two independent causes: the rule
 * never stated a cost, and whether the swap reaches the cast was unproven. So one test reads the COST, the other reads the SWAP
 * by the slot the skill reports.
 */
public class MydeiBloodfeudSkillsTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "\\u8840\\u4ec7";
    private static final String CHARGE = "\\u5929\\u8d4b\\u5145\\u80fd";

    /** \u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c\u5f53\u524d\u751f\u547d\u503c 35% \u7684\u751f\u547d\u503c\u300d-- the cost is paid, at the START of his own turn. */
    @Test
    public void theTurnStartPaysTheSkillsCost() {
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
        System.out.println("[mydei-skills] cost: own hp " + hpBefore + " -> " + hpAfter
                + " (35% of current = " + (hpBefore * 0.35) + ") ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertEquals(hpBefore * 0.65, hpAfter, hpBefore * 1e-6,
                "\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c\u5f53\u524d\u751f\u547d\u503c 35% \u7684\u751f\u547d\u503c\u300d-- and it is the CURRENT value, not the maximum");
        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");
    }

    /** \u26a0 The other half on its own: `REPLACE_SKILL` really installs the row the cast then uses. */
    @Test
    public void theSwapInstallsTheEnhancedRow() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        EffectSpec swap = new EffectSpec();
        TriggerSpecs.set(swap, "op", "REPLACE_SKILL");
        TriggerSpecs.set(swap, "skill", "SKILL");
        TriggerSpecs.set(swap, "skillId", 9);
        TriggerSpecs.set(swap, "permanent", Boolean.TRUE);
        TriggerSpecs.set(swap, "target", "self");
        him.setTriggerTable(new TriggerTable(MYDEI,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), swap)), List.of()));
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int slot = him.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[mydei-skills] SKILL slot after the swap = " + slot
                + " ; category = " + him.getSkills().get(SkillType.SKILL).getData().getCategory());
        Assertions.assertEquals(9, slot, "\u6362\u5165\u7684\u662f**\u69fd 9** \u7684\u884c\uff08\u3010\u5f11\u738b\u6210\u738b\u3011\u7834\u97e7 60/30 \u2713\uff09\uff0c\u800c\u539f\u6765\u662f\u69fd 2");
    }

    /** \u26a0 Half a turn is `beforeMove()` alone; a full one is both halves (see `ArlanEidolonFourTest`). */
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
print("ok   the two readings are written")
