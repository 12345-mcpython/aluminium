package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import java.util.Random;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Three light cones whose clauses land on scoped damage-boost attributes the engine already has, judged per rank.
 *
 * <p>Each is read raw: these attributes are pure bonuses with no base to form a ratio against (the same reason effect hit was
 * read raw in round 46).
 */
public class LightConeBoostTest {
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theUltimateBoostFollowsTheRank() {
        Assertions.assertEquals(0.28, boost(20006, AttributeType.ULTIMATE_DAMAGE_BOOST, 1), 1e-9,
                "\u7ec8\u7ed3\u6280 damage is 28% at rank 1");
        Assertions.assertEquals(0.56, boost(20006, AttributeType.ULTIMATE_DAMAGE_BOOST, 5), 1e-9,
                "and 56% at rank 5");
    }

    @Test
    public void skillAndFollowUpBoostsFollowTheRank() {
        Assertions.assertEquals(0.24, boost(21062, AttributeType.SKILL_DAMAGE_BOOST, 1), 1e-9, "skill, rank 1");
        Assertions.assertEquals(0.40, boost(21062, AttributeType.SKILL_DAMAGE_BOOST, 5), 1e-9, "skill, rank 5");
        Assertions.assertEquals(0.24, boost(21062, AttributeType.FOLLOW_UP_DAMAGE_BOOST, 1), 1e-9, "follow-up, rank 1");
        Assertions.assertEquals(0.40, boost(21062, AttributeType.FOLLOW_UP_DAMAGE_BOOST, 5), 1e-9, "follow-up, rank 5");
    }

    @Test
    public void basicAndSkillBoostsFollowTheRank() {
        Assertions.assertEquals(0.20, boost(20002, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 1), 1e-9, "basic, rank 1");
        Assertions.assertEquals(0.40, boost(20002, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 5), 1e-9, "basic, rank 5");
        Assertions.assertEquals(0.20, boost(20002, AttributeType.SKILL_DAMAGE_BOOST, 1), 1e-9, "skill, rank 1");
    }

    @Test
    public void thePropertyHalvesAreNotAuthoredTwice() {
        // 21062's crit damage is an ability_property (0.24 at rank 1), so the content must not add it again: authored twice
        // it would read 0.48, which is exactly the doubling round 46 measured for a defence property.
        // \u26a0 CRIT_ATTACK has a 0.5 base, so the judgement is a DIFFERENCE (discipline 48): the data path adds 0.24 once.
        double base = 0.5;
        Assertions.assertEquals(base + 0.24, raw(21062, AttributeType.CRIT_ATTACK, 1), 1e-9,
                "crit damage comes from the data path once, not twice");
    }

    private static double boost(int weaponId, AttributeType attribute, int rank) {
        return raw(weaponId, attribute, rank);
    }

    private static double raw(int weaponId, AttributeType attribute, int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true,
                Weapon.build(weaponId, LEVEL, false, rank));
        // \u26a0 A battle has to START: the rules hang on BATTLE_START, and without it the attribute reads 0.0 while the
        // table visibly carries the rules -- measured by a probe before this line existed (rules=2, value 0.28).
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return wearer.getAttribute(attribute).get();
    }
}
