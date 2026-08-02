package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 15** characters' hand-written kits (技能/行迹/星魂):
 * Sparxie, Yao Guang, Ashveil, Evanescia, Silver Wolf LV.999, Mortenax Blade,
 * Himeko Nova.
 */
public class Test15StarSkills {

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

    private Battle battleOf(List<Character> party, List<Enemy> enemies) {
        Battle battle = new Battle(new ArrayList<>(party), new ArrayList<>(enemies));
        battle.startBattle();
        return battle;
    }

    // ─── 1501 火花 ─────────────────────────────────────────────────────

    @Test
    public void testSparxieUltGainsLaughPoints() {
        Character sparxie = buildCharacter(1501);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(sparxie), List.of(enemy));

        double before = battle.getLaughPoints();
        battle.executeSkill(sparxie.getSkills().get(SkillType.ULTRA), sparxie, List.of(enemy));
        Assertions.assertTrue(battle.getLaughPoints() > before,
                "ult should gain 笑点");
    }

    // ─── 1502 爻光 ─────────────────────────────────────────────────────

    @Test
    public void testYaoGuangSkillField() {
        Character yaoGuang = buildCharacter(1502);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(yaoGuang, seele), List.of(enemy));

        battle.executeSkill(yaoGuang.getSkills().get(SkillType.SKILL), yaoGuang, List.of(enemy));
        Assertions.assertTrue(yaoGuang.hasBuffNamed("结界"), "skill should open the field");
        Assertions.assertTrue(seele.hasBuffNamed("结界·欢愉"),
                "the party should gain 欢愉度");
    }

    @Test
    public void testYaoGuangUltGainsLaughPoints() {
        Character yaoGuang = buildCharacter(1502);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(yaoGuang, seele), List.of(enemy));

        double before = battle.getLaughPoints();
        battle.executeSkill(yaoGuang.getSkills().get(SkillType.ULTRA), yaoGuang, List.of(enemy));
        Assertions.assertTrue(battle.getLaughPoints() > before,
                "ult should gain 5 笑点");
        Assertions.assertTrue(seele.hasBuffNamed("霓裳铁羽"),
                "the party should gain all-res penetration");
    }

    // ─── 1504 不死途 ───────────────────────────────────────────────────

    @Test
    public void testAshveilSkillBait() {
        Character ashveil = buildCharacter(1504);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = battleOf(List.of(ashveil), List.of(enemy));

        battle.executeSkill(ashveil.getSkills().get(SkillType.SKILL), ashveil, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("饲饵"), "skill should mark the bait");
        Assertions.assertTrue(enemy.hasBuffNamed("饲饵·减防"),
                "the bait should lower the enemy's DEF");
    }

    // ─── 1505 绯英 ─────────────────────────────────────────────────────

    @Test
    public void testEvanesciaUltBounces() {
        Character evanescia = buildCharacter(1505);
        noCrit(evanescia);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(evanescia), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(evanescia.getSkills().get(SkillType.ULTRA), evanescia, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        double atk = evanescia.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(lost > atk * 0.5,
                "ult (80% + 5×72% bounces) should deal heavy damage, got " + lost);
    }

    // ─── 1506 银狼LV.999 ───────────────────────────────────────────────

    @Test
    public void testSilverWolf999SkillGainsLaughPoints() {
        Character silverWolf = buildCharacter(1506);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = battleOf(List.of(silverWolf), List.of(enemy));

        double before = battle.getLaughPoints();
        battle.executeSkill(silverWolf.getSkills().get(SkillType.SKILL), silverWolf, List.of(enemy));
        Assertions.assertTrue(battle.getLaughPoints() > before,
                "skill should gain 5 笑点");
    }

    // ─── 1507 千冶·刃 ──────────────────────────────────────────────────

    @Test
    public void testMortenaxUltWrathState() {
        Character mortenax = buildCharacter(1507);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(mortenax), List.of(enemy));

        double hpBefore = mortenax.getCurrentHp();
        battle.executeSkill(mortenax.getSkills().get(SkillType.ULTRA), mortenax, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("煞火缠身"),
                "ult should apply 煞火缠身");
        Assertions.assertTrue(mortenax.hasBuffNamed("无量忿怒"),
                "ult should enter 无量忿怒 state");
        Assertions.assertTrue(hpBefore - mortenax.getCurrentHp() > 0,
                "ult should consume max HP");
        Assertions.assertFalse(mortenax.isDeath(), "Mortenax must survive the HP cost");
    }

    @Test
    public void testMortenaxBasicTaunts() {
        Character mortenax = buildCharacter(1507);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(mortenax), List.of(enemy));

        battle.executeSkill(mortenax.getSkills().get(SkillType.COMMON), mortenax, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("嘲讽"),
                "basic attack should taunt the target");
    }

    // ─── 1510 姬子·启行 ────────────────────────────────────────────────

    @Test
    public void testHimekoNovaSkillPilotFlag() {
        Character himekoNova = buildCharacter(1510);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(himekoNova, seele), List.of(enemy));

        battle.executeSkill(himekoNova.getSkills().get(SkillType.SKILL), himekoNova, List.of(enemy));
        Assertions.assertTrue(himekoNova.hasBuffNamed("领航旗语"),
                "skill should raise the pilot flag");
        Assertions.assertTrue(seele.hasBuffNamed("领航旗语·伤"),
                "the party should gain damage");
    }

    // ─── 全部 15** 行迹/星魂 结构 ──────────────────────────────────────

    @Test
    public void testAll15StarHaveHandWrittenPassives() {
        int[] cids = {1501, 1502, 1504, 1505, 1506, 1507, 1510};
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

    private void noCrit(Character character) {
        character.getAttribute(AttributeType.CRIT_CHANCE).clearModifiers();
        character.getAttribute(AttributeType.CRIT_CHANCE).base(0);
        character.getAttribute(AttributeType.CRIT_ATTACK).clearModifiers();
        character.getAttribute(AttributeType.CRIT_ATTACK).base(0);
    }
}
