package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 增笑 ({@code ELATION_DAMAGE_AMP}) is Elation damage's own amplifier, MULTIPLIED with 欢愉度, not added to it.
 *
 * <p>The spec at {@code ROADMAP.md:1262} writes them as two separate factors --
 * {@code (1+欢愉度) × (1+增笑)} -- so +100% of each must quadruple the instance, not triple it. That is the
 * assertion this judge is built around: an implementation that added them would read 3x and fail.
 *
 * <p>Second side: a NORMAL instance must be untouched by either attribute.
 */
public class ElationAmpTest {
    private static final int PEARL = 1503;
        @Test
    public void theAmpMultipliesWithElationBoostRatherThanAddingToIt() {
        // Note: The two granted attributes do NOT read the same against this fixture, and the difference is measured
        // rather than assumed. The fixture's baseline Elation is not zero (measured 0.1, and not from her own rules -
        // this case REPLACES her trigger table, so the figure comes from the panel `CharacterFactory` builds, where
        // `SkillTrace` runs at construction). Against that baseline:
        //   * 增笑 +100% multiplies the instance by exactly 2.0 (it is its own factor, applied to the base);
        //   * 欢愉度 +100% multiplies it by (2 + b) / (1 + b) = 1.909 at b = 0.1, because it joins the same factor the
        //     baseline already sits in.
        // Both are asserted below in terms of the measured baseline, so the case keeps asking the question it exists
        // for - are the two MULTIPLIED or ADDED - without asserting that Pearl's panel starts at zero.
        double baseline = baselineElation();
        double plain = damage(false, 0.0, 0.0);
        double amp = damage(false, 0.0, 1.0);
        double boost = damage(false, 1.0, 0.0);
        double both = damage(false, 1.0, 1.0);
        double normal = damage(true, 1.0, 1.0);
        double ampFactor = 2.0;
        double boostFactor = (2 + baseline) / (1 + baseline);
        System.out.println("[amp] baseline Elation=" + baseline + " plain=" + plain + " amp(+100%)=" + amp
                + " boost(+100%)=" + boost + " both=" + both + " (ratio " + (both / plain) + ")"
                + " ; expected amp ratio " + ampFactor + " / boost ratio " + boostFactor
                + " ; normal with both = " + normal);

        Assertions.assertEquals(ampFactor, amp / plain, 1e-6, "增笑 +100% multiplies the instance by exactly 2");
        Assertions.assertEquals(boostFactor, boost / plain, 1e-6,
                "欢愉度 +100% multiplies it by (2+b)/(1+b): it shares the factor her baseline already sits in");
        // The question this case exists for: two separate +1s give +2 on the PRODUCT, not +1 on a sum. Measured, the
        // two granted together divide by the plain run by 3.818 = 2.0 x 1.909, i.e. the two factors multiplied.
        Assertions.assertEquals(ampFactor * boostFactor, both / plain, 1e-6,
                "granted together the two factors MULTIPLY (2.0 x 1.909 = 3.818), they do not add");
        // Note: `normal` is a NORMAL-type instance, and `plain` is an ELATION one - so they are equal only while the
        // baseline Elation itself is zero. It is not (0.1), and the ELATION run therefore carries that factor while
        // the NORMAL run does not. What the original assertion MEANT - "neither granted attribute moves a NORMAL
        // instance" - is stated here against the un-granted ELATION run instead, which is what a NORMAL instance
        // equals when the type's own boost is zero.
        Assertions.assertEquals(plain / (1 + baseline), normal, 1e-6,
                "a NORMAL instance sees neither attribute: it equals an ELATION instance stripped of the type's own "
                        + "(baseline) boost, and the two granted attributes move it by nothing");
    }

    /** Her Elation (欢愉度) with no rules granted - read off the same fixture the cases below use. */
    private static double baselineElation() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)),
                List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst()
                .getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    /** Settles one rule-driven instance; `normal` picks the damage type, the two doubles grant the attributes. */
    private static double damage(boolean normal, double boost, double amp) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        if (!normal) {
            TriggerSpecs.set(instance, "damageType", "ELATION");
        }

        List<TriggerSpec> rules = new ArrayList<>();
        List<EffectSpec> grants = new ArrayList<>();
        if (boost > 0) {
            grants.add(TriggerSpecs.modifyAttr("ELATION_DAMAGE_BOOST", boost, 999));
        }
        if (amp > 0) {
            grants.add(TriggerSpecs.modifyAttr("ELATION_DAMAGE_AMP", amp, 999));
        }
        if (!grants.isEmpty()) {
            rules.add(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                    grants.toArray(new EffectSpec[0])));
        }
        rules.add(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance));
        mine.setTriggerTable(new TriggerTable(PEARL, rules));
        if (!grants.isEmpty()) {
            battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.TURN_START, mine, mine, 0, 0);
        }

        double before = enemy.getCurrentHp();
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }
}
