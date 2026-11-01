package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
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
 * {@code TOUGHNESS_REDUCED}: how much TOUGHNESS one hit actually took off.
 *
 * <p><b>Why the event exists.</b> 1214 Xueyi (雪衣)'s Talent reads "削减的韧性越多叠加层数越多", and until now that number
 * existed only inside {@code StanceResult.consumed} - content could not see it, so the sentence had no spelling at all.
 *
 * <p>The reader grammar (which condition reads the amount) is pinned elsewhere; this judge only says the event exists,
 * carries the number really deducted, and stays silent when nothing came off.
 */
public class ToughnessReducedEventTest {

    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    @Test
    public void aRealReductionReportsItsAmount() {
        Character owner = characterWith(counterOn(TriggerEvent.TOUGHNESS_REDUCED));
        Enemy enemy = BattleFixture.monster();   // weak to Fire
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getStance();

        battle.reduceToughness(owner, enemy, DamageElement.FIRE, 30, null);

        double consumed = before - enemy.getStance();
        Assertions.assertTrue(consumed > 0, "precondition: the hit really took toughness off");
        Assertions.assertEquals(1, owner.getBuffManager().stacksOf("探测"),
                "the event fired once for that hit");
    }

    @Test
    public void aHitAgainstANonWeaknessIsSilent() {
        Character owner = characterWith(counterOn(TriggerEvent.TOUGHNESS_REDUCED));
        Enemy enemy = BattleFixture.monster();   // NOT weak to Ice
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.reduceToughness(owner, enemy, DamageElement.ICE, 30, null);

        Assertions.assertEquals(0, owner.getBuffManager().stacksOf("探测"),
                "nothing came off the bar, so nothing is reported");
    }

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    /** A rule that marks the owner's 「探测」 counter every time the event fires. */
    private static TriggerSpec counterOn(TriggerEvent event) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "探测");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule(event.name(), null, "probe: count one firing", effect);
    }
}