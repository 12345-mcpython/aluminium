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
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 素裳 (Sushang, cid 1206) — 物理属性 巡猎.
 */
public final class SushangKit implements CharacterKit {

    @Override
    public int cid() {
        return 1206;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SushangInnocent(param(byId, 1206101, 0, 0.5), param(byId, 1206101, 1, 0.5)),
                new SushangChase(param(byId, 1206102, 0, 0.025), intParam(byId, 1206102, 1, 10)),
                new SushangBreakEnemy(param(byId, 1206103, 0, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SushangE1();
            case 2 -> new SushangE2(param(e, 0, 0.2));
            case 4 -> new SushangE4(param(e, 0, 0.4));
            case 6 -> new SushangE6();
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SushangKit::sushangSkill;
            case 3 -> SushangKit::sushangUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 对指定敌方单体造成伤害, 最后一击后有33%概率发动【剑势】 (必定对弱点击破
     * 状态的目标发动); 处于终结技【太虚形蕴·烛夜】期间额外增加2次剑势发动判定,
     * 额外判定发动的剑势伤害为原伤害的50%。
     */
    static void sushangSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMult = ctx.firstParam();
        double swordMult = ctx.param(1, 0.5);
        double chance = ctx.param(2, 0.33);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (main.isDeath()) {
            return;
        }
        boolean broken = main instanceof Enemy enemy && enemy.isBroken();
        // 首次判定: 目标处于弱点击破状态时【剑势】必定发动.
        if (broken || battle.checkEffectHit(user, main, chance)) {
            swordPotential(battle, user, main, swordMult, false);
        }
        // 额外判定 (终结技 太虚形蕴·烛夜).
        int extraJudgments = user.hasBuffNamed("太虚形蕴·烛夜") ? 2 : 0;
        for (int i = 0; i < extraJudgments; i++) {
            if (main.isDeath()) {
                break;
            }
            if (battle.checkEffectHit(user, main, chance)) {
                swordPotential(battle, user, main, swordMult, true);
            }
        }
    }

    /** 终结技: 对指定敌方单体造成伤害, 并使素裳立即行动; 攻击力提高18%, 持续2回合。 */
    static void sushangUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        double atkBoost = ctx.param(3, 0.18);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        battle.advanceByPercent(user, 1.0);
        user.removeBuff("太虚形蕴·烛夜");
        Buff buff = new Buff("太虚形蕴·烛夜", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBoost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " acts immediately and gains +"
                + String.format("%.0f%%", atkBoost * 100) + " ATK (" + turns + " turns)");
    }

    /**
     * 发动【剑势】: 造成物理附加伤害 (伤害随行迹 逐寇 的层数提高),
     * 并触发星魂2 的身之百炼 (受到伤害降低).
     */
    private static void swordPotential(Battle battle, CanHit user, CanHit target, double mult, boolean half) {
        SushangChase chase = findTrace(user, SushangChase.class);
        double boost = chase != null ? chase.addStackAndBonus() : 1.0;
        double m = mult * boost * (half ? 0.5 : 1.0);
        battle.dealAttackDamage(user, target, m, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.PHYSICAL));
        IO.println("  [剑势] " + user.getName() + " extra physical hit on " + target.getName()
                + (half ? " (half)" : "") + " x" + String.format("%.2f", m));
        SushangE2 e2 = findTrace(user, SushangE2.class);
        if (e2 != null) {
            target.removeBuff("其身百炼");
            Buff buff = new Buff("其身百炼", Buff.Category.BUFF, user, user, 1)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-e2.reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(user, buff);
        }
    }

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

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 赤子: 当前生命值百分比≤50%时, 被敌方目标攻击的概率降低。 */
    static class SushangInnocent implements Trace {
        private final double hpThreshold;
        private final double aggroReduction;

        SushangInnocent(double hpThreshold, double aggroReduction) {
            this.hpThreshold = hpThreshold;
            this.aggroReduction = aggroReduction;
        }

