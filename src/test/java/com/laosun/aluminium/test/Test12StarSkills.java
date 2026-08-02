package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 12** characters' hand-written kits (技能/行迹/星魂):
 * Qingque, Tingyun, Luocha, Jing Yuan, Blade, Sushang, Yukong, Fu Xuan,
 * Yanqing, Guinaifen, Bailu, Jingliu, Dan Heng IL, Xueyi, Hanya, Huohuo,
 * Jiaoqiu, Feixiao, Yunli, Lingsha, Moze, March 7th (Hunt), Fugue.
 */
public class Test12StarSkills {

    private Character buildCharacter(int cid) {
        return buildCharacter(cid, 0);
    }

    private Character buildCharacter(int cid, int eidolon) {
        return Character.builder().cid(cid).level(80).isPromote().eidolon(eidolon).build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100000, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    private void noCrit(Character character) {
        character.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        character.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        character.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        character.getAttribute(AttributeType.CRIT_ATTACK).base(0);
    }

    private Battle battleOf(List<Character> party, List<Enemy> enemies) {
        Battle battle = new Battle(new ArrayList<>(party), new ArrayList<>(enemies));
        battle.startBattle();
        return battle;
    }

    // ─── 1201 青雀 ────────────────────────────────────────────────────

    @Test
    public void testQingqueSkillDamageStacks() {
        Character qingque = buildCharacter(1201);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(qingque), List.of(enemy));

        battle.executeSkill(qingque.getSkills().get(SkillType.SKILL), qingque, List.of(enemy));
        Assertions.assertTrue(qingque.hasBuffNamed("海底捞月"),
                "skill should draw tiles and raise her damage");
        Buff buff = qingque.getBuffs().stream()
                .filter(b -> "海底捞月".equals(b.getName())).findFirst().orElseThrow();
        double boost = buff.getModifiers().getFirst().modifier().getValue();
        Assertions.assertEquals(0.14, boost, 0.001, "first skill cast = 1 stack (14%)");
    }

    // ─── 1202 停云 ────────────────────────────────────────────────────

    @Test
    public void testTingyunSkillBlessing() {
        Character tingyun = buildCharacter(1202);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(tingyun, seele), List.of(enemy));

