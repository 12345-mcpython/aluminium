package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The third audit batch of light cones (20012-20017), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>What this batch found.</b> Four of the six (20013 / 20014 / 20015 / 20017) had <b>no condition at all</b> while
 * every one of the sentences says 「使<b>装备者</b>…」 - and the engine fires every event at every ally's table, so a
 * TEAMMATE's skill cast, kill, basic or break paid the wearer. Each case here reads both sides.
 *
 * <p>⚠ <b>20012 is the batch's one registered gap.</b> Its sentence has two halves - 「施放攻击**或**受到攻击后」 - one needing
 * `actor == self` and the other `target == self`. Conditions are ANDed (there is no "or"), and splitting the rule in two would
 * double its `per_turn: 1` limit (the sentence says once per turn). The expressible half is shipped and the other is
 * registered; the case below pins today's reading, so it goes red when the capability lands.
 */
public class ConeAuditBatchThreeTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 20012 轮契: her own attack pays +4 energy, once per turn; the 「受到攻击」 half is registered (see the class note), so
     * being hit pays nothing today - and that half is the witness.
     */
    @Test
    public void coneTwoThousandTwelvePaysOncePerTurnForHerOwnAttack() {
        Fixture f = new Fixture(20012, true);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, f.wearer, enemy, 0, 0));
        double first = f.wearer.getCurrentEnergy();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, f.wearer, enemy, 0, 0));
        double second = f.wearer.getCurrentEnergy();
        System.out.println("[20012] 自己第一发后 " + first + " ; 同一回合第二发后 " + second);
        Assertions.assertEquals(4.0, first, 1e-9, "叠影 1 的 #1 = 4");
        Assertions.assertEquals(4.0, second, 1e-9, "「单个回合内不可重复触发」—— 计数器跨事件共享");

        // ⚠ A FRESH battle for the teammate reading: `per_turn: 1` would otherwise suppress it, and then the case would pass
        // even with the gate broken (measured: it did, until this moved into its own fixture).
        Fixture other = new Fixture(20012, true);
        other.wearer.setCurrentEnergy(0);
        other.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, other.ally, other.battle.enemyUnits().getFirst(), 0, 0));
        double teammate = other.wearer.getCurrentEnergy();
        System.out.println("[20012] 队友施放战技后 " + teammate);
        Assertions.assertEquals(0.0, teammate, 1e-9, "「使**装备者**施放攻击」—— 队友的不算");

        // ⚠ Fired as an EVENT, not through `applyDamage`: taking a hit also credits the engine's own hit energy (+10), which
        // would hide what this cone does or does not add.
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, f.wearer, 0, 0));
        System.out.println("[20012] 单发 `TAKING_HIT` 之后的能量 " + f.wearer.getCurrentEnergy()
                + "（「受到攻击」那一半仍未表达 —— 已登记 §4.3）");
        Assertions.assertEquals(0.0, f.wearer.getCurrentEnergy(), 1e-9,
                "今天的行为：被攻击不给能量（缺口见证，不是最终样子）");
    }

    /**
     * 20013 灵钥: her own SKILL cast pays +8, once per turn; a teammate's cast pays nothing.
     */
    @Test
    public void coneTwoThousandThirteenPaysForHerOwnSkillCast() {
        Fixture f = new Fixture(20013, true);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, f.wearer, enemy, 0, 0));
        double first = f.wearer.getCurrentEnergy();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, f.wearer, enemy, 0, 0));
        double second = f.wearer.getCurrentEnergy();
        // ⚠ Fresh battle for the teammate half, for the same reason as 20012: the per-turn limit would hide a broken gate.
        Fixture other = new Fixture(20013, true);
        other.wearer.setCurrentEnergy(0);
        other.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, other.ally, other.battle.enemyUnits().getFirst(), 0, 0));
        double teammate = other.wearer.getCurrentEnergy();
        System.out.println("[20013] 自己施放战技 " + first + " ; 同回合再来一次 " + second + " ; 队友施放后 " + teammate);
        Assertions.assertEquals(8.0, first, 1e-9, "叠影 1 的 #1 = 8");
        Assertions.assertEquals(8.0, second, 1e-9, "「单个回合内不可重复触发」");
        Assertions.assertEquals(0.0, teammate, 1e-9, "「使**装备者**施放战技后」—— 队友施放不给装备者能量（ctor == self）");
    }

    /**
     * 20014 相抗: her own kill raises SPEED +10%; a teammate's kill does not.
     */
    @Test
    public void coneTwoThousandFourteenSpeedsUpOnlyAfterHerOwnKill() {
        double own = speedAfterKill(true);
        double teammate = speedAfterKill(false);
        System.out.println("[20014] 自己击杀后速度增量 " + own + " ; 队友击杀后 " + teammate);
        Assertions.assertTrue(own > 0, "「消灭敌方目标后，速度提高 10%」");
        Assertions.assertEquals(0.0, teammate, 1e-9, "「使**装备者**消灭」—— 队友的击杀不算");
    }

    /**
     * 20015 蕃息: after her own basic attack, her NEXT action comes 12% earlier.
     */
    @Test
    public void coneTwoThousandFifteenAdvancesTheNextAction() {
        double withCone = remainingAfterBasic(20015);
        double bare = remainingAfterBasic(null);
        System.out.println("[20015] 普攻后的剩余行动值: 有锥 " + withCone + " ; 无锥 " + bare
                + " ; 比值 " + (withCone / bare));
        Assertions.assertEquals(0.88, withCone / bare, 1e-6, "「下一次行动提前 12%」（叠影 1）");
    }

    /**
     * 20016 俱殁: below 80% HP she carries +12% crit chance, and above it she does not.
     */
    @Test
    public void coneTwoThousandSixteenCritsBelowTheThreshold() {
        double below = critInsideTurn(20016, 0.6);
        double above = critInsideTurn(20016, 1.0);
        double bareBelow = critInsideTurn(null, 0.6);
        System.out.println("[20016] 血量 60% 时的暴击率增量 " + below + " ; 满血时 " + above
                + " ; 无锥且 60% 血 " + bareBelow);
        Assertions.assertEquals(0.12, below - bareBelow, 1e-9, "「小于 80% 时，暴击率提高 12%」（叠影 1）");
        Assertions.assertEquals(0.0, above - bareBelow, 1e-9, "满血不触发");

        // The registered half: hurt INSIDE a turn, and the boost does NOT land until her next turn start (the witness).
        Fixture midTurn = new Fixture(20016, false);
        double beforeMidTurn = midTurn.wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        midTurn.battle.applyDamage(midTurn.wearer, new Damage(midTurn.battle.enemyUnits().getFirst(),
                midTurn.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, midTurn.wearer.getMaxHp() * 0.3));
        midTurn.battle.processRequests();
        double afterMidTurn = midTurn.wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        System.out.println("[20016] 回合中途掉到 70% 血: 之前 " + beforeMidTurn + " ; 之后 " + afterMidTurn
                + "（今天要等到她下一个回合开始 —— 已登记 §4.3）");
        Assertions.assertEquals(0.0, afterMidTurn - beforeMidTurn, 1e-9,
                "缺口见证：掉血当回合不生效（不是期望的最终样子）");
    }

    /**
     * 20017 开疆: her own weakness break heals 12% of her own max HP; a teammate's break heals nothing.
     */
    @Test
    public void coneTwoThousandSeventeenHealsOnHerOwnBreak() {
        double own = healAfterBreak(true);
        double teammate = healAfterBreak(false);
        System.out.println("[20017] 自己击破后回复 " + own + " ; 队友击破后 " + teammate);
        Assertions.assertTrue(own > 0, "「击破敌方目标的弱点时，回复自身生命上限 12%」");
        Assertions.assertEquals(0.0, teammate, 1e-9, "「当**装备者**击破」—— 队友的击破不算");
    }

    // ==================================================================

    private static double speedAfterKill(boolean wearerKills) {
        Fixture f = new Fixture(20014, true);
        double before = f.wearer.getAttribute(AttributeType.SPEED).get();
        Enemy victim = f.battle.enemyUnits().getFirst();
        Character killer = wearerKills ? f.wearer : f.ally;
        f.battle.applyDamage(victim, new Damage(killer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
        return f.wearer.getAttribute(AttributeType.SPEED).get() - before;
    }

    private static double remainingAfterBasic(Integer cone) {
        Fixture f = new Fixture(cone, false);
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        Signal signal = f.battle.queue().snapshot().stream()
                .filter(s -> s.getCanHit() == f.wearer)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for the wearer"));
        return f.battle.queue().getTimeRemaining(signal);
    }

    private static double healAfterBreak(boolean wearerBreaks) {
        Fixture f = new Fixture(20017, true);
        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.6);
        double before = f.wearer.getCurrentHp();
        Enemy victim = f.battle.enemyUnits().getFirst();
        Character breaker = wearerBreaks ? f.wearer : f.ally;
        // ⚠ `applyDamage` does not empty a stance bar; the clause is about the BREAK event, so the fixture fires it.
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, breaker, victim, 0, 0));
        f.battle.processRequests();
        return f.wearer.getCurrentHp() - before;
    }

    /**
     * The crit chance inside the wearer's own turn, at a given HP fraction (a `turns: 1` modifier on a {@code TURN_START}
     * rule is alive only during that turn - measured in batch one).
     */
    private static double critInsideTurn(Integer cone, double hpFraction) {
        Fixture f = new Fixture(cone, false);
        f.wearer.consumeHp(f.wearer.getMaxHp() * (1 - hpFraction));
        for (int step = 0; step < 40; step++) {
            f.battle.stepForward();
            boolean mine = f.battle.queue().getCurrentActor().getCanHit() == f.wearer;
            if (mine) {
                f.battle.beforeMove();
                double crit = f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
                f.battle.afterMove();
                return crit;
            }
            f.battle.afterMove();
        }
        throw new AssertionError("the wearer never got a turn");
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }
    }
}
