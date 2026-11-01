package com.laosun.aluminium.test.content.characters.aventurine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1304 砂金's ultimate: 「我方目标击中【惊惶】状态下的敌方目标时，造成的暴击伤害提高 15%」.
 *
 * <p>The clause is an instance {@code CRIT_ATTACK} share (the same route 23059 uses): the number is added to that hit's
 * {@code extraCritDamage}, and the crit zone only reads it when the hit crits - so it is a crit-damage bonus without a new
 * layer. It applies to <b>any of our units</b> ({@code actor is_ally}), and the share is read from the ULTIMATE row at its
 * current level ({@code percent_from_skill_param}) because #3 moves with the level.
 */
public class AventurineUnnervedCritTest {

    private static final int LEVEL = 80;
    private static final int AVENTURINE = 1304;
    private static final int ALLY = 1002;
    private static final String UNNERVED = "惊惶";

    /**
     * Our hit on a 【惊惶】 target collects the ultimate row's #3; without the state it collects nothing.
     */
    @Test
    public void ourHitsOnAnUnnervedTargetGetTheCritDamage() {
        Fixture f = new Fixture(true);
        Assertions.assertEquals(0.15, f.extraCritDamage(f.wearer), 1e-9,
                "the wearer's hit: #3 at the level her ultimate sits at (row 10 = 15%)");
        Assertions.assertEquals(0.15, f.extraCritDamage(f.ally), 1e-9,
                "「我方目标」 - an ally's hit too");

        Fixture bare = new Fixture(false);
        Assertions.assertEquals(0.0, bare.extraCritDamage(bare.wearer), 1e-9, "no 【惊惶】, no share");
    }

    /**
     * It is crit damage: a forced crit reads 1 + (0.5 + 0.15), a forced non-crit stays 1.0.
     */
    @Test
    public void theShareOnlyCountsInsideACrit() {
        Fixture crit = new Fixture(true);
        crit.hit(crit.wearer, true);
        Assertions.assertEquals(1.65, crit.critRate, 1e-9, "1 + base 0.5 crit damage + the 15% share");

        Fixture flat = new Fixture(true);
        flat.hit(flat.wearer, false);
        Assertions.assertEquals(1.0, flat.critRate, 1e-9, "a non-crit hit is untouched");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private double critRate;

        private Fixture(boolean unnerved) {
            wearer = CharacterFactory.create(AVENTURINE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (unnerved) {
                enemy.getBuffManager().addBuff(new StateBuff(UNNERVED, 3));
            }
        }

        private double extraCritDamage(Character attacker) {
            Damage damage = new Damage(attacker, enemy, DamageElement.IMAGINARY, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            return damage.getExtraCritDamage();
        }

        private void hit(Character attacker, boolean crit) {
            DoubleValue chance = attacker.getAttribute(AttributeType.CRIT_CHANCE);
            chance.addModifier(DoubleValue.Modifier.pure(crit ? 10 : -10));
            Damage damage = new Damage(attacker, enemy, DamageElement.IMAGINARY, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            critRate = damage.critArea().getRate();
        }
    }
}
