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
 * 丹恒 (Dan Heng, cid 1002) — 风属性 巡猎.
 */
public final class DanHengKit implements CharacterKit {

    @Override
    public int cid() {
        return 1002;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new DanHengHiddenDragon(),
                new DanHengShadowRush(param(byId, 1002102, 0, 0.5),
                        param(byId, 1002102, 1, 0.2), intParam(byId, 1002102, 2, 2)),
                new DanHengGale(param(byId, 1002103, 0, 0.4)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new DanHengE1(param(e, 0, 0.5), param(e, 1, 0.12));
            case 2 -> new DanHengE2(param(e, 0, 0.18));
            case 4 -> new DanHengE4();
            case 6 -> new DanHengE6(param(e, 0, 0.08));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 3 ? DanHengKit::danHengUlt : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 对减速状态的目标伤害倍率提高. */
    static void danHengUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        if (target.hasBuffNamed("Slow") || target.hasBuffNamed("Slow+")) {
            multiplier *= 1 + ctx.param(1, 0.72);
        }
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 潜龙: 若当前生命值百分比 ≤ 50%，被敌方目标攻击的概率降低。 */
    static class DanHengHiddenDragon implements Trace {
        @Override
        public String getName() {
            return "潜龙";
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return owner.getHpPercent() <= 0.5 ? 0.5 : 1.0;
        }
    }

    /** 绝影: 施放攻击后，有50%固定概率使自身速度提高20%，持续2回合。 */
    static class DanHengShadowRush implements Trace {
        private final double chance;
        private final double speedPercent;
        private final int turns;

        DanHengShadowRush(double chance, double speedPercent, int turns) {
            this.chance = chance;
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "绝影";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (Math.random() >= chance) {
                return;
            }
            owner.removeBuff("绝影");
            Buff buff = new Buff("绝影", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 罡风: 普攻对减速状态下的敌方目标造成的伤害提高40%。 */
    static class DanHengGale implements Trace {
        private final double bonus;

        DanHengGale(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "罡风";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.COMMON && defender.hasBuffNamed("Slow") ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 穷高极天: 目标生命值≥50%时，暴击率提高12%。 */
    static class DanHengE1 implements Trace {
        private final double hpThreshold;
        private final double crit;

        DanHengE1(double hpThreshold, double crit) {
            this.hpThreshold = hpThreshold;
            this.crit = crit;
        }

        @Override
        public String getName() {
            return "穷高极天，亢盈难久";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getHpPercent() >= hpThreshold ? crit : 0;
        }
    }

    /** 星魂2 威制八毒: 天赋的冷却时间减少1回合 (代理天赋 寸长寸强). */
    static class DanHengE2 implements Trace {
        private final double pen;
        private int cooldown = 0;

        DanHengE2(double pen) {
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "威制八毒，灭却炎烟";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (cooldown > 0) {
                cooldown--;
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (cooldown > 0 || type != SkillType.SKILL || targets == null || !targets.contains(owner)) {
                return;
            }
            owner.removeBuff("寸长寸强");
            Buff buff = new Buff("寸长寸强", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            cooldown = 1;
            IO.println("  [星魂] " + owner.getName() + " gains wind RES pen (寸长寸强)");
        }
    }

    /** 星魂4 奋迅三昧: 终结技消灭目标时立即行动。 */
    static class DanHengE4 implements Trace {
        @Override
        public String getName() {
            return "奋迅三昧，如日空居";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA && targets.stream().anyMatch(CanHit::isDeath)) {
                battle.advanceByPercent(owner, 1.0);
                IO.println("  [星魂] " + owner.getName() + " acts again immediately!");
            }
        }
    }

    /** 星魂6 须绳缚身: 减速效果额外降低8%速度。 */
    static class DanHengE6 implements Trace {
        private final double extra;

        DanHengE6(double extra) {
            this.extra = extra;
        }

        @Override
        public String getName() {
            return "须绳缚身，沉潜勿用";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                for (CanHit target : targets) {
                    if (target.hasBuffNamed("Slow")) {
                        Buff extraSlow = new Buff("Slow+", Buff.Category.DEBUFF, owner, target, 2)
                                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-extra,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, extraSlow);
                        IO.println("  [星魂] " + target.getName() + " slows further (-"
                                + String.format("%.0f", extra * 100) + "% SPD)");
                    }
                }
            }
        }
    }
}
