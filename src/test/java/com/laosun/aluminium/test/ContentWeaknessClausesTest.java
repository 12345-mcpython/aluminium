package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 四条内容的内容层判据 -  - 即"那条从句真的接上了引擎的能力吗"。
 *
 * <p>底层的机制已各自验过（第 12 件 `ADD_ELEMENTAL_WEAKNESS`、第 18 件 `random_absent`／`party_first`、
 * 第 19 件 `turns`），所以这里只问内容的接线：加进去的正是那条从句该加的那个属性。
 *
 * <p>Note: 四条各用自己的触发方式 -  - 这也是"内容真的接上了"的一部分：
 * `1315` 用真实的终结技（满能量）；`1405`／`1006` 用两个事件；`1310` 用 `markTechniqueUsed` 再开战
 * （Note: 顺序：秘技状态必须在 `startBattle()` 之前登记，`Battle:42` 在任何 `BATTLE_START` 规则之前加它）。
 */
public class ContentWeaknessClausesTest {
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    private static Enemy enemyOf(Battle battle) {
        return (Enemy) battle.getOpponents(battle.characters.getFirst()).getFirst();
    }

    /** 装光锥 23050 也好、什么都不装也好，都不影响这四条 -  - 它们是角色自己的规则。 */
    private static Battle battleWith(Character wearer) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static List<DamageElement> weaknesses(Enemy enemy) {
        List<DamageElement> had = new ArrayList<>();
        for (DamageElement e : DamageElement.values()) {
            if (enemy.isWeakTo(e)) {
                had.add(e);
            }
        }
        return had;
    }

