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
 * Tests for the 14** characters' hand-written kits (技能/行迹/星魂):
 * The Herta, Aglaea, Tribbie, Mydei, Anaxa, Cipher, Castorice, Phainon,
 * Hyacine, Hysilens, Cerydra, Evernight, Dan Heng (Terrae), Cyrene.
 */
public class Test14StarSkills {

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

    // ─── 1401 大黑塔 ────────────────────────────────────────────────────

    @Test
    public void testTheHertaUltInspiration() {
        Character herta = buildCharacter(1401);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(herta), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        double atkBefore = herta.getAttribute(AttributeType.ATTACK).get();
        battle.executeSkill(herta.getSkills().get(SkillType.ULTRA), herta, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore, "ult should deal AoE damage");
        Assertions.assertTrue(herta.getAttribute(AttributeType.ATTACK).get() > atkBefore,
                "ult should buff ATK (+40%)");
    }

    @Test
    public void testTheHertaSkillDealsDamage() {
        Character herta = buildCharacter(1401);
        noCrit(herta);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(herta), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(herta.getSkills().get(SkillType.SKILL), herta, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore, "skill should deal blast damage");
    }

    // ─── 1402 阿格莱雅 ─────────────────────────────────────────────────

    @Test
    public void testAglaeaTracesPresent() {
        Character aglaea = buildCharacter(1402);
        Assertions.assertEquals(3, aglaea.getTraces().size(),
                "Aglaea should have 3 hand-written trace passives");
    }

    // ─── 1403 缇宝 ─────────────────────────────────────────────────────

    @Test
    public void testTribbieUltField() {
        Character tribbie = buildCharacter(1403);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(tribbie), List.of(enemy));

