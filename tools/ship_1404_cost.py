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
            // ⭐ 「消耗等同于万敌**当前生命值** 35% 的生命值」 (2026-10-02, reader: 1404's two enhanced skills). The
            // vocabulary had max-HP shares and a lost-HP share, and neither says "a share of what is left": 35% of MAX on a
            // wounded unit is a plausible-looking wrong number, which is exactly the kind this engine refuses to guess.
            // ⚠ `target` is the unit being resolved (for CONSUME_HP, the spender), matching the `target_*` family.
            case "target_current_hp" -> target.getCurrentHp() * share + flat;""",
    "the current-HP share",
)

# ---- 2. the content
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
rule = next(entry for entry in rules if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if "self has_state 血仇" in rule["when"]:
    sys.exit("REFUSING: the gate is already there")
rule["when"] = ["actor == self", "self has_state 血仇"]
rule["do"] = [
    {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "until": "next_attack", "target": "self"},
    {"op": "CONSUME_HP", "scale": "target_current_hp", "percent": 0.35, "target": "self"},
    {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
]
rule.pop("source", None)
rule["source"] = ("1404 万敌 天赋 以血还血 (140404) 【血仇】期间：「自身回合开始时"
                  "自动施放【弑王成王】」；【弑王成王】自己那一行：「消耗等同于万敌"
                  "**当前生命值 35%** 的生命值，对敌方单体造成等同于万敌 110% 生命上限的虚数属性伤害」")
rule["note"] = (
    "「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」⇒ `TURN_START` ＋ `self has_state 血仇` ⇒ "
    "**先换、再付代价、后放** ✓：`REPLACE_SKILL{skill: SKILL, skill_id: **9**, until: next_attack}` ✓ "
    "⇒ `CONSUME_HP{scale: target_current_hp, percent: 0.35}` ✓ ⇒ `CAST_SKILL{skill: SKILL}` ✓。"
    "⚠⚠ **2026-10-02 两处订正（均实测 ✓）**：① 原来**没有 `self has_state 血仇` 门** ✗"
    "（句子明写在【血仇】语境里 ✓）；② 原来直接 `CAST_SKILL{SKILL}` ✗ —— 那放的是**普通战技**（槽 2）"
    "而不是【弑王成王】（**槽 9** ✓，破韧 60/30 与语料逐字相符 ✓），也**没有付代价** ✗。"
    "⚠ **代价为何写在规则里** ✓：`CONSUME_HP` 是本项目对“技能自身代价”的**已有写法** ✓"
    "（`1205` 刃的「消耗等同于刃生命上限 30%」就是这么写的 ✓）；而本句是「**当前**生命值」✗，"
    "旧词汇只有最大值/已损失两种份额 ✗ ⇒ 本轮新增一个**当前生命值**份额 `target_current_hp` ✓。"
    "⚠ 仍登记 ✓：充能 150 那句（❗ 实测：「充能 ≥ 100」那条**没有【血仇】门** ✗ ⇒ 它会**反复**触发并把充能抽干 ✗"
    "，充能因此攒不到 150 ✗）。")
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
 * 1404 万敌：【弑王成王】的自动施放 (2026-10-02).
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

    /** 「消耗等同于万敌当前生命值 35% 的生命值」-- the cost is paid, at the START of his own turn. */
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
                "「消耗等同于万敌当前生命值 35% 的生命值」-- and it is the CURRENT value, not the maximum");
        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");
    }

    /** ⚠ The other half on its own: `REPLACE_SKILL` really installs the row the cast then uses. */
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
        Assertions.assertEquals(9, slot, "换入的是**槽 9** 的行（【弑王成王】破韧 60/30 ✓），而原来是槽 2");
    }

    /** ⚠ Half a turn is `beforeMove()` alone; a full one is both halves (see `ArlanEidolonFourTest`). */
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
