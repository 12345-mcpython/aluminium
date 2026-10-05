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
if "self has_state 血仇" in rule["when"]:
    sys.exit("REFUSING: the bloodfeud gate is already there")
rule["when"] = ["actor == self", "self has_state 血仇"]
rule["do"] = [
    {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "until": "next_attack", "target": "self"},
    {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
]
rule["source"] = ("1404 万敌 天赋 以血还血 (140404)：「【血仇】状态期间…自身回合开始时"
                  "自动施放【弑王成王】」")
rule["note"] = (
    "「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」⇒ `TURN_START` ＋ `self has_state 血仇` ⇒ "
    "**先换再放** ✓：`REPLACE_SKILL{skill: SKILL, skill_id: **9**, until: next_attack}` ⇒ `CAST_SKILL{skill: SKILL}` ✓。"
    "⚠⚠ **本轮订正两处（均实测 ✓）**：① 原来没有 `self has_state 血仇` 门 ✗"
    "（句子明写在【血仇】语境里 ✓）；② 原来直接 `CAST_SKILL{SKILL}` ✗ —— 那放的是**普通战技**（槽 2）"
    "而不是【弑王成王】（**槽 9** ✓）⇒ 即**声称与执行不一致** ✗。⚠ 槽号来自数据："
    "`SkillData.init(1404, 9)` 的破韧为 **60/30** ✓，与语料给【弑王成王】的破韧逐字相符 ✓"
    "（而【弑神登神】是 **90/60** ⇒ 槽 11 ✓）。"
)

# ---- 2. the 150-charge clause
if any(isinstance(entry, dict) and entry.get("id") == NEW_ID for entry in rules):
    sys.exit("REFUSING: the 150-charge rule is already there")
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": NEW_ID,
    "when": ["resource_changed:天赋充能", "self has_state 血仇", "self_resource:天赋充能 >= 150"],
    "do": [
        {"op": "SPEND_RESOURCE", "resource": "天赋充能", "amount": 150},
        {"op": "EXTRA_TURN", "target": "self"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "until": "next_attack", "target": "self"},
        {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
    ],
    "source": ("1404 万敌 天赋 以血还血 (140404)：「【血仇】状态期间充能达到 **150** 点时，"
               "万敌立即获得 1 个额外回合并自动施放【弑神登神】」"),
    "note": (
        "「充能达到 **150** 点时，立即获得 1 个**额外回合**并**自动施放【弑神登神】**」⇒ 四个已有词 ✓："
        "`SPEND_RESOURCE{150}` ✓（【弑神登神】自己那一行就写着「消耗 150 点充能」 ✓）；`EXTRA_TURN` ✓"
        "（已出货：`1102` 希儿、`1309` 知更鸟 ✓）；`REPLACE_SKILL{skill_id: **11**, until: next_attack}` ✓ —— "
        "槽 11 的破韧 **90/60** 与语料给【弑神登神】的逐字相符 ✓；`CAST_SKILL{SKILL}` ✓。"
        "⚠ 与 `turn_start_autocasts_skill` 同用 `SKILL` 槽 ✓ —— ⚠ **不冲突** ✓：两者都是"
        "“**先换再放、放完还原**”的瞬时换装 ✓（`until: next_attack` ✓），而它们各自换成不同的行 ✓。"
        "⚠ 仍登记 ✓：「下一次【弑神登神】**优先攻击指定敌方单体**」（终结技标记的目标 ✗）"
        "与 `1415` 的忆灵技能 8「使其自动施放1次**不消耗充能**的【弑神登神】」✗。"),
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
 * 1404 万敌：两个**强化战技**的自动施放 (2026-10-02).
 *
 * <p>Measured: the data has TWO BPSKILL slots for him -- 9 (破韧 60/30 = 【弑王成王】) and 11 (90/60 = 【弑神登神】) -- and
 * `REPLACE_SKILL` addresses a SLOT, so both sentences are writable. The discriminator below is the enhanced skill's own cost:
 * 【弑王成王】 「消耗等同于万敌当前生命值 35% 的生命值」, which the plain 战技 does not pay.
 */
public class MydeiBloodfeudSkillsTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "\\u8840\\u4ec7";
    private static final String CHARGE = "\\u5929\\u8d4b\\u5145\\u80fd";

    /** 「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」. */
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
                "「消耗等同于万敌当前生命值 35% 的生命值」-- 【弑王成王】 is the skill being cast");
        Assertions.assertTrue(enemyAfter < enemyBefore, "and it lands");
    }

    /** 「充能达到 150 点时，立即获得 1 个额外回合并自动施放【弑神登神】」. */
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
        // ⚠ The op is what raises RESOURCE_CHANGED (`Cone20024Test` records the same wiring), so the event is fired here the way
        // that judge does it rather than relying on a direct gain.
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 150);
        battle.processRequests();
        int chargeAfter = him.getResources().value(CHARGE);
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] 150 charge: charge " + chargeBefore + " -> " + chargeAfter
                + " ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertEquals(0, chargeAfter, "「消耗 150 点充能」-- the resource was there to spend");
        Assertions.assertTrue(enemyAfter < enemyBefore, "【弑神登神】 was cast");
    }

    /** ⚠ Half a turn is `beforeMove()` alone; expiry and turn-start work land across both halves (see `ArlanEidolonFourTest`). */
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
