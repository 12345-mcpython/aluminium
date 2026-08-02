package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Eidolons;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Tests for the 星魂 (eidolon) system of the 10** characters.
 */
public class EidolonTest {

    private Character buildCharacter(int cid) {
        return buildCharacter(cid, 6);
    }

    private Character buildCharacter(int cid, int eidolonLevel) {
        return Character.builder().cid(cid).level(80).isPromote().eidolon(eidolonLevel).build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testEidolonDataLoadedAndSkillBonuses() {
        // Rank 3: 终结技+2级, 普攻+1级 (1001) → skill levels should be boosted.
        Character march = buildCharacter(1001, 6);
        Assertions.assertEquals(6, march.getEidolonLevel());
        Assertions.assertEquals(7, march.getTraces().size(), "3 traces + 4 eidolon passives");

        Map<Integer, Integer> bonuses = Eidolons.skillLevelBonuses(1001, 6);
        Assertions.assertEquals(2, bonuses.get(3), "ult should gain +2 levels");
        Assertions.assertEquals(1, bonuses.get(1), "basic should gain +1 level");
    }

    @Test
    public void testMarchE1EnergyOnFreeze() {
        Character march = buildCharacter(1001);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(march)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Freeze the enemy first (as if by the ult), then cast ult.
        Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, march, enemy, 1)
                .control(Buff.ControlType.FROZEN);
        enemy.applyBuff(freeze);
        enemy.setControlState(Buff.ControlType.FROZEN);

        double before = march.getEnergy();
        battle.executeSkill(march.getSkills().get(SkillType.ULTRA), march, List.of(enemy));
        Assertions.assertTrue(march.getEnergy() > before + 5,
                "E1 should restore 6 energy per frozen target");
    }

