package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Aha;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DamageCalculator;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Integration tests for the HSR battle mechanics (HSR.md).
 */
public class BattleSystemTest {

    private Character buildCharacter(int cid) {
        return Character.builder().cid(cid).level(80).isPromote().build();
    }

    private Enemy buildEnemy(Element element, Element weakness, double toughness, String name) {
        return Enemy.fromTemplate(name, 80,
                20, 26, 240, 120, toughness,
                element, EnumSet.of(weakness), java.util.Map.of(element, 0.0),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testDamagePipelineRegions() {
        Character seele = buildCharacter(1102);
        Enemy trotter = buildEnemy(Element.PHYSICAL, Element.PHYSICAL, 30, "Trotter");
        double base = trotter.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get() * 0.5;
        double damage = DamageCalculator.calculateDamage(trotter, seele, base,
                DamageContext.of(DamageType.NORMAL, trotter.getElement()));
        // regions: reduction 1.0, defence ~0.68
        Assertions.assertTrue(damage > 100, "damage should be meaningful, was " + damage);
        Assertions.assertTrue(damage < base, "defence region must mitigate");
    }

    @Test
    public void testWeaknessBreak() {
        Character seele = buildCharacter(1102); // quantum
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertFalse(enemy.isBroken());
        for (int i = 0; i < 29; i++) {
            battle.dealAttackDamage(seele, enemy, 1.0, 0,
                    DamageContext.of(DamageType.NORMAL, seele.getElement()));
            battle.breakToughness(seele, enemy, 1.0);
        }
        Assertions.assertFalse(enemy.isBroken(), "should not break at 1/30");
        Assertions.assertEquals(1, enemy.getCurrentToughness(), 0.001);

        battle.dealAttackDamage(seele, enemy, 1.0, 0,
                DamageContext.of(DamageType.NORMAL, seele.getElement()));
        battle.breakToughness(seele, enemy, 1.0);
        Assertions.assertTrue(enemy.isBroken(), "toughness should hit zero");
        Assertions.assertNotNull(enemy.getBreakElement());
        Assertions.assertEquals(Element.QUANTUM, enemy.getBreakElement());
    }

    @Test
    public void testWrongElementDoesNotBreak() {
        Character danHeng = buildCharacter(1002); // wind
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        for (int i = 0; i < 5; i++) {
            enemy.reduceToughness(1.0, danHeng.getElement());
        }
        Assertions.assertFalse(enemy.isBroken(), "wind should not break quantum weakness");
        Assertions.assertEquals(30, enemy.getCurrentToughness(), 0.001);
    }

    @Test
    public void testBreakRestoresAndControl() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // quantum break = entanglement: control + damage on recovery
        battle.breakToughness(seele, enemy, 30);
        Assertions.assertTrue(enemy.isBroken());
        Assertions.assertEquals(Buff.ControlType.IMPRISONED, enemy.getControlState(),
                "quantum break should entangle");
        Assertions.assertFalse(enemy.getBuffs().isEmpty(), "a break-effect buff should be attached");
        enemy.recoverToughness();
        Assertions.assertFalse(enemy.isBroken());
        Assertions.assertEquals(30, enemy.getCurrentToughness(), 0.001);
    }

    @Test
    public void testHealTargetsAlliesNotEnemies() {
        Character natasha = buildCharacter(1105); // healer
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.PHYSICAL, Element.PHYSICAL, 30, "Trotter");
        Battle battle = new Battle(new ArrayList<>(List.of(natasha, seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double seeleBefore = seele.getCurrentHp();
        double enemyBefore = enemy.getCurrentHp();
        seele.takeDamage(300);

        // Natasha uses her skill, targeting the enemy — heal must go to allies instead.
        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(enemy));

        Assertions.assertTrue(seele.getCurrentHp() > seeleBefore - 300, "ally should be healed");
        Assertions.assertEquals(enemyBefore, enemy.getCurrentHp(), "enemy must NOT be healed");
    }

    @Test
    public void testEnergyGainAndUltimateCost() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.QUANTUM, Element.QUANTUM, 30, "Trotter");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double beforeHit = seele.getEnergy();
        battle.dealAttackDamage(enemy, seele, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(seele.getEnergy() > beforeHit, "being hit should gain energy");

        // using a skill should also give energy (skill = +30)
        double beforeSkill = seele.getEnergy();
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        Assertions.assertTrue(seele.getEnergy() > beforeSkill, "using a skill should gain energy");

        // ultimate cannot be cast before the energy bar is full
        Assertions.assertFalse(battle.castUltra(seele, List.of(enemy)),
                "ult should fail when energy is not full");
    }

    @Test
    public void testAggroTargeting() {
        Character tank = buildCharacter(1104); // Gepard, aggro 150
        Character dps = buildCharacter(1102);  // Seele, aggro 75
        Battle battle = new Battle(new ArrayList<>(List.of(tank, dps)), new ArrayList<>());
        battle.startBattle();

        // With 150 vs 75 aggro, the tank should be picked 2/3 of the time statistically.
        int tankHits = 0;
        int trials = 6000;
        for (int i = 0; i < trials; i++) {
            if (battle.selectTargetByAggro(battle.getAliveCharacters()) == tank) {
                tankHits++;
            }
        }
        // With 150 × 3.0 (刚正 trace, data param 3.0) vs 75 aggro, the tank is picked 6/7 of the time.
        double ratio = tankHits / (double) trials;
        Assertions.assertTrue(ratio > 0.83 && ratio < 0.88,
                "tank aggro ratio should be ~0.857, was " + ratio);
    }

    @Test
    public void testVictoryAndDefeat() {
        Character seele = buildCharacter(1102);
        Enemy weakEnemy = buildEnemy(Element.QUANTUM, Element.QUANTUM, 30, "Trotter");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(weakEnemy)));
        battle.startBattle();
        Assertions.assertFalse(battle.isOver());

