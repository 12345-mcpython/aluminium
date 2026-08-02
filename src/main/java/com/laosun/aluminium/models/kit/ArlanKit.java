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
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 阿兰 (Arlan, cid 1008) — 雷属性 毁灭.
 */
public final class ArlanKit implements CharacterKit {

    @Override
    public int cid() {
        return 1008;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new ArlanRevival(param(byId, 1008101, 0, 0.3), param(byId, 1008101, 1, 0.2)),
                new ArlanPerseverance(param(byId, 1008102, 0, 0.5)),
                new ArlanDefence(param(byId, 1008103, 0, 0.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new ArlanE1(param(e, 0, 0.1));
            case 2 -> new ArlanE2();
            case 4 -> new ArlanE4(param(e, 0, 0.25), intParam(e, 1, 2));
            case 6 -> new ArlanE6(param(e, 0, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 2 ? ArlanKit::arlanSkill : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 消耗自身生命上限X%的生命值 (不足则降至1点), 对目标造成伤害. */
    static void arlanSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double hpCostRatio = ctx.param(0, 0.15);
        double multiplier = ctx.param(1, 1.2);
        double cost = user.getMaxHp() * hpCostRatio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
        battle.dealAttackDamage(user, target, multiplier, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, user.getElement()));
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 苏生: 消灭敌方目标时，若当前生命值百分比 ≤ 30%，立即回复20%生命上限的生命值。 */
    static class ArlanRevival implements Trace {
        private final double hpThreshold;
        private final double healRatio;

        ArlanRevival(double hpThreshold, double healRatio) {
            this.hpThreshold = hpThreshold;
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "苏生";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (victim instanceof com.laosun.aluminium.models.Enemy && owner.getHpPercent() <= hpThreshold) {
                double heal = owner.getMaxHp() * healRatio;
                owner.heal(heal);
                IO.println("  [行迹] " + owner.getName() + " revives for "
                        + String.format("%.0f", heal) + " HP");
            }
        }
    }

    /** 坚忍: 抵抗持续伤害类负面状态的概率提高50%。 */
    static class ArlanPerseverance implements Trace {
        private final double resistance;

        ArlanPerseverance(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "坚忍";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("坚忍", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 抗御: 进入战斗时若生命值 ≤ 50%，阿兰可以抵抗除持续伤害外的所有伤害，受到攻击后解除。 */
    static class ArlanDefence implements Trace {
        private final double hpThreshold;

        ArlanDefence(double hpThreshold) {
            this.hpThreshold = hpThreshold;
        }

        @Override
        public String getName() {
            return "抗御";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (owner.getHpPercent() <= hpThreshold) {
                Buff buff = new Buff("抗御", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-1.0,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [行迹] " + owner.getName() + " is protected by 抗御!");
            }
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.hasBuffNamed("抗御")) {
                owner.removeBuff("抗御");
                IO.println("  [行迹] " + owner.getName() + "'s 抗御 was consumed!");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 万死不辞: 生命值≤50%时，战技伤害提高10%。 */
    static class ArlanE1 implements Trace {
        private final double bonus;

        ArlanE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "万死不辞";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.SKILL && owner.getHpPercent() <= 0.5 ? 1 + bonus : 1.0;
        }
    }

    /** 星魂2 除制去缚: 施放战技、终结技时解除自身1个负面效果。 */
    static class ArlanE2 implements Trace {
        @Override
        public String getName() {
            return "除制去缚";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL || type == SkillType.ULTRA) {
                owner.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                        .findFirst()
                        .ifPresent(buff -> {
                            battle.removeBuff(owner, buff);
                            IO.println("  [星魂] " + owner.getName() + " cleanses itself of " + buff.getName());
                        });
            }
        }
    }

    /** 星魂4 绝处反击: 受到致命攻击时不会倒下，回复至25%生命上限，触发1次或2回合后解除。 */
    static class ArlanE4 implements Trace {
        private final double healRatio;
        private final int turns;
        private int turnsLeft;
        private boolean used = false;

        ArlanE4(double healRatio, int turns) {
            this.healRatio = healRatio;
            this.turnsLeft = turns;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "绝处反击";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            turnsLeft = turns;
            used = false;
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (!used && turnsLeft > 0) {
                turnsLeft--;
            }
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (used || turnsLeft <= 0) {
                return;
            }
            if (owner.isDeath()) {
                used = true;
                owner.revive(owner.getMaxHp() * healRatio);
                IO.println("  [星魂] " + owner.getName() + " survives the fatal blow at "
                        + String.format("%.0f", owner.getMaxHp() * healRatio) + " HP!");
            }
        }
    }

    /** 星魂6 以身作引: 生命值≤50%时，终结技伤害提高20%。 */
    static class ArlanE6 implements Trace {
        private final double bonus;

        ArlanE6(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "以身作引";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA && owner.getHpPercent() <= 0.5 ? 1 + bonus : 1.0;
        }
    }
}
