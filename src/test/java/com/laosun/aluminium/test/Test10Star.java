package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 10** characters' skills (技能): the data-driven special
 * behaviors implemented in {@code DataSkill} (freeze/imprison/detonation/
 * HP cost/speed buff...).
 */
public class Test10Star {

    private Character buildCharacter(int cid) {
        return Character.builder().cid(cid).level(80).isPromote().build();
    }

    private Enemy buildTankyEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100000, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testMarchUltFreezes() {
        Character march = buildCharacter(1001);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(march)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 50% 基础概率 + 冰咒 15% — 堆满效果命中使测试确定性成立.
        march.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(march.getSkills().get(SkillType.ULTRA), march, List.of(enemy));
        Assertions.assertEquals(Buff.ControlType.FROZEN, enemy.getControlState(),
                "March's ult should freeze enemies");
    }

    @Test
    public void testDanHengUltBonusVsSlowed() {
        Character danHeng = buildCharacter(1002);
        // 消除随机暴击, 使伤害对比确定.
        danHeng.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        danHeng.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        danHeng.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        danHeng.getAttribute(AttributeType.CRIT_ATTACK).base(0);

        Enemy slowed = buildTankyEnemy(Element.FIRE, Element.WIND, "Slowed Foe");
        Enemy normal = buildTankyEnemy(Element.FIRE, Element.WIND, "Normal Foe");
        Buff slow = new Buff("Slow", Buff.Category.DEBUFF, danHeng, slowed, 2)
                .stat(AttributeType.SPEED, com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        slowed.applyBuff(slow);

        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)),
                new ArrayList<>(List.of(slowed, normal)));
        battle.startBattle();

        battle.executeSkill(danHeng.getSkills().get(SkillType.ULTRA), danHeng, List.of(slowed));
        double hpSlowed = slowed.getMaxHp() - slowed.getCurrentHp();
        // 减速目标受到 (1 + 72%) 倍率, 另一个目标吃同样的技能倍率 — 比较两次独立施放.
        battle.executeSkill(danHeng.getSkills().get(SkillType.ULTRA), danHeng, List.of(normal));
        double hpNormal = normal.getMaxHp() - normal.getCurrentHp();
        Assertions.assertTrue(hpSlowed > hpNormal * 1.5,
                "ult should deal +72% vs slowed targets, got " + hpSlowed + " vs " + hpNormal);
    }

    @Test
    public void testHimekoUltEnergyPerKill() {
        Character himeko = buildCharacter(1003);
        Enemy weakEnemy = Enemy.fromTemplate("Weakling", 80,
                1, 26, 240, 120, 30,
                Element.FIRE, EnumSet.of(Element.FIRE), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", Element.FIRE, 0.5, SkillAttackType.SINGLE)));
        Battle battle = new Battle(new ArrayList<>(List.of(himeko)), new ArrayList<>(List.of(weakEnemy)));
        battle.startBattle();

        double before = himeko.getEnergy();
        battle.executeSkill(himeko.getSkills().get(SkillType.ULTRA), himeko, List.of(weakEnemy));
        Assertions.assertTrue(weakEnemy.isDeath(), "ult should kill the weak enemy");
        Assertions.assertTrue(himeko.getEnergy() > before + 4,
                "ult should restore +5 energy per kill");
    }

    @Test
    public void testWeltSkillSlows() {
        Character welt = buildCharacter(1004);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(welt)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 战技减速基础概率 65% — 堆满效果命中使测试确定性成立.
        welt.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(welt.getSkills().get(SkillType.SKILL), welt, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("Slow"), "Welt's skill should slow (65% base chance)");
    }

    @Test
    public void testWeltUltImprisons() {
        Character welt = buildCharacter(1004);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(welt)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 禁锢基础概率 100%.
        battle.executeSkill(welt.getSkills().get(SkillType.ULTRA), welt, List.of(enemy));
        Assertions.assertEquals(Buff.ControlType.IMPRISONED, enemy.getControlState(),
                "Welt's ult should imprison enemies");
    }

    @Test
    public void testKafkaSkillDetonatesDots() {
        Character kafka = buildCharacter(1005);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Buff.Dot dot = new Buff.Dot("Shock", kafka, enemy, 1000, Element.THUNDER, 3);
        enemy.applyDot(dot);
        Battle battle = new Battle(new ArrayList<>(List.of(kafka)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double before = enemy.getCurrentHp();
        battle.executeSkill(kafka.getSkills().get(SkillType.SKILL), kafka, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < before - 400,
                "Kafka's skill should detonate DoTs at 60%");
    }

    @Test
    public void testKafkaUltShocks() {
        Character kafka = buildCharacter(1005);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(kafka)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 触电基础概率 100%.
        battle.executeSkill(kafka.getSkills().get(SkillType.ULTRA), kafka, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.THUNDER), "Kafka's ult should shock");
    }

    @Test
    public void testArlanSkillConsumesHp() {
        Character arlan = buildCharacter(1008);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(arlan)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double before = arlan.getCurrentHp();
        battle.executeSkill(arlan.getSkills().get(SkillType.SKILL), arlan, List.of(enemy));
        Assertions.assertEquals(arlan.getMaxHp() * 0.15, before - arlan.getCurrentHp(), 1.0,
                "Arlan's skill should consume 15% max HP");
        Assertions.assertFalse(arlan.isDeath(), "Arlan must survive the HP cost");
    }

    @Test
    public void testAstaUltSpeedBuff() {
        Character asta = buildCharacter(1009);
        Character himeko = buildCharacter(1003);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(asta, himeko)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double before = himeko.getAttribute(AttributeType.SPEED).get();
        battle.executeSkill(asta.getSkills().get(SkillType.ULTRA), asta, List.of(enemy));
        double after = himeko.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(after > before + 30,
                "Asta's ult should buff party speed by 36, got " + (after - before));
    }

    @Test
    public void testHertaSkillHighHpBonus() {
        Character herta = buildCharacter(1013);
        // 消除随机暴击, 使伤害对比确定.
        herta.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        herta.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        herta.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        herta.getAttribute(AttributeType.CRIT_ATTACK).base(0);

        Enemy full = buildTankyEnemy(Element.FIRE, Element.ICE, "Full HP Foe");
        Enemy hurt = buildTankyEnemy(Element.FIRE, Element.ICE, "Hurt Foe");
        hurt.takeDamage(hurt.getMaxHp() * 0.6); // 40% HP → 不满足 ≥50% 条件

        Battle battle = new Battle(new ArrayList<>(List.of(herta)),
                new ArrayList<>(List.of(full, hurt)));
        battle.startBattle();

        double beforeFull = full.getCurrentHp();
        double beforeHurt = hurt.getCurrentHp();
        battle.executeSkill(herta.getSkills().get(SkillType.SKILL), herta, List.of(full));
        double dmgFull = beforeFull - full.getCurrentHp();
        double dmgHurt = beforeHurt - hurt.getCurrentHp();
        Assertions.assertTrue(dmgFull > dmgHurt * 1.15,
                "Herta's skill should deal +20% vs targets ≥50% HP, got " + dmgFull + " vs " + dmgHurt);
    }

    @Test
    public void testSilverWolfUltDefDown() {
        Character silverWolf = buildCharacter(1006);
        Enemy enemy = buildTankyEnemy(Element.FIRE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(silverWolf)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // 防御力降低基础概率 85% — 堆满效果命中使测试确定性成立.
        silverWolf.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(silverWolf.getSkills().get(SkillType.ULTRA), silverWolf, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("DEF Down"), "Silver Wolf's ult should reduce DEF");
    }
}
