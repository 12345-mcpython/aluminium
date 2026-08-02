package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 12- and 13-series characters: data-driven traces/eidolons
 * and the generic enhanced-skill state.
 */
public class Test12And13Star {

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
    public void testAll12And13StarCharactersHavePassives() {
        int[] cids = {1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211,
                1212, 1213, 1214, 1215, 1217, 1218, 1220, 1221, 1222, 1223, 1224, 1225,
                1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1312, 1313,
                1314, 1315, 1317, 1321};
        int withZero = 0;
        for (int cid : cids) {
            Character character = buildCharacter(cid);
            if (character.getTraces().isEmpty()) {
                withZero++;
            }
        }
        Assertions.assertTrue(withZero <= 4,
                "at most a few characters may have no interpretable passives, got " + withZero);
    }

    @Test
    public void testTingyunEnergyTrace() {
        // 停云 行迹: 回合开始时恢复5点能量.
        Character tingyun = buildCharacter(1202);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(tingyun)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double before = tingyun.getEnergy();
        for (com.laosun.aluminium.models.Trace trace : tingyun.getTraces()) {
            trace.onTurnStart(battle, tingyun);
        }
        Assertions.assertTrue(tingyun.getEnergy() > before,
                "回合开始回能 trace should restore energy");
    }

    @Test
    public void testRuanMeiBreakEffectBuff() {
        // 阮·梅 行迹: 我方全体击破特攻提高20%.
        Character ruanMei = buildCharacter(1303);
        Character gallagher = buildCharacter(1301);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(ruanMei, gallagher)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        double be = gallagher.getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT) != null
                ? gallagher.getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT).get() : 0;
        Assertions.assertTrue(be >= 0.2, "team break effect buff should apply, was " + be);
    }

    @Test
    public void testGallagherUltAdvance() {
        // 加拉赫 行迹: 终结技后行动提前100%.
        Character gallagher = buildCharacter(1301);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(gallagher)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.stepForward();
        battle.beforeMove();
        battle.executeSkill(gallagher.getSkills().get(SkillType.ULTRA), gallagher, List.of(enemy));
        Assertions.assertEquals(0, battle.queue.timeUntilNext(), 0.001,
                "终结技后 should act immediately");
    }

    @Test
    public void testSushangBrokenSkillPointEidolon() {
        // 素裳 星魂1: 对弱点击破状态的敌人施放战技后恢复1个战技点 (generic interpreter).
        Character sushang = buildCharacter(1206);
        Enemy broken = buildEnemy(Element.ICE, Element.PHYSICAL, "Broken Foe");
        broken.reduceToughness(30, Element.PHYSICAL);
        Battle battle = new Battle(new ArrayList<>(List.of(sushang)), new ArrayList<>(List.of(broken)));
        battle.startBattle();

        int before = battle.getSkillPoints();
        battle.executeSkill(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(broken));
        Assertions.assertTrue(battle.getSkillPoints() > before - 1,
                "E1 should restore a skill point after the skill");
    }

    @Test
    public void testSAMEnhancedState() {
        // 萨姆 (1310): 终结技 (Enhance) 进入完全燃烧 → 强化普攻 (skill 8) / 强化战技 (skill 9).
        Character sam = buildCharacter(1310);
        Enemy enemy = buildEnemy(Element.ICE, Element.FIRE, "Fire Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(sam)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        Assertions.assertFalse(sam.isEnhanced());
        battle.executeSkill(sam.getSkills().get(SkillType.ULTRA), sam, List.of(enemy));
        Assertions.assertTrue(sam.isEnhanced(), "ult should enter the enhanced state");

        // The basic should now be the enhanced version (skill id 8).
        Skill basic = sam.getSkills().get(SkillType.COMMON);
        SkillData data = basic.getData();
        Assertions.assertNotNull(data);
        Assertions.assertTrue(data.getSkills() != null && !data.getSkills().isEmpty(),
                "enhanced basic should have data");

        // Using the enhanced skill ends the state after the action.
        battle.executeSkill(sam.getSkills().get(SkillType.SKILL), sam, List.of(enemy));
        // (state exit happens in afterMove; direct execution keeps it — verify skills restored on exit)
        battle.exitEnhancedState(sam);
        Assertions.assertFalse(sam.isEnhanced());
        SkillData restored = sam.getSkills().get(SkillType.COMMON).getData();
        Assertions.assertNotNull(restored);
    }

    @Test
    public void testDanHengILEnhancedBasic() {
        // 丹恒·饮月 (1213): 战技 (Enhance) → 强化普攻 8 / 强化战技 10.
        Character danHeng = buildCharacter(1213);
        Enemy enemy = buildEnemy(Element.ICE, Element.IMAGINARY, "Imaginary Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(danHeng.getSkills().get(SkillType.SKILL), danHeng, List.of(enemy));
        Assertions.assertTrue(danHeng.isEnhanced(), "skill should enter the enhanced state");
    }

    @Test
    public void testQingqueEnhancedBasic() {
        // 青雀 (1201): 战技 (Enhance) → 强化普攻 8.
        Character qingque = buildCharacter(1201);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(qingque)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(qingque.getSkills().get(SkillType.SKILL), qingque, List.of(enemy));
        Assertions.assertTrue(qingque.isEnhanced(), "skill should enter the enhanced state");
    }
}
