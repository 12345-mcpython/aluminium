"""1407 遐蝶 仓库技「月茧之庇」: a lethal blow is DEFERRED, and the carrier falls at its own turn's end (2026-10-02, item 54).

Document, verbatim (the ability page, read as UTF-8):
  「战斗中，若我方角色受到致命攻击，则本次行动中所有受到致命攻击的我方角色**获得【月茧】状态**。
    【月茧】状态下的角色会**暂时延后陷入无法战斗状态**，且**可以正常行动**。
    若**行动后**、**下一次回合开始前**当前生命值**提高或获得护盾**，则**解除【月茧】状态**，否则将**立即陷入无法战斗状态**。」
`param` read from the data: the ability's own row is `[0.1]`, ⚠ and the sentence uses no number at all -- this is a pure mechanism,
which is why the trace carries no percentage.

⭐ WHAT THIS NEEDED (and what it did NOT): two events the engine already had -- `HEALED` and `SHIELD_GRANTED`, which are
exactly 「当前生命值提高或获得护盾」 -- plus ONE new capability: a state that makes the engine HOLD a death instead of
committing it, and commit it at the carrier's TURN_END. ⚠ The commit point is the turn's END on purpose: the sentence says
the carrier 「可以正常行动」, so a turn-start commit would kill it before it acts.

⚠ 「仓库技」 (a passive that applies once the character is owned, even off-team) is only half-modelled: the engine fires a
character's rules when that character is in the party. The off-team half is registered, not approximated.
"""
import io
import json
import os

DATA = "src/main/resources/characters/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MooncocoonTest.java"
STATE = "月茧"
SOURCE = ("1407 遐蝶 仓库技 「月茧之庇 / Sanctuary of Mooncocoon」：「战斗中，若我方角色受到致命攻击，"
          "则本次行动中所有受到致命攻击的我方角色**获得【月茧】状态**。【月茧】状态下的角色会"
          "**暂时延后陷入无法战斗状态**，且**可以正常行动**。若**行动后**、**下一次回合开始前**"
          "当前生命值**提高或获得护盾**，则**解除【月茧】状态**，否则将**立即陷入无法战斗状态**」")
NOTE = ("⭐ 2026-10-02（第 54 件）：⭐ **延后死亡** ✓ —— 原句说的是「暂时延后陷入无法战斗状态」✓"
        "，而**不是**回血 ✗ ⇒ ❗ 受害者真的停在 **0 血**（那就是「延后」本身 ✓），**救它的是状态消失** ✓。"
        "⭐ 两个事件是现成的 ✓（`HEALED` ✓ 与 `SHIELD_GRANTED` ✓ = 「生命值提高或获得护盾」✓）"
        "，而**新能力只有一件**：一个使引擎**持有**死亡而不提交的状态 ✓（`defers_death` ✓ / `DeferredDeathBuff` ✓）。"
        "⚠ 提交点在该单位的 **`TURN_END`** ✓ —— 因为原句说「可以**正常行动**」✓，"
        "在回合**开始**前提交会让它没行动就倒 ✗（我第一版就是那么写的 ✗，已改 ✓）。"
        "⚠ **仓库技只实现一半** ✗：引擎只在该角色**在队**时发它的表 ✓；「获得该角色后（不上场也生效）」那一半已登记 ✗。")

rules = [
    {
        "on": "LETHAL_DAMAGE",
        "id": "mooncocoon_holds_the_blow",
        "when": ["actor is_ally"],
        "do": [{"op": "APPLY_BUFF", "buff": STATE, "defers_death": True, "permanent": True, "target": "target"}],
        "source": SOURCE,
        "note": NOTE + " ⚠ 「本次行动中**所有**」由**逐个受害者各自触发**实现 ✓（与第 51 件同一结论 ✓）。",
    },
    {
        "on": "HEALED",
        "id": "mooncocoon_a_heal_ends_it",
        "when": ["target has_state " + STATE],
        "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "target"}],
        "source": SOURCE,
        "note": "「若…当前生命值**提高**…则解除【月茧】状态」 ✓ —— 用现成的 `HEALED` 事件 ✓"
                "（`actor` = 提供者、`target` = 受惠者 ✓）。⚠ 守卫写成 `target has_state 月茧` ✓：只有**还在延后中**的那个单位才会被解除 ✓。",
    },
    {
        "on": "SHIELD_GRANTED",
        "id": "mooncocoon_a_shield_ends_it",
        "when": ["target has_state " + STATE],
        "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "target"}],
        "source": SOURCE,
        "note": "「…或**获得护盾**，则解除【月茧】状态」 ✓ —— 用现成的 `SHIELD_GRANTED` 事件 ✓。"
                "⚠ 护盾跟治疗是**两条独立的退出路** ✓（原句的「或」✓）。",
    },
]