        battle.executeSkill(tribbie.getSkills().get(SkillType.ULTRA), tribbie, List.of(enemy));
        Assertions.assertTrue(tribbie.hasBuffNamed("结界"), "ult should open the field");
        Assertions.assertTrue(enemy.hasBuffNamed("结界·易伤"),
                "the field should make enemies take more damage");
    }

    // ─── 1404 万敌 ─────────────────────────────────────────────────────

    @Test
    public void testMydeiSkillCostsHp() {
        Character mydei = buildCharacter(1404);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(mydei), List.of(enemy));

        double before = mydei.getCurrentHp();
        battle.executeSkill(mydei.getSkills().get(SkillType.SKILL), mydei, List.of(enemy));
        Assertions.assertTrue(before - mydei.getCurrentHp() > 0,
                "skill should consume current HP");
        Assertions.assertFalse(mydei.isDeath(), "Mydei must survive the HP cost");
    }

    // ─── 1405 那刻夏 ───────────────────────────────────────────────────

    @Test
    public void testAnaxaDefenceIgnoreTrace() {
        Character anaxa = buildCharacter(1405);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(anaxa), List.of(enemy));

        double ignore = anaxa.getAttribute(AttributeType.DEFENCE_IGNORE) != null
                ? anaxa.getAttribute(AttributeType.DEFENCE_IGNORE).get() : 0;
        Assertions.assertTrue(ignore > 0, "质性的嬗变 should grant DEF ignore");
    }

    @Test
    public void testAnaxaUltSublimation() {
        Character anaxa = buildCharacter(1405);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(anaxa), List.of(enemy));

        battle.executeSkill(anaxa.getSkills().get(SkillType.ULTRA), anaxa, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("升华"), "ult should sublimate the enemy");
        Assertions.assertTrue(enemy.isWeakTo(Element.FIRE),
                "升华 should add all elemental weaknesses");
    }

    // ─── 1406 赛飞儿 ───────────────────────────────────────────────────

    @Test
    public void testCipherSkillDebuffs() {
        Character cipher = buildCharacter(1406);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(cipher), List.of(enemy));

        battle.executeSkill(cipher.getSkills().get(SkillType.SKILL), cipher, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(),
                "skill should deal blast damage");
    }

    // ─── 1407 遐蝶 ─────────────────────────────────────────────────────

    @Test
    public void testCastoriceSkillCostsPartyHp() {
        Character castorice = buildCharacter(1407);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(castorice, seele), List.of(enemy));

        double before = seele.getCurrentHp();
        battle.executeSkill(castorice.getSkills().get(SkillType.SKILL), castorice, List.of(enemy));
        Assertions.assertTrue(before - seele.getCurrentHp() > 0,
                "skill should consume the party's HP");
    }

    // ─── 1408 白厄 ─────────────────────────────────────────────────────

    @Test
    public void testPhainonUltTransforms() {
        Character phainon = buildCharacter(1408);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(phainon), List.of(enemy));

        battle.executeSkill(phainon.getSkills().get(SkillType.ULTRA), phainon, List.of(enemy));
        Assertions.assertTrue(phainon.isEnhanced(),
                "ult should transform into 卡厄斯兰那 (enhanced state)");
    }

    // ─── 1409 风堇 ─────────────────────────────────────────────────────

    @Test
    public void testHyacineUltHealsAndBuff() {
        Character hyacine = buildCharacter(1409);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(hyacine, seele), List.of(enemy));

        seele.takeDamage(800);
        double before = seele.getCurrentHp();
        battle.executeSkill(hyacine.getSkills().get(SkillType.ULTRA), hyacine, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before, "ult should heal the party");
        Assertions.assertTrue(hyacine.hasBuffNamed("雨过天晴"),
                "ult should enter 雨过天晴 state");
        Assertions.assertTrue(seele.hasBuffNamed("雨过天晴·生命"),
                "the party should gain max HP");
    }

    // ─── 1410 海瑟音 ───────────────────────────────────────────────────

    @Test
    public void testHysilensUltField() {
        Character hysilens = buildCharacter(1410);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(hysilens), List.of(enemy));

        battle.executeSkill(hysilens.getSkills().get(SkillType.ULTRA), hysilens, List.of(enemy));
        Assertions.assertTrue(hysilens.hasBuffNamed("结界"), "ult should open the field");
        Assertions.assertTrue(enemy.hasBuffNamed("绝海回涛"),
                "the field should lower enemy DEF/ATK");
    }

    // ─── 1412 刻律德菈 ─────────────────────────────────────────────────

    @Test
    public void testCerydraSkillMerit() {
        Character cerydra = buildCharacter(1412);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(cerydra, seele), List.of(enemy));

        battle.executeSkill(cerydra.getSkills().get(SkillType.SKILL), cerydra, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("军功") || seele.hasBuffNamed("爵位"),
                "skill should grant 军功 (or 爵位 at high charge)");
    }

    // ─── 1413 长夜月 ───────────────────────────────────────────────────

    @Test
    public void testEvernightUltDarkness() {
        Character evernight = buildCharacter(1413);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(evernight), List.of(enemy));

        battle.executeSkill(evernight.getSkills().get(SkillType.ULTRA), evernight, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("至暗之谜"),
                "ult should enter 至暗之谜 (enemies take more damage)");
    }

    // ─── 1414 丹恒·腾荒 ────────────────────────────────────────────────

    @Test
    public void testDanHengTerraeSkillShields() {
        Character danHeng = buildCharacter(1414);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(danHeng, seele), List.of(enemy));

        battle.executeSkill(danHeng.getSkills().get(SkillType.SKILL), danHeng, List.of(seele));
        Assertions.assertTrue(seele.hasBuffNamed("同袍"), "skill should designate 同袍");
        Assertions.assertTrue(seele.getShield() > 0, "skill should shield the party");
    }

    // ─── 1415 昔涟 ─────────────────────────────────────────────────────

    @Test
    public void testCyreneSkillField() {
        Character cyrene = buildCharacter(1415);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(cyrene), List.of(enemy));

        battle.executeSkill(cyrene.getSkills().get(SkillType.SKILL), cyrene, List.of(enemy));
        Assertions.assertTrue(cyrene.hasBuffNamed("结界"),
                "skill should open the field (true damage bonus)");
    }

    // ─── 全部 14** 行迹/星魂 结构 ──────────────────────────────────────

    @Test
    public void testAll14StarHaveHandWrittenPassives() {
        int[] cids = {1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1412,
                1413, 1414, 1415};
        for (int cid : cids) {
            Character character = buildCharacter(cid, 6);
            Assertions.assertEquals(3, character.getTraces().stream()
                            .filter(t -> !t.getName().startsWith("星魂")).count(),
                    "cid " + cid + " should have 3 trace passives");
            long eidolons = character.getTraces().stream()
                    .filter(t -> t.getName().startsWith("星魂")).count();
            Assertions.assertEquals(4, eidolons,
                    "cid " + cid + " should have 4 battle eidolon passives, was " + eidolons);
        }
    }
}
