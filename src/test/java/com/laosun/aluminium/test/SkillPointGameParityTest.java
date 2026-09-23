package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.buffs.StunBuff;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 战技点与游戏规则的一致性对照（P8-4 复核）。
 *
 * <p>这个类**不是**功能测试，而是"逐条拿规则问引擎"的探针：每条都注明
 * 规则来源与结论（一致 / 不一致 / 未实现）。凡是引擎行为与规则不同的，
 * 断言写的是**引擎当前行为**，并在注释里标出差异，免得下次误以为已对齐。
 *
 * <p><b>规则来源（已核实）</b>：
 * <ul>
 *   <li>全队共享 —— 「战技点是全队共享的资源」（9game 战技点机制详解）；
 *   <li>上限 5 / 普攻 +1 —— 「角色每释放一次普攻恢复一个战技点，战技点上限为5」；
 *   <li><b>开局 3</b> —— 玩家问答明确写「正常情况下开局都是三个战技点的」；
 *   <li>开局可变 —— 项目自己的 {@code RELICS.md}：过客 4 件套「战斗开始时立即为我方恢复
 *       1 个战技点」，所以穿过客的队伍开局是 4（两个角色穿就是 5）；
 *   <li>上限可变 —— 花火天赋「战技点上限额外增加 2 点」、{@code WEAPONS.md} 里
 *       欢愉光锥「每有 1 名欢愉命途角色，战技点上限提高 1 点，最多 3 点」；
 *       甚至有光锥的触发条件是「战技点上限大于等于 6 点」→ **上限不是恒定 5**。
 * </ul>
 *
 * <p>⚠ 注意：项目的规格文档 {@code HSR.md}（{@code E:\code\blog\hsr\HSR.md}）
 * **完全没有战技点这一节** —— 只有 §6.1 一句「笑点：战技点上方计数」提到它。
 * 所以上面这套规则是从游戏机制与角色文档反推的，不是规格给的。
 */
