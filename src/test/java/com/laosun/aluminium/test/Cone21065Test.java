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
 * Light cone 21065: casting an ELATION skill raises elation damage by 12%, up to 2 layers, with NO duration.
 *
 * <p>⭐ The duration came from upstream, by absence: {@code MEquip_21065_Sub} states {@code ReplaceByCaster} and a layer cap
 * but no {@code Duration} / {@code LifeTime}, so the layers persist. ⚠ The config says 5 layers while the text and its param say
 * 2 -- the document wins, as it does everywhere else in this project.
 */
public class Cone21065Test {
    private static final int CONE = 21065;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double PER_LAYER = 0.12;

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
    public void elationCastsStackUpToTwoLayers() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        double one = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base;
        cast(battle, SkillCategory.ELATION_DAMAGE);
        double two = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base;
        cast(battle, SkillCategory.ELATION_DAMAGE);
        cast(battle, SkillCategory.ELATION_DAMAGE);
        double capped = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base;
        System.out.println("[21065] one=" + one + " two=" + two + " after four=" + capped
                + " perLayer=" + PER_LAYER);
        Assertions.assertEquals(PER_LAYER, one, 1e-9, "one elation cast is one layer of 12%");
        Assertions.assertEquals(2 * PER_LAYER, two, 1e-9, "two casts are two layers");
        Assertions.assertEquals(2 * PER_LAYER, capped, 1e-9, "and the text caps it at 2");
    }

    @Test
    public void aNonElationCastDoesNotStack() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        cast(battle, SkillCategory.ULTRA);
        System.out.println("[21065] after an ULTRA cast: delta="
                + (wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base));
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get(), 1e-9,
                "the clause names an ELATION skill (false case)");
    }

    @Test
    public void theSpecPinsTheNumbersAndThatThereIsNoDuration() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        int stacks = 0;
        int modifiers = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.CAST_SETUP,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                        SkillCategory.ELATION_DAMAGE))) {
            for (var effect : rule.effects()) {
                if ("ADD_STACK".equals(effect.getOp())) {
                    stacks++;
                    System.out.println("[21065] spec stack max=" + effect.getMaxStacks()
                            + " turns=" + effect.getTurns() + " permanent=" + effect.getPermanent());
                    Assertions.assertEquals(2, effect.getMaxStacks(), "the text says at most 2");
                    Assertions.assertEquals(Boolean.TRUE, effect.getPermanent(),
                            "upstream states no duration, so the layers persist");
                } else if ("MODIFY_ATTR".equals(effect.getOp())) {
                    modifiers++;
                }
            }
        }
        Assertions.assertEquals(1, stacks, "exactly one stacking rule from this cone");
        Assertions.assertEquals(1, modifiers, "and one modifier that reads it");
        Assertions.assertTrue(wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() > base,
                "precondition: the layer is live");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double base = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get(), 1e-9,
                "no cone, no layers (false case)");
    }
}