if os.path.exists(DATA):
    doc = json.load(io.open(DATA, encoding="utf-8"))
    existing = doc["rules"] if isinstance(doc, dict) else doc
    kept = [r for r in existing if not (isinstance(r, dict) and r.get("id", "").startswith("mooncocoon_"))]
    kept.extend(rules)
    if isinstance(doc, dict):
        doc["rules"] = kept
        out = doc
    else:
        out = kept
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407.json: three mooncocoon rules")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407：「若我方角色受到致命攻击，则…获得【月茧】状态。【月茧】状态下的角色会暂时延后陷入无法战斗状态，
 * 且可以正常行动。若行动后、下一次回合开始前当前生命值提高或获得护盾，则解除【月茧】状态，否则将立即陷入无法战斗状态」 (2026-10-02).
 *
 * <p>⭐ THREE READINGS OF ONE SENTENCE: it does not fall; it falls once its turn is over; a heal before that saves it.
 */
public class MooncocoonTest {
    private static final int CASTORICE_BANNER = 1407;
    private static final int HEALER = 1211;
    private static final int VICTIM = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "月茧";

    /** ⭐⭐ 「暂时延后陷入无法战斗状态」: the blow is held -- the victim is ALIVE, at zero HP. */
    @Test
    public void theBlowIsHeld() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(0.0, victim.getCurrentHp(), 1e-9, "它真的停在 0 血 —— 那就是「延后」");
        Assertions.assertTrue(victim.getBuffManager().hasState(STATE), "「获得【月茧】状态」");
    }

    /** ⭐⭐ 「行动后…否则将立即陷入无法战斗状态」: nothing saved it, so its own turn's END commits the death. */
    @Test
    public void itsOwnTurnEndsIt() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "precondition: the blow was held");
        takeItsTurn(battle, victim);
        Assertions.assertTrue(victim.isDeath(), "没有人救它 ⇒ 它自己的回合结束时倒下");
    }

    /** ⭐⭐ 「若行动后…当前生命值提高…则解除【月茧】状态」: a real heal from a real healer saves it. */
    @Test
    public void aHealBeforeItsTurnSaves() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(2);
        Character healer = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertTrue(victim.getBuffManager().hasState(STATE), "precondition: it carries the trace");
        Skill heal = healer.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(heal, "precondition: the healer has a skill");
        SkillExecutor.execute(battle, heal, healer, List.of(victim));
        battle.processRequests();
        Assertions.assertFalse(victim.getBuffManager().hasState(STATE),
                "生命值提高 ⇒ 【月茧】解除");
        takeItsTurn(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "【月茧】已解除 ⇒ 它不再倒下");
    }

    // ==================================================================

    /** 1407 (the passive's owner), 1211 (the healer) and the victim. */
    private static Battle fight() {
        Character owner = CharacterFactory.create(CASTORICE_BANNER, 80, false, null, null, 0);
        Character healer = CharacterFactory.create(HEALER, 80, false, null, null, 0);
        Character victim = CharacterFactory.create(VICTIM, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, healer, victim),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static void strike(Battle battle, Character victim) {
        battle.applyTrueDamage(battle.enemies.getFirst(), victim, DamageElement.ICE, victim.getMaxHp() * 2.0);
        battle.processRequests();
    }

    /** ⚠ A WHOLE turn: the early tick on `beforeMove`, the late one and TURN_END on `afterMove`. */
    private static void takeItsTurn(Battle battle, Character unit) {
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
print("ok   judge written")