public class SkillPointGameParityTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 一致的
    // ==================================================================

    /** 规则：战技点是**全队共享**的。引擎：池子在 {@code Battle} 上，不是每个角色一条。 */
    @Test
    public void poolIsPartyWideNotPerCharacter() {
        Character a = CharacterFactory.create(1003, 80);
        Character b = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(List.of(a, b));

        // A 花掉 1 点、B 花掉 1 点 —— 若池子是"每人一条"，两人各扣各的就看不出来。
        // 这里用 A 花点、再看**别人**行动时显示的池子，来证明是同一个池。
        Assertions.assertTrue(battle.spendSkillPoint(), "A 花 1 点");
        Assertions.assertEquals(2, battle.getSkillPoints(), "B 看到的池子也少了 1 点 → 共享");

        Assertions.assertTrue(battle.spendSkillPoint(), "B 花 1 点");
        Assertions.assertEquals(1, battle.getSkillPoints());
    }

    /** 规则：开局 3 点。引擎：{@code SKILL_POINT_START}。 */
    @Test
    public void battleStartsAtThree() {
        Assertions.assertEquals(3, newBattle(List.of(CharacterFactory.create(1003, 80))).getSkillPoints());
        Assertions.assertEquals(3, Constant.SKILL_POINT_START);
    }

    /**
     * 规则：**每场战斗重新开始**（战技点不跨战斗继承）。
     *
     * <p>引擎：{@code skillPoints} 是 {@code Battle} 的实例字段，新战斗自然是 3。
     */
    @Test
    public void eachBattleStartsFresh() {
        Character hero = CharacterFactory.create(1003, 80);
        Battle first = newBattle(List.of(hero));
        first.spendSkillPoint();
        first.spendSkillPoint();
        Assertions.assertEquals(1, first.getSkillPoints());

        Battle second = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Assertions.assertEquals(3, second.getSkillPoints(), "新战斗不吃上一场的剩余");
    }

    /**
     * 规则：0 点时**放不出战技**（按钮变灰）。
     *
     * <p>引擎：{@code performAction} 返回 false 且不排队 → 无伤害。
     * 这条与游戏一致（游戏是"点不动"，引擎是"返回 false"）。
     */
    @Test
    public void zeroPointsMeansSkillUnavailable() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();
        Enemy target = firstEnemy(battle);

        while (battle.spendSkillPoint()) {
            // 清零
        }
        double hp = target.getCurrentHp();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, 2), List.of(target)));
        Assertions.assertEquals(hp, target.getCurrentHp(), EPS);
    }

    /**
     * 规则：终结技**不消耗**战技点。引擎一致。
     */
    @Test
    public void ultimateDoesNotSpendPoints() {
        Character hero = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(List.of(hero));

        while (battle.getSkillPoints() > 1) {
            battle.spendSkillPoint();
        }
        Assertions.assertEquals(1, battle.getSkillPoints());

        hero.setCurrentEnergy(200);
        Assertions.assertTrue(battle.castUltra(hero, List.of(firstEnemy(battle))));
        Assertions.assertEquals(1, battle.getSkillPoints(), "终结技不花点");
    }

    // ==================================================================
    // 不一致 / 需要留意的
    // ==================================================================

    /**
     * ⚠ <b>不一致</b>：规则里"普攻 +1"的本体是**消耗 1 点行动力的普通攻击**，
     * 而引擎按 {@code attack_type == "Normal"} 判定 —— 也就是**秘技不进战斗**是对的，
     * 但 {@code MazeNormal}（地图普攻）被排除在外。
     *
     * <p>引擎当前行为：{@code MazeNormal} **中性**（+0）。这与游戏一致 ——
     * 地图普攻是**进战斗前**用的，那时还没有战技点这回事。
     * 这条探针的作用是把"为什么排除它"固定下来，防止以后有人"顺手"把非 Normal
     * 的攻击类型都算成 +1。
     */
    @Test
    public void mazeNormalIsNeutralBecauseItIsPreBattle() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Skill maze = new DefaultSkill(1003, 6, 1);
        Assertions.assertEquals("MazeNormal", maze.getData().getSkillType());

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(maze, battle.characters.getFirst()));
        Assertions.assertEquals(before, battle.getSkillPoints(), "地图普攻不产战技点");
    }

    /**
     * ⚠ <b>未实现</b>：角色 / 光锥 / 遗器都能改战技点 —— 引擎只有全队一份
     * {@code gainSkillPoint}，没有任何"按来源修正增量/上限"的钩子。
     *
     * <p>证据（都来自本项目自己的数据文档）：
     * <ul>
     *   <li>{@code 1101_布洛妮娅.md}：施放战技时 50% 概率恢复 1 个战技点（有 1 回合冷却）；
     *   <li>{@code 1201_青雀.md}：争番「施放战技时，恢复 1 个战技点，单场只能触发 1 次」；
     *   <li>{@code 1215_寒鸦.md}：每当对【承负】目标施放 2 次普攻/战技/终结技后，为我方恢复 1 点；
     *   <li>{@code 1223_貊泽.md}：施放天赋的追加攻击后恢复 1 个战技点（1 回合后可再触发）；
     *   <li>{@code 1206_素裳.md}：对击破状态目标施放战技后恢复 1 个战技点；
     *   <li>{@code 1312_米沙.md}：我方全体每消耗 1 个战技点 → 米沙下次终结技 +1 段、米沙回 2 能量
     *       （这条还要监听"消耗战技点"这件事本身）。
     * </ul>
     *
     * <p>归属：P8-7 触发器表（{@code GAIN_SKILL_POINT} / 按角色修正增量）+
     * 需要一个"改队伍级资源上限"的口子。**这条探针把"没做"固定下来**，
     * 免得把现状误读成"战技点已经完备"。
     */
    @Test
    public void characterAndGearSkillPointModifiersAreNotImplemented() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));

        // 上限恒为常量：花火天赋的 +2、欢愉光锥的 +1~3 都没有挂靠点
        battle.gainSkillPoint(100);
        Assertions.assertEquals(Constant.SKILL_POINT_MAX, battle.getSkillPoints(),
                "上限恒为常量 5，角色/光锥无法改它");

        // 增量也恒为常量：布洛妮娅的"50% 概率 +1"、素裳的"打击破目标 +1"都没有挂靠点
        Assertions.assertEquals(1, Constant.SKILL_POINT_GAIN_BASIC,
                "普攻增量恒为常量 1，角色级额外供点没有挂靠点");
    }

    /**
     * ⚠ <b>已知偏差</b>：引擎按 {@code attack_type == "Normal"} 一刀切给 +1，
     * 但游戏里有**强化普攻不恢复战技点**的例外。
     *
     * <p>证据：{@code 1315_波提欧.md} 「强化普攻**无法恢复战技点**，且仅能以处于
     * 【绝命对峙】的敌方目标为目标」。{@code 1213_丹恒•饮月.md} 的强化普攻则
     * **不消耗战技点**（"施放本技能不消耗战技点且不视为使用战技"）。
     *
     * <p>为什么现在"恰好对得上"：本项目数据里强化普攻也是 {@code "Normal"}，没有单独的
     * 类型（实测 122 条 {@code Normal} = 93 角色 × 1 + 饮月/镜流/青雀/波提欧的多档强化普攻），
     * 所以引擎给它们 +1。这对青雀是对的（{@code 1201_青雀.md} 明写"施放强化普攻后，
     * 恢复 1 个战技点"），对波提欧是**错的**。
     *
     * <p>⚠ 但**不能改成"强化普攻一律 +0"**：那会把青雀改坏。
     * 真正的修法是"每个技能自带战技点增量字段"，属数据补全，不是引擎逻辑问题。
     * 这条探针把"一刀切"这个事实写下来，避免以后有人只看 {@code engine.md} 就以为
     * "谁恢复战技点"已经精确了。
     */
    @Test
    public void normalAttackIsBlanketPlusOneSoEnhancedNormalsAlsoGain() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1315, 80)));   // 波提欧
        Skill enhancedLikeBasic = new DefaultSkill(1315, 1, 1);
        Assertions.assertEquals("Normal", enhancedLikeBasic.getData().getSkillType(),
                "前提：数据里强化普攻也标成 Normal，没有单独类型");

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(enhancedLikeBasic, battle.characters.getFirst()));
        Assertions.assertEquals(before + 1, battle.getSkillPoints(),
                "引擎一刀切 +1 —— 对青雀正确，对波提欧（强化普攻不恢复）是已知偏差");
    }

    /**
     * ⚠ <b>未实现</b>：遗器/光锥的"开局战技点"。
     *
     * <p>{@code RELICS.md}：过客 4 件套「战斗开始时，立即为我方恢复 1 个战技点」→
     * 穿它的队伍开局是 4 点（两个角色穿就是 5）。
     * 引擎的开局恒为 {@code SKILL_POINT_START}，且**遗器套装效果整体没接**
     * （{@code relic_sets.json} 连装载都没装载）。
     */
    @Test
    public void relicBattleStartSkillPointIsNotImplemented() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints(),
                "开局恒为 3，过客 4 件套的 +1 没有挂靠点");
    }

    /**
     * ⚠ <b>需要留意</b>：引擎的阵营判断用的是 {@link com.laosun.aluminium.enums.Camp}，
     * 而不是"这个人是不是玩家操控"。
     *
     * <p>后果：如果将来加了**友方召唤物 / 友方 NPC**（忆灵属 P9-4，它们是我方单位
     * 但不是"角色"），它们若用 {@code Normal} 出手**也会给我方加战技点**。
     * 游戏里忆灵的行动同样能提供战技点，所以这个行为大概率是对的 ——
     * 但它现在是**副作用**而不是明确设计，所以在这里固定下来。
     */
    @Test
    public void campPlayerIsTheGateSoFutureAlliesWouldAlsoGrantPoints() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Enemy enemy = firstEnemy(battle);
        Assertions.assertEquals(com.laosun.aluminium.enums.Camp.ENEMY, enemy.getCamp(),
                "敌人是 ENEMY → 被挡在门外");
        Assertions.assertEquals(com.laosun.aluminium.enums.Camp.PLAYER,
                battle.characters.getFirst().getCamp(), "角色是 PLAYER → 放行");
    }

    // ==================================================================
    // 边界：不影响引擎结论，但固定住"已想过的情形"
    // ==================================================================

    /**
     * 被控（{@code StunBuff}）时**既不能出手也不扣点** —— 因为 {@code canAct()} 先拦住了。
     *
     * <p>游戏里被控就是跳过回合，自然也不消耗战技点，一致。
     */
    @Test
    public void stunnedActorNeitherActsNorSpendsPoints() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();
        hero.getBuffManager().addBuff(new StunBuff(2));

        int before = battle.getSkillPoints();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, 2),
                List.of(firstEnemy(battle))), "被控 → 出不了手");
        Assertions.assertEquals(before, battle.getSkillPoints(), "被控不该扣点");
    }

    /**
     * 不产生能量条的角色（走特殊资源的 6 个）**照样受战技点约束** ——
     * 战技点是队伍级资源，与个人的能量条/层数无关。
     *
     * <p>游戏里黄泉照样要花战技点放战技，一致。
     */
    @Test
    public void specialResourceCharactersStillPaySkillPoints() {
        Character acheron = CharacterFactory.create(1308, 80);
        Assertions.assertTrue(acheron.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                "前提：黄泉走的是特殊资源 provider");

        Battle battle = newBattle(List.of(acheron));
        Assertions.assertTrue(actWithRealTurn(battle, acheron, () -> skill(acheron, 2),
                () -> List.of(firstEnemy(battle))), "黄泉的战技应当能放出来");
        Assertions.assertEquals(2, battle.getSkillPoints(), "黄泉也花战技点");
    }

    // ==================================================================
    // 辅助（与 SkillPointTest 同款，刻意重复以免两个测试类互相依赖）
    // ==================================================================

    private static Skill skill(Character hero, int slot) {
        return new DefaultSkill(hero.getCid(), slot, 1);
    }

    private static Battle newBattle(List<Character> team) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static boolean actWithRealTurn(Battle battle, CanHit actor, Supplier<Skill> skill,
                                           Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

    private static boolean actWithoutAfterMove(Battle battle, CanHit actor, Supplier<Skill> skill,
                                               List<? extends CanHit> targets) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
                battle.beforeMove();
                return battle.performAction(skill.get(), targets);
            }
            battle.afterMove();
        }
        Assertions.fail("30 步内没轮到 " + actor.getName() + " 的回合");
        return false;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemies.getFirst();
    }
}
