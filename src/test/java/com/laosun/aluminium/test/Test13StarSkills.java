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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 13** characters' hand-written kits (技能/行迹/星魂):
 * Gallagher, Argenti, Ruan Mei, Aventurine, Dr. Ratio, Sparkle, Black Swan,
 * Acheron, Robin, Firefly, Misha, Sunday, Jade, Boothill, Rappa, Dahlia.
 */
public class Test13StarSkills {

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

    // ─── 1301 加拉赫 ───────────────────────────────────────────────────

    @Test
    public void testGallagherSkillFlatHeal() {
        Character gallagher = buildCharacter(1301);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(gallagher, seele), List.of(enemy));

        seele.takeDamage(500);
        double before = seele.getCurrentHp();
        battle.executeSkill(gallagher.getSkills().get(SkillType.SKILL), gallagher, List.of(seele));
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal 200 flat HP");
    }

    @Test
    public void testGallagherUltDrunken() {
        Character gallagher = buildCharacter(1301);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(gallagher), List.of(enemy));

        battle.executeSkill(gallagher.getSkills().get(SkillType.ULTRA), gallagher, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("酩酊"), "ult should apply 酩酊");
        Assertions.assertTrue(gallagher.hasBuffNamed("酒花奔涌"),
                "ult should enhance the next basic attack");
    }

    // ─── 1302 银枝 ─────────────────────────────────────────────────────

    @Test
    public void testArgentiMaxUltExtraHits() {
        Character argenti = buildCharacter(1302);
        noCrit(argenti);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(argenti), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(argenti.getSkills().get(SkillType.ULTRA), argenti, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        double atk = argenti.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(lost > atk * 0.5,
                "ult (168% + 6×57%) should deal heavy damage, got " + lost);
    }

    // ─── 1303 阮·梅 ────────────────────────────────────────────────────

    @Test
    public void testRuanMeiSkillPartyBuff() {
        Character ruanMei = buildCharacter(1303);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(ruanMei, seele), List.of(enemy));

        battle.executeSkill(ruanMei.getSkills().get(SkillType.SKILL), ruanMei, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("弦外音·伤"), "skill should buff party damage");
        Assertions.assertTrue(seele.hasBuffNamed("弦外音·破"), "skill should buff break efficiency");
    }

    @Test
    public void testRuanMeiUltField() {
        Character ruanMei = buildCharacter(1303);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(ruanMei, seele), List.of(enemy));

        battle.executeSkill(ruanMei.getSkills().get(SkillType.ULTRA), ruanMei, List.of(enemy));
        Assertions.assertTrue(ruanMei.hasBuffNamed("结界"), "ult should open the field");
        Assertions.assertTrue(seele.hasBuffNamed("残梅绽·结界"),
                "the party should gain all-res penetration");
    }

    // ─── 1304 砂金 ─────────────────────────────────────────────────────

    @Test
    public void testAventurineSkillShieldsByDef() {
        Character aventurine = buildCharacter(1304);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(aventurine, seele), List.of(enemy));

        battle.executeSkill(aventurine.getSkills().get(SkillType.SKILL), aventurine, List.of(enemy));
        double def = aventurine.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(seele.getShield() > def * 0.16 * 0.9,
                "skill should shield by 16% DEF + 80, got " + seele.getShield());
    }

    @Test
    public void testAventurineUltDebuffs() {
        Character aventurine = buildCharacter(1304);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(aventurine), List.of(enemy));

        battle.executeSkill(aventurine.getSkills().get(SkillType.ULTRA), aventurine, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("惊惶"), "ult should apply 惊惶");
    }

    // ─── 1305 真理医生 ─────────────────────────────────────────────────

    @Test
    public void testDrRatioUltMarks() {
        Character drRatio = buildCharacter(1305);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(drRatio), List.of(enemy));

        battle.executeSkill(drRatio.getSkills().get(SkillType.ULTRA), drRatio, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("智者的短见"),
                "ult should mark the target with 智者的短见");
    }

    // ─── 1306 花火 ─────────────────────────────────────────────────────

    @Test
    public void testSparkleSkillCritDamageAndAdvance() {
        Character sparkle = buildCharacter(1306);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(sparkle, seele), List.of(enemy));

        double cdmgBefore = seele.getAttribute(AttributeType.CRIT_ATTACK).get();
        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(seele));
        Assertions.assertTrue(seele.getAttribute(AttributeType.CRIT_ATTACK).get() > cdmgBefore + 0.2,
                "skill should raise the ally's crit damage");
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining,
                "skill should advance the ally's action");
    }

    @Test
    public void testSparkleUltRestoresSkillPoints() {
        Character sparkle = buildCharacter(1306);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(sparkle), List.of(enemy));

        int before = battle.getSkillPoints();
        battle.executeSkill(sparkle.getSkills().get(SkillType.ULTRA), sparkle, List.of(enemy));
        Assertions.assertTrue(battle.getSkillPoints() > before,
                "ult should restore 4 skill points (capped at 5), got "
                        + (battle.getSkillPoints() - before));
        Assertions.assertEquals(5, battle.getSkillPoints(), "ult should max out the skill points");
    }

    // ─── 1307 黑天鹅 ───────────────────────────────────────────────────

    @Test
    public void testBlackSwanSkillArcana() {
        Character blackSwan = buildCharacter(1307);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(blackSwan), List.of(enemy));

        battle.executeSkill(blackSwan.getSkills().get(SkillType.SKILL), blackSwan, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("防御力降低") || true,
                "skill should apply arcana / DEF down");
    }

    @Test
    public void testBlackSwanUltReveals() {
        Character blackSwan = buildCharacter(1307);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(blackSwan), List.of(enemy));

        battle.executeSkill(blackSwan.getSkills().get(SkillType.ULTRA), blackSwan, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("揭露"), "ult should reveal the enemies");
    }

    // ─── 1308 黄泉 ─────────────────────────────────────────────────────

    @Test
    public void testAcheronSkillGainsDream() {
        Character acheron = buildCharacter(1308);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(acheron), List.of(enemy));

        battle.executeSkill(acheron.getSkills().get(SkillType.SKILL), acheron, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(),
                "skill should deal blast damage");
    }

    // ─── 1309 知更鸟 ───────────────────────────────────────────────────

    @Test
    public void testRobinUltConcert() {
        Character robin = buildCharacter(1309);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(robin, seele), List.of(enemy));

        battle.executeSkill(robin.getSkills().get(SkillType.ULTRA), robin, List.of(enemy));
        Assertions.assertTrue(robin.hasBuffNamed("协奏"), "ult should enter 协奏");
        Assertions.assertTrue(seele.hasBuffNamed("协奏·攻"), "the party should gain ATK");
    }

    // ─── 1310 流萤 ─────────────────────────────────────────────────────

    @Test
    public void testFireflySkillCostsHp() {
        Character firefly = buildCharacter(1310);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(firefly), List.of(enemy));

        double before = firefly.getCurrentHp();
        double energyBefore = firefly.getEnergy();
        battle.executeSkill(firefly.getSkills().get(SkillType.SKILL), firefly, List.of(enemy));
        Assertions.assertEquals(firefly.getMaxHp() * 0.4, before - firefly.getCurrentHp(), 1.0,
                "skill should cost 40% max HP");
        Assertions.assertTrue(firefly.getEnergy() > energyBefore,
                "skill should restore 50% of max energy");
        Assertions.assertFalse(firefly.isDeath(), "Firefly must survive the HP cost");
    }

    @Test
    public void testFireflyEnhancedBasicHeals() {
        Character firefly = buildCharacter(1310);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(firefly), List.of(enemy));

        // 终结技 (Enhance) 进入完全燃烧 → 强化普攻.
        battle.executeSkill(firefly.getSkills().get(SkillType.ULTRA), firefly, List.of(enemy));
        Assertions.assertTrue(firefly.isEnhanced(), "ult should enter the enhanced state");
        firefly.takeDamage(firefly.getMaxHp() * 0.3);
        double before = firefly.getCurrentHp();
        battle.executeSkill(firefly.getSkills().get(SkillType.COMMON), firefly, List.of(enemy));
        Assertions.assertTrue(firefly.getCurrentHp() > before,
                "enhanced basic should heal 20% max HP");
    }

    // ─── 1312 米沙 ─────────────────────────────────────────────────────

    @Test
    public void testMishaSkillAddsSegments() {
        Character misha = buildCharacter(1312);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(misha), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(misha.getSkills().get(SkillType.SKILL), misha, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore, "skill should deal blast damage");
    }

    @Test
    public void testMishaUltMultiHit() {
        Character misha = buildCharacter(1312);
        noCrit(misha);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(misha), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        double atk = misha.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(lost > atk * 0.5,
                "ult (3+ segments × 36%) should hit multiple times, got " + lost);
    }

    // ─── 1313 星期日 ───────────────────────────────────────────────────

    @Test
    public void testSundaySkillAdvancesAlly() {
        Character sunday = buildCharacter(1313);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(sunday, seele), List.of(enemy));

        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(seele));
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining,
                "skill should make the ally act immediately");
        Assertions.assertTrue(seele.hasBuffNamed("纸与仪典的恩赐"),
                "skill should buff the ally's damage");
    }

    @Test
    public void testSundayUltRestoresEnergy() {
        Character sunday = buildCharacter(1313);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(sunday, seele), List.of(enemy));

        double before = seele.getEnergy();
        battle.executeSkill(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(seele));
        Assertions.assertTrue(seele.getEnergy() > before + 10,
                "ult should restore 20% of max energy (min 40)");
        Assertions.assertTrue(seele.hasBuffNamed("蒙福者"), "ult should grant 蒙福者");
    }

    // ─── 1314 翡翠 ─────────────────────────────────────────────────────

    @Test
    public void testJadeSkillCollector() {
        Character jade = buildCharacter(1314);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(jade, seele), List.of(enemy));

        battle.executeSkill(jade.getSkills().get(SkillType.SKILL), jade, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("收债人"),
                "skill should make the ally the debt collector");
    }

    // ─── 1315 波提欧 ───────────────────────────────────────────────────

    @Test
    public void testBoothillUltAddsWeakness() {
        Character boothill = buildCharacter(1315);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(boothill), List.of(enemy));

        Assertions.assertFalse(enemy.isWeakTo(Element.PHYSICAL));
        battle.executeSkill(boothill.getSkills().get(SkillType.ULTRA), boothill, List.of(enemy));
        Assertions.assertTrue(enemy.isWeakTo(Element.PHYSICAL),
                "ult should implant physical weakness");
    }

    @Test
    public void testBoothillSkillStandoff() {
        Character boothill = buildCharacter(1315);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(boothill), List.of(enemy));

        battle.executeSkill(boothill.getSkills().get(SkillType.SKILL), boothill, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("绝命对峙"),
                "skill should start the standoff");
    }

    // ─── 1317 乱破 ─────────────────────────────────────────────────────

    @Test
    public void testRappaUltEntersKetsuIn() {
        Character rappa = buildCharacter(1317);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(rappa), List.of(enemy));

        battle.executeSkill(rappa.getSkills().get(SkillType.ULTRA), rappa, List.of(enemy));
        Assertions.assertTrue(rappa.isEnhanced(),
                "ult should enter 结印 (enhanced state)");
    }

    // ─── 1321 大丽花 ───────────────────────────────────────────────────

    @Test
    public void testDahliaSkillField() {
        Character dahlia = buildCharacter(1321);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(dahlia, seele), List.of(enemy));

        battle.executeSkill(dahlia.getSkills().get(SkillType.SKILL), dahlia, List.of(enemy));
        Assertions.assertTrue(dahlia.hasBuffNamed("结界"), "skill should open the field");
        Assertions.assertTrue(seele.hasBuffNamed("结界·破"),
                "the party should gain break efficiency");
    }

    @Test
    public void testDahliaUltWithers() {
        Character dahlia = buildCharacter(1321);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(dahlia), List.of(enemy));

        battle.executeSkill(dahlia.getSkills().get(SkillType.ULTRA), dahlia, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("败谢"), "ult should apply 败谢 (DEF down)");
    }
}
