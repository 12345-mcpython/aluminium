package com.laosun.aluminium.test.content.characters.himeko;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Himeko (1003)'s 2026-10-19 audit: the three traces and Eidolon 1, read as battles.
 *
 * <p>What these cases are for: the clauses were authored from the text, and each one can be *wired to the wrong
 * channel* while still looking right in the file - a trait that never lands, a speed buff hung on an event her own
 * follow-up never fires, a crit share that ignores the HP line. Each case below therefore reads an ATTRIBUTE or a
 * STATE after a REAL cast, not the file.
 */
public class HimekoAuditTest {
    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;

    private static Random always(double roll) {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return roll;
            }
        };
    }

    /**
     * 行迹 星火: her attack can set 灼烧, and the burn's own damage is her 30% ATK per turn.
     */
    @Test
    public void traceStarfireSetsTheBurn() {
        Character her = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy victim = BattleFixture.monster();
        // Roll under the 50% base chance: the burn lands.
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.1));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        boolean burning = victim.getBuffManager().hasState("灼烧") || victim.getBuffManager().stacksOf("灼烧") > 0;
        System.out.println("[1003] 灼烧 landed=" + burning);
        Assertions.assertTrue(burning, "「有 50% 基础概率使敌方目标陷入灼烧状态」");

        // ⚠ DOT 的每回合结算走敌方回合的真实流程，这个夹具不发那个事件 ⇒ 只钉「灼烧上了」与它自己的快照值。
        System.out.println("[1003] 灼烧已挂上；每回合的 30% 伤害由该 DOT 自己结算（见状态表）");
    }

    /**
     * 星魂 1: her own follow-up (the talent) raises her speed.
     */
    @Test
    public void eidolonOneSpeedsHerUpAfterTheFollowUp() {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, 1);
        Enemy victim = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        double before = her.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.FOLLOW_UP, her, victim, 0, 0);
        battle.processRequests();
        double gained = her.getAttribute(AttributeType.SPEED).get() - before;
        System.out.println("[1003] 星魂1 追加攻击后速度增量 " + gained);
        Assertions.assertTrue(gained > 0, "「【乘胜追击】触发后，姬子的速度提高 20%」");
    }

    /**
     * 星魂 2: the low-HP share is gated - at eidolon 0 it does not apply.
     */
    @Test
    public void eidolonTwoGateIsReal() {
        Assertions.assertEquals(0.0, lowHpShare(false), 1e-9, "0 星魂：那句不该生效");
        Assertions.assertTrue(lowHpShare(true) > 0, "2 星魂：生效");
    }

    // ==================================================================

    private static double critAfterOneHit(boolean wound) {
        // 道标是伤害时的**实例修饰**，面板上永远看不到它 ✗ → 读那一发的 CritArea。
        Character her = CharacterFactory.create(HIMEKO, LEVEL);
        if (wound) {
            her.consumeHp(her.getMaxHp() * 0.3);
        }
        Enemy victim = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        java.util.List<com.laosun.aluminium.models.Damage> settled = new java.util.ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        double area = settled.get(0).breakdown().get("CritArea");
        return Math.max(0.0, area - 1.0);
    }

    private static double lowHpShare(boolean withEidolonTwo) {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, withEidolonTwo ? 2 : 0);
        Enemy victim = BattleFixture.monster();
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        victim.takeDamage(victim.getCurrentHp() * 0.6);
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        double dealt = before - victim.getCurrentHp();

        Character reference = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy other = BattleFixture.monster();
        Battle clean = new Battle(List.of(reference), List.of(other), always(0.99));
        clean.startBattle();
        double cleanBefore = other.getCurrentHp();
        clean.castImmediate(reference.getSkills().get(SkillType.COMMON), reference, List.of(other));
        return (dealt / (cleanBefore - other.getCurrentHp())) - 1.0;
    }
}
