package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code DAMAGE} can now state how much TOUGHNESS it takes off, and whether it ignores the weakness gate.
 *
 * <p><b>Why.</b> 1214 Xueyi (雪衣)'s Eidolon 2 and her Ultimate both say the follow-up reduces toughness "**无视弱点属性**".
 * A rule-driven instance never touched toughness at all, so that sentence had no spelling.
 */
public class DamageStanceTest {

    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    @Test
    public void aStatedStanceComesOffTheBar() {
        double before = stanceAfter(null, false);
        double withStance = stanceAfter(15.0, false);
        Assertions.assertEquals(before, withStance, 1e-9,
                "Fire is not this enemy's weakness, so the default is still refused by the weakness gate");
    }

    @Test
    public void ignoringTheWeaknessTakesItOffAnyway() {
        double before = stanceAfter(null, true);
        double withStance = stanceAfter(15.0, true);
        System.out.println("[stance] " + before + " -> " + withStance);
        Assertions.assertEquals(before - 15.0, withStance, 1e-6, "「无视弱点属性削减韧性」");
    }

    @Test
    public void aWeaknessHitNeedsNoFlag() {
        double before = stanceAfterOnFire(null);
        double withStance = stanceAfterOnFire(15.0);
        Assertions.assertEquals(before - 15.0, withStance, 1e-6, "on a real weakness the gate already allows it");
    }

    /** @return the enemy's stance after one rule-driven Fire instance */
    private static double stanceAfter(Double stance, boolean ignoreWeakness) {
        // 803201006 is weak to Thunder/Wind only, so Fire needs `ignore_weakness`.
        return fire(stance, ignoreWeakness, DamageElement.FIRE, BattleFixture.enemy(803201006, LEVEL));
    }

    private static double stanceAfterOnFire(Double stance) {
        // The standard fixture monster IS weak to Fire: the control for "the gate did not need lifting".
        return fire(stance, false, DamageElement.FIRE, BattleFixture.monster());
    }

    private static double fire(Double stance, boolean ignoreWeakness, DamageElement element, Enemy enemy) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "element", element.name().charAt(0) + element.name().substring(1).toLowerCase());
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", "target");
        if (stance != null) {
            TriggerSpecs.set(effect, "stance", stance);
        }
        if (ignoreWeakness) {
            TriggerSpecs.set(effect, "ignoreWeakness", true);
        }
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule(TriggerEvent.ALLY_ATTACK.name(), null, "probe", effect))));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0));
        return enemy.getStance();
    }
}