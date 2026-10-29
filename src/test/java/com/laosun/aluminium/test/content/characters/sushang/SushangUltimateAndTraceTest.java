package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;

/**
 * 1206 Sushang: the ultimate's two clauses, and the trace that counts her own Sword Stance.
 *
 * <p>Each case drives the rule through {@code Battle.fireTriggers} and asserts the <b>observable</b> the
 * clause names - an extra turn, a raised ATTACK, a stack count - rather than re-reading the JSON, because
 * "the rule is in the file" is what {@code herFileCarriesTheClauses} already says and it is not evidence
 * that the clause does anything.
 */
public class SushangUltimateAndTraceTest {
    private static final int SUSHANG = 1206;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;

    /**
     * ⭐ 「并使素裳立即行动」: casting her ultimate clears her <b>wait</b>, so she is the next to act.
     *
     * <p>⚠ <b>CORRECTED (2026-10-19).</b> This case used to assert {@code getExtraTurnActor() == sushang} - i.e. it pinned
     * `EXTRA_TURN` as the reading of 「立即行动」. The game's own ability data decides it against that: the ultimate's phase
     * ({@code Avatar_Sushang_00_Skill03_Phase02}) ends with {@code SetActionDelay} on the Caster, {@code Value = 0} (her
     * delay is zeroed), and it never uses {@code RPG.GameCore.TurnInsertAbility} - the game's inserted-turn primitive
     * (measured: 1780 uses corpus-wide, none here). So the sentence is "clear the wait", and the two readings differ
     * observably: an extra turn keeps the turn she already had, zeroing the wait consumes it.
     *
     * <p>⚠ Both sides are asserted, or the reading is not pinned: the remaining wait must become <b>0</b>, and <b>no</b>
     * extra turn may be scheduled.
     */
    @Test
    public void herUltimateMakesHerActImmediately() {
        Fixture f = new Fixture();
        Assertions.assertNull(f.battle.getExtraTurnActor(), "precondition: nothing is scheduled yet");
        double before = f.remainingWaitOf(f.sushang);
        Assertions.assertTrue(before > 0, "precondition: she is waiting her turn (" + before + ")");

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, f.sushang, f.enemy, 0, 0));

        double after = f.remainingWaitOf(f.sushang);
        System.out.println("[1206] 大招前后的剩余等待: " + before + " -> " + after
                + " ; extraTurnActor = " + f.battle.getExtraTurnActor());
        Assertions.assertEquals(0.0, after, 1e-9, "「立即行动」= 等待清零（数据：`SetActionDelay` 的 `Value = 0`）");
        Assertions.assertNull(f.battle.getExtraTurnActor(),
                "「立即行动」≠「额外回合」：后者会插入一个回合（数据里那用的是 `TurnInsertAbility`）");
    }

    /**
     * ⭐ The same sentence's other clause: 「攻击力提高30%…持续2回合」.
     *
     * <p>⚠ This is a SEPARATE rule from the extra turn on purpose - one clause each - so a case can say which
     * half is missing. Asserting on ATTACK rather than on damage keeps the level convention out of it.
     */
    @Test
    public void herUltimateRaisesHerOwnAttack() {
        Fixture f = new Fixture();
        double before = f.sushang.getAttribute(AttributeType.ATTACK).get();
        double allyBefore = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, f.sushang, f.enemy, 0, 0));

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.ATTACK).get() > before,
                "「攻击力提高30%」 must raise her ATTACK: " + before + " -> "
                        + f.sushang.getAttribute(AttributeType.ATTACK).get());
        Assertions.assertEquals(allyBefore, f.ally.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "and it is HER attack (「其」 = Sushang, the subject of 「使素裳立即行动」), not the whole party's");
    }

    /**
     * ⭐ 行迹 逐寇: each Sword Stance that lands is counted, up to the 10-layer ceiling.
     *
     * <p>⚠ Driven by a REAL Skill cast, not by a hand-fired {@code DAMAGE_SETTLED}. Measured: the synthetic
     * event carries no damage instance, so {@code damage_is_additional} cannot be evaluated and the rule
     * correctly does not fire - the case read 0. The discriminator this clause rests on is a property of the
     * instance, so only a real hit can exercise it.
     *
     * <p>⚠ The dice are pinned to 0.0 so the 33% Sword Stance always triggers; and the count is asserted at
     * the cap as well, because a ceiling that is declared but not enforced is the failure mode this pins.
     */
    @Test
    public void theTraceCountsSwordStanceLayersUpToTen() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.sushang.getBuffManager().stacksOf("剑势"),
                "precondition: nothing counted yet");

        // One cast: the Sword Stance lands (the dice are pinned so it always does) and must be counted once.
        // ⚠ The hit is verified by the enemy's HP rather than assumed, so a count of 0 would mean the
        // counter is broken and not that the Sword Stance never happened.
        double before = f.enemy.getCurrentHp();
        f.castSkill();
        Assertions.assertTrue(f.enemy.getCurrentHp() < before, "precondition: the Skill hit");
        Assertions.assertEquals(1, f.sushang.getBuffManager().stacksOf("剑势"),
                "「每发动1次【剑势】」 counts one layer per Sword Stance that lands");

        for (int i = 0; i < 15; i++) {
            f.castSkill();
        }
        Assertions.assertEquals(10, f.sushang.getBuffManager().stacksOf("剑势"),
                "「最多叠加10层」 must hold: the cap is declared on ADD_STACK and must be enforced there");
    }


    /**
     * Census of the clauses this class asserts, so a rule that disappears is named rather than silently
     * shrinking the file.
     */
    @Test
    public void theFileCarriesTheUltimateAndTraceClauses() {
        var table = TriggerTables.of(SUSHANG);
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.ULT_CAST),
                "the extra turn and the attack raise - one clause each, so a missing half is visible");
        // Two: the Sword Stance counter, and eidolon 2's damage reduction - both keyed on "this instance is
        // additional damage", which is how the engine names a Sword Stance. The pair sharing one discriminator
        // is the point, so the count is stated rather than left implicit.
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.DAMAGE_SETTLED),
                "the Sword Stance counter and eidolon 2");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "the per-stack boost");
    }

    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(sushang, ally), List.of(enemy),
                BattleFixture.alwaysRoll(0.0));

        private Fixture() {
            battle.startBattle();
        }

        /** Her Skill at the enemy, through the engine's own immediate-cast entry point. */
        private void castSkill() {
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(enemy));
        }

        /** A unit's remaining wait, read off the action bar's own snapshot (the battle clock does not move). */
        private double remainingWaitOf(com.laosun.aluminium.entity.CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }
    }
}
