package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;
import com.laosun.aluminium.models.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the hand-written 20*** light cone passives (三星光锥被动).
 */
public class TestWeaponPassives {

    private Character buildWithWeapon(int cid, int wid) {
        return Character.builder().cid(cid).level(80).isPromote()
                .weapon(Weapon.build(wid, 80)).build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100000, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    private Battle battleOf(List<Character> party, List<Enemy> enemies) {
        Battle battle = new Battle(new ArrayList<>(party), new ArrayList<>(enemies));
        battle.startBattle();
        return battle;
    }

    private double attr(Character c, AttributeType type) {
        var value = c.getAttribute(type);
        return value != null ? value.get() : 0;
    }

    private double traceDmgMultiplier(Character c, Battle battle, Enemy enemy, SkillType type) {
        double mult = 1.0;
        for (Trace trace : c.getTraces()) {
            mult *= trace.damageMultiplier(battle, c, enemy, type);
        }
        return mult;
    }

    // ─── 20000 锋镝: 战斗开始暴击率+12%持续3回合 ───────────────────────

    @Test
    public void testArrowsStartCrit() {
        Character seele = buildWithWeapon(1102, 20000);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("锋镝"), "锋镝 should grant crit at battle start");
        double crit = attr(seele, AttributeType.CRIT_CHANCE);
        double base = 0.05;
        Assertions.assertEquals(base + 0.12, crit, 0.001, "crit +12% (base 5%)");
    }

    // ─── 20001 物穰: 战技/终结技后治疗量+12% ───────────────────────────

