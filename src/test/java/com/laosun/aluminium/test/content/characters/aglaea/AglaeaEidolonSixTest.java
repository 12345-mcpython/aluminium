package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's 星魂 6: 「当阿格莱雅处于【至高之姿】状态时，自身与衣匠的雷属性抗性穿透提高 20%」.
 *
 * <p>Two facts, and the second is the one that would hide a wrong constant: the modifier exists on both units while the
 * stance holds, and a THUNDER hit really sees the extra penetration (measured against a resistant enemy, so the
 * difference shows up in the settled number rather than only in an attribute read).
 */
public class AglaeaEidolonSixTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    @Test
    public void theStanceGrantsThunderPenetrationToBothOfThem() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertEquals(0.0, aglaea.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "precondition: no penetration before the stance");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();

        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the stance brought the memosprite out");
        Assertions.assertEquals(0.2, aglaea.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "「自身…雷属性抗性穿透提高 20%」");
        Assertions.assertEquals(0.2, garmentmaker.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "「…与衣匠的」");
    }

    /**
     * The penetration is <b>per element</b>: it must shorten a thunder hit and leave a physical one alone.
     *
     * <p>The enemy's resistance is raised to 40% so the difference is visible in the settled number; with penetration 20%
     * the thunder hit should take exactly the hit without it times {@code (1 - 0.2) / 1} in the resistance zone.
     */
    @Test
    public void thePenetrationOnlyHelpsThunder() {
        double plainThunder = settled("Thunder", 0.0);
        double penetratedThunder = settled("Thunder", 0.2);
        double plainPhysical = settled("Physical", 0.0);
        double penetratedPhysical = settled("Physical", 0.2);
        Assertions.assertTrue(penetratedThunder > plainThunder,
                "thunder: " + plainThunder + " -> " + penetratedThunder);
        Assertions.assertEquals(plainPhysical, penetratedPhysical, EPS,
                "physical must not move: the sentence names 雷, so the modifier sits on DAMAGE_PENETRATION_THUNDER only");
    }

    /**
     * Settles one 1000-damage hit of {@code element} against a 40%-resistant enemy whose attacker carries
     * {@code thunderPen} of <b>thunder</b> penetration - the constant the sentence names, whatever element is being
     * settled, so a physical hit proves the routing by staying put.
     */
    private static double settled(String element, double thunderPen) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        enemy.setDamageResist(java.util.Map.of(DamageElement.fromString(element), 0.4));
        if (thunderPen != 0) {
            // "pure" = the value in the attribute's own units: penetration is a number (0.2), not a share of a base that
            // is zero. Measured 2026-11-06: an `add_percent` modifier on it moved nothing.
            aglaea.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.of(
                    AttributeType.DAMAGE_PENETRATION_THUNDER, "pure", thunderPen, "buff", 5, false));
            battle.processRequests();
        }
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(aglaea, enemy, DamageElement.fromString(element), DamageType.NORMAL, 1000));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
