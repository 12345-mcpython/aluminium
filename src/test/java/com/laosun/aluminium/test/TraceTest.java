package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 行迹技能 (trace passive) system of the 10** characters.
 */
public class TraceTest {

    private Character buildCharacter(int cid) {
        return Character.builder().cid(cid).level(80).isPromote().build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testAll10StarCharactersHaveTraces() {
        for (int cid : new int[]{1001, 1002, 1003, 1004, 1005, 1006, 1008, 1009, 1013}) {
            Character character = buildCharacter(cid);
            Assertions.assertEquals(3, character.getTraces().size(),
                    "cid " + cid + " should have 3 trace passives");
            for (Trace trace : character.getTraces()) {
                Assertions.assertNotNull(trace.getName());
            }
        }
    }

    @Test
    public void testMarchShieldExtension() {
        Character march = buildCharacter(1001);
        Character danHeng = buildCharacter(1002);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(march, danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // March casts her skill → shields all allies for 3 turns (data); 加护 extends to 4.
        battle.executeSkill(march.getSkills().get(SkillType.SKILL), march, List.of(enemy));
        Assertions.assertTrue(danHeng.getShield() > 0, "allies should be shielded");
        Assertions.assertEquals(4, danHeng.getShieldTurns(), "加护 should extend the shield by 1 turn");
    }

    @Test
    public void testMarchCleansesAlliesNotEnemies() {
        Character march = buildCharacter(1001);
        Character danHeng = buildCharacter(1002);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(march, danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Buff defDown = new Buff("DEF Down", Buff.Category.DEBUFF, enemy, danHeng, 2)
                .stat(AttributeType.DEFENCE, com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        danHeng.applyBuff(defDown);
        Buff enemyDebuff = new Buff("DEF Down", Buff.Category.DEBUFF, danHeng, enemy, 2)
                .stat(AttributeType.DEFENCE, com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        enemy.applyBuff(enemyDebuff);

        battle.executeSkill(march.getSkills().get(SkillType.SKILL), march, List.of(enemy));
        Assertions.assertFalse(danHeng.hasBuffNamed("DEF Down"), "ally debuff should be cleansed");
        Assertions.assertTrue(enemy.hasBuffNamed("DEF Down"), "enemy debuff must remain");
    }

    @Test
    public void testDanHengSlowSynergy() {
        Character danHeng = buildCharacter(1002);
        Enemy enemy = buildEnemy(Element.FIRE, Element.WIND, "Wind Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Apply Slow (罡风 boosts basic attacks vs slowed enemies).
        Buff slow = new Buff("Slow", Buff.Category.DEBUFF, danHeng, enemy, 2)
                .stat(AttributeType.SPEED, com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        enemy.applyBuff(slow);

        double multiplier = 1.0;
        for (Trace trace : danHeng.getTraces()) {
            multiplier *= trace.damageMultiplier(battle, danHeng, enemy, SkillType.COMMON);
        }
        Assertions.assertEquals(1.4, multiplier, 0.001, "罡风 should boost basic attacks vs slowed");
    }

    @Test
    public void testHimekoBeaconCritBuff() {
        Character himeko = buildCharacter(1003);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(himeko)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Battle start with full HP: 道标 active.
        Assertions.assertTrue(himeko.hasBuffNamed("道标"), "道标 should be active at high HP");

        // Drop below 80% HP: buff removed at turn start.
        himeko.takeDamage(himeko.getMaxHp() * 0.3);
        for (Trace trace : himeko.getTraces()) {
            trace.onTurnStart(battle, himeko);
        }
        Assertions.assertFalse(himeko.hasBuffNamed("道标"), "道标 should drop below 80% HP");
    }

    @Test
    public void testWeltUltGainsEnergy() {
        Character welt = buildCharacter(1004);
        Enemy enemy = buildEnemy(Element.FIRE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(welt)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double before = welt.getEnergy();
        battle.executeSkill(welt.getSkills().get(SkillType.ULTRA), welt, List.of(enemy));
        Assertions.assertTrue(welt.getEnergy() > before + 5, "审判 should restore extra energy");
        Assertions.assertTrue(enemy.hasBuffNamed("惩戒") || enemy.isDeath(),
                "惩戒 should apply vulnerability on ult targets");
    }

    @Test
    public void testKafkaUltDetonatesDots() {
        Character kafka = buildCharacter(1005);
        Enemy enemy = buildEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(kafka)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Buff.Dot dot = new Buff.Dot("Shock", kafka, enemy, 500, Element.THUNDER, 2);
        enemy.applyDot(dot);
        double before = enemy.getCurrentHp();
        battle.executeSkill(kafka.getSkills().get(SkillType.ULTRA), kafka, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < before - 300,
                "折磨 should detonate all DoTs on ult");
    }

    @Test
    public void testSilverWolfWeaknessImplant() {
        Character silverWolf = buildCharacter(1006);
        Enemy enemy = buildEnemy(Element.FIRE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(silverWolf)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 75% 基础概率 (战技数据) — 堆满效果命中使测试确定性成立.
        silverWolf.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        Assertions.assertFalse(enemy.isWeakTo(Element.QUANTUM));
        battle.executeSkill(silverWolf.getSkills().get(SkillType.SKILL), silverWolf, List.of(enemy));
        Assertions.assertTrue(enemy.isWeakTo(Element.QUANTUM),
                "Silver Wolf's skill should implant a quantum weakness (75% base chance)");
    }

    @Test
    public void testAstaFireTeamBoost() {
        Character asta = buildCharacter(1009);
        Character himeko = buildCharacter(1003);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(asta, himeko)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(himeko.hasBuffNamed("点燃"), "点燃 should buff the whole party's fire damage");
        double fireBoost = himeko.getAttribute(AttributeType.FIRE_DAMAGE_BOOST) != null
                ? himeko.getAttribute(AttributeType.FIRE_DAMAGE_BOOST).get() : 0;
        // 0.224 from Himeko's own trace tree + 0.18 from Asta's 点燃.
        Assertions.assertEquals(0.404, fireBoost, 0.001);
    }

    @Test
    public void testHertaControlResistance() {
        Character herta = buildCharacter(1013);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(herta)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(herta.hasBuffNamed("人偶"), "人偶 should grant control resistance at battle start");
        double res = herta.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        Assertions.assertEquals(0.35, res, 0.001);
    }

    @Test
    public void testArlanDefenceProtection() {
        Character arlan = buildCharacter(1008);
        Enemy enemy = buildEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        arlan.takeDamage(arlan.getMaxHp() * 0.6); // drop to 40% HP before battle
        Battle battle = new Battle(new ArrayList<>(List.of(arlan)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(arlan.hasBuffNamed("抗御"), "抗御 should activate below 50% HP");
        battle.dealAttackDamage(enemy, arlan, 10, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertFalse(arlan.hasBuffNamed("抗御"), "抗御 should break after being attacked");
    }
}
