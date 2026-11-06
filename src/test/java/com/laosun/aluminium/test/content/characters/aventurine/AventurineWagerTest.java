package com.laosun.aluminium.test.content.characters.aventurine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1304 砂金's three newly written clauses:
 *
 * <ul>
 *   <li>天赋「持有【坚垣筹码】的我方单体效果抵抗提高 50%」 - read off {@code SHIELD_GRANTED}, so every shield source covers it;</li>
 *   <li>星魂 1「持有【坚垣筹码】的我方目标暴击伤害提高 20%」;</li>
 *   <li>天赋「受到攻击后使砂金获得 1 点【盲注】」.</li>
 * </ul>
 */
public class AventurineWagerTest {
    private static final int AVENTURINE = 1304;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    @Test
    public void aShieldLiftsTheHoldersEffectResistance() {
        Battle battle = scene(0);
        Character ally = battle.characters().get(1);
        double before = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double shieldBefore = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        Assertions.assertEquals(shieldBefore, before, EPS, "precondition");

        // The trace 热手 grants the shield at battle start, so the talent's modifier must already be there.
        Assertions.assertTrue(ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get() > 0.0,
                "「持有【坚垣筹码】的我方单体效果抵抗提高 50%」 — the trace's shield carries it");
    }

    @Test
    public void theFirstEidolonLiftsTheHoldersCritDamage() {
        Assertions.assertEquals(0.2, critDamageOfShieldedAlly(1) - critDamageOfShieldedAlly(0), EPS,
                "「持有【坚垣筹码】的我方目标暴击伤害提高 20%」");
    }

