package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23064: casting an ELATION skill grants [风口], which raises SPEED by 24%.
 *
 * <p>The state has no duration upstream ({@code Equip45.json} states {@code OnStack} and {@code ReplaceByCaster} for
 * {@code MEquip_23064_Buff_1} and no {@code Duration} / {@code LifeTime} anywhere), the same signature as 21065's layers -- so
 * the state persists and the modifier is permanent with it (there is no counter here, so nothing can come apart).
 *
 * <p>向浪花掷下盛夏 (23064): 「若施放的欢愉技与上一次施放的不同，则额外获得【潮流】，【潮流】使装备者的欢愉度提高 #3%」.
 *
 * <p><b>What it needed.</b> The engine now remembers the slot of the last <b>finished</b> Elation cast
 * ({@code last_elation_skill_id}), so the clause is the two-variable comparison
 * {@code from_skill_id != last_elation_skill_id} on {@code CAST_SETUP}. The comparison's behaviour is pinned by
 * {@code LastElationSkillTest}; this test pins the content side (the state, the attribute and the per-rank share).
 */
public class SummerRidesTheSurfTest {
    private static final int CONE = 23064;
    private static final int WEARER = 1205;
    private static final int ELATION_WEARER = 1506;   // carries both Elation slots
    private static final int LEVEL = 80;
    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private void cast(Battle battle, SkillCategory category) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, category, 0, 0, java.util.List.of()));
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void anElationCastGrantsWindGustAndRaisesSpeed() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.SPEED).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        double delta = wearer.getAttribute(AttributeType.SPEED).get() - base;
        boolean state = wearer.getBuffManager().hasState("char:23064/outlet");
        System.out.println("[23064] after an elation cast: speed +" + delta + " (base " + base + ") has Updraft (char:23064/outlet)=" + state);
        Assertions.assertTrue(state, "the state itself is granted");
        Assertions.assertEquals(base * 0.24, delta, 1e-6, "and it is +24% SPEED (a share of the base)");
    }

    @Test
    public void aNonElationCastGrantsNothing() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.SPEED).get();
        cast(battle, SkillCategory.ULTRA);
        System.out.println("[23064] after an ULTRA cast: speed delta="
                + (wearer.getAttribute(AttributeType.SPEED).get() - base));
        Assertions.assertFalse(wearer.getBuffManager().hasState("char:23064/outlet"), "the clause names an ELATION skill");
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9, "false case");
    }

    @Test
    public void theSpecPinsTheStateTheShareAndTheAbsenceOfDuration() {
        Battle battle = battle(true);
        int states = 0;
        int modifiers = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.CAST_SETUP,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                        SkillCategory.ELATION_DAMAGE))) {
            for (var effect : rule.effects()) {
                if ("APPLY_BUFF".equals(effect.getOp())) {
                    states++;
                    System.out.println("[23064] spec state=" + effect.getBuff() + " turns=" + effect.getTurns()
                            + " permanent=" + effect.getPermanent());
                    Assertions.assertEquals("char:23064/outlet", effect.getBuff(), "the document's name for the state");
                    Assertions.assertEquals(Boolean.TRUE, effect.getPermanent(), "no duration upstream");
                } else if ("MODIFY_ATTR".equals(effect.getOp())) {
                    modifiers++;
                    Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "24% SPEED at rank 1");
                    Assertions.assertEquals(Boolean.TRUE, effect.getPermanent(), "and it lives with the state");
                }
            }
        }
        Assertions.assertEquals(1, states, "exactly one state-granting rule");
        Assertions.assertEquals(1, modifiers, "and one modifier");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double base = wearer.getAttribute(AttributeType.SPEED).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9, "no cone, no change");
    }

    /**
     * Casting an Elation skill grants [潮流] and raises the wearer's Elation degree by the rank's #3 (S1: 40%).
     */
    @Test
    public void anElationCastGrantsTideAndItsElationShare() {
        Fixture f = new Fixture(1);
        f.cast(SkillType.ELATION_SKILL);
        Assertions.assertTrue(f.wearer.getBuffManager().hasState("char:23064/trend"), "「额外获得【char:23064/trend】」");
        double withoutCone = bareElation();
        Assertions.assertEquals(0.4, f.elation() - withoutCone, 1e-9, "S1 #3 = 40% on top of the kit basis");
        f.cast(SkillType.ELATION_SKILL);
        Assertions.assertEquals(0.4, f.elation() - withoutCone, 1e-9, "the same skill again does not stack the state");
    }

    /**
     * The share is per rank (40 / 55 / 70 / 85 / 100%).
     */
    @Test
    public void theShareFollowsTheRank() {
        double[] expected = {0.4, 0.55, 0.7, 0.85, 1.0};
        double withoutCone = bareElation();
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank);
            f.cast(SkillType.ELATION_SKILL);
            Assertions.assertEquals(expected[rank - 1], f.elation() - withoutCone, 1e-9, "rank " + rank + ": #3");
        }
    }

    /** The 1506 kit casts the same skill, so its own elation share is the baseline the cone adds to. */
    private static double bareElation() {
        Character wearer = CharacterFactory.create(ELATION_WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(enemy));
        return wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int rank) {
            wearer = CharacterFactory.create(ELATION_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void cast(SkillType slot) {
            battle.castImmediate(wearer.getSkills().get(slot), wearer, List.of(enemy));
        }

        private double elation() {
            return wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        }
    }
}
