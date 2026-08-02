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
 * 镜流 (Jingliu, cid 1212) — 冰属性 毁灭.
 *
 * <p>核心机制【朔望】/【转魄】: 【朔望】层数由行迹 死境 的计数器字段代理 —
 * 战技/终结技获得1层, 强化战技 (寒川映月, 技能9) 消耗1层; 达到2层 (天赋#5) 时通过
 * {@link Battle#enterEnhancedState} 进入【转魄】(引擎自动将战技替换为技能9),
 * 行动提前#6 并获得暴击率#7 加成; 层数归零时退出 (引擎在回合结束时也会自动退出)。
 */
public final class JingliuKit implements CharacterKit {

    @Override
    public int cid() {
        return 1212;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new JingliuDeadland(param(byId, 1212101, 0, 0.35)),
                new JingliuSwordmaster(param(byId, 1212102, 0, 0.1)),
                new JingliuFrost(param(byId, 1212103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new JingliuE1(param(e, 0, 0.24), param(e, 1, 1.0), intParam(e, 2, 1));
            case 2 -> new JingliuE2(param(e, 0, 0.8));
            case 4 -> new JingliuE4(param(e, 0, 0.9), param(e, 1, 0.3));
            case 6 -> new JingliuE6(intParam(e, 0, 1), param(e, 1, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 3 -> JingliuKit::jingliuUlt;
            case 9 -> JingliuKit::jingliuEnhancedSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 对指定敌方单体造成#1%攻击力的冰属性伤害, 对相邻目标造成#3%攻击力的冰属性伤害
     *  (并获得#2层【朔望】, 由行迹 死境 记录)。 */
    static void jingliuUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMultiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(2, 0.9);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 强化战技 寒川映月: 对指定敌方单体造成#1%攻击力的冰属性伤害, 对相邻目标造成#3%攻击力的
     *  冰属性伤害 (消耗#2层【朔望】, 由行迹 死境 记录)。 */
    static void jingliuEnhancedSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMultiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(2, 0.625);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 死境: 【转魄】状态下效果抵抗提高#1。
     * 兼作天赋 澹月转魄 的代理: 记录【朔望】层数 (战技/终结技+1, 强化战技-1); 达到#5层时
     * 进入【转魄】状态 (行动提前#6, 暴击率提高#7), 层数归零时退出; 【转魄】状态下施放攻击时
     * 消耗队友#2生命上限的生命值 (每位队友最多消耗到1点), 根据消耗总量#3提高攻击力,
     * 最高不超过基础攻击力的#4 (星魂4 额外提高, 数值等同于消耗总量的#1, 上限提高#2)。
     */
    static class JingliuDeadland implements Trace {
        private final double resistance;
        private int stacks = 0;

        JingliuDeadland(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "死境";
        }

        /** 天赋参数: 消耗比例/攻击转化/上限/转魄阈值/行动提前/暴击率. */
        private List<Double> talentParams(Character owner) {
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                return talent.getSkills().getFirst();
            }
            return List.of(0.3, 0.04, 5.4, 0.9, 2.0, 1.0, 0.4);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (!owner.isEnhanced()) {
                owner.removeBuff("转魄");
                owner.removeBuff("转魄·攻");
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            List<Double> t = talentParams(owner);
            int threshold = (int) Math.round(t.get(4));
            boolean wasEnhanced = owner.isEnhanced();
            if (type == SkillType.SKILL) {
                stacks += wasEnhanced ? -1 : 1;
            } else if (type == SkillType.ULTRA) {
                stacks++;
            }
            // 进入【转魄】: 行动提前 + 暴击率提高, 并交换战技为 寒川映月 (技能9).
            if (stacks >= threshold && !owner.isEnhanced()) {
                battle.enterEnhancedState(owner);
                battle.advanceByPercent(owner, t.get(5));
                owner.removeBuff("转魄");
                Buff trance = new Buff("转魄", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(t.get(6),
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, trance);
                if (owner.getEidolonLevel() >= 6) {
                    stacks += 1;
                }
                IO.println("  [行迹] " + owner.getName() + " enters 【转魄】 (朔望 " + stacks
                        + ", crit +" + String.format("%.0f%%", t.get(6) * 100)
                        + ", advance " + String.format("%.0f%%", t.get(5) * 100) + ")");
            }
            // 退出【转魄】: 层数归零.
            if (stacks <= 0 && owner.isEnhanced()) {
                battle.exitEnhancedState(owner);
                owner.removeBuff("转魄");
                owner.removeBuff("转魄·攻");
                IO.println("  [行迹] " + owner.getName() + " leaves 【转魄】 (朔望 0)");
            }
            // 【转魄】状态下施放攻击: 消耗队友生命值, 并据消耗总量提高攻击力
            // (仅限进入【转魄】之后发动的攻击).
            if (wasEnhanced && (type == SkillType.COMMON || type == SkillType.SKILL)) {
                drainAndEmpower(battle, owner, t);
            }
        }

        /** 消耗队友生命值并提高自身攻击力 (持续至本次攻击结束, 引擎代理为1回合). */
        private void drainAndEmpower(Battle battle, Character owner, List<Double> t) {
            double drainRatio = t.get(1);
            double convert = t.get(2);
            double capRatio = t.get(3);
            double total = 0;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally == owner || ally.isDeath()) {
                    continue;
                }
                double cost = Math.min(ally.getMaxHp() * drainRatio, ally.getCurrentHp() - 1);
                if (cost > 0) {
                    ally.takeDamage(cost);
                    total += cost;
                }
            }
            if (total <= 0) {
                return;
            }
            double baseAtk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            double extraRatio = 0;
            double extraCap = 0;
            if (owner.getEidolonLevel() >= 4) {
                extraRatio = 0.9;
                extraCap = 0.3;
            }
            double atkBonus = Math.min(baseAtk * (capRatio + extraCap), total * (convert + extraRatio));
            owner.removeBuff("转魄·攻");
            Buff buff = new Buff("转魄·攻", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atkBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " drains " + String.format("%.0f", total)
                    + " ally HP -> ATK +" + String.format("%.0f", atkBonus)
                    + " (转魄·攻, E4 " + String.format("%.0f%%", extraRatio * 100) + ")");
        }
    }

    /** 剑首: 施放【无罅飞光】后，下次行动提前#1。 */
    static class JingliuSwordmaster implements Trace {
        private final double advance;

        JingliuSwordmaster(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "剑首";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL && !owner.isEnhanced()) {
                battle.advanceByPercent(owner, advance);
                IO.println("  [行迹] " + owner.getName() + " advances "
                        + String.format("%.0f%%", advance * 100) + " (无罅飞光, 剑首)");
            }
        }
    }

    /** 霜魄: 【转魄】状态下，终结技造成的伤害提高#1。 */
    static class JingliuFrost implements Trace {
        private final double bonus;

        JingliuFrost(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "霜魄";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return owner.isEnhanced() && type == SkillType.ULTRA ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 月犯天关: 施放终结技或强化战技时，镜流的暴击伤害提高#1，持续#3回合。
     * 若只攻击了1个敌方目标，则额外对该目标造成1次等同于镜流#2攻击力的冰属性伤害。
     */
    static class JingliuE1 implements Trace {
        private final double critDamage;
        private final double extraRatio;
        private final int turns;

        JingliuE1(double critDamage, double extraRatio, int turns) {
            this.critDamage = critDamage;
            this.extraRatio = extraRatio;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            boolean enhancedSkill = type == SkillType.SKILL && owner.isEnhanced();
            if (type != SkillType.ULTRA && !enhancedSkill) {
                return;
            }
            owner.removeBuff("月犯天关");
            Buff buff = new Buff("月犯天关", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            if (battle.getAliveEnemies().size() == 1 && !targets.isEmpty()) {
                CanHit target = targets.getFirst();
                battle.dealAttackDamage(owner, target, extraRatio, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
                IO.println("  [星魂] " + owner.getName() + " deals an extra hit (月犯天关)");
            }
        }
    }

    /** 星魂2 朔晕七星: 施放终结技后，下一次强化战技的伤害提高#1 (施放后消耗)。 */
    static class JingliuE2 implements Trace {
        private final double bonus;

        JingliuE2(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                owner.removeBuff("朔晕七星");
                Buff buff = new Buff("朔晕七星", Buff.Category.BUFF, owner, owner, -1);
                battle.applyBuff(owner, buff);
                IO.println("  [星魂] " + owner.getName() + ": next enhanced skill +"
                        + String.format("%.0f%%", bonus * 100) + " damage");
            } else if (type == SkillType.SKILL && owner.isEnhanced()) {
                owner.removeBuff("朔晕七星");
            }
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.SKILL && owner.isEnhanced() && owner.hasBuffNamed("朔晕七星")
                    ? 1 + bonus : 1.0;
        }
    }

    /**
     * 星魂4 持秉玄烛: 【转魄】状态下消耗队友生命值获得的攻击力额外提高 (数值等同于我方全体
     * 生命值消耗总量的90%), 获得的攻击力上限提高30%。
     * (数值由行迹 死境 在消耗队友生命值时按星魂等级读取.)
     */
    static class JingliuE4 implements Trace {
        @SuppressWarnings("unused")
        private final double extraRatio;
        @SuppressWarnings("unused")
        private final double extraCap;

        JingliuE4(double extraRatio, double extraCap) {
            this.extraRatio = extraRatio;
            this.extraCap = extraCap;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /**
     * 星魂6 蚀变于娄: 镜流进入【转魄】状态时，【朔望】的上限层数提高1层并额外获得1层【朔望】
     * (由行迹 死境 在进入转魄时按星魂等级读取); 【转魄】状态下暴击伤害提高#2。
     */
    static class JingliuE6 implements Trace {
        @SuppressWarnings("unused")
        private final int extraStacks;
        private final double critDamage;

        JingliuE6(int extraStacks, double critDamage) {
            this.extraStacks = extraStacks;
            this.critDamage = critDamage;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean buffed = owner.hasBuffNamed("蚀变于娄");
            if (owner.isEnhanced() && !buffed) {
                Buff buff = new Buff("蚀变于娄", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            } else if (!owner.isEnhanced() && buffed) {
                owner.removeBuff("蚀变于娄");
            }
        }
    }
}
