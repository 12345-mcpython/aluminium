package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Huohuo (藿藿) (121), from her own file (2026-09-28): the Skill (战技)'s cleanse, the ultimate's per-recipient energy, and [禳命].
 *
 * <p><b>What it needed.</b> Nothing new: `DISPEL`, `other_allies`, `GAIN_ENERGY` with a per-target share of max energy, a
 * [`owner_max_hp` + constant] heal, `ticks_on: "self"`, and `target_when` with `target_hp_percent` for "each of our targets whose current HP
 * percentage is <= 50% produces 1 stack each".
 *
 * <p><b>What is registered</b> (the file's notes): the Skill (战技)'s "adjacent target" heal (no position axis), the talent's "after being enhanced" variant
 * (a talent upgrade no gate can select) and its "or when casting the Ultimate" trigger (no selector names a cast's caster for a heal).
 */
public class HuohuoTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The state she applies to herself, on her own clock. */
    @Test
    public void herSkillGrantsTheStateToHerself() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.huohuo.getBuffManager().hasState("禳命"), "nothing before the cast");

        f.skill();

        Assertions.assertTrue(f.huohuo.getBuffManager().hasState("禳命"), "「施放战技后藿藿获得【禳命】」");
        Assertions.assertFalse(f.ally.getBuffManager().hasState("禳命"), "…and it is hers (「藿藿获得」), not the party's");
    }

    /** Note: "除自身以外的队友": the ultimate boosts every OTHER ally's attack (the energy half is registered). */
    @Test
    public void herUltimatePaysTheOthersNotHerself() {
        Fixture f = new Fixture();
        double allyBefore = f.ally.getAttribute(AttributeType.ATTACK).get();
        double herBefore = f.huohuo.getAttribute(AttributeType.ATTACK).get();
        double allyEnergyBefore = f.ally.getCurrentEnergy();
        double herEnergyBefore = f.huohuo.getCurrentEnergy();

        f.ultimate();

        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ATTACK).get() > allyBefore,
                "「使其攻击力提高40.00%」 -- an ally is boosted");
        Assertions.assertEquals(herBefore, f.huohuo.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "⚠ 「**除自身以外**的队友」: she is not (boosting everyone would be the plausible-looking wrong reading)");
        Assertions.assertTrue(f.ally.getCurrentEnergy() > allyEnergyBefore,
                "「为除自身以外的队友恢复等同于**各自**20.00%能量上限的能量」 -- a share of the ALLY's own maximum");
        Assertions.assertEquals(herEnergyBefore, f.huohuo.getCurrentEnergy(), 1e-6,
                "⚠ …and her own bar is untouched: 「除自身以外」 governs both halves");
    }

    /** The talent's trigger is gated on HER state, and both of its halves are on the rule. */
    @Test
    public void theTalentTriggerNeedsHerState() {
        Fixture f = new Fixture();
        TriggerTable table = TriggerTables.of(HUOHUO);

        Assertions.assertTrue(table.matching(TriggerEvent.TURN_START, ctx(f, false)).isEmpty(),
                "no 【禳命】 on her, no trigger");

        f.skill();
        Assertions.assertFalse(table.matching(TriggerEvent.TURN_START, ctx(f, true)).isEmpty(),
                "with 【禳命】 up, a turn start matches");
    }

    /** Census: the clauses are where the notes say they are. */
    @Test
    public void herFileCarriesTheClauses() {
        Assertions.assertEquals(2, TriggerTables.of(HUOHUO).ruleCount(TriggerEvent.SKILL_CAST),
                "the cleanse and the state");
        Assertions.assertEquals(1, TriggerTables.of(HUOHUO).ruleCount(TriggerEvent.ULT_CAST));
        Assertions.assertEquals(1, TriggerTables.of(HUOHUO).ruleCount(TriggerEvent.TURN_START));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerTable.TriggerContext ctx(Fixture f, boolean ignored) {
        return new TriggerTable.TriggerContext(f.huohuo, f.ally, f.ally, 0, 0, null, f.battle);
    }

    private static final class Fixture {
        private final Character huohuo = CharacterFactory.create(HUOHUO, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(huohuo, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void skill() {
            battle.castImmediate(huohuo.getSkills().get(SkillType.SKILL), huohuo, List.of(ally));
        }

        private void ultimate() {
            battle.castImmediate(huohuo.getSkills().get(SkillType.ULTRA), huohuo, List.of(enemy));
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
