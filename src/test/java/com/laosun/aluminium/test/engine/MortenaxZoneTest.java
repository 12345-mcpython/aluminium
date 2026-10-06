package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "结界持续期间..." - the zone's clock is a COUNTDOWN, so its effects last exactly as long as that unit is alive.
 *
 * <p>The sentence (Traces (行迹), Soul, Tempered ad Mortem (千锻魂)) has three effects and the upstream modifier writes exactly three properties
 * (AggroAddedRatio + AllDamageReduce + HealTakenRatio, from the same row whose ParamList is [10, 0.5, 0.5]). All three are
 * applied named at the Ultimate and taken off on the countdown's turn, which is what "the zone is dispelled when the countdown turn begins" says.
 */
public class MortenaxZoneTest {
    private static final int MORTENAX = 1507;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    @Test
    public void theZoneGrantsThreeEffectsAndTheCountdownTakesThemAllOff() {
        Character mortenax = CharacterFactory.create(MORTENAX, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(mortenax, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double baseWeight = battle.aggroOf(mortenax);
        double baseHeal = battle.calculateHeal(ally, mortenax, 1000);

        battle.castImmediate(mortenax.getSkills().get(SkillType.ULTRA), mortenax, List.of(enemy));

        Assertions.assertEquals(baseWeight * 11.0, battle.aggroOf(mortenax), EPS,
                "「被敌方攻击的概率提高」 (is more likely to be attacked by enemies) -- the unclaimed 10 reads as x (1 + 10)");
        Assertions.assertEquals(1, mortenax.getBuffManager().countBuffs(ReductionBuff.class),
                "\"DMG taken is reduced by 50%\" (「自身受到的伤害降低50%」)");
        Assertions.assertEquals(baseHeal * 1.5, battle.calculateHeal(ally, mortenax, 1000), EPS,
                "\"healing received is raised by 50%\" (「受到的治疗量提高50%」)");

        battle.fireTriggers(TriggerEvent.COUNTDOWN_TURN, battle.countdownsOf(mortenax).getFirst(), mortenax, 0, 0);

        Assertions.assertEquals(baseWeight, battle.aggroOf(mortenax), EPS,
                "「倒计时回合开始时结界解除」 (the zone is removed when the countdown's turn starts) -- the weight goes back");
        Assertions.assertEquals(0, mortenax.getBuffManager().countBuffs(ReductionBuff.class),
                "and the reduction comes off with it");
        Assertions.assertEquals(baseHeal, battle.calculateHeal(ally, mortenax, 1000), EPS,
                "and so does the healing taken bonus");
    }
    /**
     * Eidolon 1 (星魂 1): the zone also lowers enemies' resistance, and the countdown takes that off with everything else.
     *
     * <p>Three ways, because the gate is half the clause: rank 1 states 0.2 on every enemy; the countdown removes it; and
     * rank 0 must not state it at all.
     */
    @Test
    public void herFirstEidolonLowersEnemyResistanceForTheZonesLifetime() {
        Character withEidolon = CharacterFactory.create(MORTENAX, LEVEL, true, null, null, 1);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withEidolon), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.castImmediate(withEidolon.getSkills().get(SkillType.ULTRA), withEidolon, List.of(enemy));

        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "\"while the zone lasts, all enemies get 20% All-Type RES Reduction\" (「结界持续期间，使敌方全体全属性抗性降低20%」)");

        battle.fireTriggers(TriggerEvent.COUNTDOWN_TURN, battle.countdownsOf(withEidolon).getFirst(), withEidolon, 0, 0);

        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "「倒计时回合开始时结界解除」 (the zone is removed when the countdown's turn starts) -- the reduction goes with the zone");

        Character withoutEidolon = CharacterFactory.create(MORTENAX, LEVEL);
        Enemy plain = EnemyFactory.create(MONSTER, 90, 1);
        Battle second = new Battle(List.of(withoutEidolon), List.of(plain), new Random(0));
        second.startBattle();

        second.castImmediate(withoutEidolon.getSkills().get(SkillType.ULTRA), withoutEidolon, List.of(plain));

        Assertions.assertEquals(0.0, plain.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "Eidolon (星魂) 1 is the gate: without it the clause must not fire");
    }
}
