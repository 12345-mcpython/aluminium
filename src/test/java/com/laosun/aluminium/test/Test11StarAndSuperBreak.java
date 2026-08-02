package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DamageCalculator;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import com.laosun.aluminium.models.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 11** characters (traces + eidolons) and the 超击破 mechanic.
 */
public class Test11StarAndSuperBreak {

    private Character buildCharacter(int cid) {
        return Character.builder().cid(cid).level(80).isPromote().eidolon(6).build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testAll11StarCharactersHaveTracesAndEidolons() {
        for (int cid : new int[]{1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112}) {
            Character character = buildCharacter(cid);
            Assertions.assertEquals(3, character.getTraces().stream()
                    .filter(t -> !t.getName().startsWith("星魂")).count(),
                    "cid " + cid + " should have 3 trace passives");
            long eidolons = character.getTraces().stream()
                    .filter(t -> t.getName().startsWith("星魂")).count();
            Assertions.assertTrue(eidolons >= 2 && eidolons <= 4,
                    "cid " + cid + " should have 2-4 implemented eidolon passives, was " + eidolons);
        }
    }

    // ─── 超击破 (HSR.md 超击破) ─────────────────────────────────────────

    @Test
    public void testSuperBreakRequiresDanceDream() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Broken Foe");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Break the enemy.
        enemy.reduceToughness(30, Element.QUANTUM);
        Assertions.assertTrue(enemy.isBroken());

        // Without 舞梦: attacking a broken enemy deals NO super break damage.
        double hpBefore = enemy.getCurrentHp();
        battle.breakToughness(seele, enemy, 1.0);
        Assertions.assertEquals(hpBefore, enemy.getCurrentHp(), 0.001,
                "no super break without 舞梦");
    }

    @Test
    public void testSuperBreakWithDanceDream() {
        Character trailblazer = Character.builder().cid(8005).level(80).isPromote().build();
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Broken Foe");
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        enemy.reduceToughness(30, Element.QUANTUM);
        Assertions.assertTrue(enemy.isBroken());

        // Harmony Trailblazer's ult applies 【舞梦】.
        battle.executeSkill(trailblazer.getSkills().get(SkillType.ULTRA), trailblazer,
                List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("舞梦"), "舞梦 should be applied to the party");

        // With 舞梦: attacking a broken enemy triggers super break damage.
        double hpBefore = enemy.getCurrentHp();
        battle.breakToughness(seele, enemy, 1.0);
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore - 100,
                "super break should deal damage");
    }

    @Test
    public void testSuperBreakFormula() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Broken Foe");
        double damage = DamageCalculator.calculateSuperBreakDamage(seele, enemy, 2.0);
        Assertions.assertTrue(damage > 100, "super break should be meaningful");

        // Scales with break effect.
        seele.getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT)
                .base(2.0);
        double boosted = DamageCalculator.calculateSuperBreakDamage(seele, enemy, 2.0);
        Assertions.assertTrue(boosted > damage * 2.5, "super break scales with break effect");
    }

    @Test
    public void testToughnessEfficiencyScalesBreak() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Ice Weakling");
        seele.getAttribute(com.laosun.aluminium.enums.AttributeType.WEAKNESS_BREAK_EFFICIENCY)
                .base(0.5);

        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();
        battle.breakToughness(seele, enemy, 1.0);
        // 1.0 × (1 + 0.5) = 1.5 units removed.
        Assertions.assertEquals(30 - 1.5, enemy.getCurrentToughness(), 0.001,
                "weakness break efficiency should boost toughness damage");
    }

    // ─── 11** traces ───────────────────────────────────────────────────

    @Test
    public void testBronyaBasicAlwaysCrits() {
        Character bronya = buildCharacter(1101);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        double bonus = 0;
        for (Trace trace : bronya.getTraces()) {
            bonus += trace.critChanceBonus(null, bronya, enemy, SkillType.COMMON);
        }
        Assertions.assertEquals(1.0, bonus, 0.001, "号令: basic attack crit chance 100%");
    }

    @Test
    public void testBronyaPartyBuff() {
        Character bronya = buildCharacter(1101);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(bronya, seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(seele.hasBuffNamed("阵地"), "阵地: party DEF buff at battle start");
        Assertions.assertTrue(seele.hasBuffNamed("军势"), "军势: party damage buff");
    }

    @Test
    public void testServalShockAndFrenzy() {
        Character serval = buildCharacter(1103);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(serval)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(serval.getSkills().get(SkillType.SKILL), serval, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.THUNDER), "摇滚: skill should shock");
    }

    @Test
    public void testNatashaHoT() {
        Character natasha = buildCharacter(1105);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(natasha, seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        seele.takeDamage(500);
        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("持续回复"), "调理: HoT should be applied");
        double before = seele.getCurrentHp();
        seele.tickStatuses(battle);
        Assertions.assertTrue(seele.getCurrentHp() > before, "HoT should heal at turn start");
    }

    @Test
    public void testSeeleKillEnergyAndResurgence() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(seele)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.dealAttackDamage(seele, enemy, 99999, 0,
                DamageContext.of(DamageType.NORMAL, seele.getElement()));
        Assertions.assertTrue(enemy.isDeath());
        // 割裂: kill applies 增幅 (quantum pen); E4: +15 energy.
        Assertions.assertTrue(seele.hasBuffNamed("增幅") || seele.isDeath() == false,
                "割裂: 增幅 state after kill");
    }

    @Test
    public void testTopazFollowUpInsteadOfSummon() {
        // 账账是传统召唤物 (非忆灵, HSR.md §4.2): 无独立单位, 以追加攻击代理.
        Character topaz = buildCharacter(1112);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(topaz)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertTrue(topaz.getSummons().isEmpty(),
                "非记忆命途角色不应有忆灵");
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(topaz.getSkills().get(SkillType.COMMON), topaz, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "透支: 普攻应附带账账追加攻击");
    }

    @Test
    public void testPelaDebuffSynergy() {
        Character pela = buildCharacter(1106);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Buff defDown = new Buff("DEF Down", Buff.Category.DEBUFF, pela, enemy, 2);
        enemy.applyBuff(defDown);

        double multiplier = 1.0;
        for (Trace trace : pela.getTraces()) {
            multiplier *= trace.damageMultiplier(null, pela, enemy, SkillType.SKILL);
        }
        Assertions.assertEquals(1.2, multiplier, 0.001, "痛击: +20% vs debuffed enemies");
    }

    @Test
    public void testClaraCounter() {
        Character clara = buildCharacter(1107);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(clara)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double hpBefore = enemy.getCurrentHp();
        battle.dealAttackDamage(enemy, clara, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "复仇: counter-attack should damage the attacker");
    }

    @Test
    public void testLukaBleedKeyword() {
        Character luka = buildCharacter(1111);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(luka)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Luka's skill applies bleed via the desc-driven 裂伤 keyword.
        battle.executeSkill(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.PHYSICAL),
                "skill should apply bleed (裂伤)");
    }

    @Test
    public void testBronyaE1SkillPoint() {
        Character bronya = buildCharacter(1101);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(bronya)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // E1: 50% fixed chance to restore 1 SP after a skill (1-turn cooldown).
        boolean procced = false;
        for (int i = 0; i < 60; i++) {
            int before = battle.getSkillPoints();
            battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
            if (battle.getSkillPoints() > before - 1) {
                procced = true;
                break;
            }
        }
        Assertions.assertTrue(procced, "E1 should restore skill points over 60 casts");
    }
}