    @Test
    public void testMarchE2BattleStartShield() {
        Character march = buildCharacter(1001);
        Character danHeng = buildCharacter(1002);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        danHeng.takeDamage(500); // make Dan Heng the lowest-HP ally
        Battle battle = new Battle(new ArrayList<>(List.of(march, danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(danHeng.getShield() > 0, "E2 should shield the lowest-HP ally at battle start");
        Assertions.assertEquals(3, danHeng.getShieldTurns());
    }

    @Test
    public void testDanHengE1CritBonus() {
        Character danHeng = buildCharacter(1002);
        Enemy fullHp = buildEnemy(Element.FIRE, Element.WIND, "Full HP");
        Enemy lowHp = buildEnemy(Element.FIRE, Element.WIND, "Low HP");
        lowHp.takeDamage(lowHp.getMaxHp() * 0.6);

        double critBonusHigh = 0;
        double critBonusLow = 0;
        for (Trace trace : danHeng.getTraces()) {
            critBonusHigh += trace.critChanceBonus(null, danHeng, fullHp, SkillType.SKILL);
            critBonusLow += trace.critChanceBonus(null, danHeng, lowHp, SkillType.SKILL);
        }
        Assertions.assertEquals(0.12, critBonusHigh, 0.001, "E1: crit bonus vs high-HP targets");
        Assertions.assertEquals(0.0, critBonusLow, 0.001);
    }

    @Test
    public void testDanHengE4ImmediateAction() {
        Character danHeng = buildCharacter(1002);
        Enemy enemy = buildEnemy(Element.FIRE, Element.WIND, "Wind Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Kill the enemy with the ult.
        battle.dealAttackDamage(danHeng, enemy, 99999, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, danHeng.getElement()));
        double remaining = battle.queue.timeUntilNext();
        // The E4 hook fires via afterAction during executeSkill.
        battle.executeSkill(danHeng.getSkills().get(SkillType.ULTRA), danHeng, List.of(enemy));
        Assertions.assertEquals(0, battle.queue.timeUntilNext(), 0.001,
                "E4: Dan Heng should act immediately after an ult kill");
    }

    @Test
    public void testDanHengE2WindPenOnAllySkill() {
        // 威制八毒: 天赋的冷却时间减少1回合 — 被友方战技指定后下一次攻击获得风属性抗性穿透.
        Character danHeng = buildCharacter(1002, 2);
        Character march = buildCharacter(1001);
        Enemy enemy = buildEnemy(Element.FIRE, Element.WIND, "Wind Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng, march)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(march.getSkills().get(SkillType.SKILL), march, List.of(danHeng));
        Assertions.assertTrue(danHeng.hasBuffNamed("寸长寸强"),
                "E2: ally skill targeting Dan Heng should grant wind RES pen");
    }

    @Test
    public void testWeltE4SkillSlowBoost() {
        // 义的名号: 战技减速基础概率 +35% (65% + 35% = 100%).
        Character welt = buildCharacter(1004, 4);
        Enemy enemy = buildEnemy(Element.FIRE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(welt)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(welt.getSkills().get(SkillType.SKILL), welt, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("Slow"), "E4: skill slow should always land");
    }

    @Test
    public void testAstaE2UltPreservesCharge() {
        // 月见圆缺之意: 施放终结技后, 下回合不会减少蓄能层数.
        Character asta = buildCharacter(1009, 2);
        Character himeko = buildCharacter(1003);
        Enemy enemy = buildEnemy(Element.FIRE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(asta, himeko)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 2 次战技 → 2 层蓄能.
        battle.addSkillPoints(5);
        for (int i = 0; i < 2; i++) {
            battle.executeSkill(asta.getSkills().get(SkillType.SKILL), asta, List.of(enemy));
        }
        Assertions.assertTrue(himeko.hasBuffNamed("天象学"), "charge should buff party ATK");

        // 终结技 → 下回合开始时蓄能层数不衰减.
        battle.executeSkill(asta.getSkills().get(SkillType.ULTRA), asta, List.of(enemy));
        Assertions.assertTrue(asta.hasBuffNamed("蓄能不衰"), "ult should set the no-decay marker");
        for (com.laosun.aluminium.models.Trace trace : asta.getTraces()) {
            trace.onTurnStart(battle, asta);
        }
        Assertions.assertTrue(himeko.hasBuffNamed("天象学"),
                "E2: charge must not decay after ult");
    }

    @Test
    public void testAstaE6ReducesChargeDecay() {
        // 眠于银河之下: 每回合减少的蓄能层数 3 → 2.
        Character asta = buildCharacter(1009, 6);
        Character himeko = buildCharacter(1003);
        Enemy enemy = buildEnemy(Element.FIRE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(asta, himeko)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(asta.hasBuffNamed("眠于银河之下"), "E6 marker buff at battle start");

        // 3 次战技 → 3 层蓄能; E6 使衰减从3降为2 → 剩1层仍有效 (无 E6 则归零).
        battle.addSkillPoints(5);
        for (int i = 0; i < 3; i++) {
            battle.executeSkill(asta.getSkills().get(SkillType.SKILL), asta, List.of(enemy));
        }
        for (com.laosun.aluminium.models.Trace trace : asta.getTraces()) {
            trace.onTurnStart(battle, asta);
        }
        Assertions.assertTrue(himeko.hasBuffNamed("天象学"),
                "E6: reduced decay should keep charge stacks");
    }

    @Test
    public void testHimekoE2LowHpBonus() {
        Character himeko = buildCharacter(1003);
        Enemy lowHp = buildEnemy(Element.FIRE, Element.ICE, "Low HP");
        lowHp.takeDamage(lowHp.getMaxHp() * 0.6);

        double multiplier = 1.0;
        for (Trace trace : himeko.getTraces()) {
            multiplier *= trace.damageMultiplier(null, himeko, lowHp, SkillType.SKILL);
        }
        Assertions.assertEquals(1.15, multiplier, 0.001, "E2: +15% vs low-HP targets");
    }

    @Test
    public void testKafkaE2PartyDotBoost() {
        Character kafka = buildCharacter(1005);
        Character march = buildCharacter(1001);
        Enemy enemy = buildEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, march)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double dotBoost = march.getAttribute(AttributeType.DOT_DAMAGE_BOOST) != null
                ? march.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get() : 0;
        Assertions.assertEquals(0.25, dotBoost, 0.001, "E2: party DoT damage +25%");
    }

    @Test
    public void testSilverWolfE2EnemyResistanceDown() {
        Character silverWolf = buildCharacter(1006);
        Enemy enemy = buildEnemy(Element.FIRE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(silverWolf)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double resistance = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE) != null
                ? enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get() : 0;
        Assertions.assertEquals(-0.2, resistance, 0.001, "E2: enemy effect RES -20%");
    }

    @Test
    public void testSilverWolfE6DebuffScaling() {
        Character silverWolf = buildCharacter(1006);
        Enemy enemy = buildEnemy(Element.FIRE, Element.PHYSICAL, "Physical Weakling");
        Buff debuff1 = new Buff("D1", Buff.Category.DEBUFF, silverWolf, enemy, 2);
        Buff debuff2 = new Buff("D2", Buff.Category.DEBUFF, silverWolf, enemy, 2);
        enemy.applyBuff(debuff1);
        enemy.applyBuff(debuff2);

        double multiplier = 1.0;
        for (Trace trace : silverWolf.getTraces()) {
            multiplier *= trace.damageMultiplier(null, silverWolf, enemy, SkillType.ULTRA);
        }
        Assertions.assertEquals(1.4, multiplier, 0.001, "E6: +20% per debuff");
    }

    @Test
    public void testArlanE4SurvivesFatalBlow() {
        Character arlan = buildCharacter(1008);
        Enemy enemy = buildEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(arlan)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertFalse(arlan.isDeath());
        battle.dealAttackDamage(enemy, arlan, 99999, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertFalse(arlan.isDeath(), "E4: should survive the fatal blow");
        Assertions.assertEquals(arlan.getMaxHp() * 0.25, arlan.getCurrentHp(), 1.0,
                "E4: revived at 25% max HP");
    }

    @Test
    public void testHertaE6UltAtkBuff() {
        Character herta = buildCharacter(1013);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(herta)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(herta.getSkills().get(SkillType.ULTRA), herta, List.of(enemy));
        Assertions.assertTrue(herta.hasBuffNamed("世上没人能负我"), "E6: ATK buff after ult");
    }
}
