package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1220 Feixiao (飞霄)'s ultimate branches: "if the target is in the weakness-broken state, the damage multiplier is increased by 30%" and "if the target is not in the weakness-broken state, the damage multiplier is increased by 30%".
 *
 * <p>`BOOST_DAMAGE` boosts the DAMAGE INSTANCE, so the judgement is a damage number: measured on a 900k victim with a no-crit Random. Three arms --
 * the character with a synthetic level-only table (D0), the full content with the enemy NOT broken (D1, the negated branch) and with the enemy broken
 * (D2, the mirror branch, broken through the engine with the enemy's own declared weakness).
 *
 * <p>Note: One caveat stated rather than hidden: the control arm replaces her table, so it also drops any other rule of hers that might touch the
 * ultimate's damage. If the ratio comes out above 1.3, that is why -- and the assertion's message carries the measured numbers.
 */
public class FeixiaoBranchTest {
    private static final int FEIXIAO = 1220;
    private static final int LEVEL = 80;
    private static final int VICTIM = 1002011;

    @Test
    public void bothBranchesRaiseTheDamageAndNeitherAppliesTwice() {
        double control = damage(false, false);
        double unbroken = damage(true, false);
        double broken = damage(true, true);
        Assertions.assertTrue(control > 0, "precondition: the ultimate landed");
        Assertions.assertTrue(unbroken > control, "the unbroken branch fired: " + control + " -> " + unbroken);
        Assertions.assertEquals(unbroken, broken, Math.max(1.0, 0.02 * unbroken),
                "either branch gives the same +30%: unbroken " + unbroken + " vs broken " + broken);
        Assertions.assertTrue(unbroken / control > 1.15 && unbroken / control < 1.6,
                "the boost is in the +30% band (control " + control + ", unbroken " + unbroken + ", ratio "
                        + (unbroken / control) + ")");
    }

    private static double damage(boolean fullContent, boolean broken) {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        if (!fullContent) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "RAISE_SKILL_LEVEL");
            TriggerSpecs.set(raise, "skill", "ULTRA");
            TriggerSpecs.set(raise, "amount", 9.0);
            feixiao.setTriggerTable(new TriggerTable(9970, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of(), raise))));
        }
        Enemy victim = EnemyFactory.create(VICTIM, 90, 1);
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(feixiao), List.of(victim), noCrit);
        battle.startBattle();
        if (broken) {
            battle.reduceToughness(feixiao, victim, victim.getStanceWeak().iterator().next(), 999);
            Assertions.assertTrue(victim.getBuffManager().hasState("弱点击破"), "precondition: the target is broken");
        }
        double before = victim.getCurrentHp();
        battle.castImmediate(feixiao.getSkills().get(SkillType.ULTRA), feixiao, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        return dealt;
    }
}
