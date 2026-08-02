package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DataSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 丹恒•饮月 (Dan Heng • Imbibitor Lunae, cid 1213) — 虚数属性 毁灭.
 *
 * <p>核心机制【强化普攻】: 战技 龙力自在 不消耗战技点, 使普攻强化 — 引擎的
 * {@link Battle#enterEnhancedState} 在战技执行后自动将普攻替换为技能8 (瞬华)、
 * 战技替换为技能9 (取消)。三层强化等级由行迹 伏辰 的计数器字段代理:
 * 每施放1次 龙力自在 提升1级 (1级: 瞬华; 2级: 天矢阴, 技能10; 3级: 盘拏耀跃,
 * 技能12), 等级提升时手动把普攻槽替换为对应技能; 施放 取消 (技能9) 时退出强化。
 *
 * <p>【亢心】(天赋) 与【叱咤】(战技) 均为"持续至自身回合结束"的层数堆叠增益,
 * 由行迹 伏辰 的计数器字段 + 叠加 buff 代理 (引擎无法逐段计数, 每次攻击行为近似
 * 为1段)。【逆鳞】由终结技获得, 代理为施放战技时返还战技点。
 */
public final class DanHengILKit implements CharacterKit {

    @Override
    public int cid() {
        return 1213;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new DanHengFuchen(param(byId, 1213101, 0, 15)),
                new DanHengXiuyu(param(byId, 1213102, 0, 0.35)),
                new DanHengQizhe(param(byId, 1213103, 0, 0.24)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new DanHengE1(intParam(e, 0, 4));
            case 2 -> new DanHengE2();
            case 4 -> new DanHengE4();
            case 6 -> new DanHengE6(param(e, 0, 0.2), intParam(e, 1, 3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            // 龙力自在: 不消耗战技点, 仅进入强化状态 (引擎处理技能替换), 记录强化意图.
            case 2 -> DanHengILKit::danHengEnhance;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 龙力自在: 施放本技能不消耗战技点且不视为使用战技 — 引擎在执行后自动进入
     * 强化状态 (普攻 → 瞬华), 本行为仅记录"本次战技为强化"的意图, 由行迹 伏辰
     * 在 afterAction 中提升强化等级并返还战技点。
     */
    static void danHengEnhance(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (user instanceof Character character) {
            DanHengFuchen fuchen = findTrace(character, DanHengFuchen.class);
            if (fuchen != null) {
                fuchen.pendingEnhance = true;
            }
        }
        IO.println("  " + user.getName() + " 施放 龙力自在 (普攻强化, 引擎自动替换技能)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 伏辰: 战斗开始时，立即恢复15点能量。
     * 兼作天赋 亢心 / 战技强化等级 / 逆鳞 的代理:
     * <ul>
     *   <li>【亢心】每段攻击获得1层 (每次行动近似1层), 每层使伤害提高#1, 最多#2层,
     *   持续至自身回合结束 — 叠加为 "亢心" buff, 回合开始时清空 (星魂1: 上限+4, 每次+1层)。</li>
     *   <li>强化等级: 每次 龙力自在 +1级 (最高3), 2级时普攻→天矢阴 (技能10),
     *   3级时普攻→盘拏耀跃 (技能12); 施放 取消 时退出强化状态。</li>
     *   <li>【叱咤】: 施放天矢阴/盘拏耀跃时获得 (近似: 每次强化普攻获得与等级对应的层数,
     *   2级2层, 3级4层), 每层使暴击伤害提高#1, 持续至自身回合结束 (星魂4: 持续到下回合)。</li>
     *   <li>【逆鳞】: 终结技获得#3个, 最多持有#4个 — 代理为施放战技时返还战技点。</li>
     * </ul>
     */
    static class DanHengFuchen implements Trace {
        private final double energy;
        private int enhanceLevel = 0;
        private int insanity = 0;
        private int reverseScale = 0;
        private boolean pendingEnhance = false;

        DanHengFuchen(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "伏辰";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (伏辰)");
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            // 亢心/叱咤 持续至自身回合结束 — 新回合开始时清空.
            owner.removeBuff("亢心");
            owner.removeBuff("叱咤");
            insanity = 0;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                if (pendingEnhance) {
                    pendingEnhance = false;
                    // 龙力自在: 不消耗战技点, 返还本次消耗; 强化等级+1, 引擎已进入强化状态.
                    battle.addSkillPoints(1);
                    enhanceLevel = Math.min(3, enhanceLevel + 1);
                    if (enhanceLevel >= 2) {
                        swapCommon(owner, enhanceLevel == 3 ? 12 : 10);
                    }
                    IO.println("  [行迹] " + owner.getName() + " 强化等级 " + enhanceLevel
                            + " (龙力自在, SP refunded)");
                } else if (owner.isEnhanced()) {
                    // 取消 (技能9): 退出强化状态.
                    battle.exitEnhancedState(owner);
                    enhanceLevel = 0;
                    IO.println("  [行迹] " + owner.getName() + " cancels the enhancement (取消)");
                }
                // 逆鳞: 抵扣战技点消耗 → 返还1个战技点.
                if (reverseScale > 0) {
                    reverseScale--;
                    battle.addSkillPoints(1);
                    IO.println("  [行迹] " + owner.getName() + " consumes 1 【逆鳞】 (SP refunded)");
                }
            } else if (type == SkillType.COMMON && owner.isEnhanced()) {
                // 强化普攻 (瞬华/天矢阴/盘拏耀跃): 亢心+1层; 叱咤按强化等级叠加.
                gainInsanity(battle, owner, 1);
                applyZhaZha(battle, owner, enhanceLevel >= 3 ? 4 : enhanceLevel >= 2 ? 2 : 0);
                // 星魂6 见谯: 施放盘拏耀跃后消耗.
                DanHengE6 e6 = findTrace(owner, DanHengE6.class);
                if (e6 != null && enhanceLevel >= 3) {
                    e6.consume(battle, owner);
                }
            } else if (type == SkillType.ULTRA) {
                gainInsanity(battle, owner, 1);
                addReverseScale(owner, 2);
                DanHengE2 e2 = findTrace(owner, DanHengE2.class);
                if (e2 != null) {
                    e2.afterUltra(battle, owner);
                }
            }
        }

        /** 把普攻槽替换为指定技能的强化版本 (技能10/12). */
        private static void swapCommon(Character owner, int skillId) {
            Skill common = owner.getSkills().get(SkillType.COMMON);
            int level = common != null ? common.getLevel() : 1;
            owner.getSkills().put(SkillType.COMMON, new DataSkill(1213, skillId, level));
        }

        /** 【亢心】: 每次攻击叠加1层 (星魂1 额外+1层), 每层伤害提高#1, 最多#2层. */
        private void gainInsanity(Battle battle, Character owner, int base) {
            double perStack = 0.05;
            int cap = 6;
            int extraPerAction = 0;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                perStack = talent.getSkills().getFirst().get(0);
                cap = (int) Math.round(talent.getSkills().getFirst().get(1));
            }
            DanHengE1 e1 = findTrace(owner, DanHengE1.class);
            if (e1 != null) {
                cap += e1.extraCap;
                extraPerAction = e1.extraPerAction;
            }
            insanity = Math.min(cap, insanity + base + extraPerAction);
            owner.removeBuff("亢心");
            Buff buff = new Buff("亢心", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * insanity,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " 【亢心】 " + insanity + "/" + cap
                    + " stacks (damage +" + String.format("%.0f%%", perStack * insanity * 100) + ")");
        }

        /** 【叱咤】: 施放天矢阴/盘拏耀跃时叠加, 每层暴击伤害提高#1 (星魂4 持续到下回合). */
        private void applyZhaZha(Battle battle, Character owner, int stacks) {
            if (stacks <= 0) {
                return;
            }
            double perStack = 0.06;
            int cap = 4;
            int duration = 1;
            SkillData skill = KitSupport.skillData(owner, SkillType.SKILL);
            if (skill != null && skill.getSkills() != null && !skill.getSkills().isEmpty()
                    && !skill.getSkills().getFirst().isEmpty()) {
                perStack = skill.getSkills().getFirst().get(0);
                cap = (int) Math.round(skill.getSkills().getFirst().get(1));
            }
            DanHengE4 e4 = findTrace(owner, DanHengE4.class);
            if (e4 != null) {
                duration = 2;
            }
            int total = Math.min(cap, stacks);
            owner.removeBuff("叱咤");
            Buff buff = new Buff("叱咤", Buff.Category.BUFF, owner, owner, duration)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(perStack * total,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " 【叱咤】 " + total + "/" + cap
                    + " stacks (crit DMG +" + String.format("%.0f%%", perStack * total * 100) + ")");
        }

        /** 【逆鳞】: 终结技获得2个 (星魂2 额外+1), 最多持有终结技#4个. */
        void addReverseScale(Character owner, int amount) {
            int max = 3;
            SkillData ultra = KitSupport.skillData(owner, SkillType.ULTRA);
            if (ultra != null && ultra.getSkills() != null && !ultra.getSkills().isEmpty()
                    && ultra.getSkills().getFirst().size() > 3) {
                max = (int) Math.round(ultra.getSkills().getFirst().get(3));
            }
            reverseScale = Math.min(max, reverseScale + amount);
            IO.println("  [行迹] " + owner.getName() + " gains " + amount + " 【逆鳞】 ("
                    + reverseScale + "/" + max + ")");
        }
    }

    /** 修禹: 抵抗控制类负面状态的概率提高35%。 */
    static class DanHengXiuyu implements Trace {
        private final double resistance;

        DanHengXiuyu(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "修禹";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("修禹");
            Buff buff = new Buff("修禹", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " control resistance +"
                    + String.format("%.0f%%", resistance * 100) + " (修禹)");
        }
    }

    /**
     * 起蛰: 对拥有虚数属性弱点的敌方目标造成伤害时，暴击伤害提高24%。
     * 引擎没有暴击伤害的条件钩子, 近似为对虚数弱点目标造成伤害提高 (代理)。
     */
    static class DanHengQizhe implements Trace {
        private final double bonus;

        DanHengQizhe(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "起蛰";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return SkillContext.isWeakTo(defender, Element.IMAGINARY) ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 萦天: 使【亢心】的可叠加层数增加4层, 且每段攻击额外获得1层【亢心】。
     * (数值由行迹 伏辰 在叠加亢心时读取.)
     */
    static class DanHengE1 implements Trace {
        private final int extraCap;
        private final int extraPerAction = 1;

        DanHengE1(int extraCap) {
            this.extraCap = extraCap;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 九斿: 施放终结技后行动提前100%, 并额外获得1个【逆鳞】。 */
    static class DanHengE2 implements Trace {
        @Override
        public String getName() {
            return "星魂2";
        }

        void afterUltra(Battle battle, Character owner) {
            battle.advanceByPercent(owner, 1.0);
            DanHengFuchen fuchen = findTrace(owner, DanHengFuchen.class);
            if (fuchen != null) {
                fuchen.addReverseScale(owner, 1);
            }
            IO.println("  [星魂] " + owner.getName() + " advances 100% after ult (九斿)");
        }
    }

    /** 星魂4 嘲风: 【叱咤】的增益效果会持续到下一个自身回合结束。
     *  (持续回合数由行迹 伏辰 在叠加叱咤时读取.) */
    static class DanHengE4 implements Trace {
        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /**
     * 星魂6 见谯: 我方其他角色施放终结技后, 丹恒•饮月下一次施放【盘拏耀跃】时
     * 虚数属性抗性穿透提高#1, 最多叠加#2层。
     */
    static class DanHengE6 implements Trace {
        private final double penetration;
        private final int maxStacks;
        private int stacks = 0;

        DanHengE6(double penetration, int maxStacks) {
            this.penetration = penetration;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA || actor == owner) {
                return;
            }
            stacks = Math.min(maxStacks, stacks + 1);
            owner.removeBuff("见谯");
            Buff buff = new Buff("见谯", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " 见谯 " + stacks + "/" + maxStacks
                    + " stacks (imaginary RES PEN +" + String.format("%.0f%%", penetration * stacks * 100) + ")");
        }

        /** 施放盘拏耀跃 (强化普攻) 后消耗全部层数. */
        void consume(Battle battle, Character owner) {
            if (stacks <= 0) {
                return;
            }
            stacks = 0;
            owner.removeBuff("见谯");
            IO.println("  [星魂] " + owner.getName() + " consumes 见谯 (盘拏耀跃)");
        }
    }

    // ─── 工具 ───────────────────────────────────────────────────────────

    private static <T extends Trace> T findTrace(CanHit user, Class<T> type) {
        if (!(user instanceof Character character)) {
            return null;
        }
        for (Trace trace : character.getTraces()) {
            if (type.isInstance(trace)) {
                return type.cast(trace);
            }
        }
        return null;
    }
}
