package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 希儿 (Seele, cid 1102) — 量子属性 巡猎.
 */
public final class SeeleKit implements CharacterKit {

    @Override
    public int cid() {
        return 1102;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SeeleNightWalk(),
                new SeeleCleave(param(byId, 1102102, 0, 0.2)),
                new SeeleRipple(param(byId, 1102103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SeeleE1(param(e, 0, 0.8), param(e, 1, 0.15));
            case 2 -> new SeeleE2(intParam(e, 0, 2));
            case 4 -> new SeeleE4(param(e, 0, 15));
            case 6 -> new SeeleE6(param(e, 0, 0.15), intParam(e, 1, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SeeleKit::seeleSkill;
            case 3 -> SeeleKit::seeleUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使希儿的速度提高#2%, 持续#3回合, 并对目标造成#1%量子属性伤害. */
    static void seeleSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double speedPercent = ctx.param(1, 0.25);
        int turns = ctx.intParam(2, 2);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        user.removeBuff("蝶舞");
        Buff buff = new Buff("蝶舞", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " gains +" + String.format("%.0f", speedPercent * 100)
                + "% SPD (蝶舞)");
    }

    /** 终结技: 立即进入增幅状态, 并对目标造成#1%量子属性伤害. */
    static void seeleUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        applyAmplification(battle, user, 0.2);
    }

    /** 增幅状态: 量子属性抗性穿透提高 (数值与行迹 割裂 一致). */
    static void applyAmplification(Battle battle, CanHit user, double penetration) {
        user.removeBuff("增幅");
        Buff buff = new Buff("增幅", Buff.Category.BUFF, user, user, 1)
                .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " enters 增幅 state (+"
                + String.format("%.0f", penetration * 100) + "% quantum pen)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 夜行: 若当前生命值 ≤ 50%，被敌方目标攻击的概率降低。 */
    static class SeeleNightWalk implements Trace {
        @Override
        public String getName() {
            return "夜行";
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return owner.getHpPercent() <= 0.5 ? 0.5 : 1.0;
        }
    }

    /** 割裂: 增幅状态下希儿的量子属性抗性穿透提高20%。
     *  兼作天赋 幻影 的代理: 消灭敌方目标时立即获得1个额外回合并进入增幅状态。 */
    static class SeeleCleave implements Trace {
        private final double penetration;

        SeeleCleave(double penetration) {
            this.penetration = penetration;
        }

        @Override
        public String getName() {
            return "割裂";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Enemy)) {
                return;
            }
            applyAmplification(battle, owner, penetration);
            battle.advanceByPercent(owner, 1.0);
            IO.println("  [行迹] " + owner.getName() + " gains an extra turn (幻影)");
        }
    }

    /** 涟漪: 施放普攻后，希儿的下一次行动提前20%。 */
    static class SeeleRipple implements Trace {
        private final double advance;

        SeeleRipple(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "涟漪";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                battle.advanceByPercent(owner, advance);
                IO.println("  [行迹] " + owner.getName() + "'s next action advances "
                        + String.format("%.0f", advance * 100) + "%");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 对生命值≤80%的敌方目标造成伤害时，暴击率提高15%。 */
    static class SeeleE1 implements Trace {
        private final double hpThreshold;
        private final double crit;

        SeeleE1(double hpThreshold, double crit) {
            this.hpThreshold = hpThreshold;
            this.crit = crit;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getHpPercent() <= hpThreshold ? crit : 0;
        }
    }

    /** 星魂2: 战技的加速效果可以叠加，最多2层。 */
    static class SeeleE2 implements Trace {
        private final int maxStacks;
        private int stacks = 0;

        SeeleE2(int maxStacks) {
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            // 战技已提供 25% 速度加成 (蝶舞); 星魂2 使该加成可叠加至2层.
            stacks = Math.min(maxStacks, stacks + 1);
            owner.removeBuff("蝶舞");
            Buff buff = new Buff("蝶舞", Buff.Category.BUFF, owner, owner, 2)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(0.25 * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " speed stacks: " + stacks);
        }
    }

    /** 星魂4: 消灭敌方目标时，恢复15点能量。 */
    static class SeeleE4 implements Trace {
        private final double energy;

        SeeleE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (victim instanceof Enemy) {
                owner.gainEnergy(energy);
                IO.println("  [星魂] " + owner.getName() + " gains "
                        + String.format("%.0f", energy) + " energy");
            }
        }
    }

    /** 星魂6: 终结技使目标陷入【乱蝶】状态，受击时额外受到终结技伤害15%的量子附加伤害。 */
    static class SeeleE6 implements Trace {
        private final double ratio;
        private final int turns;

        SeeleE6(double ratio, int turns) {
            this.ratio = ratio;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            double ultMult = KitSupport.actionMultiplier(owner, SkillType.ULTRA);
            for (CanHit target : targets) {
                Buff mark = new Buff("乱蝶", Buff.Category.DEBUFF, owner, target, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(ratio * ultMult,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(target, mark);
            }
            IO.println("  [星魂] " + owner.getName() + " marks targets with 乱蝶");
        }
    }
}
