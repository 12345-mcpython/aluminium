package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 重构的**目的验证**：引擎是否真的"不认识角色机制"却又能被角色机制扩展。
 *
 * <p>{@code DOC_VS_CODE.md} §F 的 <b>F-8</b> 说"战技点的策略要从 {@code Battle} 抽出去"，
 * 本类就是那句话的验收 —— 全程**不改引擎一行**，只替换
 * {@link Battle#skillPointPolicy}，看引擎会不会照着新规则走。
 *
 * <p>三个断言分别对应未来三类真实需求：
 * <ol>
 *   <li>{@link #customPolicyChangesTheBasicAttackGain()} —— 角色级供点
 *       （花火"每 3 次普攻额外 +1"、素裳"打击破目标 +1"）；</li>
 *   <li>{@link #customPolicyRaisesTheCap()} —— 上限类修正
 *       （花火天赋 +2、欢愉光锥每名欢愉角色 +1，对应 §F 的 F-1）；</li>
 *   <li>{@link #customPolicyCanChangeTheStartingValue()} —— 开局类修正
 *       （过客 4 件套"战斗开始时 +1"，对应 §F 的 F-2）。</li>
 * </ol>
 *
 * <p>⚠ 这些子类是**测试替身**，不是要交付的角色实现 —— 真做角色时应该由
 * P8-7 的触发器表驱动（{@code cid} 只出现在装配点或效果表里，P8-0 三分法）。
 * 本类证明的是"引擎侧的口子够用"，不是"角色已经做了"。
 */
public class SkillPointPolicyExtensibilityTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. 角色级供点：换掉策略 → 引擎照新规则走
    // ==================================================================

    /**
     * 自定义策略：普攻回 **2** 点（而不是 1）。
     *
     * <p>模拟"花火在队伍里，普攻额外 +1"这类效果。
     */
    private static final class DoubleGainPolicy extends StandardSkillPointPolicy {
        @Override
        protected int gainForCast(CanHit user, Skill skill, SkillCategory category) {
            return 2;
        }
    }

    @Test
    public void customPolicyChangesTheBasicAttackGain() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();

        // 换策略 —— 这是唯一的"接线"动作，Battle 一行没改
        battle.skillPointPolicy = new DoubleGainPolicy();
        Assertions.assertEquals(3, battle.getSkillPoints(), "开局仍是 3（策略的初始值）");

        // ⚠ 策略从 3 起，一次普攻 +2 → 4（封顶 5），所以断言"至少涨了 2 而不是 1"
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, 1),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(5, battle.getSkillPoints(),
                "3 + 2 = 5（若还是内置的 +1 则是 4）");
    }

    // ==================================================================
    // 2. 上限类修正：上限不再是常量 5
    // ==================================================================

    /**
     * 自定义策略：上限 **7**、开局 3（花火天赋 +2 的效果）。
     *
     * <p>对应 {@code DOC_VS_CODE.md} §F 的 <b>F-1</b>：引擎原先把上限写死在
     * {@code Constant.SKILL_POINT_MAX}，无法被队伍配置抬高。
     */
    @Test
    public void customPolicyRaisesTheCap() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        battle.skillPointPolicy = new StandardSkillPointPolicy(7, 3);

        Assertions.assertEquals(7, battle.getSkillPointMax(), "上限跟着策略走，不再是常量 5");
        Assertions.assertEquals(3, battle.getSkillPoints());

        battle.gainSkillPoint(100);
        Assertions.assertEquals(7, battle.getSkillPoints(), "封在新的上限 7");
    }

    // ==================================================================
    // 3. 开局类修正：开局不再是常量 3
    // ==================================================================

    /**
     * 自定义策略：开局 **4**、上限 5（过客 4 件套"战斗开始时 +1"的效果）。
     *
     * <p>对应 {@code DOC_VS_CODE.md} §F 的 <b>F-2</b>。
     */
    @Test
    public void customPolicyCanChangeTheStartingValue() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        battle.skillPointPolicy = new StandardSkillPointPolicy(5, 4);

        Assertions.assertEquals(4, battle.getSkillPoints(), "开局 4，不再是常量 3");
        Assertions.assertEquals(5, battle.getSkillPointMax());
    }

    // ==================================================================
    // 4. 默认策略不能被绕过：接口是唯一入口
    // ==================================================================

    /**
     * 默认策略下，{@code Battle} 的读写口与策略**永远一致** ——
     * 不存在"引擎里还有一份没人管的战技点状态"。
     */
    @Test
    public void battleFacadeNeverDivergesFromThePolicy() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        SkillPointPolicy policy = battle.skillPointPolicy;

        battle.gainSkillPoint(1);
        Assertions.assertEquals(policy.getValue(), battle.getSkillPoints());
        Assertions.assertEquals(policy.getMax(), battle.getSkillPointMax());

        battle.spendSkillPoint();
        Assertions.assertEquals(policy.getValue(), battle.getSkillPoints());
        Assertions.assertEquals(policy.canAfford(), battle.hasSkillPoint());

        while (battle.spendSkillPoint()) {
            // 清空
        }
        Assertions.assertEquals(0, battle.getSkillPoints());
        Assertions.assertFalse(battle.hasSkillPoint());
        Assertions.assertEquals(0, policy.getValue());
    }

    /**
     * 默认策略就是游戏基础规则，一个字都没变 —— 重构不该改行为。
     */
    @Test
    public void defaultPolicyIsStillTheVanillaRule() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));

        Assertions.assertTrue(battle.skillPointPolicy instanceof StandardSkillPointPolicy);
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints());
        Assertions.assertEquals(Constant.SKILL_POINT_MAX, battle.getSkillPointMax());

        Character hero = battle.characters.getFirst();
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, 1),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(Constant.SKILL_POINT_START + Constant.SKILL_POINT_GAIN_BASIC,
                battle.getSkillPoints(), "普攻 +1（默认策略）");
    }

    // ==================================================================
    // 5. 策略注入对"敌方"的语义也成立
    // ==================================================================

    /**
     * 自定义策略**自己**决定要不要判阵营 —— 引擎不再替它判断。
     *
     * <p>这条把责任边界固定下来：敌方行动算不算战技点，是**策略**的事
     * （{@code StandardSkillPointPolicy} 判 {@code Camp.PLAYER}），
     * 不是 {@code Battle} 的事。将来加"友方召唤物"（P9-4）要调这条规则时，
     * 改的是策略，不是引擎。
     */
    @Test
    public void campJudgementBelongsToThePolicyNotTheBattle() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Enemy enemy = firstEnemy(battle);

        // 故意换成"不判阵营"的策略：敌人普攻也该涨点（证明是策略在管，不是引擎）
        battle.skillPointPolicy = new StandardSkillPointPolicy() {
            @Override
            public boolean onSkillCast(CanHit user, Skill skill) {
                if (skill != null && skill.getData() != null
                        && skill.getData().getCategory() == SkillCategory.NORMAL) {
                    gain(1);
                }
                return true;
            }
        };

        int before = battle.getSkillPoints();
        Assertions.assertTrue(actWithRealTurn(battle, enemy,
                () -> new DefaultSkill(1003, 1, 1), () -> List.of(battle.characters.getFirst())));
        Assertions.assertEquals(before + 1, battle.getSkillPoints(),
                "换成不判阵营的策略后，敌方普攻也会涨点 → 说明阵营判断在策略里，不在 Battle 里");
    }

    // ==================================================================
    // 辅助
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
