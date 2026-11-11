package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 叩问天工的名冶 (relic set 132), 4-piece: 「装备者对防御力降低状态的敌方目标造成的暴击伤害提高 28%。装备者对敌方目标施加防御力降低状态后，
 * 使我方全体获得【助燃】，持续 2 回合，该效果无法叠加，持有【助燃】的我方目标造成的伤害提高 15%。该效果在装备者施放攻击后可再次触发。」
 *
 * <p><b>Why the precondition needs its own words.</b> This set is written entirely in terms of 「防御力降低状态」, and the engine
 * has no state by that name: a DEF reduction IS a {@code DEFENCE} modifier, the same way 「减速」 IS a {@code SPEED} modifier
 * (that is why {@code is_slowed} asks the attribute). The new entity sub-predicate {@code attribute_reduced} asks exactly that,
 * so both halves of the sentence can be written without inventing a status name the game's data does not have.
 *
 * <p>The critical-damage half is measured as a <b>difference</b> between two enemies of the same level - one reduced, one not -
 * because the crit area is the only zone the clause touches.
 */
public class Relic132ComburentTest {

    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;
    private static final int SET = 132;
    /** 1307 Black Swan (黑天鹅): her Skill lowers the target's DEFENCE by 20.8% - the event this set hangs on. */
    private static final int WEARER = 1307;
    private static final String COMBURENT = "助燃";

    /** 「施加防御力降低状态后，使我方全体获得【助燃】…造成的伤害提高 15%」. */
    @Test
    public void everyAllyGetsComburentAfterTheWearerLowersDefence() {
        Run run = new Run();
        Assertions.assertFalse(run.wearer.getBuffManager().hasState(COMBURENT), "precondition: nothing before the cast");
        Assertions.assertEquals(0.0, run.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "precondition: the ally has no damage boost");

        run.lowerDefence();

        Assertions.assertTrue(run.wearer.getBuffManager().hasState(COMBURENT),
                "「使我方全体获得【助燃】」 — the wearer");
        Assertions.assertTrue(run.ally.getBuffManager().hasState(COMBURENT), "and the ally");
        Assertions.assertEquals(0.15, run.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「持有【助燃】的我方目标造成的伤害提高 15%」");
    }

    /** The control: an attack that lowers no defence grants nothing. */
    @Test
    public void anAttackWithoutDefenceReductionGrantsNothing() {
        Run run = new Run();
        run.basicAttack();
        Assertions.assertFalse(run.ally.getBuffManager().hasState(COMBURENT), "no reduction ⇒ no 【助燃】");
        Assertions.assertEquals(0.0, run.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "and no boost");
    }

    /** 「对防御力降低状态的敌方目标造成的暴击伤害提高 28%」 — the same board with and without the reduction. */
    @Test
    public void theReducedEnemyTakesTheExtraCriticalDamage() {
        double againstReduced = Run.critMultiplierAgainst(true);
        double againstPlain = Run.critMultiplierAgainst(false);

        Assertions.assertEquals(0.28, againstReduced - againstPlain, 1e-9,
                "「暴击伤害提高 28%」 — 1.78 vs 1.5 on the crit area: " + againstReduced + " vs " + againstPlain);
    }

    /** 「该效果无法叠加」: a second reduction refreshes the boost rather than adding a second 15%. */
    @Test
    public void theBoostDoesNotStack() {
        Run run = new Run();
        run.lowerDefence();
        run.lowerDefence();
        Assertions.assertEquals(0.15, run.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "a second 【助燃】 does not make it 30%");
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Character ally = CharacterFactory.create(1001, LEVEL);
        private final Enemy reduced;
        private final Enemy plain;
        private final Battle battle;

        private Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(SET, 4, RELIC_LEVEL));
            reduced = BattleFixture.monster();
            plain = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(reduced, plain)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        /** Her Skill, aimed at the first enemy: 「防御力降低 20.80%」. */
        private void lowerDefence() {
            battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(reduced));
            battle.processRequests();
        }

        private void basicAttack() {
            battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(plain));
            battle.processRequests();
        }

        /** The crit zone's multiplier on the wearer's next hit against {@code enemy}. */
        private double criticalMultiplierOn(Enemy enemy) {
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) -> {
                if (damage.isOnAttackerSide(wearer)) {
                    settled.add(damage);
                }
            });
            battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(enemy));
            battle.processRequests();
            return settled.stream()
                    .mapToDouble(damage -> damage.critArea().getRate())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no instance settled against " + enemy.getName()));
        }

        /**
         * The crit multiplier of one basic attack, on a fresh board - with the target's defence lowered first when asked.
         *
         * <p>A fresh board per reading: {@code MODIFY_ATTR {instance: true}} rides the instance but is attached to the
         * wearer, so reading two attacks inside one battle would measure the first one's modifier on the second.
         */
        private static double critMultiplierAgainst(boolean reducedFirst) {
            Run run = new Run();
            if (reducedFirst) {
                run.lowerDefence();
            }
            return run.criticalMultiplierOn(run.reduced);
        }
    }
}
