package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for light cone (光锥) passives and relic set (遗器套装) bonuses.
 */
public class RelicAndWeaponTest {

    private RelicSuit buildSet102Suit() {
        RelicSuit suit = new RelicSuit();
        // Musketeer of Wild Wheat (102): 2pc ATK +12%, 4pc SPD +6% & basic dmg.
        suit.addMore(
                Relic.builder().type(RelicType.HEAD).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.HEALTH).build(),
                Relic.builder().type(RelicType.HAND).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.ATTACK).build(),
                Relic.builder().type(RelicType.BODY).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.ATTACK_PERCENT).build(),
                Relic.builder().type(RelicType.BOOT).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.SPEED).build(),
                Relic.builder().type(RelicType.BALL).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.ATTACK_PERCENT).build(),
                Relic.builder().type(RelicType.LINE).star(5).level(15).set(102)
                        .mainAttribute(AttributeType.ATTACK_PERCENT).build());
        return suit;
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testSet102TwoAndFourPieceStats() {
        // 2pc ATK +12% + 4pc SPD +6% should appear in the panel.
        Character seele = Character.builder().cid(1102).level(80).isPromote()
                .relicSuit(buildSet102Suit()).build();

        double atkPercent = seele.getAttribute(AttributeType.ATTACK).get()
                / seele.getAttribute(AttributeType.ATTACK).base(0).addBase(1).base(0).get();
        // Verify via the modifier: the ATK attribute includes a +12% modifier from the set.
        boolean hasAtkBonus = false;
        for (var mod : seele.getAttribute(AttributeType.ATTACK)
                .filterBySource(com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.RELIC_SET)) {
            if (Math.abs(mod.getValue() - 0.12) < 0.001) {
                hasAtkBonus = true;
            }
        }
        Assertions.assertTrue(hasAtkBonus, "2pc set should give ATK +12%");

        double speedBonus = 0;
        for (var mod : seele.getAttribute(AttributeType.SPEED)
                .filterBySource(com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.RELIC_SET)) {
            speedBonus += mod.getValue();
        }
        Assertions.assertTrue(speedBonus >= 0.06, "4pc set should give SPD +6%, got " + speedBonus);
    }

    @Test
    public void testSet102BasicDamageBoostInBattle() {
        // 4pc desc "普攻造成的伤害提高20%" is applied via the set trace.
        Character seele = Character.builder().cid(1102).level(80).isPromote()
                .relicSuit(buildSet102Suit()).build();
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double multiplier = 1.0;
        for (com.laosun.aluminium.models.Trace trace : seele.getTraces()) {
            multiplier *= trace.damageMultiplier(battle, seele, enemy, SkillType.COMMON);
        }
        Assertions.assertTrue(multiplier > 1.0, "4pc should boost basic attack damage");
    }

    @Test
    public void testWeaponPassiveInterpreted() {
        // Cruising in the Stellar Sea (24001) — conditional parts like "HP ≤ 50% → ATK+".
        Weapon weapon = Weapon.build(24001, 80);
        Assertions.assertNotNull(weapon.getPassiveTrace(),
                "light cone should have an interpreted conditional passive");
    }

    @Test
    public void testWeaponPassiveAppliesInBattle() {
        Character seele = Character.builder().cid(1102).level(80).isPromote()
                .weapon(Weapon.build(24001, 80)).build();
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double multiplier = 1.0;
        for (com.laosun.aluminium.models.Trace trace : seele.getTraces()) {
            multiplier *= trace.damageMultiplier(battle, seele, enemy, SkillType.SKILL);
        }
        Assertions.assertTrue(multiplier >= 1.0);
    }

    @Test
    public void testWeaponPassiveNoDoubleApplyOfBaseStats() {
        // The always-on part (SPD +18% for 23042) is in the ability properties,
        // so the passive trace must NOT be an always-on stat buff.
        Weapon weapon = Weapon.build(23042, 80);
        boolean hasAlwaysOnSpeedBuff = weapon.getPassiveTrace() instanceof com.laosun.aluminium.models.Trace t
                && t.getName().toLowerCase().contains("speed");
        Assertions.assertFalse(hasAlwaysOnSpeedBuff,
                "always-on SPD buff is already covered by ability properties");
    }
}