    @Test
    public void testGrainHealBoostAfterSkill() {
        Character natasha = buildWithWeapon(1105, 20001);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha), List.of(enemy));

        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(enemy));
        Assertions.assertTrue(natasha.hasBuffNamed("物穰"), "物穰 should buff healing after skill");
        double boost = attr(natasha, AttributeType.OUTGOING_HEALING_BOOST);
        Assertions.assertTrue(boost >= 0.12, "healing boost +12%, got " + boost);
    }

    // ─── 20002 天倾: 普攻和战技伤害+20% ────────────────────────────────

    @Test
    public void testSkyfallBasicAndSkill() {
        Character danHeng = buildWithWeapon(1002, 20002);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Assertions.assertEquals(1.2, traceDmgMultiplier(danHeng, null, enemy, SkillType.COMMON), 0.001);
        Assertions.assertEquals(1.2, traceDmgMultiplier(danHeng, null, enemy, SkillType.SKILL), 0.001);
        Assertions.assertEquals(1.0, traceDmgMultiplier(danHeng, null, enemy, SkillType.ULTRA), 0.001);
    }

    // ─── 20003 琥珀: 生命<50%时防御额外+16% (永久+16%在属性中) ─────────

    @Test
    public void testAmberConditionalDef() {
        Character gepard = buildWithWeapon(1104, 20003);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));

        double defBase = attr(gepard, AttributeType.DEFENCE);
        Assertions.assertTrue(defBase > 0, "permanent 16% DEF from props");
        gepard.takeDamage(gepard.getMaxHp() * 0.6);
        for (Trace trace : gepard.getTraces()) {
            trace.onTurnStart(battle, gepard);
        }
        Assertions.assertTrue(gepard.hasBuffNamed("琥珀"),
                "琥珀 should grant extra DEF below 50% HP");
    }

    // ─── 20004 幽邃: 战斗开始效果命中+20%持续3回合 ─────────────────────

    @Test
    public void testAbyssStartEhr() {
        Character pela = buildWithWeapon(1106, 20004);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(pela), List.of(enemy));

        Assertions.assertTrue(pela.hasBuffNamed("幽邃"));
        Assertions.assertTrue(attr(pela, AttributeType.EFFECT_HIT_RATE) >= 0.2);
    }

    // ─── 20005 齐颂: 进入战斗我方全体攻击力+8% ─────────────────────────

    @Test
    public void testHymnPartyAtk() {
        Character bronya = buildWithWeapon(1101, 20005);
        Character seele = buildWithWeapon(1102, 20000);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("齐颂"), "齐颂 should buff the whole party");
        Assertions.assertTrue(attr(seele, AttributeType.ATTACK) > 0);
    }

    // ─── 20006 智库: 终结技伤害+28% ────────────────────────────────────

    @Test
    public void testLibraryUltOnly() {
        Character herta = buildWithWeapon(1013, 20006);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Assertions.assertEquals(1.28, traceDmgMultiplier(herta, null, enemy, SkillType.ULTRA), 0.001);
        Assertions.assertEquals(1.0, traceDmgMultiplier(herta, null, enemy, SkillType.COMMON), 0.001);
    }

    // ─── 20007 离弦: 消灭目标后攻击力+24%持续3回合 ─────────────────────

    @Test
    public void testBowstringKillAtk() {
        Character seele = buildWithWeapon(1102, 20007);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double atkBefore = attr(seele, AttributeType.ATTACK);
        battle.dealAttackDamage(seele, enemy, 99999, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.QUANTUM));
        Assertions.assertTrue(enemy.isDeath(), "enemy should die");
        Assertions.assertTrue(seele.hasBuffNamed("离弦"), "离弦 should buff ATK after a kill");
        Assertions.assertTrue(attr(seele, AttributeType.ATTACK) > atkBefore * 1.05,
                "离弦 should raise ATK (+24%) after a kill");
    }

    // ─── 20008 嘉果: 战斗开始全体恢复6能量 ──────────────────────────────

    @Test
    public void testFruitPartyEnergy() {
        Character natasha = buildWithWeapon(1105, 20008);
        Character seele = buildWithWeapon(1102, 20008);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha, seele), List.of(enemy));

        Assertions.assertTrue(seele.getEnergy() >= 6, "嘉果 should restore 6 energy at battle start");
        Assertions.assertTrue(natasha.getEnergy() >= 6);
    }

    // ─── 20009 乐圮: 对生命>50%目标伤害+20% ────────────────────────────

    @Test
    public void testRuinHighHpTargets() {
        Character arlan = buildWithWeapon(1008, 20009);
        Enemy highHp = buildEnemy(Element.ICE, Element.THUNDER, "Full");
        Enemy lowHp = buildEnemy(Element.ICE, Element.THUNDER, "Low");
        lowHp.takeDamage(lowHp.getMaxHp() * 0.6);
        Assertions.assertEquals(1.2, traceDmgMultiplier(arlan, null, highHp, SkillType.SKILL), 0.001);
        Assertions.assertEquals(1.0, traceDmgMultiplier(arlan, null, lowHp, SkillType.SKILL), 0.001);
    }

    // ─── 20010 戍御: 施放终结技时回复生命上限18% ───────────────────────

    @Test
    public void testBastionUltHeal() {
        Character gepard = buildWithWeapon(1104, 20010);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));

        gepard.takeDamage(gepard.getMaxHp() * 0.5);
        double before = gepard.getCurrentHp();
        gepard.gainEnergy(gepard.getMaxEnergy());
        battle.executeSkill(gepard.getSkills().get(SkillType.ULTRA), gepard, List.of(enemy));
        Assertions.assertTrue(gepard.getCurrentHp() > before + gepard.getMaxHp() * 0.15,
                "戍御 should heal 18% max HP on ult");
    }

    // ─── 20011 渊环: 对减速目标伤害+24% ────────────────────────────────

    @Test
    public void testDepthVsSlowed() {
        Character welt = buildWithWeapon(1004, 20011);
        Enemy slowed = buildEnemy(Element.ICE, Element.IMAGINARY, "S");
        Buff slow = new Buff("Slow", Buff.Category.DEBUFF, welt, slowed, 2)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-0.1));
        slowed.applyBuff(slow);
        Enemy normal = buildEnemy(Element.ICE, Element.IMAGINARY, "N");
        Assertions.assertEquals(1.24, traceDmgMultiplier(welt, null, slowed, SkillType.SKILL), 0.001);
        Assertions.assertEquals(1.0, traceDmgMultiplier(welt, null, normal, SkillType.SKILL), 0.001);
    }

    // ─── 20012 轮契: 攻击/受击后+4能量 (每回合1次) ─────────────────────

    @Test
    public void testTurningWheelEnergy() {
        Character seele = buildWithWeapon(1102, 20012);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double before = seele.getEnergy();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        // 普攻基础回能 20 + 轮契 4.
        Assertions.assertEquals(24, seele.getEnergy() - before, 0.001, "轮契 +4 energy per attack");
        // 同一回合内第二次攻击不再触发 (仅基础 20).
        double before2 = seele.getEnergy();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        Assertions.assertEquals(20, seele.getEnergy() - before2, 0.001, "once per turn");
    }

    // ─── 20013 灵钥: 战技后+8能量 (每回合1次) ──────────────────────────

    @Test
    public void testKeySkillEnergy() {
        Character seele = buildWithWeapon(1102, 20013);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        battle.addSkillPoints(5);
        seele.consumeEnergy();
        double before = seele.getEnergy();
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        // 战技基础回能 30 + 灵钥 8.
        Assertions.assertEquals(38, seele.getEnergy() - before, 0.001, "灵钥 +8 energy per skill");
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        Assertions.assertEquals(30, seele.getEnergy() - before - 38, 0.001, "once per turn");
    }

    // ─── 20014 相抗: 消灭目标后速度+10%持续2回合 ───────────────────────

    @Test
    public void testResistKillSpeed() {
        Character seele = buildWithWeapon(1102, 20014);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double spdBefore = attr(seele, AttributeType.SPEED);
        battle.dealAttackDamage(seele, enemy, 99999, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.QUANTUM));
        Assertions.assertTrue(attr(seele, AttributeType.SPEED) > spdBefore * 1.08,
                "相抗 should raise SPD by 10% after a kill");
    }

    // ─── 20015 蕃息: 普攻后行动提前12% ─────────────────────────────────

    @Test
    public void testGrowthBasicAdvance() {
        Character seele = buildWithWeapon(1102, 20015);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining,
                "蕃息 should advance the next action after a basic attack");
    }

    // ─── 20016 俱殁: 生命<80%时暴击率+12% ──────────────────────────────

    @Test
    public void testPerishLowHpCrit() {
        Character arlan = buildWithWeapon(1008, 20016);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        arlan.takeDamage(arlan.getMaxHp() * 0.3);
        double bonus = 0;
        for (Trace trace : arlan.getTraces()) {
            bonus += trace.critChanceBonus(null, arlan, enemy, SkillType.SKILL);
        }
        Assertions.assertEquals(0.12, bonus, 0.001, "俱殁 +12% crit below 80% HP");
    }

    // ─── 20017 开疆: 击破弱点时回复生命上限12% ─────────────────────────

    @Test
    public void testFrontierBreakHeal() {
        Character welt = buildWithWeapon(1004, 20017);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(welt), List.of(enemy));

        welt.takeDamage(welt.getMaxHp() * 0.5);
        double before = welt.getCurrentHp();
        enemy.reduceToughness(30, Element.IMAGINARY);
        Assertions.assertTrue(enemy.isBroken());
        for (Trace trace : welt.getTraces()) {
            trace.onEnemyBreak(battle, welt, enemy);
        }
        Assertions.assertTrue(welt.getCurrentHp() > before + welt.getMaxHp() * 0.1,
                "开疆 should heal 12% max HP on break");
    }

    // ─── 20018 匿影: 战技后下一次普攻附加60%攻击力伤害 ─────────────────

    @Test
    public void testConcealExtraHit() {
        Character seele = buildWithWeapon(1102, 20018);
        noCrit(seele);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        battle.addSkillPoints(5);
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        double atk = attr(seele, AttributeType.ATTACK);
        Assertions.assertTrue(hpBefore - enemy.getCurrentHp() > atk * 0.5,
                "匿影 should add a 60% ATK extra hit on the next basic");
    }

    // ─── 20019 调和: 进入战斗全体速度+12点持续1回合 ────────────────────

    @Test
    public void testHarmonyPartySpeed() {
        Character bronya = buildWithWeapon(1101, 20019);
        Character seele = buildWithWeapon(1102, 20019);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("调和"), "调和 should buff the party's speed");
        Buff buff = seele.getBuffs().stream()
                .filter(b -> "调和".equals(b.getName())).findFirst().orElseThrow();
        Assertions.assertEquals(12.0, buff.getModifiers().getFirst().modifier().getValue(), 0.001);
    }

    // ─── 20020 睿见: 施放终结技时攻击力+24%持续2回合 ───────────────────

    @Test
    public void testForesightUltAtk() {
        Character danHeng = buildWithWeapon(1002, 20020);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(danHeng), List.of(enemy));

        double atkBefore = attr(danHeng, AttributeType.ATTACK);
        danHeng.gainEnergy(danHeng.getMaxEnergy());
        battle.executeSkill(danHeng.getSkills().get(SkillType.ULTRA), danHeng, List.of(enemy));
        Assertions.assertTrue(attr(danHeng, AttributeType.ATTACK) > atkBefore * 1.2,
                "睿见 should raise ATK by 24% after the ult");
    }

    // ─── 20021 焚影: 首次召唤忆灵时恢复1战技点+12能量 ──────────────────

    @Test
    public void testPyreFirstSummon() {
        Character aglaea = buildWithWeapon(1402, 20021);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(aglaea), List.of(enemy));

        int spBefore = battle.getSkillPoints();
        double energyBefore = aglaea.getEnergy();
        battle.executeSkill(aglaea.getSkills().get(SkillType.SKILL), aglaea, List.of(enemy));
        Assertions.assertFalse(aglaea.getSummons().isEmpty(), "衣匠 should be summoned");
        // 战技消耗 1 SP 由 焚影 返还 → 净消耗为 0.
        Assertions.assertTrue(battle.getSkillPoints() >= spBefore,
                "焚影 should refund the skill point on the first summon");
        Assertions.assertTrue(aglaea.getEnergy() > energyBefore + 40,
                "焚影 should restore 12 energy on the first summon (skill gives 30 + 12)");
    }

    // ─── 20022 溯忆: 忆灵在场时每回合1层【缅怀】(伤害+8%/层, 最多4层) ──

    @Test
    public void testRecallMourningStacks() {
        Character aglaea = buildWithWeapon(1402, 20022);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(aglaea), List.of(enemy));

        battle.summonRequest(aglaea);
        Assertions.assertFalse(aglaea.getSummons().isEmpty());
        for (Trace trace : aglaea.getTraces()) {
            trace.onTurnStart(battle, aglaea);
        }
        Assertions.assertTrue(aglaea.hasBuffNamed("缅怀"), "溯忆 should stack 缅怀 with a memosprite");
    }

    // ─── 20023 嗤笑: 阿哈时刻发动时欢愉度+16% ─────────────────────────

    @Test
    public void testSnickerAhaMoment() {
        Character sparxie = buildWithWeapon(1501, 20023);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));

        battle.ahaMoment();
        Assertions.assertTrue(sparxie.hasBuffNamed("嗤笑"),
                "嗤笑 should buff 欢愉度 during the Aha Moment");
        double elation = attr(sparxie, AttributeType.ELATION_DAMAGE_BOOST);
        Assertions.assertTrue(elation >= 0.16, "欢愉度 +16%, got " + elation);
    }

    // ─── 20024 残泪: 笑点≥10时暴击伤害+20% ─────────────────────────────

    @Test
    public void testTearsLaughCritDamage() {
        Character sparxie = buildWithWeapon(1501, 20024);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));

        Assertions.assertFalse(sparxie.hasBuffNamed("残泪"), "no buff below 10 laugh points");
        battle.addLaughPoints(10);
        for (Trace trace : sparxie.getTraces()) {
            trace.onTurnStart(battle, sparxie);
        }
        Assertions.assertTrue(sparxie.hasBuffNamed("残泪"),
                "残泪 should raise crit damage with ≥10 laugh points");
        double cdmg = attr(sparxie, AttributeType.CRIT_ATTACK);
        Assertions.assertTrue(cdmg >= 0.7, "crit damage +20%, got " + cdmg);
    }

    private void noCrit(Character character) {
        character.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        character.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        character.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        character.getAttribute(AttributeType.CRIT_ATTACK).base(0);
    }
}
