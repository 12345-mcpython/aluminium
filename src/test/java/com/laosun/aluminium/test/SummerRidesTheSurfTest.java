package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23064: casting an ELATION skill grants [风口], which raises SPEED by 24%.
 *
 * <p>The state has no duration upstream ({@code Equip45.json} states {@code OnStack} and {@code ReplaceByCaster} for
 * {@code MEquip_23064_Buff_1} and no {@code Duration} / {@code LifeTime} anywhere), the same signature as 21065's layers -- so
 * the state persists and the modifier is permanent with it (there is no counter here, so nothing can come apart).
 */
public class SummerRidesTheSurfTest {
    private static final int CONE = 23064;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void cast(Battle battle, SkillCategory category) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, category);
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void anElationCastGrantsWindGustAndRaisesSpeed() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.SPEED).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        double delta = wearer.getAttribute(AttributeType.SPEED).get() - base;
        boolean state = wearer.getBuffManager().hasState("风口");
        System.out.println("[23064] after an elation cast: speed +" + delta + " (base " + base + ") has 风口=" + state);
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
        Assertions.assertFalse(wearer.getBuffManager().hasState("风口"), "the clause names an ELATION skill");
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
                    Assertions.assertEquals("风口", effect.getBuff(), "the document's name for the state");
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
}
