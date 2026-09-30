package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23061's second clause, first half: while the crown is up, the WHOLE PARTY ignores 20% of the target's defence.
 *
 * <p>\u2b50 The expectation is derived from the engine's own defence zone (discipline 192), and the judge reads the WEARER and the
 * ALLY separately -- the zone reads the ATTACKER's own attribute, so a modifier aimed at one unit would show up here.
 */
public class Cone23061DefenceIgnoreTest {
    private static final int CONE = 23061;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int THRESHOLD = 4;
    private static final double IGNORE = 0.2;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        for (Character unit : List.of(wearer, ally)) {
            unit.getAttribute(AttributeType.CRIT_CHANCE)
                    .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230611));
        }
        return battle;
    }

    private double hit(Battle battle, Character attacker) {
        return battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    private void spendAs(Battle battle, Character who, int times) {
        var skill = who.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        for (int i = 0; i < times; i++) {
            battle.gainSkillPoint(1);
            Assertions.assertTrue(battle.applySkillPointCost(skill, who), "room to spend");
        }
    }

    @Test
    public void thePartyIgnoresTwentyPercentOnceTheCrownIsUp() {
        Battle baseline = battle(false);
        double wearerNone = hit(baseline, wearer);
        double allyNone = hit(baseline, ally);
        double defence = enemy.getAttribute(AttributeType.DEFENCE).get();
        double zoneNone = 1.0 / (defence + 200 + 10 * LEVEL);
        double zoneIgnore = 1.0 / (defence * (1 - IGNORE) + 200 + 10 * LEVEL);
        double expected = wearerNone * zoneIgnore / zoneNone;

        Battle battle = battle(true);
        spendAs(battle, ally, THRESHOLD);
        double wearerAfter = hit(battle, wearer);
        double allyAfter = hit(battle, ally);
        System.out.println("[23061def] none=" + wearerNone + " expected=" + expected + " wearer=" + wearerAfter
                + " ally=" + allyAfter + " (defence=" + defence + ")");
        Assertions.assertEquals(expected, wearerAfter, Math.abs(expected) * 1e-6,
                "the wearer's own hits ignore 20% -- the zone reads the ATTACKER's attribute");
        Assertions.assertEquals(wearerNone * (zoneIgnore / zoneNone), allyAfter, Math.abs(wearerAfter) * 1e-6,
                "and so do the ally's -- the panel covers \u6211\u65b9\u5168\u4f53");
    }
}
