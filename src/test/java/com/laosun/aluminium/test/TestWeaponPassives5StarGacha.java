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
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Trace;
import com.laosun.aluminium.models.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the hand-written 23*** light cone passives (五星光锥).
 */
public class TestWeaponPassives5StarGacha {

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

    private Trace cone(Character c, String name) {
        for (Trace trace : c.getTraces()) {
            if (name.equals(trace.getName())) {
                return trace;
            }
        }
        return null;
    }

    private double coneMult(Character c, Battle battle, Enemy enemy, SkillType type, String name) {
        Trace trace = cone(c, name);
        return trace != null ? trace.damageMultiplier(battle, c, enemy, type) : 1.0;
    }

    // ─── 23000 银河铁道之夜 ───────────────────────────────────────────

    @Test
    public void testNightTrain() {
        Character seele = buildWithWeapon(1102, 23000);
        Enemy e1 = buildEnemy(Element.ICE, Element.QUANTUM, "A");
        Enemy e2 = buildEnemy(Element.ICE, Element.QUANTUM, "B");
        Battle battle = battleOf(List.of(seele), List.of(e1, e2));
        Assertions.assertTrue(seele.hasBuffNamed("银河铁道之夜·攻"), "2个敌人 → 攻击+18%");
        for (Trace trace : seele.getTraces()) {
            trace.onEnemyBreak(battle, seele, e1);
        }
        Assertions.assertTrue(seele.hasBuffNamed("银河铁道之夜"), "击破后伤害+30%");
    }

    // ─── 23001 于夜色中 ───────────────────────────────────────────────

    @Test
    public void testNightSpeedScaling() {
        Character seele = buildWithWeapon(1102, 23001);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        double mult = coneMult(seele, null, enemy, SkillType.COMMON, "于夜色中");
        double speed = attr(seele, AttributeType.SPEED);
        int stacks = (int) Math.min(6, Math.max(0, (speed - 100) / 10));
        Assertions.assertEquals(1 + stacks * 0.06, mult, 0.001);
    }

    // ─── 23002 无可取代的东西 ─────────────────────────────────────────

    @Test
    public void testIrreplaceableHealOnHit() {
        Character arlan = buildWithWeapon(1008, 23002);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(arlan), List.of(enemy));
        arlan.takeDamage(arlan.getMaxHp() * 0.3);
        double hpBefore = arlan.getCurrentHp();
        battle.dealAttackDamage(enemy, arlan, 0.05, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(arlan.getCurrentHp() > hpBefore, "受击后应回血");
        Assertions.assertTrue(arlan.hasBuffNamed("无可取代的东西"), "受击后伤害+24%");
    }

    // ─── 23003 但战斗还未结束 ─────────────────────────────────────────

