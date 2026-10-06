package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): the art barrier only lands on an Elation archetype, and the talent's reduction tracks the HP threshold. */
public class PearlArtBarrierAndTalentTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;   // Yao Guang, Elation
    private static final int HUNTER = 1204;
    private static final String BARRIER = "\u827a\u672f\u58c1\u5792";
    private static final String GRIT = "\u541e\u7eb3\u6c99\u783e";

    @Test
    public void theBarrierLandsOnlyOnAnElationArchetypeAndRefundsNinety() {
        Battle battle = newBattle();
        Character pearl = battle.characters.get(0);
        Character elation = battle.characters.get(1);
        Character hunter = battle.characters.get(2);

        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, elation, 0, 0);
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0);
        boolean onElation = elation.getBuffManager().hasState(BARRIER);
        boolean onHunter = hunter.getBuffManager().hasState(BARRIER);

        double pearlBefore = pearl.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ULT_CAST, elation, elation, 0, 0);
        double refund = pearl.getCurrentEnergy() - pearlBefore;
        System.out.println("[pearl_barrier] on the Elation ally = " + onElation + " ; on the Hunt ally = " + onHunter
                + " ; refund when the holder casts = " + refund);

        Assertions.assertTrue(onElation, "an Elation-Path archetype gets the barrier");
        Assertions.assertFalse(onHunter, "a Hunt ally must NOT get it (the rule gates on target has_path)");
        Assertions.assertEquals(90.0, refund, 1e-9, "and its holder's ultimate refunds Pearl 90 energy");
    }

    @Test
    public void theTalentReductionFollowsTheHalfHpThreshold() {
        Battle battle = newBattle();
        Character ally = battle.characters.get(2);

        ally.takeDamage(ally.getMaxHp() * 0.8);
        battle.fireTriggers(TriggerEvent.HP_LOST, ally, ally, 0, 0);
        boolean below = ally.getBuffManager().hasState(GRIT);

        ally.heal(ally.getMaxHp());
        battle.fireTriggers(TriggerEvent.HEALED, ally, ally, 0, 0);
        boolean above = ally.getBuffManager().hasState(GRIT);
        System.out.println("[pearl_talent] reduction below half = " + below + " ; after healing above half = " + above);

        Assertions.assertTrue(below, "below 50% HP the ally carries the damage reduction");
        Assertions.assertFalse(above, "and healing back above 50% removes it");
    }

    /**
     * The talent's NUMBER, not just its state: below half HP the ally takes 15% less.
     *
     * <p>Note: Why this case exists. The state assertion above passes for a buff carrying the right NAME and any
     * number at all, and a mutation of the shipped `-0.15` to `-0.5` was measured to leave the whole class GREEN. So
     * the magnitude is pinned here, on the engine's own settlement entrance ({@code Battle.applyDamage}, the single
     * outlet - a hand-fired {@code HP_LOST} never reaches a rule), as a RATIO of two readings taken on the same ally
     * in one battle: before the talent is up, and after.
     */
    @Test
    public void theTalentsReductionIsFifteenPercent() {
        Battle battle = newBattle();
        Character ally = battle.characters.get(2);
        Enemy attacker = battle.enemyUnits().getFirst();

        // The talent's own gate is "at or below half HP", and its event is HP_LOST - so the ally has to really be
        // brought below half by a SETTLED hit before the rule can fire.
        // Note: The setup goes through `Battle.applyDamage` too, because that is the only entrance that broadcasts
        // HP_LOST. Driving it with `takeDamage` was measured to leave the rule silent even with the ally at 20% HP
        // (read: `reduction up = false`), which is the same trap HANDOFF discipline 6 records - that call changes HP
        // and nothing else. The instance is stated as a multiple of the reading's so the zones are the same for both.
        // Measured: the setup lands the ally at 31.3% of Max HP, below the half line and with room for the reading.
        double readingInstance = ally.getMaxHp() * 0.1;
        battle.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                readingInstance * 10));
        Assertions.assertTrue(ally.getBuffManager().hasState(GRIT),
                "precondition: the ally was brought below half HP, so the talent is up");
        Assertions.assertTrue(ally.getCurrentHp() / ally.getMaxHp() <= 0.5,
                "precondition: the ally really is below half (" + ally.getCurrentHp() / ally.getMaxHp() + ")");

        double withTalent = hurtBy(battle, attacker, ally, readingInstance);
        ally.getBuffManager().removeState(GRIT);
        battle.fireTriggers(TriggerEvent.HEALED, ally, ally, 0, 0);
        Assertions.assertFalse(ally.getBuffManager().hasState(GRIT),
                "precondition: the reduction is off for the control reading");
        double without = hurtBy(battle, attacker, ally, readingInstance);

        System.out.println("[pearl_talent] one hit takes " + withTalent + " with the talent and " + without
                + " without it ; ratio = " + (withTalent / without));

        Assertions.assertTrue(without > 0, "the control hit must land (" + without + ")");
        // Note: The ratio, not the absolute numbers: the same attacker, the same victim and the same instance value
        // are used in both readings, so the damage zones cancel into the ratio. A reduction of -0.15 divides by
        // 1 - 0.15; a wrong magnitude (measured: -0.5) lands at 0.5 and is caught here.
        Assertions.assertEquals(1 / 1.15, withTalent / without, 0.02,
                "「受到的伤害降低 15%」: the hit is divided by 1 - 0.15, not by anything else");
    }

    /**
     * One settled hit of the given instance value by {@code attacker} on {@code victim}, returned as the HP lost.
     *
     * <p>Both readings pass the SAME instance value on purpose: the outcome is then a pure ratio, with the defence,
     * resistance and level zones cancelling - which is what makes "the reduction is 15%" assertable without
     * re-deriving the engine's whole damage formula in the test.
     */
    private static double hurtBy(Battle battle, Enemy attacker, Character victim, double instance) {
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - victim.getCurrentHp();
    }

    private static Battle newBattle() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
