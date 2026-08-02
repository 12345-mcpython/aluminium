package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 11** characters' special skill behaviors (技能):
 * Bronya 战技/终结技, Seele 战技/终结技, Gepard 战技/终结技,
 * Natasha 战技, Pela 战技/终结技, Sampo 战技/终结技, Hook 战技/终结技,
 * Lynx 战技/终结技, Luka 战技/终结技, Topaz 战技/终结技, Clara 战技/终结技.
 */
public class Test11StarSkills {

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

    // ─── Bronya ────────────────────────────────────────────────────────

    @Test
    public void testBronyaSkillCleansesAndAdvances() {
        Character bronya = buildCharacter(1101);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Buff debuff = new Buff("DEF Down", Buff.Category.DEBUFF, enemy, seele, 2)
                .stat(AttributeType.DEFENCE,
                        com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        seele.applyBuff(debuff);
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        double remaining = battle.queue.timeUntilNext();
        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
        Assertions.assertFalse(seele.hasBuffNamed("DEF Down"), "skill should cleanse the ally's debuff");
        Assertions.assertTrue(seele.hasBuffNamed("作战指令"), "skill should buff the ally's damage");
        Assertions.assertTrue(battle.queue.timeUntilNext() < remaining,
                "skill should advance the designated ally's action");
    }

    @Test
    public void testBronyaUltPartyAtkCritDamage() {
        Character bronya = buildCharacter(1101);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(bronya, seele), List.of(enemy));

        double atkBefore = seele.getAttribute(AttributeType.ATTACK).get();
        double critDmgBefore = seele.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.executeSkill(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        double atkAfter = seele.getAttribute(AttributeType.ATTACK).get();
        double critDmgAfter = seele.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertTrue(seele.hasBuffNamed("作战指令"),
                "ult should buff the party's ATK");
        Assertions.assertTrue(atkAfter > atkBefore * 1.2,
                "ult should raise party ATK by ~33%, got " + (atkAfter / atkBefore - 1));
        Assertions.assertTrue(critDmgAfter > critDmgBefore + 0.15,
                "ult should raise party crit damage, got " + (critDmgAfter - critDmgBefore));
    }

    // ─── Seele ─────────────────────────────────────────────────────────

    @Test
    public void testSeeleSkillSpeedBuff() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        double before = seele.getAttribute(AttributeType.SPEED).get();
        battle.executeSkill(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
        double after = seele.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(after > before * 1.24,
                "skill should raise SPD by 25%, got " + (after / before - 1));
    }

    @Test
    public void testSeeleUltEntersAmplification() {
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(seele), List.of(enemy));

        battle.executeSkill(seele.getSkills().get(SkillType.ULTRA), seele, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("增幅"), "ult should enter 增幅 state");
    }

    // ─── Gepard ────────────────────────────────────────────────────────

    @Test
    public void testGepardSkillFreezes() {
        Character gepard = buildCharacter(1104);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(gepard), List.of(enemy));

