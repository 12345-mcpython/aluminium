package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Dot;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P4-5 acceptance: 击破 DOT（先上先结算、按回合开始结算、走乘区但不可暴击）。
 *
 * <p>锚点：防 100 的靶子、Lv80 攻击者（防御区 = 1000/1100）、无抗性无增伤
 * → 一发 base=500 的 DOT 结算 500 × 1000/1100 ≈ 454.55。
 */
public class DotTest {
    private static final double EPS = 1e-6;

    @Test
    public void dotTicksAtTurnStartAndExpiresAfterItsTurns() {
        Character source = character("source", 0.0);
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), new Random(0));
        dummy.addDot(new Dot(source, DamageElement.FIRE, 500, 2));

        double expected = 500 * 1000.0 / (100 + 1000.0);
        double first = battle.tickDots(dummy);
        Assertions.assertEquals(expected, first, 1e-6);
        Assertions.assertEquals(1, dummy.getDots().size(), "还剩 1 次");

        double second = battle.tickDots(dummy);
        Assertions.assertEquals(expected, second, 1e-6);
        Assertions.assertTrue(dummy.getDots().isEmpty(), "结算完最后一次就移除");

        Assertions.assertEquals(0, battle.tickDots(dummy), EPS, "没有 DOT 了 → 0");
        Assertions.assertEquals(dummy.getMaxHp() - 2 * expected, dummy.getCurrentHp(), 1e-6);
    }

    @Test
    public void dotsSettleInApplicationOrder() {
        Character source = character("source", 0.0);
        List<DamageElement> seen = new ArrayList<>();
        Enemy recorder = recorder(100_000, 100, 100, seen);
        Battle battle = new Battle(List.of(source), List.of(recorder), new Random(0));
        recorder.addDot(new Dot(source, DamageElement.THUNDER, 100, 1));
        recorder.addDot(new Dot(source, DamageElement.FIRE, 100, 1));

        battle.tickDots(recorder);

        Assertions.assertEquals(List.of(DamageElement.THUNDER, DamageElement.FIRE), seen, "先上先结算");
    }

    @Test
    public void dotIsBoostedButNeverCrits() {
        Character source = character("source", 1.0);          // 增伤 +100%
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), new Random(0));
        dummy.addDot(new Dot(source, DamageElement.FIRE, 500, 1));

        double settled = battle.tickDots(dummy);

        Assertions.assertEquals(500 * 2 * 1000.0 / (100 + 1000.0), settled, 1e-6, "DOT 吃增伤");
        Assertions.assertFalse(DamageType.DOT.isCrittable());
        Assertions.assertTrue(DamageType.DOT.isBoostable());
    }

    @Test
    public void breakingWithFireAttachesABurn() {
        Character himeko = character("himeko", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);   // 弱火，韧性 60
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // 削空 → 击破

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(1, iceEdge.getDots().size());
        Dot dot = iceEdge.getDots().getFirst();
        Assertions.assertEquals(DamageElement.FIRE, dot.getElement());
        Assertions.assertEquals(himeko, dot.getSource());
        Assertions.assertEquals(Constant.DOT_TURNS, dot.getRemainingTurns());
        Assertions.assertEquals(376.75535 * Constant.DOT_RATIO, dot.getBaseDamage(), 1e-6);
    }

    @Test
    public void breakingWithAFrozenElementAttachesNoDot() {
        Character mar7th = character("mar7th", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        iceEdge.setStanceWeak(Set.of(DamageElement.ICE));      // 临时改成弱冰，好让冰属性打出击破
        Battle battle = new Battle(List.of(mar7th), List.of(iceEdge), new Random(0));

        for (int i = 0; i < 2; i++) {
            battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));
        }

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(DamageElement.ICE, iceEdge.getBrokenElement());
        Assertions.assertTrue(iceEdge.getDots().isEmpty(), "冰=冻结，不是 DOT");
    }

    @Test
    public void dotTicksThroughBattleWhenTheEnemyTurnStarts() {
        Character hero = character("hero", 0.0);                        // 速度 100 → 周期 100
        Enemy fast = dummy(100_000, 100, 200);                          // 速度 200 → 周期 50，先动
        Battle battle = new Battle(List.of(hero), List.of(fast), new Random(0));
        fast.addDot(new Dot(hero, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        Assertions.assertSame(fast, battle.queue.getCurrentActor().getCanHit());
        battle.beforeMove();

        Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, fast.getCurrentHp(), 1e-6,
                "敌人回合开始时 DOT 自动结算");
    }

    private static Character character(String name, double boost) {
        Character c = Character.fromAttributes(name, 10_000, 100, 100, 100);
        c.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(boost));
        return c;
    }

    private static Enemy dummy(double hp, double defence, double speed) {
        return Enemy.fromAttributes("dummy", hp, defence, 100, speed);
    }

    /** 记录 onDamage 收到的元素顺序，用来断言"先上先结算"。 */
    private static Enemy recorder(double hp, double defence, double speed, List<DamageElement> seen) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, hp)
                .setBase(AttributeType.DEFENCE, defence)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, speed);
        return new Enemy("recorder", builder.build()) {
            @Override
            public void onDamage(Battle battle, com.laosun.aluminium.models.Damage damage) {
                seen.add(damage.getElement());
                super.onDamage(battle, damage);
            }
        };
    }
}