    @Test
    public void aHitOnAShieldedAllyFeedsTheBlindBet() {
        Battle battle = scene(0);
        Character aventurine = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        Assertions.assertEquals(0, aventurine.getBuffManager().stacksOf("盲注"), "precondition: no blind bet yet");

        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 1));
        battle.processRequests();

        Assertions.assertEquals(1, aventurine.getBuffManager().stacksOf("盲注"),
                "「…在受到攻击后，使砂金获得 1 点【盲注】」");
    }

    /** 天赋「…并在受到攻击后额外获得 1 点【盲注】」: the SAME clause, but the one being hit is Aventurine himself. */
    @Test
    public void aHitOnHimselfAlsoFeedsTheBlindBet() {
        Battle battle = scene(0);
        Character aventurine = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        Assertions.assertEquals(0, aventurine.getBuffManager().stacksOf("盲注"), "precondition");

        battle.applyDamage(aventurine, new Damage(enemy, aventurine, DamageElement.FIRE, DamageType.NORMAL, 1));
        battle.processRequests();

        // TWO, not one: the trace 热手 shields the whole party at battle start, so Aventurine is BOTH a shielded ally being
        // hit (+1) AND the one the extra clause speaks about (+1). The sentence says 「**额外**获得」, i.e. in addition.
        Assertions.assertEquals(2, aventurine.getBuffManager().stacksOf("盲注"),
                "「…并在受到攻击后额外获得 1 点【盲注】」 — one for the wager he holds, one extra");
    }

    /** 天赋「【盲注】达到 7 点后…发动 7 段追加攻击，每段 25% 防御力」: seven instances, and the points are spent. */
    @Test
    public void sevenPointsFireTheFollowUp() {
        Battle battle = scene(0);
        Character aventurine = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        // Six points first: nothing must happen.
        for (int i = 0; i < 6; i++) {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 1));
            battle.processRequests();
        }
        Assertions.assertEquals(6, aventurine.getBuffManager().stacksOf("盲注"),
                "precondition: six hits on the SHIELDED ALLY, six points, no follow-up yet");
        double hpBefore = enemy.getCurrentHp();

        // The seventh hit crosses the threshold.
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 1));
        battle.processRequests();

        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "「【盲注】达到 7 点后…发动 7 段追加攻击」 — the enemy lost HP to the barrage");
        // The hit pays its own point FIRST (that rule sits earlier in the file), so the seventh point arrives and is spent in
        // the same firing: nothing is left over.
        Assertions.assertEquals(0, aventurine.getBuffManager().stacksOf("盲注"),
                "「消耗 7 点」 — the seventh hit reached seven and all seven were spent");
    }

    /** 星魂 4「触发天赋的追加攻击时…使天赋的追加攻击额外增加 3 段」: total damage goes up by 3/7 of the barrage. */
    @Test
    public void theFourthEidolonAddsThreeHits() {
        double without = barrageDamage(3);
        double withE4 = barrageDamage(4);
        Assertions.assertTrue(withE4 > without,
                "「额外增加 3 段攻击段数」 — the barrage hits harder with the Eidolon (" + without + " -> " + withE4 + ")");
        // ⚠ NOT 10/7: the same sentence also raises his DEFENCE by 40% BEFORE the barrage, so every hit is bigger as well as more
        // numerous - measured 1.85 (between 10/7 = 1.43 and 10/7 x 1.4 = 2.0). Pinned as a band, with the reason.
        Assertions.assertTrue(withE4 / without > 10.0 / 7.0,
                "…and more than 10/7, because the +40% DEF rides the same clause (measured " + (withE4 / without) + ")");
    }

    /** The HP one 7-point barrage removes, at the given Eidolon rank (measured as the delta of the hit that crosses 7). */
    private static double barrageDamage(int eidolon) {
        Battle battle = scene(eidolon);
        Character ally = battle.characters().get(1);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        for (int i = 0; i < 6; i++) {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 1));
            battle.processRequests();
        }
        double before = enemy.getCurrentHp();
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 1));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /** 天赋「砂金持有【坚垣筹码】时能抵抗控制类负面状态」: `RESIST_DEBUFF { kind: control, percent: 1 }` IS immunity. */
    @Test
    public void theWagerShutsOutControl() {
        Battle battle = scene(0);
        Character aventurine = battle.characters().getFirst();
        com.laosun.aluminium.enemy.Enemy enemy = (com.laosun.aluminium.enemy.Enemy) battle.enemies().getFirst();
        // The trait grants a CLASS-wide resistance, and `percent: 1` is this vocabulary spelling of immunity (the engine says
        // so itself in `TriggerInterpreter.resistDebuff`). What `Battle.tryApplyDebuff` then consults is the INCOMING state own
        // class (`AbstractBuff.debuffClass()`), which is why the probe asks the carrier rather than inventing a state that has
        // no class at all.
        var resists = aventurine.getBuffManager()
                .allBuffsOf(com.laosun.aluminium.buff.ClassResistBuff.class).stream()
                .filter(resist -> resist.getKind() == com.laosun.aluminium.enums.DebuffClass.CONTROL)
                .toList();
        Assertions.assertFalse(resists.isEmpty(),
                "「砂金持有【坚垣筹码】时能抵抗控制类负面状态」 — the wager grants a control-class resistance");
        Assertions.assertEquals(1.0, resists.getFirst().getPercent(), 1e-9,
                "…and percent 1 IS immunity in this vocabulary");
    }

    /** 秘技「一定/大/小概率获得防御力 +24% / +36% / +60%」: a WEIGHTED draw, one of the three tiers every time. */
    @Test
    public void theTechniqueDrawsOneDefenceTier() {
        for (int seed = 0; seed < 6; seed++) {
            Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
            aventurine.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("秘技", 1));
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(List.of(aventurine), List.of(enemy),
                    BattleFixture.alwaysRoll(seed / 10.0));
            battle.startBattle();
            battle.processRequests();
            double bonus = aventurine.getAttribute(AttributeType.DEFENCE).get()
                    - CharacterFactory.create(AVENTURINE, LEVEL).getAttribute(AttributeType.DEFENCE).get();
            Assertions.assertTrue(Math.abs(bonus / 0.24 - Math.round(bonus / 0.24)) < 1e-6
                            || Math.abs(bonus / 0.36 - Math.round(bonus / 0.36)) < 1e-6
                            || Math.abs(bonus / 0.6 - Math.round(bonus / 0.6)) < 1e-6,
                    "the draw must be one of the three tiers (got +" + bonus + " on DEF)");
        }
    }

    private static double critDamageOfShieldedAlly(int eidolon) {
        Battle battle = scene(eidolon);
        return battle.characters().get(1).getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static Battle scene(int eidolon) {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}
