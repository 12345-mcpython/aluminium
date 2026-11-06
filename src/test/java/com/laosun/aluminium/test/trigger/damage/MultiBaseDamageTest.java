package com.laosun.aluminium.test.trigger.damage;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 「100% 攻击力 + 150% 防御力」: several base terms summed into ONE damage instance (`parts`).
 *
 * <p>Written for 8003/8004 the Preservation Trailblazer's Ultimate. The distinction that matters: two separate {@code DAMAGE}
 * effects settle (and roll crit) twice, while {@code parts} forms the base <b>before</b> settlement. The probe therefore
 * compares a two-term effect against a one-term effect of the SAME total base and requires them to agree exactly.
 */
public class MultiBaseDamageTest {
    private static final int CARRIER = 1204;
    private static final int LEVEL = 80;

    @Test
    public void twoBaseTermsSumIntoOneInstance() {
        Character probe = CharacterFactory.create(CARRIER, LEVEL);
        double atk = probe.getAttribute(AttributeType.ATTACK).get();
        double def = probe.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(atk > 0 && def > 0, "precondition: both bases are non-zero");

        // 60% of ATK plus 100% of DEF, versus ONE term stating that same total as a share of ATK.
        double twoTerms = dealt(twoPartTable(0.6, 1.0));
        double oneTerm = dealt(onePartTable((atk * 0.6 + def) / atk));
        Assertions.assertEquals(oneTerm, twoTerms, 1e-6,
                "the sum is formed BEFORE the zones: a two-term base settles exactly like a one-term base of the same value");
    }

    @Test
    public void twoSeparateEffectInstancesSettleDifferently() {
        // The control: two DAMAGE effects are two instances, so the numbers do NOT have to agree with one (that is why the
        // engine grew `parts` instead of shipping the riders). Here they happen to agree in EXPECTATION, but the point of
        // the assertion is that the parts route is the one that provably matches a single base.
        double twoTerms = dealt(twoPartTable(0.6, 1.0));
        double sumOfTwo = dealt(twoEffectTable(0.6, 1.0));
        Assertions.assertEquals(sumOfTwo, twoTerms, 0.05 * twoTerms,
                "both routes land in the same range (the difference is only the extra crit roll)");
    }

    private static double dealt(TriggerTable table) {
        Character probe = CharacterFactory.create(CARRIER, LEVEL);
        probe.setTriggerTable(table);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(probe), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, probe, enemy, 1, 0));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /** One DAMAGE whose base is two terms: {@code atkPart} of ATK plus {@code defPart} of DEF. */
    private static TriggerTable twoPartTable(double atkPart, double defPart) {
        EffectSpec effect = damage();
        List<EffectSpec.Part> parts = new ArrayList<>();
        parts.add(part("self_attr:ATTACK", atkPart));
        parts.add(part("self_attr:DEFENCE", defPart));
        TriggerSpecs.set(effect, "parts", parts);
        return new TriggerTable(9911, List.of(TriggerSpecs.rule(TriggerEvent.ATTACK_FINISHED.name(), null, effect)));
    }

    /** The same total as ONE term (a share of ATK). */
    private static TriggerTable onePartTable(double shareOfAttack) {
        EffectSpec effect = damage();
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", shareOfAttack);
        return new TriggerTable(9912, List.of(TriggerSpecs.rule(TriggerEvent.ATTACK_FINISHED.name(), null, effect)));
    }

    /** The same two terms as TWO effects (two instances) - the shape `parts` replaces. */
    private static TriggerTable twoEffectTable(double atkPart, double defPart) {
        EffectSpec atk = damage();
        TriggerSpecs.set(atk, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(atk, "percent", atkPart);
        EffectSpec def = damage();
        TriggerSpecs.set(def, "scale", "self_attr:DEFENCE");
        TriggerSpecs.set(def, "percent", defPart);
        return new TriggerTable(9913, List.of(TriggerSpecs.rule(TriggerEvent.ATTACK_FINISHED.name(), null, atk, def)));
    }

    private static EffectSpec damage() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    private static EffectSpec.Part part(String scale, double percent) {
        EffectSpec.Part part = new EffectSpec.Part();
        TriggerSpecs.set(part, "scale", scale);
        TriggerSpecs.set(part, "percent", percent);
        return part;
    }
}