        // 65% 基础概率 — 堆满效果命中使测试确定性成立.
        gepard.getAttribute(AttributeType.EFFECT_HIT_RATE).base(1.0);
        battle.executeSkill(gepard.getSkills().get(SkillType.SKILL), gepard, List.of(enemy));
        Assertions.assertEquals(Buff.ControlType.FROZEN, enemy.getControlState(),
                "Gepard's skill should freeze (65% base chance)");
    }

    @Test
    public void testGepardUltDefBasedShield() {
        Character gepard = buildCharacter(1104);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(gepard, seele), List.of(enemy));

        battle.executeSkill(gepard.getSkills().get(SkillType.ULTRA), gepard, List.of(enemy));
        double def = gepard.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(seele.getShield() > def * 0.3 * 0.9,
                "ult should shield the party by 30% DEF + 150, got " + seele.getShield());
    }

    // ─── Natasha ───────────────────────────────────────────────────────

    @Test
    public void testNatashaSkillSingleHealAndHoT() {
        Character natasha = buildCharacter(1105);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(natasha, seele), List.of(enemy));

        seele.takeDamage(500);
        double before = seele.getCurrentHp();
        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before,
                "skill should heal the designated ally");
        Assertions.assertTrue(seele.hasBuffNamed("持续回复"),
                "skill should apply the HoT (2 turns, +1 from 调理)");
        Buff hot = seele.getBuffs().stream()
                .filter(b -> "持续回复".equals(b.getName())).findFirst().orElseThrow();
        Assertions.assertEquals(3, hot.getDuration(), "调理 should extend the HoT by 1 turn");
    }

    // ─── Pela ──────────────────────────────────────────────────────────

    @Test
    public void testPelaSkillDispelsBuff() {
        Character pela = buildCharacter(1106);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Buff buff = new Buff("Atk Up", Buff.Category.BUFF, enemy, enemy, 2)
                .stat(AttributeType.ATTACK,
                        com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(0.2));
        enemy.applyBuff(buff);
        Battle battle = battleOf(List.of(pela), List.of(enemy));

        battle.executeSkill(pela.getSkills().get(SkillType.SKILL), pela, List.of(enemy));
        Assertions.assertFalse(enemy.hasBuffNamed("Atk Up"),
                "Pela's skill should dispel 1 enemy buff");
    }

    @Test
    public void testPelaUltUniversalSolver() {
        Character pela = buildCharacter(1106);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = battleOf(List.of(pela), List.of(enemy));

        double defBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        battle.executeSkill(pela.getSkills().get(SkillType.ULTRA), pela, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("通解"),
                "ult should apply 通解 (DEF -30%, 100% base chance)");
        double defAfter = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(defAfter < defBefore * 0.75,
                "通解 should reduce DEF by 30%, got " + (defAfter / defBefore - 1));
    }

    // ─── Sampo ─────────────────────────────────────────────────────────

    @Test
    public void testSampoSkillBounceMultiplier() {
        Character sampo = buildCharacter(1108);
        noCrit(sampo);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(sampo), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(sampo.getSkills().get(SkillType.SKILL), sampo, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        // 战技 5 段 × 28% = 1.4 × 普攻 50% 的 2.8 倍 (同一防御乘区, 比值抵消).
        double hpBeforeBasic = enemy.getCurrentHp();
        battle.executeSkill(sampo.getSkills().get(SkillType.COMMON), sampo, List.of(enemy));
        double basicLost = hpBeforeBasic - enemy.getCurrentHp();
        double ratio = lost / basicLost;
        Assertions.assertTrue(ratio > 2.4 && ratio < 3.2,
                "skill should be 5 hits × 28% (2.8× of the basic), got " + ratio);
    }

    @Test
    public void testSampoUltDotTakenDebuff() {
        Character sampo = buildCharacter(1108);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "Wind Weakling");
        Battle battle = battleOf(List.of(sampo), List.of(enemy));

        battle.executeSkill(sampo.getSkills().get(SkillType.ULTRA), sampo, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("持续伤害提高"),
                "ult should raise DoT taken by 20%");
    }

    // ─── Hook ──────────────────────────────────────────────────────────

    @Test
    public void testHookSkillBurns() {
        Character hook = buildCharacter(1109);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(hook), List.of(enemy));

        battle.executeSkill(hook.getSkills().get(SkillType.SKILL), hook, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.FIRE),
                "Hook's skill should burn (100% base chance)");
    }

    @Test
    public void testHookUltEnhancesNextSkill() {
        Character hook = buildCharacter(1109);
        noCrit(hook);
        Enemy main = buildEnemy(Element.ICE, Element.FIRE, "Main Foe");
        Enemy adjacent = buildEnemy(Element.ICE, Element.FIRE, "Adjacent Foe");
        Battle battle = battleOf(List.of(hook), List.of(main, adjacent));

        battle.executeSkill(hook.getSkills().get(SkillType.ULTRA), hook, List.of(main));
        double hpAdjBefore = adjacent.getCurrentHp();
        battle.executeSkill(hook.getSkills().get(SkillType.SKILL), hook, List.of(main));
        Assertions.assertTrue(adjacent.getCurrentHp() < hpAdjBefore,
                "enhanced skill should also hit adjacent enemies");
    }

    // ─── Lynx ──────────────────────────────────────────────────────────

    @Test
    public void testLynxSkillSurvivalResponse() {
        Character lynx = buildCharacter(1110);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(lynx, seele), List.of(enemy));

        seele.takeDamage(500);
        double before = seele.getCurrentHp();
        double maxBefore = seele.getMaxHp();
        battle.executeSkill(lynx.getSkills().get(SkillType.SKILL), lynx, List.of(enemy));
        Assertions.assertTrue(seele.getCurrentHp() > before, "skill should heal the ally");
        Assertions.assertTrue(seele.hasBuffNamed("求生反应"),
                "skill should apply 求生反应 (max HP +5% + 50)");
        Assertions.assertTrue(seele.getMaxHp() > maxBefore + 50,
                "求生反应 should raise max HP, got " + (seele.getMaxHp() - maxBefore));
    }

    @Test
    public void testLynxE2DebuffResist() {
        Character lynx = buildCharacter(1110, 2);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(lynx, seele), List.of(enemy));

        battle.executeSkill(lynx.getSkills().get(SkillType.SKILL), lynx, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("求生抵抗"),
                "E2 should grant 求生抵抗 with 求生反应");

        Buff defDown = new Buff("DEF Down", Buff.Category.DEBUFF, enemy, seele, 2)
                .stat(AttributeType.DEFENCE,
                        com.laosun.aluminium.models.DoubleValue.Modifier.addPercent(-0.1));
        battle.applyBuff(seele, defDown);
        Assertions.assertFalse(seele.hasBuffNamed("DEF Down"),
                "E2: the holder should resist 1 debuff application");
        Assertions.assertFalse(seele.hasBuffNamed("求生抵抗"),
                "E2: the resist marker should be consumed");
    }

    @Test
    public void testLynxE6SurvivalResponseBoost() {
        Character lynx = buildCharacter(1110, 6);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = battleOf(List.of(lynx, seele), List.of(enemy));

        battle.executeSkill(lynx.getSkills().get(SkillType.SKILL), lynx, List.of(enemy));
        Assertions.assertTrue(seele.hasBuffNamed("求生反应·强化"),
                "E6 should boost 求生反应 (max HP +6%, effect RES +30%)");
        double res = seele.getAttribute(AttributeType.EFFECT_RESISTANCE) != null
                ? seele.getAttribute(AttributeType.EFFECT_RESISTANCE).get() : 0;
        Assertions.assertTrue(res >= 0.29, "E6 should raise effect RES by 30%, got " + res);
    }

    // ─── Luka ──────────────────────────────────────────────────────────

    @Test
    public void testLukaSkillBleedScaling() {
        Character luka = buildCharacter(1111);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(luka), List.of(enemy));

        battle.executeSkill(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        Assertions.assertTrue(enemy.hasDotOfElement(Element.PHYSICAL),
                "skill should bleed (100% base chance)");
        Buff.Dot bleed = enemy.getDots().stream()
                .filter(d -> d.getElement() == Element.PHYSICAL).findFirst().orElseThrow();
        // 24% 目标生命上限, 上限为卢卡攻击力的 130%.
        double expected = Math.min(enemy.getMaxHp() * 0.24,
                luka.getAttribute(AttributeType.ATTACK).get() * 1.3);
        Assertions.assertEquals(expected, bleed.getDamage(), expected * 0.01,
                "bleed should be min(24% max HP, 130% ATK)");
    }

    @Test
    public void testLukaUltVulnerability() {
        Character luka = buildCharacter(1111);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "Physical Weakling");
        Battle battle = battleOf(List.of(luka), List.of(enemy));

        battle.executeSkill(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("易伤"),
                "ult should apply vulnerability (+12% damage taken)");
    }

    // ─── Topaz ─────────────────────────────────────────────────────────

    @Test
    public void testTopazSkillDebtProof() {
        Character topaz = buildCharacter(1112);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(topaz), List.of(enemy));

        battle.executeSkill(topaz.getSkills().get(SkillType.SKILL), topaz, List.of(enemy));
        Assertions.assertTrue(enemy.hasBuffNamed("负债证明"),
                "skill should mark the target with 负债证明");
    }

    @Test
    public void testTopazUltNumbyBoost() {
        Character topaz = buildCharacter(1112);
        noCrit(topaz);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = battleOf(List.of(topaz), List.of(enemy));

        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(topaz.getSkills().get(SkillType.ULTRA), topaz, List.of(enemy));
        battle.executeSkill(topaz.getSkills().get(SkillType.COMMON), topaz, List.of(enemy));
        double lost = hpBefore - enemy.getCurrentHp();
        Assertions.assertTrue(lost > topaz.getAttribute(AttributeType.ATTACK).get() * 0.5,
                "涨幅惊人 should boost 账账's follow-up damage, got " + lost);
    }

    // ─── Clara ─────────────────────────────────────────────────────────

    @Test
    public void testClaraSkillBonusVsMarked() {
        Character clara = buildCharacter(1107);
        noCrit(clara);
        Enemy marked = buildEnemy(Element.ICE, Element.PHYSICAL, "Marked Foe");
        Enemy plain = buildEnemy(Element.ICE, Element.PHYSICAL, "Plain Foe");
        Battle battle = battleOf(List.of(clara), List.of(marked, plain));

        // 被攻击 → 标上【反击标记】.
        battle.dealAttackDamage(marked, clara, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, marked.getElement()));
        double hpMarked = marked.getCurrentHp();
        double hpPlain = plain.getCurrentHp();
        battle.executeSkill(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked));
        double lostMarked = hpMarked - marked.getCurrentHp();
        double lostPlain = hpPlain - plain.getCurrentHp();
        Assertions.assertTrue(lostMarked > lostPlain * 1.5,
                "skill should deal extra damage to counter-marked enemies, got "
                        + lostMarked + " vs " + lostPlain);
    }

    @Test
    public void testClaraE1KeepsCounterMarks() {
        Character clara = buildCharacter(1107, 1);
        noCrit(clara);
        Enemy marked = buildEnemy(Element.ICE, Element.PHYSICAL, "Marked Foe");
        Battle battle = battleOf(List.of(clara), List.of(marked));

        // 被攻击 → 标记; 战技后标记不失效 (星魂1) → 第二次战技仍吃额外伤害.
        battle.dealAttackDamage(marked, clara, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, marked.getElement()));
        double hpBefore1 = marked.getCurrentHp();
        battle.executeSkill(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked));
        double lost1 = hpBefore1 - marked.getCurrentHp();
        double hpBefore2 = marked.getCurrentHp();
        battle.executeSkill(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked));
        double lost2 = hpBefore2 - marked.getCurrentHp();
        Assertions.assertTrue(lost2 > lost1 * 0.9,
                "E1: the mark should persist, so the 2nd skill deals comparable damage, got "
                        + lost2 + " vs " + lost1);
    }

    @Test
    public void testClaraMarksClearedWithoutE1() {
        Character clara = buildCharacter(1107);
        noCrit(clara);
        Enemy marked = buildEnemy(Element.ICE, Element.PHYSICAL, "Marked Foe");
        Battle battle = battleOf(List.of(clara), List.of(marked));

        // 被攻击 → 标记; 战技后标记失效 → 第二次战技不再有额外伤害.
        battle.dealAttackDamage(marked, clara, 0.5, 0,
                DamageContext.of(DamageType.NORMAL, marked.getElement()));
        double hpBefore1 = marked.getCurrentHp();
        battle.executeSkill(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked));
        double lost1 = hpBefore1 - marked.getCurrentHp();
        double hpBefore2 = marked.getCurrentHp();
        battle.executeSkill(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked));
        double lost2 = hpBefore2 - marked.getCurrentHp();
        Assertions.assertTrue(lost2 < lost1 * 0.8,
                "without E1 the mark should be cleared, so the 2nd skill deals less damage, got "
                        + lost2 + " vs " + lost1);
    }
}