        @Override
        public String getName() {
            return "赤子";
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return owner.getHpPercent() <= hpThreshold ? 1 - aggroReduction : 1.0;
        }
    }

    /** 逐寇: 每发动1次【剑势】，【剑势】造成的伤害提高2.5%，最多叠加10层。 */
    static class SushangChase implements Trace {
        private final double bonusPerStack;
        private final int maxStacks;
        private int stacks = 0;

        SushangChase(double bonusPerStack, int maxStacks) {
            this.bonusPerStack = bonusPerStack;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "逐寇";
        }

        /** 剑势 +1 层, 返回本次剑势的伤害倍率 (1 + 层数×每层加成). */
        double addStackAndBonus() {
            stacks = Math.min(maxStacks, stacks + 1);
            return 1 + stacks * bonusPerStack;
        }
    }

    /**
     * 破敌: 施放普攻或战技后，若场上有敌方目标处于弱点击破状态，则素裳的行动提前15%。
     * 同时代理天赋 游刃若水 (场上敌方目标弱点被击破时, 素裳速度提高15%, 持续2回合;
     * 星魂6 使该加速可叠加2层并战斗开始即获得1层).
     */
    static class SushangBreakEnemy implements Trace {
        private final double advance;
        private final double speedPercent = 0.15;
        private final int speedTurns = 2;
        private int speedStacks = 0;

        SushangBreakEnemy(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "破敌";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL) {
                return;
            }
            boolean anyBroken = battle.getAliveEnemies().stream().anyMatch(Enemy::isBroken);
            if (anyBroken) {
                battle.advanceByPercent(owner, advance);
                IO.println("  [行迹] " + owner.getName() + " advances by "
                        + String.format("%.0f%%", advance * 100) + " (破敌)");
            }
        }

        /** 天赋: 敌方弱点被击破 → 速度提高15%, 持续2回合 (星魂6 可叠2层). */
        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            applySpeedStack(battle, owner);
        }

        /** 加速 +1 层 (上限: 无星魂6 为1层, 星魂6 为2层) 并刷新速度 buff. */
        void applySpeedStack(Battle battle, Character owner) {
            int cap = 1;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof SushangE6) {
                    cap = 2;
                }
            }
            speedStacks = Math.min(cap, speedStacks + 1);
            owner.removeBuff("游刃若水");
            Buff buff = new Buff("游刃若水", Buff.Category.BUFF, owner, owner, speedTurns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(
                            speedPercent * speedStacks, DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " speeds up +"
                    + String.format("%.0f%%", speedPercent * speedStacks * 100)
                    + " (" + speedStacks + "/" + cap + " stacks, 游刃若水)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 游刃有余: 对陷入弱点击破状态的敌方目标施放战技后，恢复1个战技点。 */
    static class SushangE1 implements Trace {
        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL || targets == null) {
                return;
            }
            boolean hitBroken = targets.stream()
                    .anyMatch(t -> t instanceof Enemy enemy && enemy.isBroken());
            if (hitBroken) {
                battle.addSkillPoints(1);
                IO.println("  [星魂] " + owner.getName() + " restores 1 skill point (broken target)");
            }
        }
    }

    /** 星魂2 其身百炼: 触发【剑势】后，素裳受到的伤害降低20%，持续1回合。 */
    static class SushangE2 implements Trace {
        private final double reduction;

        SushangE2(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 其心百辟: 素裳的击破特攻提高40%。 */
    static class SushangE4 implements Trace {
        private final double breakingEffect;

        SushangE4(double breakingEffect) {
            this.breakingEffect = breakingEffect;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("其心百辟");
            Buff buff = new Buff("其心百辟", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakingEffect,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " breaking effect +"
                    + String.format("%.0f%%", breakingEffect * 100));
        }
    }

    /** 星魂6 上善若水: 天赋的加速效果可以叠加2层, 且进入战斗后素裳立即获得1层加速效果。 */
    static class SushangE6 implements Trace {
        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof SushangBreakEnemy breakEnemy) {
                    breakEnemy.applySpeedStack(battle, owner);
                    return;
                }
            }
        }
    }
}
