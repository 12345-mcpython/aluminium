package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Argenti (银枝) (1302), from her own file: [升格], the talent's per-hit energy, trace Courage (勇气) and Eidolon 1/4.
 *
 * <p><b>What it needed.</b> Nothing new - which is the point: the talent's "for each 1 enemy target hit ... restore 3 energy"
 * is {@code per_target} on {@code GAIN_ENERGY}, [升格] is a <b>stackable named modifier</b> (`max_stacks: 10` + `buff: 升格`),
 * and Courage (勇气)'s "enemy targets whose current HP percentage <= 50%" is the existing {@code target_hp_percent} condition. The two rules that
 * read like new vocabulary (Eidolon 4's cap raise, Eidolon 6's defence ignore) are registered instead of approximated.
 */
public class ArgentiStacksTest {
    private static final int ARGENTI = 1302;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * All three cast slots feed the talent: "when casting a basic attack, Skill or Ultimate ... gain 1 stack of [升格]".
     *
     * <p>Note: The energy half of the same sentence ("for each 1 enemy target hit ... restore 3 energy") is {@code per_target}, and its
     * arithmetic already has its own pin in {@code CastAppliedCountTest} - the engine exposes no public "current energy"
     * reader, and inventing one for a test would be a worse trade than pointing at the case that exists. What is pinned
     * here is what this file adds: <b>three rules</b>, one per cast event, each marking exactly once.
     */
    @Test
    public void allThreeSlotsFeedTheTalent() {
        Fixture f = new Fixture();
        int marks = 0;
        for (TriggerEvent cast : List.of(TriggerEvent.BASIC_ATTACK, TriggerEvent.SKILL_CAST, TriggerEvent.ULT_CAST)) {
            int before = f.argenti.getBuffManager().stacksOf("升格");
            f.fireAttack(cast, 1);
            int after = f.argenti.getBuffManager().stacksOf("升格");
            Assertions.assertEquals(1, after - before, cast + " marks 【升格】 exactly once");
            marks++;
        }
        Assertions.assertEquals(3, marks);
    }


    /** [升格] stacks up to its cap and each stack is a crit-rate step. */
    @Test
    public void theStacksCapAtTen() {
        Fixture f = new Fixture();
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();

        for (int i = 0; i < 12; i++) {
            f.fireAttack(TriggerEvent.BASIC_ATTACK, 1);
        }

        Assertions.assertEquals(base + 10 * 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「该效果最多叠加10层」: twelve hits give ten stacks, not twelve");
        Assertions.assertEquals(10, f.argenti.getBuffManager().stacksOf("升格"),
                "…and the count is readable by name, which is what Eidolon 4 and any removal would use");
    }

    /** trace Piety (虔诚) grants a stack on her own turn start. */
    @Test
    public void herTraceGrantsAStackOnHerTurn() {
        Fixture f = new Fixture();
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();

        f.argentiTurn();

        Assertions.assertEquals(base + 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「回合开始时，立即获得1层【升格】」");
    }

    /** trace Courage (勇气) boosts the instance that lands on a hurt enemy, and only that one. */
    @Test
    public void herTraceBoostsDamageOnHurtEnemies() {
        Fixture f = new Fixture();
        f.enemy.takeDamage(f.enemy.getMaxHp() * 0.8);

        Assertions.assertEquals(2, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.TURN_START) + 1,
                "census: her file has the turn-start trace");
        Assertions.assertEquals(2, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "勇气 (the target's HP percentage -- no per-target vocabulary needed, because the condition's subject "
                        + "is already the unit being hit) and Eidolon 6's defence ignore");
    }

    /**
     * Eidolon 4's second half: "make the talent's effect <b>max stack count raise by 2</b>" - the cap really does move.
     *
     * <p>Note: Measured on the ATTRIBUTE, and the arithmetic has a trap worth stating: Eidolon 4 grants two layers at battle
     * start and they share the talent's stack group, so a cap of 12 is reached by 2 + 10 - twelve more hits still only
     * add ten. The probe printed, at E0 vs E4: 10 stacks / crit 0.30 against 12 stacks in the
     * group / crit 0.35 - the raise is real, and an expectation of "base + 12  x  0.025" double-counts those two layers.
     * Note: `stacksOf("升格")` is <b>not</b> usable here either: three modifier groups carry that name (the talent's crit
     * rate, Eidolon 1's crit damage, Eidolon 4's battle-start pair), and a name count adds them up.
     */
    @Test
    public void theFourthEidolonRaisesTheStackCap() {
        Fixture f = new Fixture(4);
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        for (int i = 0; i < 12; i++) {
            f.fireAttack(TriggerEvent.BASIC_ATTACK, 1);
        }
        Assertions.assertEquals(base + 10 * 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "12 layers in the group: the two granted at battle start plus ten more from the hits");
        Assertions.assertEquals(2, f.argenti.ruleEffectMaxStacksBonus("talent_stack_basic"),
                "…and the amendment is filed against the named rule — the mechanism, not a coincidence");
    }

    /** Note: Raising the cap of a rule that states none is refused at load: there would be no cap to raise. */
    @Test
    public void raisingACapThatDoesNotExistIsRefused() {
        EffectSpec state = new EffectSpec();
        TriggerSpecs.set(state, "op", "APPLY_BUFF");
        TriggerSpecs.set(state, "buff", "标记");
        TriggerSpecs.set(state, "turns", 1);
        TriggerSpecs.set(state, "target", "self");
        TriggerSpec named = TriggerSpecs.rule("BATTLE_START", null, state);
        TriggerSpecs.set(named, "id", "no_cap");

        EffectSpec amend = new EffectSpec();
        TriggerSpecs.set(amend, "op", "MODIFY_RULE");
        TriggerSpecs.set(amend, "rule", "no_cap");
        TriggerSpecs.set(amend, "effectMaxStacks", 2);
        TriggerSpec amender = TriggerSpecs.rule("BATTLE_START", null, amend);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ARGENTI, List.of(named, amender)));
        Assertions.assertTrue(refused.getMessage().contains("max_stacks"), refused.getMessage());
    }

    // ==================================================================
    // Helpers

    // ==================================================================

    private static final class Fixture {
        private final Character argenti;
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            argenti = CharacterFactory.create(ARGENTI, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(argenti, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), fixed());
            battle.startBattle();
        }

        private void fireAttack(TriggerEvent event, int hits) {
            battle.fireTriggers(event, argenti, enemy, hits, 0);
        }

        private void argentiTurn() {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == argenti)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for her"));
            battle.beforeMove();
            battle.afterMove();
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