    @Test
    public void charactersOwnClausesLand() {
        // ---- 1315 波提欧：终结技"为指定敌方单体添加物理弱点，持续 2 回合"（turns 由 13ccc3补上）
        Character bto = CharacterFactory.create(1315, LEVEL);
        Battle b1 = battleWith(bto);
        Enemy e1 = enemyOf(b1);
        Assertions.assertFalse(e1.isWeakTo(DamageElement.PHYSICAL), "1002011 must not already have Physical");
        bto.setCurrentEnergy(bto.getMaxEnergy());
        Assertions.assertTrue(b1.castUltra(bto, List.of(e1)), "full energy, so the ultimate must cast");
        System.out.println("[content] 1315 ultimate -> physical=" + e1.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertTrue(e1.isWeakTo(DamageElement.PHYSICAL), "the ultimate's own clause inserts Physical");

        // ---- 1405 那刻夏：天赋"击中后添加 1 个随机属性弱点，持续 3 回合，优先未拥有"
        Character ana = CharacterFactory.create(1405, LEVEL);
        Battle b2 = battleWith(ana);
        Enemy e2 = enemyOf(b2);
        List<DamageElement> before = weaknesses(e2);
        b2.fireTriggers(TriggerEvent.ALLY_ATTACK, ana, e2, 0, 0);
        List<DamageElement> added = new ArrayList<>(weaknesses(e2));
        added.removeAll(before);
        System.out.println("[content] 1405 ally-attack -> had=" + before.size() + " added=" + added);
        Assertions.assertEquals(1, added.size(), "exactly one random weakness is inserted");
        Assertions.assertFalse(before.contains(added.getFirst()), "and it is one the target did not have");

        // ---- 1006 银狼：战技"添加 1 个场上我方目标持有属性的弱点"（技能说明：编队第一位）
        Character sw = CharacterFactory.create(1006, LEVEL);
        Battle b3 = battleWith(sw);
        Enemy e3 = enemyOf(b3);
        Assertions.assertFalse(e3.isWeakTo(sw.getElement()), "the enemy must not already have the wearer's element");
        b3.fireTriggers(TriggerEvent.SKILL_CAST, sw, e3, 0, 0);
        System.out.println("[content] 1006 skill -> " + sw.getElement() + "=" + e3.isWeakTo(sw.getElement()));
        Assertions.assertTrue(e3.isWeakTo(sw.getElement()), "the first party member's element is what its text names");
    }

    @Test
    public void theTechniqueClauseAppliesToEveryEnemy() {
        // ---- 1310 流萤：秘技"每个波次开始时为敌方全体添加火属性弱点，持续 2 回合"
        Character firefly = CharacterFactory.create(1310, LEVEL);
        // Note: 只需一个没有火弱点的（它是对照：技前没有、技后必须有），第二个不设条件 -  - 
        // 需要的对照是"它原本没有"，不是"两个都没有"（实测那个区间里只有 1 个没有火）。
        // Note: 两个都必须原本没有火弱点 -  - Note: 上一版让第二个随便一个，
        // 而它挑中了本来就有火的 1002011 so 那句 true 从来没证明过任何事。
        List<Enemy> picked = new ArrayList<>();
        for (int cid = 1002011; cid <= 1002100 && picked.size() < 2; cid++) {
            try {
                Enemy candidate = EnemyFactory.create(cid, 90, 1);
                if (!candidate.isWeakTo(DamageElement.FIRE)) {
                    picked.add(candidate);
                }
            } catch (RuntimeException ignored) {
                // 没有这个 id 就跳过
            }
        }
        Assertions.assertEquals(2, picked.size(),
                "need TWO enemies that do NOT already have Fire, or the assertion proves nothing");
        Enemy a = picked.get(0);
        Enemy b = picked.get(1);
        // Note: markTechniqueUsed 必须在 startBattle() 之前 -  - startBattle 会在任何 BATTLE_START 规则之前加那个状态
        Battle battle = new Battle(List.of(firefly), List.of(a, b), new Random(0));
        battle.markTechniqueUsed(firefly);
        battle.startBattle();
        // Note: WAVE_START 由 WaveManager.nextWave() 发（它不在 startBattle() 里）。
        // Note: 它不带 actor、不带 subject，像 BATTLE_START 一样是"关于战斗的事实"。
        // Note: 这里直接发，因为本条要证的是"1310 的从句接上了引擎"；"WAVE_START 会被发出"本身已有 10 个先例。
        battle.fireTriggers(TriggerEvent.WAVE_START);
        System.out.println("[content] 1310 technique -> a=" + a.isWeakTo(DamageElement.FIRE)
                + " b=" + b.isWeakTo(DamageElement.FIRE));
        Assertions.assertTrue(a.isWeakTo(DamageElement.FIRE) && b.isWeakTo(DamageElement.FIRE),
                "the technique clause reaches EVERY enemy, not just the first");
    }

    /**
     * Note: 内容层的另一半：那条从句真的把 	urns 交给了引擎吗。
     *
     * <p>底层已证（第 19 件），所以这里只问内容：1315 那条规则带
     * "turns": 2，于是它的物理弱点应当会过期。
     *
     * <p>Note: 变异点：删掉 1315.json 里的 "turns": 2 so 这条必红。否则这弱点永久留着 -  - 
     * 而"永久"与"文档说的 2 回合"在断言眼里长得一模一样，除非真的走几个回合。
     */
    @Test
    public void theInsertedWeaknessFromContentActuallyExpires() {
        Character bto = CharacterFactory.create(1315, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(bto), List.of(enemy), new Random(0));
        battle.startBattle();
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL), "1002011 must not already have Physical");
        bto.setCurrentEnergy(bto.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(bto, List.of(enemy)), "the ultimate must cast at full energy");
        Assertions.assertTrue(enemy.isWeakTo(DamageElement.PHYSICAL), "the clause inserts it");

        int steps = 0;
        while (enemy.isWeakTo(DamageElement.PHYSICAL) && !battle.isOver() && steps < 60) {
            battle.stepForward();      // Note: 只换 currentMove（实测：它不执行回合）
            battle.afterMove();        // 结束那次行动 - - 计时弱点就在这里递减
            steps++;
        }
        System.out.println("[content] 1315 timed: physical gone after " + steps + " steps (over=" + battle.isOver() + ")");
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL),
                "the content carries turns: 2, so it must expire -- " + steps + " steps and it is still there");
        Assertions.assertTrue(steps > 1, "but not on the first step: the count would be wrong, not just the ticking");
    }
}
