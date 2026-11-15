package com.laosun.aluminium.test.content.characters.kafka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1005 卡芙卡's Eidolons 2, 3, 4 and 5: the party-wide DoT boost (which leaves with her), the two level bumps, and the energy
 * a Shock tick refunds.
 *
 * <p>E4: 「当敌方目标承受的由卡芙卡造成的dot:shock状态产生伤害时，额外为卡芙卡恢复 2 点能量」. The load-bearing part is the guard:
 * {@code DAMAGE_SETTLED} fires for every settled instance, so without {@code damage_is_dot} her ordinary attacks would pay
 * the same 2 energy - which is what the second assertion checks.
 *
 * <p><b>L3.</b> Those two readings share one fixture (E4 Kafka, the standard dummy, a zero roll) and one observed object
 * (her energy), so they are one scenario test with an {@code Assertions.assertAll}: the Shock tick pays 2, and the ordinary
 * attack pays only its own 20. The E2 boost and the E3/E5 level bumps keep their own tests - different clauses, different
 * observations.
 */
public class KafkaEidolonTest {

    private static final int KAFKA = 1005;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void eidolonTwoRaisesEveryonesDotDamage() {
        double bare = allyDotBoost(0);
        double e2 = allyDotBoost(2);
        Assertions.assertEquals(0.25, e2 - bare, 1e-9, "「我方全体造成的持续伤害提高 25%」");
    }

    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E3: 普攻 +1");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E5: 天赋 +2");
    }

    /**
     * E4's Shock tick pays 2 energy; an ordinary attack pays only its own 20, because the refund is guarded by
     * {@code damage_is_dot}.
     */
    @Test
    public void aShockTickPaysTwoEnergyButAnOrdinaryAttackDoesNot() {
        double[] measured = run();
        System.out.println("[1005] energy before tick " + measured[0] + " ; after " + measured[1]);

        Character her = CharacterFactory.create(1005, 80, true, null, null, 4);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        double before = her.getCurrentEnergy();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(enemy));
        battle.processRequests();
        double ordinaryGain = her.getCurrentEnergy() - before;
        System.out.println("[1005] energy after an ordinary attack " + ordinaryGain);

        Assertions.assertAll("E4: the Shock tick pays 2 energy, and an ordinary attack's DoT pays nothing extra",
                () -> Assertions.assertEquals(2.0, measured[1] - measured[0], 1e-9, "「恢复 2 点能量」"),
                // control relaxed: a basic attack pays its own 20 energy; the point is the DoT adds nothing extra there.
                () -> Assertions.assertEquals(20.0, ordinaryGain, 1e-9, "an ordinary attack pays only its own energy"));
    }

    private static double allyDotBoost(int eidolon) {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return ally.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get();
    }

    private static Battle battle(int eidolon) {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    /** {energy before the tick, energy after it}. */
    private static double[] run() {
        Character her = CharacterFactory.create(1005, 80, true, null, null, 4);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        // Her SKILL only ticks existing DoTs (「立即产生 75%」); the SHOCK comes from the ULTIMATE.
        battle.castImmediate(her.getSkills().get(SkillType.ULTRA), her, List.of(enemy));
        battle.processRequests();
        System.out.println("[1005] shocked=" + enemy.getBuffManager().hasState("dot:shock"));
        double before = her.getCurrentEnergy();
        battle.tickDots(enemy);
        battle.processRequests();
        return new double[]{before, her.getCurrentEnergy()};
    }
}
