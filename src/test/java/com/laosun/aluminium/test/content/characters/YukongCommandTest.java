package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Yukong (驭空) (120), from her own file: [鸣弦号令] (Bowstring Command), and the pair of capabilities it needed.
 *
 * <p><b>What it needed.</b> {@code TURN_END} ("at the end of each of our targets' turns" - the next unit's start is a different fact, and a
 * buff's duration tick is not an event) and {@code REMOVE_STACK} by name ("remove 1 stack of [鸣弦号令] from Yukong": the attribute
 * form cannot address a named stack, and `REMOVE_STATE` takes all of them off). The ordering inside one event is what
 * makes "while holding ..." and "the layer count reaches zero" both expressible: conditions are evaluated when a rule is reached, so the trace sees the
 * pre-removal count and the cleanup sees the post-removal one.
 */
public class YukongCommandTest {
    private static final int YUKONG = 1207;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The Skill grants two layers and the party boost; the layers are one buff each. */
    @Test
    public void herSkillGrantsTwoLayersAndThePartyBoost() {
        Fixture f = new Fixture();
        double allyAttack = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.castSkill();

        Assertions.assertEquals(2, f.yukong.getBuffManager().stacksOf("鸣弦号令"), "「gains 2 layers of [鸣弦号令]」");
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ATTACK).get() > allyAttack,
                "「increases the ATK of all of our side by 80%」 -- the boost lands on the whole side, not just on her");
    }

    /** Note: An ally's turn end removes exactly ONE layer; her own does not. */
    @Test
    public void anAllysTurnEndRemovesOneLayerButHersDoesNot() {
        Fixture f = new Fixture();
        f.castSkill();

        f.turnEndOf(f.yukong);
        Assertions.assertEquals(2, f.yukong.getBuffManager().stacksOf("鸣弦号令"),
                "「on the turn Yukong casts the Skill and gains [鸣弦号令], it is not removed」");

        f.turnEndOf(f.ally);
        Assertions.assertEquals(1, f.yukong.getBuffManager().stacksOf("鸣弦号令"),
                "「at the end of each of our targets' turns, remove 1 layer from Yukong」");
    }

    /** When the last layer goes, the party boost goes with it - one removal, everyone. */
    @Test
    public void thePartyBoostEndsWithTheLastLayer() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(AttributeType.ATTACK).get();
        f.castSkill();
        double boosted = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.turnEndOf(f.ally);
        f.turnEndOf(f.ally);

        Assertions.assertEquals(0, f.yukong.getBuffManager().stacksOf("鸣弦号令"), "two ally turn ends, two layers");
        Assertions.assertTrue(boosted > before, "precondition: the boost was up");
        Assertions.assertEquals(before, f.ally.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "「while Yukong holds [鸣弦号令]」 -- with no layers left the boost is off, for every ally");
    }

    /** The ultimate's crit buffs only appear while she holds a layer. */
    @Test
    public void herUltimateNeedsALayerToBuffTheParty() {
        Fixture without = new Fixture();
        double base = without.ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        without.castUltimate();
        Assertions.assertEquals(base, without.ally.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「if Yukong holds [鸣弦号令]」 is false, so nothing is granted");

        Fixture with = new Fixture();
        with.castSkill();
        double before = with.ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        with.castUltimate();
        Assertions.assertTrue(with.ally.getAttribute(AttributeType.CRIT_CHANCE).get() > before,
                "…and with a layer up, the party gets +28% crit and +65% crit damage");
    }

    /** The file is present as described, and the registered clauses are absent on purpose. */
    @Test
    public void herFileCarriesWhatItSays() {
        Assertions.assertEquals(3, TriggerTables.of(YUKONG).ruleCount(TriggerEvent.TURN_END),
                "census: three TURN_END rules (the energy trace, the one-layer removal, and the cleanup)");
        Assertions.assertEquals(1, TriggerTables.of(YUKONG).ruleCount(TriggerEvent.SKILL_CAST));
        Assertions.assertEquals(1, TriggerTables.of(YUKONG).ruleCount(TriggerEvent.BASIC_ATTACK),
                "the talent's additional damage (its 「toughness reduction increased by 100%」 half is registered)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character yukong = CharacterFactory.create(YUKONG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(yukong, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void castSkill() {
            battle.castImmediate(yukong.getSkills().get(SkillType.SKILL), yukong, List.of(ally));
        }

        private void castUltimate() {
            battle.castImmediate(yukong.getSkills().get(SkillType.ULTRA), yukong, List.of(enemy));
        }

        /** One unit's turn end, the way the engine delivers it. */
        private void turnEndOf(Character unit) {
            battle.fireTriggers(TriggerEvent.TURN_END, unit, unit, 0, 0);
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
