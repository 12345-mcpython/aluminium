package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23055 「献给明日的色彩」 (The Unreachable Dream's counterpart: 挥墨), as a battle.
 *
 * <p>「使装备者的<b>防御力提高 #1%</b>。装备者<b>对我方全体施放欢愉技</b>时，使敌方全体<b>受到的伤害提高 #4%</b>，持续 <b>#3</b>
 * 回合，为装备者<b>固定恢复 #2 点能量</b>，并为我方全体回复等同于<b>装备者防御力 #5%</b> 的生命值。」
 *
 * <p>⚠ <b>Four clauses, four different failure shapes, so four separate readings:</b>
 * ① the defence is the CONE's own {@code ability_property} (no rule may state it again) - measured as the difference
 * between wearing it and not; ② the gate is an Elation skill aimed at an ALLY, which no shipped kit produces, so the
 * fixture builds it (§6.3·3); ③ 「固定」energy must NOT take the regeneration rate - the rate is set to 50% here, where
 * "fixed" reads 10 and a normal grant would read 15; ④ the heal is a share of the wearer's <b>DEFENCE</b>, not of Max HP.
 *
 * <p>⚠ The wearer is 1506 because she has an Elation skill (data slot 20); the cone's rules ride on whoever holds it.
 */
public class Cone23055Test {
    private static final int CONE = 23055;
    private static final int WEARER = 1506;
    private static final int ALLY = 1204;
    private static final int SUPERIMPOSITION = 5;
    private static final double VULNERABILITY = 0.44;
    private static final double HEAL_SHARE_OF_DEF = 0.20;
    private static final double FIXED_ENERGY = 10;
    /** Enough to separate "fixed" from "scaled": a normal +10 at this rate exists as +15. */
    private static final double REGEN_RATE = 0.5;

    /**
     * ③ 「固定恢复」 ignores the energy-regeneration rate.
     */
    @Test
    public void theEnergyIsFixedNotScaledByTheRate() {
        Fixture f = new Fixture();
        f.wearer.setCurrentEnergy(0);
        double before = f.wearer.getCurrentEnergy();
        f.castElationSkillAtAnAlly();
        double gained = f.wearer.getCurrentEnergy() - before;
        System.out.println("[23055] 效率 " + REGEN_RATE + " 下的能量增量: " + gained
                + "（固定 = " + FIXED_ENERGY + "；按效率算会是 " + (FIXED_ENERGY * (1 + REGEN_RATE)) + "）");
        Assertions.assertEquals(FIXED_ENERGY, gained, 1e-9,
                "「固定恢复 10 点」+ 50% 效率 ⇒ 仍是 10（读成 15 就是 `fixed` 没生效）");
    }

    /**
     * ② ＋ ④ the vulnerability and the DEF-based heal, both keyed on the ally-targeted Elation cast.
     */
    @Test
    public void theVulnerabilityAndTheDefenceHealLand() {
        Fixture f = new Fixture();
        // Precondition: everybody is hurt, so a heal is visible at all (a full-HP fixture reads 0 whatever the share is).
        for (Character ally : f.battle.characters()) {
            ally.consumeHp(ally.getMaxHp() * 0.5);
        }
        double[] hpBefore = f.battle.characters().stream().mapToDouble(Character::getCurrentHp).toArray();
        double defence = f.wearer.getAttribute(AttributeType.DEFENCE).get();

        f.castElationSkillAtAnAlly();

        for (int i = 0; i < f.battle.characters().size(); i++) {
            double healed = f.battle.characters().get(i).getCurrentHp() - hpBefore[i];
            System.out.println("[23055] " + f.battle.characters().get(i).getName() + " 回复 " + healed
                    + "（= 防御 " + defence + " × " + HEAL_SHARE_OF_DEF + " = " + (defence * HEAL_SHARE_OF_DEF) + "）");
            Assertions.assertEquals(defence * HEAL_SHARE_OF_DEF, healed, 1e-6,
                    "「为我方全体回复等同于装备者防御力 20%」—— 是**防御力**的份额，不是生命上限");
        }

        // The vulnerability: one hit on an enemy, whose BoostArea-free reading carries the VulnerableArea factor.
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double vulnerable = settled.stream()
                .map(d -> d.breakdown().getOrDefault("VulnerableArea", 1.0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the follow-up hit settled nothing"));
        System.out.println("[23055] 敌方易伤区 ×" + vulnerable);
        Assertions.assertEquals(1 + VULNERABILITY, vulnerable, 1e-9,
                "「使敌方全体受到的伤害提高 44%」（叠影 5）");
    }

    /**
     * ① the defence comes from the cone's own `ability_property` - so it must NOT also be a rule.
     *
     * <p>⚠ The expected number is {@code (character base + the cone's flat DEF) x (1 + 0.96)}: the cone carries a flat
     * defence AND the skill's `defence_percent`, so "with minus without" is neither the property nor the flat stat. A rule
     * that stated the +96% again would square the factor and miss this by a mile - which is exactly what it is for.
     */
    @Test
    public void theWearerGetsTheConesDefence() {
        Fixture f = new Fixture();
        double withCone = f.wearer.getAttribute(AttributeType.DEFENCE).get();
        double withoutCone = Fixture.withoutCone().getAttribute(AttributeType.DEFENCE).get();
        double coneFlat = f.cone.getDefence();
        System.out.println("[23055] 防御力: 无锥 " + withoutCone + " ; 锥的固定项 " + coneFlat + " ; 叠影 5 " + withCone
                + "（(无锥 + 固定项) × 1.96 = " + ((withoutCone + coneFlat) * 1.96) + "）");
        Assertions.assertEquals((withoutCone + coneFlat) * 1.96, withCone, withCone * 1e-9,
                "光锥 23055 的 `ability_property` = defence_percent 0.96（叠影 5）⇒ 只加**一次** +96%");
    }

    /**
     * And the gate is real: the same cast aimed at an ENEMY pays nothing.
     */
    @Test
    public void aCastAimedAtAnEnemyPaysNothing() {
        Fixture f = new Fixture();
        f.wearer.setCurrentEnergy(0);
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.ELATION_SKILL), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23055] 欢愉技打敌人时的能量增量: " + f.wearer.getCurrentEnergy());
        Assertions.assertEquals(0.0, f.wearer.getCurrentEnergy(), 1e-9,
                "「对我方全体施放」—— 打敌人不触发（`target is_ally` 这一半）");
    }

    // ==================================================================

    private static final class Fixture {
        private final Weapon cone = Weapon.build(CONE, 80, false, SUPERIMPOSITION);
        private final Character wearer = CharacterFactory.create(WEARER, 80, false, cone);
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Battle battle = new Battle(List.of(wearer, ally), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.9999));

        private Fixture() {
            battle.startBattle();
            battle.processRequests();
            // A non-zero rate is what separates the fixed grant from a scaled one.
            wearer.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(REGEN_RATE));
        }

        /** The same wearer and party, with no light cone. */
        private static Character withoutCone() {
            Battle battle = new Battle(List.of(CharacterFactory.create(WEARER, 80), CharacterFactory.create(ALLY, 80)),
                    List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
            return battle.characters().getFirst();
        }

        /** 「对我方全体施放欢愉技」: the wearer's Elation skill, aimed at her teammate. */
        private void castElationSkillAtAnAlly() {
            battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(ally));
            battle.processRequests();
        }
    }
}