        battle.dealAttackDamage(seele, weakEnemy, 9999, 0,
                DamageContext.of(DamageType.NORMAL, seele.getElement()));
        Assertions.assertTrue(weakEnemy.isDeath());
        battle.afterMove();
        Assertions.assertTrue(battle.isOver());
        Assertions.assertTrue(battle.isPlayerWon());
    }

    @Test
    public void testEffectHitChance() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.QUANTUM, Element.QUANTUM, 30, "Trotter");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 100% base chance with no modifiers must always land.
        Assertions.assertTrue(battle.checkEffectHit(seele, enemy, 1.0));
        // 0% base chance must never land.
        Assertions.assertFalse(battle.checkEffectHit(seele, enemy, 0.0));
    }

    // ─── 忆灵 system (HSR.md §2) ───────────────────────────────────────

    @Test
    public void testMemospriteInheritsStatsAndOwnsHp() {
        Character aglaea = buildCharacter(1402);
        Summon summon = new Summon(aglaea, 11402);

        Assertions.assertEquals("Garmentmaker", summon.getName().split(" ")[0]);
        // HP from the real formula: 44% 阿格莱雅生命上限 + 180 (talent params #5/#6).
        Assertions.assertEquals(aglaea.getMaxHp() * 0.44 + 180, summon.getMaxHp(), 1.0);
        // ATK inherited from the summoner (HSR.md §2.1).
        Assertions.assertEquals(aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(),
                summon.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 0.001);
        // Speed from the real formula: 35% of the summoner's SPD (talent #4).
        double expectedSpeed = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get() * 0.35;
        Assertions.assertEquals(expectedSpeed, summon.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(),
                0.001);
        // Modifying the summoner must NOT affect the memosprite (snapshot inheritance).
        aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK)
                .addModifier(DoubleValue.Modifier.addPercent(0.5));
        Assertions.assertNotEquals(aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(),
                summon.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 0.001);
    }

    @Test
    public void testMemospriteJoinsQueueAndIsTargetable() {
        Character aglaea = buildCharacter(1402);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(aglaea)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Aglaea's skill (sid 2) summons Garmentmaker.
        battle.executeSkill(aglaea.getSkills().get(SkillType.SKILL), aglaea, List.of(enemy));
        Assertions.assertFalse(aglaea.getSummons().isEmpty(), "memosprite should be summoned");
        Summon summon = aglaea.getSummons().getFirst();
        Assertions.assertFalse(summon.isDeath());

        // The memosprite is a valid target for enemies (HSR.md §2.1).
        Assertions.assertTrue(battle.getAlivePlayerUnits().contains(summon));
        // Re-summoning while alive must not duplicate.
        battle.executeSkill(aglaea.getSkills().get(SkillType.SKILL), aglaea, List.of(enemy));
        Assertions.assertEquals(1, aglaea.getSummons().size(), "no duplicate summons");
    }

    @Test
    public void testAhaSpeedFormula() {
        // HSR.md §3.2: 阿哈速度 = 80 + a/5 + b/10 + c/20 + d/50
        double speed = Aha.computeAhaSpeed(List.of(200.0, 150.0, 100.0));
        Assertions.assertEquals(80 + 200.0 / 5 + 150.0 / 10 + 100.0 / 20, speed, 0.001);
        // With no elation characters there is no meaningful speed; formula yields base.
        Assertions.assertEquals(80, Aha.computeAhaSpeed(List.of()), 0.001);
    }

    // ─── 欢愉 system (HSR.md §3) ───────────────────────────────────────

    @Test
    public void testElationSkillLoaded() {
        Character sparxie = buildCharacter(1501);
        Assertions.assertTrue(sparxie.getSkills().containsKey(SkillType.ELATION),
                "1501 should have an elation skill");
        Character seele = buildCharacter(1102);
        Assertions.assertFalse(seele.getSkills().containsKey(SkillType.ELATION),
                "1102 should not have an elation skill");
    }

    @Test
    public void testElationDamageScalesWithLaughPoints() {
        Character sparxie = buildCharacter(1501);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        // Zero out crit chance (base + trace tree) to keep the roll deterministic.
        sparxie.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE)
                .clearModifiers();
        sparxie.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE).base(0);
        double damageLow = DamageCalculator.calculateElationDamage(sparxie, enemy, 1.0, 0, 0, 0);
        double damageHigh = DamageCalculator.calculateElationDamage(sparxie, enemy, 1.0, 1000, 0, 0);
        Assertions.assertTrue(damageHigh > damageLow * 2, "laugh points must boost elation damage");
        // Elation damage must not scale with the attacker's ATK.
        double before = DamageCalculator.calculateElationDamage(sparxie, enemy, 1.0, 0, 0, 0);
        sparxie.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK)
                .addModifier(DoubleValue.Modifier.addPercent(1.0));
        double after = DamageCalculator.calculateElationDamage(sparxie, enemy, 1.0, 0, 0, 0);
        Assertions.assertEquals(before, after, 0.001, "elation damage ignores ATK (HSR.md §3.5)");
    }

    @Test
    public void testAhaMomentCastsElationSkillsAndConsumesLaughPoints() {
        Character sparxie = buildCharacter(1501);
        Character yaoguang = buildCharacter(1502);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, 30, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(sparxie, yaoguang)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(battle.getElationCharacters().size() >= 2);
        double enemyHpBefore = enemy.getCurrentHp();
        battle.addLaughPoints(500);

        battle.ahaMoment();
        Assertions.assertEquals(0, battle.getLaughPoints(), "laugh points must be consumed");
        Assertions.assertTrue(enemy.getCurrentHp() < enemyHpBefore, "aha moment must damage enemies");
    }
}