    @Test
    public void testBattleNotOverSpAndAllyBuff() {
        Character bronya = buildWithWeapon(1101, 23003);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));
        int spBefore = battle.getSkillPoints();
        battle.executeSkill(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        battle.executeSkill(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        Assertions.assertEquals(spBefore + 1, battle.getSkillPoints(), "第2次终结技回复1战技点");
        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("但战斗还未结束"), "下个行动的队友增伤");
    }

    // ─── 23010 拂晓之前 ───────────────────────────────────────────────

    @Test
    public void testBeforeDawn() {
        Character jingYuan = buildWithWeapon(1204, 23010);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(jingYuan), List.of(enemy));
        Assertions.assertEquals(1.18, coneMult(jingYuan, battle, enemy, SkillType.SKILL, "拂晓之前"), 0.001);
        battle.executeSkill(jingYuan.getSkills().get(SkillType.SKILL), jingYuan, List.of(enemy));
        Assertions.assertTrue(jingYuan.hasBuffNamed("梦身"));
        Assertions.assertEquals(1.48, coneMult(jingYuan, battle, enemy, SkillType.TALENT, "拂晓之前"), 0.001);
    }

    // ─── 23004 以世界之名 ─────────────────────────────────────────────

    @Test
    public void testWorldNameDebuffedBonus() {
        Character welt = buildWithWeapon(1006, 23004);
        Enemy debuffed = buildEnemy(Element.ICE, Element.IMAGINARY, "D");
        debuffed.applyBuff(new Buff("Slow", Buff.Category.DEBUFF, welt, debuffed, 2));
        Enemy normal = buildEnemy(Element.ICE, Element.IMAGINARY, "N");
        Assertions.assertEquals(1.0, coneMult(welt, null, normal, SkillType.SKILL, "以世界之名"), 0.001);
        Assertions.assertEquals(1.24, coneMult(welt, null, debuffed, SkillType.SKILL, "以世界之名"), 0.001);
    }

    // ─── 23005 制胜的瞬间 ─────────────────────────────────────────────

    @Test
    public void testMomentVictoryDefBuff() {
        Character gepard = buildWithWeapon(1104, 23005);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));
        double aggro = 1.0;
        for (Trace trace : gepard.getTraces()) {
            aggro *= trace.aggroMultiplier(battle, gepard);
        }
        Assertions.assertTrue(aggro >= 2.0, "受到攻击概率提高");
        double defBefore = attr(gepard, AttributeType.DEFENCE);
        battle.dealAttackDamage(enemy, gepard, 0.05, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(attr(gepard, AttributeType.DEFENCE) > defBefore, "受击后防御+24%");
    }

    // ─── 23006 只需等待 ───────────────────────────────────────────────

    @Test
    public void testWaitingSpeedAndThread() {
        Character kafka = buildWithWeapon(1005, 23006);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(kafka), List.of(enemy));
        battle.executeSkill(kafka.getSkills().get(SkillType.COMMON), kafka, List.of(enemy));
        Assertions.assertTrue(kafka.hasBuffNamed("只需等待·速"), "攻击后速度提高");
        Assertions.assertTrue(enemy.hasDotOfElement(Element.THUNDER), "游丝视作触电");
        Assertions.assertTrue(enemy.hasBuffNamed("游丝"));
    }

    // ─── 23007 雨一直下 ───────────────────────────────────────────────

    @Test
    public void testRainCritAndEtherCode() {
        Character silverWolf = buildWithWeapon(1009, 23007);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(silverWolf), List.of(enemy));
        for (int i = 0; i < 3; i++) {
            enemy.applyBuff(new Buff("D" + i, Buff.Category.DEBUFF, silverWolf, enemy, 2));
        }
        double crit = 0;
        for (Trace trace : silverWolf.getTraces()) {
            crit += trace.critChanceBonus(battle, silverWolf, enemy, SkillType.SKILL);
        }
        Assertions.assertTrue(crit >= 0.12, "≥3个负面效果 → 暴击率+12%");
        silverWolf.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(silverWolf.getSkills().get(SkillType.COMMON), silverWolf, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("以太编码"), "攻击后施加以太编码");
    }

    // ─── 23008 棺的回响 ───────────────────────────────────────────────

    @Test
    public void testCoffinEnergyAndSpeed() {
        Character luocha = buildWithWeapon(1107, 23008);
        Enemy e1 = buildEnemy(Element.ICE, Element.IMAGINARY, "A");
        Enemy e2 = buildEnemy(Element.ICE, Element.IMAGINARY, "B");
        Battle battle = battleOf(List.of(luocha), List.of(e1, e2));
        luocha.consumeEnergy();
        battle.executeSkill(luocha.getSkills().get(SkillType.COMMON), luocha, List.of(e1, e2));
        Assertions.assertEquals(26, luocha.getEnergy(), 0.001, "basic 20 + 2目标×3");
        battle.executeSkill(luocha.getSkills().get(SkillType.ULTRA), luocha, List.of(e1, e2));
        Assertions.assertTrue(luocha.hasBuffNamed("棺的回响"), "终结技后全队速度+12");
    }

    // ─── 23009 到不了的彼岸 ──────────────────────────────────────────

    @Test
    public void testUnreachableBuffOnHit() {
        Character blade = buildWithWeapon(1108, 23009);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(blade), List.of(enemy));
        battle.dealAttackDamage(enemy, blade, 0.05, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(blade.hasBuffNamed("到不了的彼岸"), "受击后伤害+24%");
        battle.executeSkill(blade.getSkills().get(SkillType.COMMON), blade, List.of(enemy));
        Assertions.assertFalse(blade.hasBuffNamed("到不了的彼岸"), "攻击后解除");
    }

    // ─── 23011 她已闭上双眼 ───────────────────────────────────────────

    @Test
    public void testClosedEyes() {
        Character fuXuan = buildWithWeapon(1203, 23011);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(fuXuan, seele), List.of(enemy));
        battle.dealAttackDamage(enemy, fuXuan, 0.1, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(seele.hasBuffNamed("她已闭上双眼"), "生命降低时全队增伤");
    }

    // ─── 23012 如泥酣眠 ───────────────────────────────────────────────

    @Test
    public void testSleepMudCrit() {
        Character yanqing = buildWithWeapon(1201, 23012);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(yanqing), List.of(enemy));
        battle.executeSkill(yanqing.getSkills().get(SkillType.COMMON), yanqing, List.of(enemy));
        Assertions.assertTrue(yanqing.hasBuffNamed("如泥酣眠"), "普攻后暴击率+36%");
    }

    // ─── 23013 时节不居 ───────────────────────────────────────────────

    @Test
    public void testSeasonExtraHit() {
        Character bailu = buildWithWeapon(1211, 23013);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(bailu, seele), List.of(enemy));
        seele.takeDamage(seele.getMaxHp() * 0.5);
        Trace season = cone(bailu, "时节不居");
        Assertions.assertNotNull(season);
        season.onTurnStart(battle, bailu);
        battle.executeSkill(bailu.getSkills().get(SkillType.SKILL), bailu, List.of(seele));
        double hpBefore = enemy.getCurrentHp();
        season.onAllyAction(battle, bailu, seele, SkillType.COMMON, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore, "记录治疗量的附加伤害");
    }

    // ─── 23014 此身为剑 ───────────────────────────────────────────────

    @Test
    public void testSwordBodyEclipse() {
        Character clara = buildWithWeapon(1107, 23014);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(clara, seele), List.of(enemy));
        Assertions.assertEquals(1.0, coneMult(clara, battle, enemy, SkillType.SKILL, "此身为剑"), 0.001);
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        Assertions.assertTrue(coneMult(clara, battle, enemy, SkillType.SKILL, "此身为剑") >= 1.14,
                "队友行动后获得月蚀层");
    }

    // ─── 23015 比阳光更明亮的 ─────────────────────────────────────────

    @Test
    public void testBrighterDragonRoar() {
        Character danHengIL = buildWithWeapon(1213, 23015);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(danHengIL), List.of(enemy));
        double atkBefore = attr(danHengIL, AttributeType.ATTACK);
        battle.executeSkill(danHengIL.getSkills().get(SkillType.COMMON), danHengIL, List.of(enemy));
        battle.executeSkill(danHengIL.getSkills().get(SkillType.COMMON), danHengIL, List.of(enemy));
        Assertions.assertTrue(danHengIL.hasBuffNamed("龙吟") && attr(danHengIL, AttributeType.ATTACK) > atkBefore,
                "普攻获得龙吟");
    }

    // ─── 23016 烦恼着，幸福着 ─────────────────────────────────────────

    @Test
    public void testHappyGentle() {
        Character topaz = buildWithWeapon(1112, 23016);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(topaz), List.of(enemy));
        Assertions.assertEquals(1.30, coneMult(topaz, battle, enemy, SkillType.TALENT, "烦恼着，幸福着"), 0.001);
        for (Trace trace : topaz.getTraces()) {
            trace.afterAction(battle, topaz, SkillType.TALENT, List.of(enemy));
        }
        Assertions.assertTrue(enemy.hasBuffNamed("温驯"), "追加攻击使目标温驯");
        Assertions.assertTrue(coneMult(topaz, battle, enemy, SkillType.SKILL, "烦恼着，幸福着") >= 1.12,
                "击中温驯目标暴伤+12%");
    }

    // ─── 23017 惊魂夜 ─────────────────────────────────────────────────

    @Test
    public void testNightFright() {
        Character huohuo = buildWithWeapon(1210, 23017);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(huohuo, seele), List.of(enemy));
        seele.takeDamage(seele.getMaxHp() * 0.6);
        double before = seele.getCurrentHp();
        for (Trace trace : huohuo.getTraces()) {
            trace.onAllyAction(battle, huohuo, seele, SkillType.ULTRA, List.of(enemy));
        }
        Assertions.assertTrue(seele.getCurrentHp() > before, "终结技时治疗生命最低角色");
        Assertions.assertTrue(seele.hasBuffNamed("惊魂夜·攻"), "治疗目标攻击+2.4%");
    }

    // ─── 23018 片刻，留在眼底 ─────────────────────────────────────────

    @Test
    public void testMomentEyeUlt() {
        Character jingYuan = buildWithWeapon(1212, 23018);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        double expected = 1 + Math.min(180, jingYuan.getMaxEnergy()) * 0.0036;
        Assertions.assertEquals(expected, coneMult(jingYuan, null, enemy, SkillType.ULTRA, "片刻，留在眼底"), 0.001);
    }

    // ─── 23019 镜中故我 ───────────────────────────────────────────────

    @Test
    public void testMirror() {
        Character ruanMei = buildWithWeapon(1303, 23019);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "I");
        Battle battle = battleOf(List.of(ruanMei, seele), List.of(enemy));
        battle.executeSkill(ruanMei.getSkills().get(SkillType.ULTRA), ruanMei, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("镜中故我"), "终结技后全队增伤");
        Assertions.assertTrue(seele.getEnergy() >= 10, "进入战斗全队+10能量");
    }

    // ─── 23020 纯粹思维的洗礼 ─────────────────────────────────────────

    @Test
    public void testBaptism() {
        Character drRatio = buildWithWeapon(1225, 23020);
        Enemy debuffed = buildEnemy(Element.ICE, Element.IMAGINARY, "D");
        for (int i = 0; i < 2; i++) {
            debuffed.applyBuff(new Buff("D" + i, Buff.Category.DEBUFF, drRatio, debuffed, 2));
        }
        Assertions.assertEquals(1.16, coneMult(drRatio, null, debuffed, SkillType.SKILL, "纯粹思维的洗礼"), 0.001);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(drRatio), List.of(enemy));
        battle.executeSkill(drRatio.getSkills().get(SkillType.ULTRA), drRatio, List.of(enemy));
        Assertions.assertTrue(drRatio.hasBuffNamed("论辩"), "终结技后获得论辩");
    }

    // ─── 23021 游戏尘寰 ───────────────────────────────────────────────

    @Test
    public void testGameWorldMask() {
        Character sparkle = buildWithWeapon(1306, 23021);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(sparkle, seele), List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("假面"), "战斗开始获得假面");
    }

    // ─── 23022 重塑时光之忆 ───────────────────────────────────────────

    @Test
    public void testRecastProphet() {
        Character blackSwan = buildWithWeapon(1309, 23022);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(blackSwan), List.of(enemy));
        Assertions.assertTrue(blackSwan.hasBuffNamed("先知"), "战斗开始获得先知(近似满层)");
    }

    // ─── 23023 命运从未公平 ───────────────────────────────────────────

    @Test
    public void testFateUnfair() {
        Character aventurine = buildWithWeapon(1308, 23023);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(aventurine), List.of(enemy));
        Assertions.assertTrue(attr(aventurine, AttributeType.CRIT_ATTACK) >= 0.4, "提供护盾后暴伤+40%");
        for (Trace trace : aventurine.getTraces()) {
            trace.afterAction(battle, aventurine, SkillType.TALENT, List.of(enemy));
        }
        Assertions.assertTrue(enemy.hasBuffNamed("命运从未公平·易伤"), "追加攻击使目标易伤");
    }

    // ─── 23024 行于流逝的岸 ───────────────────────────────────────────

    @Test
    public void testFadingShoreBubble() {
        Character acheron = buildWithWeapon(1309, 23024);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "L");
        Battle battle = battleOf(List.of(acheron), List.of(enemy));
        battle.executeSkill(acheron.getSkills().get(SkillType.COMMON), acheron, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("泡影"), "击中使目标陷入泡影");
        Assertions.assertEquals(1.24, coneMult(acheron, battle, enemy, SkillType.SKILL, "行于流逝的岸"), 0.001);
        Assertions.assertEquals(1.48, coneMult(acheron, battle, enemy, SkillType.ULTRA, "行于流逝的岸"), 0.001);
    }

    // ─── 23025 梦应归于何处 ───────────────────────────────────────────

    @Test
    public void testDreamReturn() {
        Character firefly = buildWithWeapon(1310, 23025);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(firefly), List.of(enemy));
        enemy.reduceToughness(30, Element.FIRE);
        for (Trace trace : firefly.getTraces()) {
            trace.onEnemyBreak(battle, firefly, enemy);
        }
        Assertions.assertTrue(enemy.hasBuffNamed("溃败"), "击破后使目标陷入溃败");
    }

    // ─── 23026 夜色流光溢彩 ───────────────────────────────────────────

    @Test
    public void testGlitterNight() {
        Character robin = buildWithWeapon(1309, 23026);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(robin, seele), List.of(enemy));
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        Assertions.assertTrue(robin.hasBuffNamed("歌咏"), "我方攻击时获得歌咏");
        battle.executeSkill(robin.getSkills().get(SkillType.ULTRA), robin, List.of(enemy));
        Assertions.assertTrue(robin.hasBuffNamed("华彩"), "终结技移除歌咏获得华彩");
        Assertions.assertTrue(seele.hasBuffNamed("华彩·队"), "全队伤害+24%");
    }

    // ─── 23027 驶向第二次生命 ─────────────────────────────────────────

    @Test
    public void testSecondLifeSpeed() {
        Character boothill = buildWithWeapon(1305, 23027);
        boothill.getAttribute(AttributeType.BREAKING_EFFECT).base(1.6);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(boothill), List.of(enemy));
        Assertions.assertTrue(boothill.hasBuffNamed("驶向第二次生命"), "击破特攻≥150%时速度+12%");
    }

    // ─── 23028 偏偏希望无价 ───────────────────────────────────────────

    @Test
    public void testHope() {
        Character feixiao = buildWithWeapon(1307, 23028);
        feixiao.getAttribute(AttributeType.CRIT_ATTACK).base(2.0);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(feixiao), List.of(enemy));
        Assertions.assertTrue(feixiao.hasBuffNamed("偏偏希望无价"), "战斗开始终结技/追加无视防御");
        double mult = coneMult(feixiao, battle, enemy, SkillType.TALENT, "偏偏希望无价");
        int stacks = Math.min(4, (int) ((2.0 - 1.2) / 0.2));
        Assertions.assertEquals(1 + stacks * 0.12, mult, 0.001, "暴伤越高追加伤害越高");
    }

    // ─── 23029 那无数个春天 ───────────────────────────────────────────

    @Test
    public void testInnumerableSprings() {
        Character jiaoqiu = buildWithWeapon(1218, 23029);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(jiaoqiu), List.of(enemy));
        jiaoqiu.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(jiaoqiu.getSkills().get(SkillType.COMMON), jiaoqiu, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("卸甲") || enemy.hasBuffNamed("穷寇"),
                "攻击后使目标卸甲");
    }

    // ─── 23030 落日时起舞 ─────────────────────────────────────────────

    @Test
    public void testSunsetDance() {
        Character phainon = buildWithWeapon(1410, 23030);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(phainon), List.of(enemy));
        double aggro = 1.0;
        for (Trace trace : phainon.getTraces()) {
            aggro *= trace.aggroMultiplier(battle, phainon);
        }
        Assertions.assertTrue(aggro >= 2.0);
        battle.executeSkill(phainon.getSkills().get(SkillType.ULTRA), phainon, List.of(enemy));
        Assertions.assertTrue(coneMult(phainon, battle, enemy, SkillType.TALENT, "落日时起舞") >= 1.35,
                "终结技后火舞使追加攻击+36%");
    }

    // ─── 23031 我将，巡征追猎 ─────────────────────────────────────────

    @Test
    public void testHuntLight() {
        Character feixiao = buildWithWeapon(1220, 23031);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(feixiao), List.of(enemy));
        Assertions.assertEquals(1.0, coneMult(feixiao, battle, enemy, SkillType.ULTRA, "我将，巡征追猎"), 0.001);
        for (Trace trace : feixiao.getTraces()) {
            trace.afterAction(battle, feixiao, SkillType.TALENT, List.of(enemy));
            trace.afterAction(battle, feixiao, SkillType.TALENT, List.of(enemy));
        }
        Assertions.assertEquals(1.54, coneMult(feixiao, battle, enemy, SkillType.ULTRA, "我将，巡征追猎"), 0.001,
                "2层流光 → 终结技无视54%防御");
    }

    // ─── 23032 唯有香如故 ─────────────────────────────────────────────

    @Test
    public void testScentForget() {
        Character gallagher = buildWithWeapon(1301, 23032);
        gallagher.getAttribute(AttributeType.BREAKING_EFFECT).base(1.6);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(gallagher), List.of(enemy));
        battle.executeSkill(gallagher.getSkills().get(SkillType.ULTRA), gallagher, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("忘忧"), "终结技使目标陷入忘忧");
    }

    // ─── 23033 忍法帖•缭乱破魔 ────────────────────────────────────────

    @Test
    public void testNinja() {
        Character seele = buildWithWeapon(1102, 23033);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(seele), List.of(enemy));
        Assertions.assertTrue(seele.getEnergy() >= 30, "进入战斗恢复30能量");
        battle.executeSkill(seele.getSkills().get(SkillType.ULTRA), seele, List.of(enemy));
        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        battle.executeSkill(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining * 0.6,
                "2次普攻后行动提前50%");
    }

    // ─── 23034 回到大地的飞行 ─────────────────────────────────────────

    @Test
    public void testFlight() {
        Character natasha = buildWithWeapon(1105, 23034);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha, seele), List.of(enemy));
        natasha.consumeEnergy();
        int spBefore = battle.getSkillPoints();
        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(seele));
        Assertions.assertEquals(36, natasha.getEnergy(), 0.001, "战技基础30 + 飞行6");
        Assertions.assertTrue(seele.hasBuffNamed("圣咏"), "目标获得圣咏");
        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(seele));
        Assertions.assertEquals(spBefore - 1, battle.getSkillPoints(), "每2次恢复1战技点(净耗1)");
    }

    // ─── 23035 长路终有归途 ───────────────────────────────────────────

    @Test
    public void testHomeward() {
        Character fugue = buildWithWeapon(1315, 23035);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(fugue), List.of(enemy));
        enemy.reduceToughness(30, Element.FIRE);
        for (Trace trace : fugue.getTraces()) {
            trace.onEnemyBreak(battle, fugue, enemy);
        }
        Assertions.assertTrue(enemy.hasBuffNamed("焚灼"), "击破后使目标焚灼");
    }

    // ─── 23036 将光阴织成黄金 ─────────────────────────────────────────

    @Test
    public void testGoldenWeave() {
        Character aglaea = buildWithWeapon(1402, 23036);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(aglaea), List.of(enemy));
        for (int i = 0; i < 3; i++) {
            battle.executeSkill(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        }
        Assertions.assertEquals(1.27, coneMult(aglaea, battle, enemy, SkillType.SKILL, "将光阴织成黄金"), 0.001,
                "3次攻击 → 3层织锦");
        battle.summonRequest(aglaea);
        Summon summon = aglaea.getSummons().getFirst();
        battle.executeSkill(summon.getSkills().get(SkillType.SUMMON_SKILL), summon, List.of(enemy));
        Assertions.assertEquals(1.36, coneMult(aglaea, battle, enemy, SkillType.SKILL, "将光阴织成黄金"), 0.001,
                "忆灵攻击也叠织锦");
    }

    // ─── 23037 向着不可追问处 ─────────────────────────────────────────

    @Test
    public void testUnaskable() {
        Character cyrene = buildWithWeapon(1409, 23037);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(cyrene), List.of(enemy));
        int spBefore = battle.getSkillPoints();
        battle.executeSkill(cyrene.getSkills().get(SkillType.ULTRA), cyrene, List.of(enemy));
        Assertions.assertTrue(cyrene.hasBuffNamed("向着不可追问处"), "终结技后战技/终结技+60%");
        if (cyrene.getMaxEnergy() >= 140) {
            Assertions.assertEquals(spBefore + 1, battle.getSkillPoints(), "能量上限≥140恢复1战技点");
        }
    }

    // ─── 23038 如果时间是一朵花 ───────────────────────────────────────

    @Test
    public void testFlowerTime() {
        Character robin = buildWithWeapon(1309, 23038);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(robin, seele), List.of(enemy));
        Assertions.assertTrue(robin.getEnergy() >= 21, "进入战斗恢复21能量");
        Assertions.assertTrue(seele.hasBuffNamed("谕示"), "进入战斗全队获得谕示");
    }

    // ─── 23039 血火啊，燃烧前路 ────────────────────────────────────────

    @Test
    public void testFireBlood() {
        Character mydei = buildWithWeapon(1404, 23039);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(mydei), List.of(enemy));
        double hpBefore = mydei.getCurrentHp();
        battle.executeSkill(mydei.getSkills().get(SkillType.SKILL), mydei, List.of(enemy));
        Assertions.assertTrue(mydei.getCurrentHp() < hpBefore, "战技消耗生命");
        Assertions.assertTrue(mydei.hasBuffNamed("血火啊，燃烧前路"), "本次攻击伤害提高");
    }

    // ─── 23040 让告别，更美一些 ────────────────────────────────────────

    @Test
    public void testFarewellPrettier() {
        Character castorice = buildWithWeapon(1407, 23040);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(castorice), List.of(enemy));
        battle.dealAttackDamage(enemy, castorice, 0.1, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(castorice.hasBuffNamed("冥花"), "损失生命后获得冥花");
    }

    // ─── 23041 生命当付之一炬 ─────────────────────────────────────────

    @Test
    public void testOnceFlame() {
        Character fugue = buildWithWeapon(1315, 23041);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(fugue), List.of(enemy));
        double energyBefore = fugue.getEnergy();
        battle.executeSkill(fugue.getSkills().get(SkillType.COMMON), fugue, List.of(enemy));
        Assertions.assertTrue(fugue.getEnergy() >= energyBefore + 10, "回合开始恢复10能量");
        Assertions.assertTrue(enemy.hasBuffNamed("生命当付之一炬·防"), "攻击使目标防御降低");
    }

    // ─── 23042 愿虹光永驻天空 ─────────────────────────────────────────

    @Test
    public void testRainbowSky() {
        Character castorice = buildWithWeapon(1407, 23042);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(castorice, seele), List.of(enemy));
        double hpBefore = seele.getCurrentHp();
        battle.executeSkill(castorice.getSkills().get(SkillType.COMMON), castorice, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() < hpBefore, "普攻时全队消耗生命");
        double enemyHp = enemy.getCurrentHp();
        battle.summonRequest(castorice);
        Summon summon = castorice.getSummons().getFirst();
        battle.executeSkill(summon.getSkills().get(SkillType.SUMMON_SKILL), summon, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < enemyHp, "忆灵下次攻击造成消耗量2.5倍附加伤害");
        Assertions.assertTrue(enemy.hasBuffNamed("愿虹光永驻天空"), "忆灵施放忆灵技时敌方易伤");
    }

    // ─── 23043 谎言在风中飘扬 ─────────────────────────────────────────

    @Test
    public void testLiesWind() {
        Character cipher = buildWithWeapon(1406, 23043);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(cipher), List.of(enemy));
        battle.executeSkill(cipher.getSkills().get(SkillType.COMMON), cipher, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("茫然") || enemy.hasBuffNamed("失窃"),
                "攻击后使目标茫然/失窃");
    }

    // ─── 23044 黎明恰如此燃烧 ─────────────────────────────────────────

    @Test
    public void testDawnBurn() {
        Character phainon = buildWithWeapon(1410, 23044);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(phainon), List.of(enemy));
        double mult = coneMult(phainon, battle, enemy, SkillType.SKILL, "黎明恰如此燃烧");
        Assertions.assertEquals(1.18, mult, 0.001, "伤害无视18%防御");
        battle.executeSkill(phainon.getSkills().get(SkillType.ULTRA), phainon, List.of(enemy));
        Assertions.assertEquals(1.18 * 1.6, coneMult(phainon, battle, enemy, SkillType.SKILL, "黎明恰如此燃烧"), 0.001,
                "烈阳期间伤害+60%");
    }

    // ─── 23045 没有回报的加冕 ─────────────────────────────────────────

    @Test
    public void testCoronation() {
        Character phainon = buildWithWeapon(1410, 23045);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(phainon), List.of(enemy));
        double atkBefore = attr(phainon, AttributeType.ATTACK);
        battle.executeSkill(phainon.getSkills().get(SkillType.ULTRA), phainon, List.of(enemy));
        Assertions.assertTrue(attr(phainon, AttributeType.ATTACK) > atkBefore, "终结技后攻击力提高");
        if (phainon.getMaxEnergy() >= 300) {
            Assertions.assertTrue(phainon.getEnergy() > 0, "能量上限≥300恢复10%能量");
        }
    }

    // ─── 23046 理想燃烧的地狱 ─────────────────────────────────────────

    @Test
    public void testHellIdeal() {
        Character danHengIL = buildWithWeapon(1213, 23046);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(danHengIL), List.of(enemy));
        double atkBefore = attr(danHengIL, AttributeType.ATTACK);
        battle.executeSkill(danHengIL.getSkills().get(SkillType.SKILL), danHengIL, List.of(enemy));
        Assertions.assertTrue(attr(danHengIL, AttributeType.ATTACK) > atkBefore, "战技后攻击+10%");
    }

    // ─── 23047 海洋为何而歌 ───────────────────────────────────────────

    @Test
    public void testOceanSong() {
        Character hysilens = buildWithWeapon(1413, 23047);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(hysilens), List.of(enemy));
        battle.executeSkill(hysilens.getSkills().get(SkillType.COMMON), hysilens, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("魂迷"), "陷入负面效果时概率魂迷");
    }

    // ─── 23048 金血铭刻的时代 ─────────────────────────────────────────

    @Test
    public void testGoldBlood() {
        Character sunday = buildWithWeapon(1313, 23048);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(sunday, seele), List.of(enemy));
        int spBefore = battle.getSkillPoints();
        battle.executeSkill(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(enemy));
        Assertions.assertEquals(spBefore + 1, battle.getSkillPoints(), "终结技后恢复1战技点");
        battle.executeSkill(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("金血铭刻的时代"), "战技使目标战技伤害+54%");
    }

    // ─── 23049 致长夜的星光 ───────────────────────────────────────────

    @Test
    public void testNightStar() {
        Character aglaea = buildWithWeapon(1402, 23049);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(aglaea), List.of(enemy));
        battle.summonRequest(aglaea);
        Summon summon = aglaea.getSummons().getFirst();
        battle.executeSkill(summon.getSkills().get(SkillType.SUMMON_SKILL), summon, List.of(enemy));
        Assertions.assertTrue(aglaea.hasBuffNamed("夜色"), "忆灵行动后获得夜色");
        Assertions.assertTrue(aglaea.hasBuffNamed("夜色·队"), "全队忆灵伤害无视防御");
    }

    // ─── 23050 勿忘她的火焰 ───────────────────────────────────────────

    @Test
    public void testHerFlame() {
        Character jiaoqiu = buildWithWeapon(1218, 23050);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(jiaoqiu, seele), List.of(enemy));
        Assertions.assertTrue(jiaoqiu.hasBuffNamed("勿忘她的火焰"), "装备者击破伤害提高");
        Assertions.assertTrue(seele.hasBuffNamed("勿忘她的火焰"), "另一位队友击破伤害提高");
    }

    // ─── 23051 纵然山河万程 ───────────────────────────────────────────

    @Test
    public void testMountains() {
        Character aventurine = buildWithWeapon(1308, 23051);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(aventurine, seele), List.of(enemy));
        seele.takeDamage(seele.getMaxHp() * 0.5);
        double before = seele.getCurrentHp();
        battle.executeSkill(aventurine.getSkills().get(SkillType.ULTRA), aventurine, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before, "终结技时全队回复");
        Assertions.assertTrue(seele.hasBuffNamed("卫戍"), "获得卫戍增伤");
    }

    // ─── 23052 爱如此刻永恒 ───────────────────────────────────────────

    @Test
    public void testEternity() {
        Character castorice = buildWithWeapon(1407, 23052);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(castorice, seele), List.of(enemy));
        battle.summonRequest(castorice);
        Summon summon = castorice.getSummons().getFirst();
        battle.executeSkill(summon.getSkills().get(SkillType.SUMMON_SKILL), summon, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("诗行"), "忆灵对敌方施放忆灵技 → 我方暴击伤害");
        Assertions.assertNotNull(summon.getSecondarySkill(), "忆灵应有忆灵技");
        battle.executeSkill(summon.getSecondarySkill(), summon, List.of(seele));
        Assertions.assertTrue(enemy.hasBuffNamed("空白"), "忆灵对队友施放忆灵技 → 敌方易伤");
    }

    // ─── 23053 花花世界迷人眼 ────────────────────────────────────────

    @Test
    public void testFlowerWorld() {
        Character sparxie = buildWithWeapon(1501, 23053);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));
        battle.addSkillPoints(5);
        for (int i = 0; i < 4; i++) {
            battle.executeSkill(sparxie.getSkills().get(SkillType.SKILL), sparxie, List.of(enemy));
        }
        Assertions.assertTrue(sparxie.hasBuffNamed("推流"), "同回合消耗4战技点获得推流");
        Assertions.assertTrue(coneMult(sparxie, battle, enemy, SkillType.ELATION, "花花世界迷人眼") >= 1.2,
                "消耗战技点提高欢愉伤害");
    }

    // ─── 23054 当她决定看见 ───────────────────────────────────────────

    @Test
    public void testSheSees() {
        Character cyrene = buildWithWeapon(1409, 23054);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(cyrene, seele), List.of(enemy));
        Assertions.assertTrue(cyrene.getEnergy() >= 15, "波次开始恢复15能量");
        Assertions.assertTrue(seele.hasBuffNamed("上上签"), "进入战斗获得上上签");
    }

    // ─── 23056 一场谎言的终幕 ─────────────────────────────────────────

    @Test
    public void testLieEnd() {
        Character feixiao = buildWithWeapon(1307, 23056);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(feixiao), List.of(enemy));
        Assertions.assertTrue(feixiao.hasBuffNamed("影噬"), "战斗开始获得影噬");
        Assertions.assertTrue(enemy.hasBuffNamed("影噬·敌"), "敌方全体易伤20%");
    }

    // ─── 23057 欢迎来到银河城 ─────────────────────────────────────────

    @Test
    public void testGalaxyCity() {
        Character sparxie = buildWithWeapon(1501, 23057);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));
        Assertions.assertEquals(1.2, coneMult(sparxie, battle, enemy, SkillType.ELATION, "欢迎来到银河城"), 0.001);
        battle.executeSkill(sparxie.getSkills().get(SkillType.ULTRA), sparxie, List.of(enemy));
        Assertions.assertTrue(battle.getLaughPoints() >= 20, "终结技获得20点笑点");
    }

    // ─── 23058 邂逅于下一个花季 ───────────────────────────────────────

    @Test
    public void testFlowerSeason() {
        Character sparxie = buildWithWeapon(1501, 23058);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));
        Assertions.assertTrue(attr(sparxie, AttributeType.ENERGY_REGENERATION_RATE) >= 0.1,
                "能量恢复效率提高");
        battle.executeSkill(sparxie.getSkills().get(SkillType.ELATION), sparxie, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("邂逅于下一个花季"), "欢愉技使敌方易伤");
    }

    // ─── 23059 灼尽炼狱的新骸 ─────────────────────────────────────────

    @Test
    public void testInferno() {
        Character firefly = buildWithWeapon(1310, 23059);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(firefly), List.of(enemy));
        double energyBefore = firefly.getEnergy();
        for (Trace trace : firefly.getTraces()) {
            trace.onTurnStart(battle, firefly);
        }
        Assertions.assertTrue(firefly.getEnergy() >= energyBefore + 20, "回合开始固定恢复20能量");
        battle.executeSkill(firefly.getSkills().get(SkillType.SKILL), firefly, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("炼狱"), "战技使目标陷入炼狱");
    }

    // ─── 23060 当一颗星照亮夜空 ───────────────────────────────────────

    @Test
    public void testStarNight() {
        Character sparxie = buildWithWeapon(1501, 23060);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));
        double energyBefore = sparxie.getEnergy();
        battle.executeSkill(sparxie.getSkills().get(SkillType.ELATION), sparxie, List.of(enemy));
        Assertions.assertTrue(sparxie.getEnergy() >= energyBefore + 6, "助战技恢复6能量");
        Assertions.assertTrue(coneMult(sparxie, battle, enemy, SkillType.ELATION, "当一颗星照亮夜空") >= 1.52,
                "启航使助战技伤害提高");
    }

    // ─── 23061 星火悄然闪耀 ───────────────────────────────────────────

    @Test
    public void testSpark() {
        Character sparkle = buildWithWeapon(1306, 23061);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Q");
        Battle battle = battleOf(List.of(sparkle), List.of(enemy));
        battle.addSkillPoints(5);
        for (int i = 0; i < 4; i++) {
            battle.executeSkill(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(enemy));
        }
        Assertions.assertTrue(sparkle.hasBuffNamed("闪耀王冠"), "同回合消耗≥4战技点获得闪耀王冠");
    }

    // ─── 23062 所见即我 ───────────────────────────────────────────────

    @Test
    public void testSeeMe() {
        Character phainon = buildWithWeapon(1410, 23062);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(phainon, seele), List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("王之娱乐"), "进入战斗全队暴击伤害提高");
        double expected = 1 + Math.min(0.72, phainon.getMaxEnergy() * 0.002);
        Assertions.assertEquals(expected, coneMult(phainon, battle, enemy, SkillType.ULTRA, "所见即我"), 0.001);
    }
}
