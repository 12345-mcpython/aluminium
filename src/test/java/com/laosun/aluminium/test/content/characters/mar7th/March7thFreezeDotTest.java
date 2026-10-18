package com.laosun.aluminium.test.content.characters.mar7th;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1001 三月七's ultimate, second clause: 「冻结状态下，敌方目标不能行动同时**每回合开始时**受到等同于三月七
 * <b>60%</b>攻击力的冰属性<u>附加伤害</u>」.
 *
 * <h2>Why this judge exists</h2>
 * The clause has two halves and only the first was ever visible: the freeze itself ({@code APPLY_CONTROL}) is
 * pinned by the control suites, but "the state carries a per-turn ice hit" travels through a path nobody was
 * asserting - the rule hands {@code element} / {@code scale} / {@code percent_from_skill_param: ULTRA:3} to
 * {@code APPLY_CONTROL}, which turns them into a {@code DotBuff} attached by {@code ControlBuff}, which the
 * victim's own turn start then settles. Every one of those three hand-offs is silent when it breaks.
 *
 * <h2>What is asserted, and why in this shape</h2>
 * Not "the enemy lost HP" - a free-frozen enemy that takes a hit at the WRONG row would also lose HP. The
 * assertions are on the settled instance itself: exactly one {@code DOT} instance, its element is
 * {@code ICE}, and its <b>base</b> is {@code 0.6 x her ATK} - the row the clause names ({@code #4} of
 * {@code 100103}'s Lv10 line). The zones after the base (defence, resistance) are not asserted: they belong to
 * the engine's chain, not to this clause.
 */
public class March7thFreezeDotTest {

    private static final int MARCH_7TH = 1001;
    /** 「60% 攻击力」 - {@code ULTRA:3} on the Lv10 row, which is why the file's level convention is load-bearing. */
    private static final double SHARE = 0.6;

    @Test
    public void theFrozenEnemyTakesSixtyPercentOfHerAttackAtTheStartOfItsTurn() {
        Character march = CharacterFactory.create(MARCH_7TH, 80);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(march), List.of(enemy), alwaysRoll(0.0));

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        battle.startBattle();
        battle.processRequests();
        march.setCurrentEnergy(march.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(march, List.of(enemy)),
                "precondition: her ultimate went off (base chance 50%, dice forced to pass)");
        Assertions.assertTrue(enemy.getBuffManager().hasState("冻结"),
                "precondition: the freeze landed -- without it there is no per-turn payload to check");

        // The victim's own turn start is where the clause says the hit lands (the same tick as any DOT).
        settled.clear();
        battle.tickDots(enemy);

        List<Damage> dots = settled.stream().filter(damage -> damage.getType() == DamageType.DOT).toList();
        Assertions.assertEquals(1, dots.size(),
                "「每回合开始时受到…冰属性附加伤害」 is ONE instance per settlement; settled " + settled.size()
                        + " instance(s) in total: " + settled.stream().map(Damage::getType).toList());
        Damage dot = dots.getFirst();
        Assertions.assertEquals(DamageElement.ICE, dot.getElement(),
                "the per-turn hit is 冰属性 -- an element-less DOT is the silent failure this pins");
        double expected = SHARE * march.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(expected, dot.breakdown().getOrDefault("base", Double.NaN),
                Math.max(1e-6, expected * 1e-9),
                "「等同于三月七 60% 攻击力的冰属性附加伤害」: base = 0.6 x ATK (" + expected + ")");
    }

    /**
     * Without the freeze there is no per-turn hit: the payload rides the STATE, not the ultimate.
     */
    @Test
    public void aResistedFreezeCarriesNoPerTurnHit() {
        Character march = CharacterFactory.create(MARCH_7TH, 80);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(march), List.of(enemy), alwaysRoll(1.0));

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        battle.startBattle();
        battle.processRequests();
        march.setCurrentEnergy(march.getMaxEnergy());
        battle.castUltra(march, List.of(enemy));
        Assertions.assertFalse(enemy.getBuffManager().hasState("冻结"), "precondition: the roll failed, so no freeze");

        settled.clear();
        battle.tickDots(enemy);

        Assertions.assertTrue(settled.stream().noneMatch(damage -> damage.getType() == DamageType.DOT),
                "no state, no payload -- a per-turn hit here would be the ultimate's own AoE counted again");
    }

    /**
     * A dice that forces every roll: {@code 0.0} passes any positive chance, {@code 1.0} fails any chance below 1.
     */
    private static Random alwaysRoll(double value) {
        return new Random(0L) {
            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
