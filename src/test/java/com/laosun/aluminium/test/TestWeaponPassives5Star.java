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
 * Tests for the hand-written 22*** light cone passives (五星活动光锥).
 */
public class TestWeaponPassives5Star {

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

    private Enemy multiWeakEnemy() {
        return Enemy.fromTemplate("Multi", 80,
                100000, 26, 240, 120, 30,
                Element.ICE, EnumSet.of(Element.FIRE, Element.WIND, Element.THUNDER, Element.QUANTUM),
                java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", Element.ICE, 0.5, SkillAttackType.SINGLE)));
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

    private double coneDmgMult(Character c, Battle battle, Enemy enemy, SkillType type, String coneName) {
        for (Trace trace : c.getTraces()) {
            if (coneName.equals(trace.getName())) {
                return trace.damageMultiplier(battle, c, enemy, type);
            }
        }
        return 1.0;
    }

    // ─── 22000 新手任务开始前: 攻击防御被降低的目标后+4能量 ─────────────

    @Test
    public void testNewbieTaskEnergyOnDefDown() {
        Character bronya = buildWithWeapon(1101, 22000);
        Enemy defDown = buildEnemy(Element.ICE, Element.WIND, "D");
        defDown.applyBuff(new Buff("攻陷", Buff.Category.DEBUFF, bronya, defDown, 2)
                .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-0.12)));
        Enemy normal = buildEnemy(Element.ICE, Element.WIND, "N");
        Battle battle = battleOf(List.of(bronya), List.of(normal, defDown));

        bronya.consumeEnergy();
        battle.executeSkill(bronya.getSkills().get(SkillType.COMMON), bronya, List.of(defDown));
        Assertions.assertEquals(24, bronya.getEnergy(), 0.001, "basic 20 + 新手任务 4");
        bronya.consumeEnergy();
        battle.addSkillPoints(5);
        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(normal));
        Assertions.assertEquals(30, bronya.getEnergy(), 0.001, "无降防目标时不触发");
    }

    // ─── 22001 嘿，我在这儿: 战技后治疗量+16%持续2回合 ──────────────────

    @Test
    public void testHeyOverHereSkillHealBoost() {
        Character natasha = buildWithWeapon(1105, 22001);
        Enemy enemy = buildEnemy(Element.ICE, Element.PHYSICAL, "P");
        Battle battle = battleOf(List.of(natasha), List.of(enemy));

        battle.executeSkill(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(enemy));
        Assertions.assertTrue(natasha.hasBuffNamed("嘿，我在这儿"));
        Assertions.assertTrue(attr(natasha, AttributeType.OUTGOING_HEALING_BOOST) >= 0.16);
    }

    // ─── 22002 为了明日的旅途: 终结技后伤害+18%持续1回合 ────────────────

    @Test
    public void testTomorrowJourneyUltBonus() {
        Character bronya = buildWithWeapon(1101, 22002);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya), List.of(enemy));

        battle.executeSkill(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        Assertions.assertTrue(bronya.hasBuffNamed("为了明日的旅途"));
        Assertions.assertTrue(attr(bronya, AttributeType.ALL_DAMAGE_TYPE_BOOST) >= 0.18);
    }

    // ─── 22003 忍事录•音律狩猎: 损失生命后暴伤+18%持续2回合 (每回合1次) ──

    @Test
    public void testHuntMelodyCdmgOnHit() {
        Character arlan = buildWithWeapon(1008, 22003);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "T");
        Battle battle = battleOf(List.of(arlan), List.of(enemy));

        Assertions.assertFalse(arlan.hasBuffNamed("忍事录•音律狩猎"));
        battle.dealAttackDamage(enemy, arlan, 0.5, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, enemy.getElement()));
        Assertions.assertTrue(arlan.hasBuffNamed("忍事录•音律狩猎"),
                "损失生命后应获得暴伤加成");
    }

    // ─── 22004 宇宙大生意: 每弱点+4% (最多7个) ──────────────────────────

    @Test
    public void testCosmicDealPerWeakness() {
        Character danHeng = buildWithWeapon(1002, 22004);
        Enemy single = buildEnemy(Element.ICE, Element.WIND, "S");
        Enemy multi = multiWeakEnemy();
        Assertions.assertEquals(1.04, coneDmgMult(danHeng, null, single, SkillType.SKILL, "宇宙大生意"), 0.001);
        Assertions.assertEquals(1.16, coneDmgMult(danHeng, null, multi, SkillType.SKILL, "宇宙大生意"), 0.001,
                "4个弱点 → +16%");
    }

    // ─── 22005 永远的迷境饭: 战技后攻击+8%/层, 最多3层 ─────────────────

    @Test
    public void testForeverMealSkillStacks() {
        Character bronya = buildWithWeapon(1101, 22005);
        Enemy enemy = buildEnemy(Element.ICE, Element.WIND, "W");
        Battle battle = battleOf(List.of(bronya), List.of(enemy));

        double atkBase = attr(bronya, AttributeType.ATTACK);
        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
        double atk1 = attr(bronya, AttributeType.ATTACK);
        battle.executeSkill(bronya.getSkills().get(SkillType.SKILL), bronya, List.of(enemy));
        Assertions.assertTrue(attr(bronya, AttributeType.ATTACK) > atk1 * 1.05,
                "每层应提高攻击力");
        Assertions.assertTrue(atk1 > atkBase * 1.05, "第1层也应提高攻击力");
    }

    // ─── 22006 飞向粉色的明天: 记忆开拓者装备时全队+8%, 强化普攻+60% ──

    @Test
    public void testPinkTomorrowTrailblazer() {
        Character tb = buildWithWeapon(8006, 22006);
        Character seele = buildWithWeapon(1102, 21002);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "I");
        Battle battle = battleOf(List.of(tb, seele), List.of(enemy));

        Assertions.assertTrue(seele.hasBuffNamed("飞向粉色的明天"),
                "我方全体应获得增伤");
        double before = seele.getBuffs().stream()
                .filter(b -> "飞向粉色的明天".equals(b.getName()))
                .mapToDouble(b -> attr(seele, AttributeType.ALL_DAMAGE_TYPE_BOOST)).sum();
        Assertions.assertTrue(before >= 0.08);
    }

    // ─── 22007 未来，有我们一起: 终结技后全队欢愉度+8%持续1回合 ────────

    @Test
    public void testFutureTogetherElation() {
        Character sparxie = buildWithWeapon(1501, 22007);
        Character cyrene = buildWithWeapon(1409, 22007);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "F");
        Battle battle = battleOf(List.of(sparxie, cyrene), List.of(enemy));

        battle.executeSkill(sparxie.getSkills().get(SkillType.ULTRA), sparxie, List.of(enemy));
        Assertions.assertTrue(cyrene.hasBuffNamed("未来，有我们一起"),
                "我方全体应获得欢愉度加成");
    }
}
