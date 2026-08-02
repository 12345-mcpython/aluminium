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
 * 真理医生 (Dr. Ratio, cid 1305) — 虚数属性 巡猎.
 */
public final class DrRatioKit implements CharacterKit {

    @Override
    public int cid() {
        return 1305;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new DrRatioInduction(param(byId, 1305101, 0, 0.025), param(byId, 1305101, 1, 0.05),
                        intParam(byId, 1305101, 2, 6)),
                new DrRatioDeduction(param(byId, 1305102, 0, 1.0), param(byId, 1305102, 1, 0.1),
                        intParam(byId, 1305102, 2, 2)),
                new DrRatioReasoning(intParam(byId, 1305103, 0, 3), param(byId, 1305103, 1, 0.1),
                        param(byId, 1305103, 2, 0.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new DrRatioE1(intParam(e, 0, 4), intParam(e, 1, 4));
            case 2 -> new DrRatioE2(param(e, 0, 0.2), intParam(e, 1, 4));
            case 4 -> new DrRatioE4(param(e, 0, 15));
            case 6 -> new DrRatioE6(intParam(e, 0, 1), param(e, 1, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 3 ? DrRatioKit::drRatioUlt : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 造成#1%伤害, 并附上【智者的短见】(最多触发#2次). */
    static void drRatioUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        int triggers = ctx.intParam(1, 2);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (!target.isDeath()) {
            target.removeBuff("智者的短见");
            Buff mark = new Buff("智者的短见", Buff.Category.DEBUFF, user, target, -1);
            battle.applyBuff(target, mark);
            setShortSightTriggers(user, triggers);
            IO.println("  " + target.getName() + " is marked with 智者的短见 (" + triggers + " triggers)");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 归纳: 施放战技时, 目标每有1个负面效果, 暴击率+2.5%、暴击伤害+5%, 最多6层。 */
    static class DrRatioInduction implements Trace {
        private final double critPerDebuff;
        private final double cdmgPerDebuff;
        private final int maxStacks;

        DrRatioInduction(double critPerDebuff, double cdmgPerDebuff, int maxStacks) {
            this.critPerDebuff = critPerDebuff;
            this.cdmgPerDebuff = cdmgPerDebuff;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "归纳";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            // 星魂1: 战斗开始时立即获得4层【归纳】.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof DrRatioE1 e1) {
                    DrRatioKit.setInductionStacks(owner, e1.startStacks);
                }
            }
            refresh(battle, owner);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL || targets.isEmpty()) {
                return;
            }
            int debuffs = (int) targets.getFirst().getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
            int cap = maxStacks;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof DrRatioE1 e1) {
                    cap += e1.capBonus;
                }
            }
            DrRatioKit.setInductionStacks(owner, Math.min(cap, debuffs));
            refresh(battle, owner);
            // 天赋 我思故我在 代理: 战技后有概率发动追加攻击.
            DrRatioKit.triggerFollowUp(battle, owner, targets.getFirst());
        }

        private void refresh(Battle battle, Character owner) {
            int stacks = DrRatioKit.inductionStacks(owner);
            owner.removeBuff("归纳");
            Buff buff = new Buff("归纳", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critPerDebuff * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmgPerDebuff * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 演绎: 施放战技攻击后, 有100%基础概率使目标效果抵抗降低10%, 持续2回合。 */
    static class DrRatioDeduction implements Trace {
        private final double chance;
        private final double resDown;
        private final int turns;

        DrRatioDeduction(double chance, double resDown, int turns) {
            this.chance = chance;
            this.resDown = resDown;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "演绎";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (!target.isDeath() && battle.checkEffectHit(owner, target, chance)) {
                    Buff debuff = new Buff("演绎", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(-resDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }
    }

    /** 推理: 目标负面效果≥3个时, 每有1个负面效果伤害提高10%, 最多50%。 */
    static class DrRatioReasoning implements Trace {
        private final int threshold;
        private final double perDebuff;
        private final double cap;

        DrRatioReasoning(int threshold, double perDebuff, double cap) {
            this.threshold = threshold;
            this.perDebuff = perDebuff;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "推理";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            long debuffs = defender.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
            if (debuffs < threshold) {
                return 1.0;
            }
            return 1 + Math.min(cap, debuffs * perDebuff);
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            // 终结技的【智者的短见】: 队友攻击持有标记的目标时, 真理医生发动1次天赋追加攻击.
            if (targets == null || targets.isEmpty()) {
                return;
            }
            CanHit target = targets.getFirst();
            if (target.hasBuffNamed("智者的短见") && DrRatioKit.consumeShortSight(owner) > 0) {
                triggerFollowUp(battle, owner, target);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 倨傲生祸患: 【归纳】叠加上限+4层, 战斗开始时获得4层。 */
    static class DrRatioE1 implements Trace {
        private final int capBonus;
        private final int startStacks;

        DrRatioE1(int capBonus, int startStacks) {
            this.capBonus = capBonus;
            this.startStacks = startStacks;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 显微而阐幽: 天赋追加攻击击中目标时, 目标每有1个负面效果额外造成20%攻击力伤害。 */
    static class DrRatioE2 implements Trace {
        private final double perDebuff;
        private final int maxTriggers;

        DrRatioE2(double perDebuff, int maxTriggers) {
            this.perDebuff = perDebuff;
            this.maxTriggers = maxTriggers;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 盲目造痴愚: 触发天赋时, 额外恢复15点能量。 */
    static class DrRatioE4 implements Trace {
        private final double energy;

        DrRatioE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 永恒唯真理: 【智者的短见】触发次数+1, 天赋追加攻击伤害提高50%。 */
    static class DrRatioE6 implements Trace {
        private final int extraTriggers;
        private final double followUpBonus;

        DrRatioE6(int extraTriggers, double followUpBonus) {
            this.extraTriggers = extraTriggers;
            this.followUpBonus = followUpBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 天赋 我思故我在 代理 (战技后概率追加攻击) ──────────────────────

    /** 施放战技后, 有40%+每负面效果20%的固定概率对目标发动1次追加攻击 (135%伤害). */
    static void triggerFollowUp(Battle battle, Character owner, CanHit target) {
        if (target.isDeath()) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            target = alive.get((int) (Math.random() * alive.size()));
        }
        double chance = 0.4;
        long debuffs = target.getBuffs().stream()
                .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
        chance += debuffs * 0.2;
        double mult = 1.35;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof DrRatioE6 e6) {
                mult *= 1 + e6.followUpBonus;
            }
        }
        if (Math.random() < chance) {
            battle.dealAttackDamage(owner, target, mult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                            Element.IMAGINARY));
            // 星魂2: 每负面效果额外附加伤害.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof DrRatioE2 e2) {
                    double extra = Math.min(e2.maxTriggers, debuffs) * e2.perDebuff;
                    if (extra > 0) {
                        battle.dealAttackDamage(owner, target, extra, 0,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                        Element.IMAGINARY));
                    }
                }
            }
            // 星魂4: 触发天赋时恢复能量.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof DrRatioE4 e4) {
                    owner.gainEnergy(e4.energy);
                }
            }
            IO.println("  [天赋] " + owner.getName() + " follows up on " + target.getName());
        }
    }

    /** 【智者的短见】触发次数 (星魂6 +1). */
    static int shortSightTriggers(CanHit owner) {
        int base = 2;
        if (owner instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof DrRatioE6 e6) {
                    base += e6.extraTriggers;
                }
            }
        }
        return base;
    }

    static void setShortSightTriggers(CanHit owner, int triggers) {
        SHORT_SIGHT.put(owner, triggers);
    }

    static int consumeShortSight(CanHit owner) {
        int left = SHORT_SIGHT.getOrDefault(owner, 0);
        if (left > 0) {
            SHORT_SIGHT.put(owner, left - 1);
        }
        return left;
    }

    private static final java.util.Map<CanHit, Integer> SHORT_SIGHT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> INDUCTION = new java.util.concurrent.ConcurrentHashMap<>();

    static int inductionStacks(CanHit owner) {
        return INDUCTION.getOrDefault(owner, 0);
    }

    static void setInductionStacks(CanHit owner, int stacks) {
        INDUCTION.put(owner, Math.max(0, stacks));
    }
}
