package com.laosun.aluminium.models.ai;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.buffs.TauntBuff;

import java.util.List;
import java.util.Random;

/**
 * 目标选择（P5-4）：决定"这一次攻击打谁"。
 *
 * <p>两条策略，按优先级：
 * <ol>
 *   <li><b>嘲讽硬约束</b>：候选里存在活着的嘲讽者 → 直接选中它（见 {@link TauntBuff}）。
 *       只对 {@link Intent#SINGLE} 与 {@link Intent#BLAST} 生效 —— 单体与扩散的**中心**受约束；
 *       群攻本来打全体，不需要选；弹射每段各自随机，不受约束。</li>
 *   <li><b>仇恨加权随机</b>：按 {@code 该单位仇恨 / 候选总仇恨} 的概率抽一个。
 *       存护 150 比常规 100 更容易被打到。</li>
 * </ol>
 *
 * <p><b>候选集口径</b>：调用方必须传"活着的对手"。别在这里自己过滤阵营 ——
 * 引擎里"谁可以被选为目标"的唯一出口是 {@code Battle.targetableEnemies()}（敌方）
 * 与调用方自筛的我方列表；选到尸体就是鞭尸的来源。
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    /**
     * 这次攻击的"意图"——决定嘲讽是否约束目标选择。
     */
    public enum Intent {
        /** 单体攻击：受嘲讽约束。 */
        SINGLE,
        /** 扩散攻击（中心 + 相邻）：**中心**受嘲讽约束。 */
        BLAST,
        /** 群攻：打全体，不受嘲讽约束。 */
        AOE,
        /** 弹射/随机：每段各自随机，不受嘲讽约束。 */
        RANDOM
    }

    /**
     * 按意图选一个主目标。
     *
     * @param battle     进行中的战斗（用来查仇恨值）
     * @param candidates 候选目标（**必须已过滤死亡**；空列表返回 {@code null}）
     * @param intent     攻击意图
     * @param rng        注入的随机源（保证可复现）
     * @return 选中的目标；没有候选则 {@code null}
     */
    public static CanHit select(Battle battle, List<? extends CanHit> candidates, Intent intent, Random rng) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        CanHit taunted = findTaunter(candidates, intent);
        if (taunted != null) {
            return taunted;                     // 嘲讽是硬约束：跳过随机
        }
        return weighted(battle, candidates, rng);
    }

    /**
     * 候选里活着的嘲讽者（同一个候选集里理论上只该有一个；多个时取第一个）。
     *
     * @return 嘲讽者；没有 / 意图不受约束 → {@code null}
     */
    private static CanHit findTaunter(List<? extends CanHit> candidates, Intent intent) {
        if (intent != Intent.SINGLE && intent != Intent.BLAST) {
            return null;                        // 群攻打全体、弹射逐段随机：不受嘲讽影响
        }
        for (CanHit candidate : candidates) {
            if (!candidate.isDeath() && candidate.getBuffManager().hasBuff(TauntBuff.class)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 仇恨加权随机。
     */
    private static CanHit weighted(Battle battle, List<? extends CanHit> candidates, Random rng) {
        double total = 0;
        for (CanHit candidate : candidates) {
            total += battle.aggroOf(candidate);
        }
        if (total <= 0) {
            return candidates.getFirst();        // 全都 0 权重：退化为取第一个，不做除零
        }
        double roll = rng.nextDouble() * total;
        for (CanHit candidate : candidates) {
            roll -= battle.aggroOf(candidate);
            if (roll <= 0) {
                return candidate;
            }
        }
        return candidates.getLast();             // 浮点误差兜底
    }
}
