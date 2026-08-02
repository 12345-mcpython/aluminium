package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.Summon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the data-driven 忆灵 system: real stats (talent formulas),
 * real 忆灵技能 (servant_skills.json), and Cyrene's character-specific support.
 */
public class MemospriteDataTest {

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
    public void testGarmentmakerRealStatsAndSkills() {
        Character aglaea = buildCharacter(1402);
        Summon summon = new Summon(aglaea, 11402);
        // 真实面板: 44% 生命上限 + 180, 35% 速度 (天赋参数).
        Assertions.assertEquals(aglaea.getMaxHp() * 0.44 + 180, summon.getMaxHp(), 1.0);
        // 真实技能: 1140201 刺纹之陷 Blast (55%/33% 攻击力), 韧性 [30, 0, 15].
        Skill basic = summon.getSkills().get(SkillType.SUMMON_SKILL);
        Assertions.assertNotNull(basic.getData());
        Assertions.assertEquals("Blast", basic.getData().getSkillEffect());
        Assertions.assertEquals(0.55, basic.getData().getSkills().get(0).get(0), 0.001);
        Assertions.assertEquals(30, basic.getData().getStanceList().single());
        Assertions.assertEquals(15, basic.getData().getStanceList().spread());
    }

    @Test
    public void testPolluxRealHpBasedDamage() {
        // 死龙 1140701 擘裂冥茫的爪痕: 对全体造成 20% 生命上限的量子伤害.
        Character castorice = buildCharacter(1407);
        Summon pollux = new Summon(castorice, 11407);
        Skill basic = pollux.getSkills().get(SkillType.SUMMON_SKILL);
        Assertions.assertEquals("AoEAttack", basic.getData().getSkillEffect());
        Assertions.assertEquals(0.2, basic.getData().getSkills().get(0).get(0), 0.001);

        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(castorice)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();
        double hpBefore = enemy.getCurrentHp();
        battle.summonAction(pollux);
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore,
                "死龙 should deal HP-based quantum damage to all enemies");
    }

    @Test
    public void testOnlyMemoryPathCharactersHaveMemosprite() {
        Character aglaea = buildCharacter(1402);
        Character topaz = buildCharacter(1112);
        Character seele = buildCharacter(1102);
        Character himeko = buildCharacter(1003);
        Battle battle = new Battle(new ArrayList<>(List.of(aglaea, topaz, seele, himeko)),
                new ArrayList<>(List.of(buildEnemy(Element.ICE, Element.THUNDER, "T"))));
        battle.startBattle();

        Assertions.assertEquals(11402, battle.servantIdOf(aglaea), "阿格莱雅 (记忆) 应有忆灵");
        Assertions.assertEquals(0, battle.servantIdOf(topaz), "托帕 (巡猎) 不应有忆灵");
        Assertions.assertEquals(0, battle.servantIdOf(seele), "希儿 (巡猎) 不应有忆灵");
        Assertions.assertEquals(0, battle.servantIdOf(himeko), "姬子 (智识) 不应有忆灵");
    }

    @Test
    public void testCyreneSupportForMemoryTrailblazer() {
        // 献予「创世」之诗: 对记忆开拓者施放忆灵技 → ATK = 德谬歌生命上限 × 8%.
        Character cyrene = buildCharacter(1415);
        Character memoryTb = buildCharacter(8007);
        Character seele = buildCharacter(1102);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, memoryTb, seele)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy)); // 昔涟终结技召唤德谬歌
        Assertions.assertFalse(cyrene.getSummons().isEmpty(), "德谬歌 should be summoned");
        Summon demouge = cyrene.getSummons().getFirst();

        battle.summonAction(demouge); // 忆灵技 targets the Memory TB (priority)
        Assertions.assertTrue(memoryTb.hasBuffNamed("献予「创世」之诗"),
                "记忆开拓者 should receive the 创世之诗 buff");
        double expectedAtk = demouge.getMaxHp() * 0.08;
        Assertions.assertTrue(memoryTb.getAttribute(AttributeType.ATTACK).get() > expectedAtk * 0.9,
                "ATK should scale with 德谬歌's max HP");
    }

    @Test
    public void testCyreneSupportForAglaea() {
        // 献予「浪漫」之诗: 对阿格莱雅施放忆灵技 → 阿格莱雅获得【浪漫】(SPD + 70).
        Character cyrene = buildCharacter(1415);
        Character aglaea = buildCharacter(1402);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, aglaea)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        Summon demouge = cyrene.getSummons().getFirst();
        battle.summonAction(demouge);

        Assertions.assertTrue(aglaea.hasBuffNamed("浪漫"),
                "阿格莱雅 should receive the 浪漫 buff");
    }

    @Test
    public void testCyreneSupportsAll14StarExceptHerta() {
        // 昔涟的忆灵技应优先辅助除大黑塔(1401)外的所有14**角色与记忆开拓者.
        Character cyrene = buildCharacter(1415);
        Character herta = buildCharacter(1401);
        Character aventurine = buildCharacter(1304);
        Character drRatio = buildCharacter(1305);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, herta, aventurine, drRatio)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        Summon demouge = cyrene.getSummons().getFirst();

        // 无14**/记忆开拓者时, 忆灵技不对 1304/1305 提供诗篇辅助.
        battle.summonAction(demouge);
        Assertions.assertFalse(aventurine.hasBuffNamed("献予「海洋」之诗"));
        Assertions.assertFalse(drRatio.hasBuffNamed("献予「海洋」之诗"));
    }

    @Test
    public void testCyrenePoemForHaishen() {
        // 献予「海洋」之诗: 对海瑟音(1410)施放 → 伤害+60% 并恢复60点能量.
        Character cyrene = buildCharacter(1415);
        Character haishen = buildCharacter(1410);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, haishen)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        Summon demouge = cyrene.getSummons().getFirst();
        battle.summonAction(demouge);

        Assertions.assertTrue(haishen.hasBuffNamed("献予「海洋」之诗"),
                "海瑟音 should receive 海洋之诗");
        double dmgBoost = haishen.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) != null
                ? haishen.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() : 0;
        Assertions.assertTrue(dmgBoost >= 0.6, "damage should boost by 60%");
    }

    @Test
    public void testPoemMechanicRomanceEnergyOnAttack() {
        // 浪漫: 阿格莱雅攻击后消耗【浪漫】恢复能量 (单次).
        Character cyrene = buildCharacter(1415);
        Character aglaea = buildCharacter(1402);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, aglaea)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        battle.summonAction(cyrene.getSummons().getFirst());
        Assertions.assertTrue(aglaea.hasBuffNamed("浪漫"));

        double before = aglaea.getEnergy();
        battle.executeSkill(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        Assertions.assertTrue(aglaea.getEnergy() > before + 50,
                "浪漫 should restore energy after an attack");
        Assertions.assertFalse(aglaea.hasBuffNamed("浪漫"),
                "浪漫 should be consumed after one attack");
    }

    @Test
    public void testPoemMechanicOceanDetonatesDots() {
        // 海洋: 海瑟音普攻/战技后引爆目标持续伤害.
        Character cyrene = buildCharacter(1415);
        Character haishen = buildCharacter(1410);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, haishen)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        battle.summonAction(cyrene.getSummons().getFirst());

        Buff.Dot dot = new Buff.Dot("Wind Shear", haishen, enemy, 500, Element.WIND, 3);
        enemy.applyDot(dot);
        double hpBefore = enemy.getCurrentHp();
        battle.executeSkill(haishen.getSkills().get(SkillType.COMMON), haishen, List.of(enemy));
        Assertions.assertTrue(enemy.getCurrentHp() < hpBefore - 100,
                "海洋之诗 should detonate the target's DoTs");
    }

    @Test
    public void testPoemMechanicKnowledgeBuffsErudition() {
        // 理性: 那刻夏施放普攻/战技 → 【真知】: 智识角色攻击力/战技伤害提升.
        Character cyrene = buildCharacter(1415);
        Character naxian = buildCharacter(1405);
        Character herta = buildCharacter(1013); // 智识
        Character seele = buildCharacter(1102); // 巡猎
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, naxian, herta, seele)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        battle.summonAction(cyrene.getSummons().getFirst());
        Assertions.assertTrue(naxian.hasBuffNamed("献予「理性」之诗"));

        battle.executeSkill(naxian.getSkills().get(SkillType.COMMON), naxian, List.of(enemy));
        Assertions.assertTrue(herta.hasBuffNamed("真知"), "智识角色 should receive 真知");
        Assertions.assertFalse(seele.hasBuffNamed("真知"), "非智识角色 should NOT receive 真知");
    }

    @Test
    public void testPoemMechanicCreationGrantsDemiurgeExtraTurn() {
        // 创世: 记忆开拓者行动后 → 德谬歌获得额外回合 (行动提前100%).
        Character cyrene = buildCharacter(1415);
        Character memoryTb = buildCharacter(8007);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, memoryTb)),
                new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        cyrene.gainEnergy(cyrene.getMaxEnergy());
        battle.castUltra(cyrene, List.of(enemy));
        Summon demouge = cyrene.getSummons().getFirst();
        battle.summonAction(demouge);
        Assertions.assertTrue(memoryTb.hasBuffNamed("献予「创世」之诗"));

        double remainingBefore = battle.queue.getTimeRemaining(
                battle.getQueueSnapshot().stream()
                        .filter(s -> s.getCanHit() == demouge).findFirst().orElseThrow());
        battle.executeSkill(memoryTb.getSkills().get(SkillType.COMMON), memoryTb, List.of(enemy));
        double remainingAfter = battle.queue.getTimeRemaining(
                battle.getQueueSnapshot().stream()
                        .filter(s -> s.getCanHit() == demouge).findFirst().orElseThrow());
        Assertions.assertTrue(remainingAfter < remainingBefore,
                "德谬歌 should gain an extra turn after the Memory TB acts");
    }
}