        double atkBefore = seele.getAttribute(AttributeType.ATTACK).get();
        battle.executeSkill(tingyun.getSkills().get(SkillType.SKILL), tingyun, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("赐福"), "skill should grant 【赐福】");
        Assertions.assertTrue(seele.getAttribute(AttributeType.ATTACK).get() > atkBefore,
                "赐福 should raise the target's ATK");
    }

    @Test
    public void testTingyunUltRestoresEnergy() {
        Character tingyun = buildCharacter(1202);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(tingyun, seele), List.of(enemy));

        double before = seele.getEnergy();
        battle.executeSkill(tingyun.getSkills().get(SkillType.ULTRA), tingyun, List.of(seele));
        Assertions.assertTrue(seele.getEnergy() > before + 45,
                "ult should restore 50 energy, got " + (seele.getEnergy() - before));
    }

    // ─── 1203 罗刹 ────────────────────────────────────────────────────

    @Test
    public void testLuochaSkillHeals() {
        Character luocha = buildCharacter(1203);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(luocha, seele), List.of(enemy));

        seele.takeDamage(800);
        double before = seele.getCurrentHp();
        battle.executeSkill(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(seele));
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal the ally");
    }

    @Test
    public void testLuochaUltDispelsBuffs() {
        Character luocha = buildCharacter(1203);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Buff buff = new Buff("Atk Up", Buff.Category.BUFF, enemy, enemy, 2)
                .stat(AttributeType.ATTACK,
                        com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(0.2));
        enemy.applyBuff(buff);
        Battle battle = battleOf(List.of(luocha), List.of(enemy));

        battle.executeSkill(luocha.getSkills().get(SkillType.ULTRA), luocha, List.of(enemy));
        Assertions.assertFalse(enemy.hasBuffNamed("Atk Up"), "ult should dispel enemy buffs");
    }

    // ─── 1204 景元 ────────────────────────────────────────────────────

    @Test
    public void testJingYuanLordFollowUp() {
        Character jingYuan = buildCharacter(1204);
        noCrit(jingYuan);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(jingYuan), List.of(enemy));

        // 战技增加【神君】段数; 神君代理在回合开始时发动追加攻击.
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(jingYuan.getSkills().get(SkillType.SKILL), jingYuan, List.of(enemy));
        for (Trace trace : jingYuan.getTraces()) {
            trace.onTurnStart(battle, jingYuan);
        }
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "神君 (proxy) should follow up at turn start");
    }

    // ─── 1205 刃 ──────────────────────────────────────────────────────

    @Test
    public void testBladeSkillCostsHp() {
        Character blade = buildCharacter(1205);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(blade), List.of(enemy));

        double before = blade.getCurrentHp();
        battle.executeSkill(blade.getSkills().get(SkillType.SKILL), blade, List.of(enemy));
        Assertions.assertEquals(blade.getMaxHp() * 0.3, before - blade.getCurrentHp(), 1.0,
                "skill should consume 30% max HP");
        Assertions.assertTrue(blade.hasBuffNamed("地狱变"), "skill should enter 地狱变");
        Assertions.assertFalse(blade.isDeath(), "Blade must survive the HP cost");
    }

    @Test
    public void testBladeUltHpScaling() {
        Character blade = buildCharacter(1205);
        noCrit(blade);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(blade), List.of(enemy));

        blade.takeDamage(blade.getMaxHp() * 0.4);
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(blade.getSkills().get(SkillType.ULTRA), blade, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "ult should deal ATK + max-HP scaled damage");
        Assertions.assertTrue(blade.getCurrentHp() > blade.getMaxHp() * 0.45,
                "ult should restore HP to 50% max HP");
    }

    // ─── 1206 素裳 ────────────────────────────────────────────────────

    @Test
    public void testSushangUltAdvances() {
        Character sushang = buildCharacter(1206);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(sushang), List.of(enemy));

        battle.stepForward();
        battle.beforeMove();
        battle.executeSkill(sushang.getSkills().get(SkillType.ULTRA), sushang, List.of(enemy));
        Assertions.assertEquals(0, battle.queue.timeUntilNext(), 0.001,
                "ult should make Sushang act immediately");
    }

    // ─── 1207 驭空 ────────────────────────────────────────────────────

    @Test
    public void testYukongSkillBowOrder() {
        Character yukong = buildCharacter(1207);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(yukong, seele), List.of(enemy));

        battle.executeSkill(yukong.getSkills().get(SkillType.SKILL), yukong, List.of(enemy));
        Assertions.assertTrue(yukong.hasBuffNamed("鸣弦号令"),
                "skill should grant 鸣弦号令 (party ATK buff)");
        Assertions.assertTrue(seele.hasBuffNamed("鸣弦号令·攻"),
                "the party should receive the ATK buff");
    }

    // ─── 1208 符玄 ────────────────────────────────────────────────────

    @Test
    public void testFuXuanSkillMatrix() {
        Character fuXuan = buildCharacter(1208);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(fuXuan, seele), List.of(enemy));

        battle.executeSkill(fuXuan.getSkills().get(SkillType.SKILL), fuXuan, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("鉴知"),
                "穷观阵 should grant 鉴知 to allies");
    }

    // ─── 1209 彦卿 ────────────────────────────────────────────────────

    @Test
    public void testYanqingSkillSwordHeart() {
        Character yanqing = buildCharacter(1209);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(yanqing), List.of(enemy));

        battle.executeSkill(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(enemy));
        Assertions.assertTrue(yanqing.hasBuffNamed("智剑连心"),
                "skill should grant 智剑连心");
    }

    // ─── 1210 桂乃芬 ──────────────────────────────────────────────────

    @Test
    public void testGuinaifenSkillBurns() {
        Character guinaifen = buildCharacter(1210);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(guinaifen), List.of(enemy));

        battle.executeSkill(guinaifen.getSkills().get(SkillType.SKILL), guinaifen, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.FIRE),
                "skill should burn the target");
    }

    @Test
    public void testGuinaifenUltDetonatesBurn() {
        Character guinaifen = buildCharacter(1210);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Buff.Dot dot = new Buff.Dot("Burn (灼烧)", guinaifen, enemy, 500, Element.FIRE, 2);
        enemy.applyDot(dot);
        Battle battle = battleOf(List.of(guinaifen), List.of(enemy));

        double before = enemy.getCurrentHp();
        battle.executeSkill(guinaifen.getSkills().get(SkillType.ULTRA), guinaifen, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < before - 300,
                "ult should detonate the burn for 72% of its damage");
    }

    // ─── 1211 白露 ────────────────────────────────────────────────────

    @Test
    public void testBailuSkillHeals() {
        Character bailu = buildCharacter(1211);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(bailu, seele), List.of(enemy));

        seele.takeDamage(800);
        double before = seele.getCurrentHp();
        battle.executeSkill(bailu.getSkills().get(SkillType.SKILL), bailu, List.of(seele));
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal the ally");
    }

    @Test
    public void testBailuUltInvigoration() {
        Character bailu = buildCharacter(1211);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(bailu, seele), List.of(enemy));

        battle.executeSkill(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("生息"),
                "ult should grant 生息 (HoT) to allies");
    }

    // ─── 1212 镜流 ────────────────────────────────────────────────────

    @Test
    public void testJingliuSkillMoonStacks() {
        Character jingliu = buildCharacter(1212);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(jingliu), List.of(enemy));

        // 2 次战技 → 2 层【朔望】 → 进入【转魄】强化状态.
        battle.executeSkill(jingliu.getSkills().get(SkillType.SKILL), jingliu, List.of(enemy));
        Assertions.assertFalse(jingliu.isEnhanced(), "1 stack should not trigger 转魄 yet");
        battle.executeSkill(jingliu.getSkills().get(SkillType.SKILL), jingliu, List.of(enemy));
        Assertions.assertTrue(jingliu.isEnhanced(),
                "2 朔望 stacks should enter 转魄 (enhanced state)");
    }

    // ─── 1213 丹恒·饮月 ───────────────────────────────────────────────

    @Test
    public void testDanHengILEnhance() {
        Character danHengIL = buildCharacter(1213);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(danHengIL), List.of(enemy));

        battle.executeSkill(danHengIL.getSkills().get(SkillType.SKILL), danHengIL, List.of(enemy));
        Assertions.assertTrue(danHengIL.isEnhanced() || danHengIL.hasBuffNamed("亢心"),
                "skill should enhance the next basic attack (强化普攻 proxy)");
    }

    // ─── 1214 雪衣 ────────────────────────────────────────────────────

    @Test
    public void testXueyiUltBreaksWithoutWeakness() {
        Character xueyi = buildCharacter(1214);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(xueyi), List.of(enemy));

        double toughnessBefore = enemy.getCurrentToughness();
        battle.executeSkill(xueyi.getSkills().get(SkillType.ULTRA), xueyi, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentToughness() < toughnessBefore,
                "ult should reduce toughness regardless of weakness");
    }

    // ─── 1215 寒鸦 ────────────────────────────────────────────────────

    @Test
    public void testHanyaSkillMarksBurden() {
        Character hanya = buildCharacter(1215);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(hanya), List.of(enemy));

        battle.executeSkill(hanya.getSkills().get(SkillType.SKILL), hanya, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("承负"),
                "skill should apply 承负 (damage taken debuff)");
    }

    // ─── 1217 藿藿 ────────────────────────────────────────────────────

    @Test
    public void testHuohuoSkillCleansesAndHeals() {
        Character huohuo = buildCharacter(1217);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(huohuo, seele), List.of(enemy));

        Buff defDown = new Buff("DEF Down", Buff.Category.DEBUFF, enemy, seele, 2)
                .stat(AttributeType.DEFENCE,
                        com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        seele.applyBuff(defDown);
        seele.takeDamage(800);
        double before = seele.getCurrentHp();
        battle.executeSkill(huohuo.getSkills().get(SkillType.SKILL), huohuo, List.of(seele));
        Assertions.assertFalse(seele.hasBuffNamed("DEF Down"), "skill should cleanse the debuff");
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal the ally");
    }

    @Test
    public void testHuohuoUltRestoresEnergy() {
        Character huohuo = buildCharacter(1217);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(huohuo, seele), List.of(enemy));

        double energyBefore = seele.getEnergy();
        double atkBefore = seele.getAttribute(AttributeType.ATTACK).get();
        battle.executeSkill(huohuo.getSkills().get(SkillType.ULTRA), huohuo, List.of(enemy));
        Assertions.assertTrue(seele.getEnergy() > energyBefore + 5,
                "ult should restore 15% of the ally's max energy");
        Assertions.assertTrue(seele.getAttribute(AttributeType.ATTACK).get() > atkBefore,
                "ult should buff the ally's ATK");
    }

    // ─── 1218 椒丘 ────────────────────────────────────────────────────

    @Test
    public void testJiaoqiuSkillEmberStacks() {
        Character jiaoqiu = buildCharacter(1218);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(jiaoqiu), List.of(enemy));

        battle.executeSkill(jiaoqiu.getSkills().get(SkillType.SKILL), jiaoqiu, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("烬煨"),
                "skill should apply 烬煨 stacks");
    }

    // ─── 1220 飞霄 ────────────────────────────────────────────────────

    @Test
    public void testFeixiaoSkillTriggersFollowUp() {
        Character feixiao = buildCharacter(1220);
        noCrit(feixiao);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(feixiao), List.of(enemy));

        // 战技 100% + 天赋追加攻击 55% (含 天通 增伤) — 应显著高于普攻 50%.
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(feixiao.getSkills().get(SkillType.SKILL), feixiao, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        double hpBeforeBasic = enemy.getCurrentHp();
        battle.executeSkill(feixiao.getSkills().get(SkillType.COMMON), feixiao, List.of(enemy));
        double basicLost = hpBeforeBasic - enemy.getCurrentHp();
        double ratio = lost / basicLost;
        Assertions.assertTrue(ratio > 1.5,
                "skill + talent follow-up should out-damage the basic, got ratio " + ratio);
    }

    // ─── 1221 云璃 ────────────────────────────────────────────────────

    @Test
    public void testYunliCounter() {
        Character yunli = buildCharacter(1221);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(yunli), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.dealAttackDamage(enemy, yunli, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "talent proxy: being attacked should trigger a counter");
    }

    // ─── 1222 灵砂 ────────────────────────────────────────────────────

    @Test
    public void testLingshaSkillHeals() {
        Character lingsha = buildCharacter(1222);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(lingsha, seele), List.of(enemy));

        seele.takeDamage(800);
        double before = seele.getCurrentHp();
        battle.executeSkill(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal the party");
    }

    @Test
    public void testLingshaUltDrunken() {
        Character lingsha = buildCharacter(1222);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(lingsha), List.of(enemy));

        battle.executeSkill(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("醇醉"),
                "ult should apply 醇醉 (break damage taken up)");
    }

    // ─── 1223 貊泽 ────────────────────────────────────────────────────

    @Test
    public void testMozeSkillPreys() {
        Character moze = buildCharacter(1223);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(moze), List.of(enemy));

        battle.executeSkill(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("猎物"),
                "skill should mark the target as 猎物");
    }

    // ─── 1224 三月七·巡猎 ─────────────────────────────────────────────

    @Test
    public void testMarchHuntSkillMaster() {
        Character march = buildCharacter(1224);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(march, seele), List.of(enemy));

        battle.executeSkill(march.getSkills().get(SkillType.SKILL), march, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("师父"),
                "skill should make the ally her 师父");
    }

    // ─── 1225 忘归人 ──────────────────────────────────────────────────

    @Test
    public void testFugueSkillFoxBlessing() {
        Character fugue = buildCharacter(1225);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(fugue, seele), List.of(enemy));

        battle.executeSkill(fugue.getSkills().get(SkillType.SKILL), fugue, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("狐祈"),
                "skill should grant 狐祈 (break effect buff)");
        Assertions.assertTrue(fugue.hasBuffNamed("炽灼"),
                "skill should put Fugue into 炽灼 state");
    }
}
