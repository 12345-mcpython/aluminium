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
 * Tests for the hand-written 21*** light cone passives (四星光锥被动).
 */
public class TestWeaponPassives4Star {

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

    private double dmgMult(Character c, Battle battle, Enemy enemy, SkillType type) {
        double mult = 1.0;
        for (Trace trace : c.getTraces()) {
            mult *= trace.damageMultiplier(battle, c, enemy, type);
        }
        return mult;
    }

    // ─── 21000 一场术后对话: 终结技后治疗量+12% ────────────────────────

    @Test
    public void testPostOpUltHealBoost() {
        Character natasha = buildWithWeapon(1105, 21000);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha), List.of(enemy));

        battle.executeSkill(natasha.getSkills().get(SkillType.ULTRA), natasha, List.of(enemy));
        Assertions.assertTrue(natasha.hasBuffNamed("一场术后对话"));
        Assertions.assertTrue(attr(natasha, AttributeType.OUTGOING_HEALING_BOOST) >= 0.1);
    }

    // ─── 21001 晚安与睡颜: 每负面效果伤害+12% (最多3层) ────────────────

    @Test
    public void testGoodnightPerDebuff() {
        Character pela = buildWithWeapon(1106, 21001);
        Enemy clean = buildEnemy(Element.ICE, Element.ICE, "Clean");
        Enemy debuffed = buildEnemy(Element.ICE, Element.ICE, "Debuffed");
        for (int i = 0; i < 2; i++) {
            debuffed.applyBuff(new Buff("D" + i, Buff.Category.DEBUFF, pela, debuffed, 2));
        }
        Assertions.assertEquals(1.0, coneDmgMult(pela, null, clean, SkillType.SKILL, "晚安与睡颜"), 0.001);
        Assertions.assertEquals(1.24, coneDmgMult(pela, null, debuffed, SkillType.SKILL, "晚安与睡颜"), 0.001,
                "每负面效果+12%");
    }

    // ─── 21002 余生的第一天: 进入战斗我方全体减伤8% ────────────────────

    @Test
    public void testFirstDayPartyReduction() {
        Character gepard = buildWithWeapon(1104, 21002);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard, seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("余生的第一天"));
    }

    // ─── 21003 唯有沉默: 敌方≤2时暴击率+12% ───────────────────────────

    @Test
    public void testOnlySilenceFewEnemies() {
        Character seele = buildWithWeapon(1102, 21003);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double bonus = 0;
        for (Trace trace : seele.getTraces()) {
            bonus += trace.critChanceBonus(battle, seele, enemy, SkillType.SKILL);
        }
        Assertions.assertTrue(bonus >= 0.12, "≤2 enemies → crit +12%");
    }

    // ─── 21004 记忆中的模样: 攻击后+4能量 (每回合1次) ───────────────────

    @Test
    public void testMemoryShapeEnergy() {
        Character bronya = buildWithWeapon(1101, 21004);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya), List.of(enemy));

        double before = bronya.getEnergy();
        battle.executeSkill(bronya.getSkills().get(SkillType.COMMON), bronya, List.of(enemy));
        Assertions.assertEquals(24, bronya.getEnergy() - before, 0.001, "basic 20 + 记忆中的模样 4");
    }

    // ─── 21005 鼹鼠党欢迎你: 每次攻击1层淘气值 (攻击+12%/层) ───────────

    @Test
    public void testMoleStacks() {
        Character arlan = buildWithWeapon(1008, 21005);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(arlan), List.of(enemy));

        double atkBefore = attr(arlan, AttributeType.ATTACK);
        battle.executeSkill(arlan.getSkills().get(SkillType.COMMON), arlan, List.of(enemy));
        Assertions.assertTrue(attr(arlan, AttributeType.ATTACK) > atkBefore,
                "淘气值 should raise ATK");
    }

    // ─── 21006 「我」的诞生: 低血量目标额外增伤 ────────────────────────

    @Test
    public void testMyBirthLowHpBonus() {
        Character herta = buildWithWeapon(1013, 21006);
        Enemy low = buildEnemy(Element.ICE, Element.ICE, "Low");
        low.takeDamage(low.getMaxHp() * 0.6);
        Enemy high = buildEnemy(Element.ICE, Element.ICE, "High");
        double lowMult = dmgMult(herta, null, low, SkillType.SKILL);
        double highMult = dmgMult(herta, null, high, SkillType.SKILL);
        Assertions.assertEquals(1.48 / 1.24, lowMult / highMult, 0.01,
                "低血量目标应获得额外24%增伤");
    }

    // ─── 21009 朗道的选择: 仇恨×2 + 减伤16% ────────────────────────────

    @Test
    public void testLandauAggroAndReduction() {
        Character gepard = buildWithWeapon(1104, 21009);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));

        double aggro = 1.0;
        for (Trace trace : gepard.getTraces()) {
            aggro *= trace.aggroMultiplier(battle, gepard);
        }
        Assertions.assertTrue(aggro >= 2.0, "朗道应使仇恨至少×2");
    }

    // ─── 21012 秘密誓心: 目标生命%≥自身时额外+20% ─────────────────────

    @Test
    public void testSolemnVowHpCompare() {
        Character arlan = buildWithWeapon(1008, 21012);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        arlan.takeDamage(arlan.getMaxHp() * 0.5);
        Assertions.assertEquals(1.2, dmgMult(arlan, null, enemy, SkillType.SKILL), 0.001,
                "enemy full HP ≥ own 50% → +20%");
    }

    // ─── 21013 别让世界静下来: 进入战斗+20能量, 终结技+32% ────────────

    @Test
    public void testWorldQuietStartEnergyAndUlt() {
        Character danHeng = buildWithWeapon(1002, 21013);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(danHeng), List.of(enemy));

        Assertions.assertTrue(danHeng.getEnergy() >= 20, "battle start +20 energy");
        Assertions.assertEquals(1.32, dmgMult(danHeng, battle, enemy, SkillType.ULTRA), 0.001);
        Assertions.assertEquals(1.0, dmgMult(danHeng, battle, enemy, SkillType.COMMON), 0.001);
    }

    // ─── 21015 决心如汗珠般闪耀: 击中后60%基础概率施加攻陷 (防御-12%) ──

    @Test
    public void testResolveDefDown() {
        Character pela = buildWithWeapon(1106, 21015);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(pela), List.of(enemy));

        pela.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(pela.getSkills().get(SkillType.COMMON), pela, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("攻陷"), "攻陷 should apply with high EHR");
    }

    // ─── 21016 宇宙市场趋势: 受击后100%基础概率灼烧 (防御力40%) ───────

    @Test
    public void testMarketTrendBurnOnHit() {
        Character gepard = buildWithWeapon(1104, 21016);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));

        battle.dealAttackDamage(enemy, gepard, 0.5, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.FIRE),
                "受到攻击后应使攻击者灼烧");
    }

    // ─── 21017 点个关注吧！: 普攻/战技+24%, 能量满额外+24% ────────────

    @Test
    public void testFollowBasicSkillBonus() {
        Character seele = buildWithWeapon(1102, 21017);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        Assertions.assertEquals(1.24, dmgMult(seele, battle, enemy, SkillType.COMMON), 0.001);
        Assertions.assertEquals(1.24, dmgMult(seele, battle, enemy, SkillType.SKILL), 0.001);
        seele.consumeEnergy();
        seele.gainEnergy(seele.getMaxEnergy());
        Assertions.assertEquals(1.48, dmgMult(seele, battle, enemy, SkillType.COMMON), 0.001,
                "满能量时额外+24%");
    }

    // ─── 21018 舞！舞！舞！: 终结技后全体行动提前16% ────────────────────

    @Test
    public void testDanceDanceUltAdvance() {
        Character bronya = buildWithWeapon(1101, 21018);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = slowEnemy(Element.WIND);
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining * 0.95,
                "舞舞舞 should advance the party after the ult");
    }

    // ─── 21021 等价交换: 回合开始时为能量<50%的队友恢复8能量 ───────────

    @Test
    public void testEquivalentEnergyShare() {
        Character natasha = buildWithWeapon(1105, 21021);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha, seele), List.of(enemy));

        seele.consumeEnergy();
        double before = seele.getEnergy();
        for (Trace trace : natasha.getTraces()) {
            trace.onTurnStart(battle, natasha);
        }
        Assertions.assertTrue(seele.getEnergy() > before + 7,
                "等价交换 should restore energy to a low-energy ally");
    }

    // ─── 21023 我们是地火: 战斗开始全体回血+减伤8%持续5回合 ───────────

    @Test
    public void testGroundfirePartyHeal() {
        Character firefly = buildWithWeapon(1310, 21023);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        seele.takeDamage(seele.getMaxHp() * 0.5);
        double before = seele.getCurrentHp();
        Battle battle = battleOf(List.of(firefly, seele), List.of(enemy));

        Assertions.assertTrue(seele.getCurrentHp() > before,
                "我们是地火 should heal lost HP at battle start");
        Assertions.assertTrue(seele.hasBuffNamed("我们是地火"));
    }

    // ─── 21024 春水初生: 进入战斗速度+8%伤害+12%, 受击后失效 ──────────

    @Test
    public void testSpringsSpeedAndDmg() {
        Character seele = buildWithWeapon(1102, 21024);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("春水初生"));
        double spd = attr(seele, AttributeType.SPEED);
        battle.dealAttackDamage(enemy, seele, 0.1, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertFalse(seele.hasBuffNamed("春水初生"),
                "受到伤害后增益应失效");
        Assertions.assertTrue(attr(seele, AttributeType.SPEED) < spd,
                "速度加成应随增益消失");
    }

    // ─── 21025 过往未来: 战技后下一个行动的我方其他目标伤害+16% ────────

    @Test
    public void testPastFutureNextAlly() {
        Character bronya = buildWithWeapon(1101, 21025);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
        boolean anyBuff = seele.getBuffs().stream()
                .anyMatch(b -> "过往未来".equals(b.getName()));
        Assertions.assertTrue(anyBuff, "下个行动的队友应获得增伤");
    }

    // ─── 21027 早餐的仪式感: 每消灭1个目标攻击+4% (最多3层) ────────────

    @Test
    public void testBreakfastKillStacks() {
        Character herta = buildWithWeapon(1013, 21027);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(herta), List.of(enemy));

        battle.dealAttackDamage(herta, enemy, 99999, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
        Assertions.assertTrue(herta.hasBuffNamed("早餐的仪式感"));
    }

    // ─── 21028 暖夜不会漫长: 普攻/战技后全队回复2%生命上限 ─────────────

    @Test
    public void testWarmNightPartyHeal() {
        Character natasha = buildWithWeapon(1105, 21028);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha, seele), List.of(enemy));

        seele.takeDamage(seele.getMaxHp() * 0.3);
        double before = seele.getCurrentHp();
        battle.executeSkill(natasha.getSkills().get(SkillType.COMMON), natasha, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before,
                "暖夜 should heal the party after a basic attack");
    }

    // ─── 21029 后会有期: 普攻/战技后附加48%攻击力伤害 ──────────────────

    @Test
    public void testSeeYouExtraHit() {
        Character seele = buildWithWeapon(1102, 21029);
        noCrit(seele);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        double atk = attr(seele, AttributeType.ATTACK);
        Assertions.assertTrue(hpBefore - enemy.getCurrentHp() > atk * 0.45,
                "后会有期 should add a 48% ATK extra hit");
    }

    // ─── 21034 今日亦是和平的一日: 每点能量上限伤害+0.02% (上限160点) ──

    @Test
    public void testPeaceDayEnergyDmg() {
        Character danHeng = buildWithWeapon(1002, 21034);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(danHeng), List.of(enemy));

        double expected = 1 + Math.min(160, danHeng.getMaxEnergy()) * 0.002;
        Assertions.assertEquals(expected, dmgMult(danHeng, battle, enemy, SkillType.SKILL), 0.001,
                "每点能量上限伤害+0.2% (上限160点)");
    }

    // ─── 21038 在火的远处: 单次受击损失>25%时回血15%+伤害提高25% ──────

    @Test
    public void testFireDistanceThreshold() {
        Character arlan = buildWithWeapon(1008, 21038);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(arlan), List.of(enemy));

        double before = arlan.getCurrentHp();
        battle.dealAttackDamage(enemy, arlan, 20, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(arlan.hasBuffNamed("在火的远处") || arlan.isDeath(),
                "单次损失>25%时应触发回血+增伤");
    }

    // ─── 21044 无边曼舞: 对防御降低/减速敌人暴击伤害+24% ──────────────

    @Test
    public void testBoundlessVsDebuffed() {
        Character pela = buildWithWeapon(1106, 21044);
        Enemy slowed = buildEnemy(Element.ICE, Element.ICE, "S");
        Buff slow = new Buff("Slow", Buff.Category.DEBUFF, pela, slowed, 2)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-0.1));
        slowed.applyBuff(slow);
        Enemy normal = buildEnemy(Element.ICE, Element.ICE, "N");
        Assertions.assertEquals(1.24, coneDmgMult(pela, null, slowed, SkillType.SKILL, "无边曼舞"), 0.001,
                "对减速目标暴伤+24%");
        Assertions.assertEquals(1.0, coneDmgMult(pela, null, normal, SkillType.SKILL, "无边曼舞"), 0.001);
    }

    private double coneDmgMult(Character c, Battle battle, Enemy enemy, SkillType type, String coneName) {
        for (Trace trace : c.getTraces()) {
            if (coneName.equals(trace.getName())) {
                return trace.damageMultiplier(battle, c, enemy, type);
            }
        }
        return 1.0;
    }

    // ─── 21047 黑夜如影随行: 进入战斗/击破后速度+8%持续2回合 ──────────

    @Test
    public void testNightShadowSpeed() {
        Character gallagher = buildWithWeapon(1301, 21047);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(gallagher), List.of(enemy));

        Assertions.assertTrue(gallagher.hasBuffNamed("黑夜如影随行"),
                "进入战斗时应获得速度加成");
        enemy.reduceToughness(30, Element.FIRE);
        for (Trace trace : gallagher.getTraces()) {
            trace.onEnemyBreak(battle, gallagher, enemy);
        }
        Assertions.assertTrue(gallagher.hasBuffNamed("黑夜如影随行"),
                "击破后应刷新速度加成");
    }

    // ─── 21048 梦的蒙太奇: 攻击击破目标后+3能量 (每回合最多2次) ────────

    @Test
    public void testMontageBrokenEnergy() {
        Character seele = buildWithWeapon(1102, 21048);
        Enemy broken = buildEnemy(Element.ICE, Element.QUANTUM, "B");
        broken.reduceToughness(30, Element.QUANTUM);
        Battle battle = battleOf(List.of(seele), List.of(broken));

        double before = seele.getEnergy();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(broken));
        Assertions.assertEquals(23, seele.getEnergy() - before, 0.001, "basic 20 + 蒙太奇 3");
    }

    // ─── 21052 多流汗，少流泪: 忆灵在场时伤害+24% ──────────────────────

    @Test
    public void testSweatTearsMemosprite() {
        Character aglaea = buildWithWeapon(1402, 21052);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(aglaea), List.of(enemy));

        Assertions.assertEquals(1.0, dmgMult(aglaea, battle, enemy, SkillType.SKILL), 0.001);
        battle.summonRequest(aglaea);
        Assertions.assertEquals(1.24, dmgMult(aglaea, battle, enemy, SkillType.SKILL), 0.001,
                "忆灵在场时伤害+24%");
    }

    // ─── 21061 假日浴场大冒险: 攻击后100%基础概率易伤10% ───────────────

    @Test
    public void testResortVulnerability() {
        Character jiaoqiu = buildWithWeapon(1218, 21061);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(jiaoqiu), List.of(enemy));

        jiaoqiu.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(jiaoqiu.getSkills().get(SkillType.COMMON), jiaoqiu, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("假日浴场"),
                "攻击后应使目标易伤");
    }

    // ─── 21064 菇菇嘎嘎历险记: 施放欢愉技时敌方全体易伤6% ─────────────

    @Test
    public void testMushroomElationVuln() {
        Character sparxie = buildWithWeapon(1501, 21064);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));

        battle.executeSkill(sparxie.getSkills().get(SkillType.ELATION), sparxie, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("菇菇嘎嘎"),
                "欢愉技应使敌方易伤");
    }

    // ─── 21065 今日好手气: 施放欢愉技时欢愉度+12% (最多2层) ───────────

    @Test
    public void testLuckyDayElationStacks() {
        Character sparxie = buildWithWeapon(1501, 21065);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));

        battle.executeSkill(sparxie.getSkills().get(SkillType.ELATION), sparxie, List.of(enemy));
        battle.executeSkill(sparxie.getSkills().get(SkillType.ELATION), sparxie, List.of(enemy));
        Assertions.assertTrue(attr(sparxie, AttributeType.ELATION_DAMAGE_BOOST) >= 0.2,
                "欢愉度应叠加提高");
    }

    private void noCrit(Character character) {
        character.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        character.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        character.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        character.getAttribute(AttributeType.CRIT_ATTACK).base(0);
    }

    private Enemy slowEnemy(Element element) {
        return Enemy.fromTemplate("Slow", 80,
                100000, 26, 240, 10, 30,
                element, EnumSet.of(element), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }
}
