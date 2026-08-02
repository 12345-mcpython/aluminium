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
 * 流萤 (Firefly, cid 1310) — 火属性 毁灭.
 */
public final class FireflyKit implements CharacterKit {

    @Override
    public int cid() {
        return 1310;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new FireflyAlpha(param(byId, 1310101, 0, 0.55)),
                new FireflyBeta(param(byId, 1310102, 0, 2), param(byId, 1310102, 1, 3.6),
                        param(byId, 1310102, 2, 0.35), param(byId, 1310102, 3, 0.5)),
                new FireflyGamma(param(byId, 1310103, 0, 1800), param(byId, 1310103, 1, 10),
                        param(byId, 1310103, 2, 0.008)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new FireflyE1(param(e, 0, 0.15));
            case 2 -> new FireflyE2(intParam(e, 0, 1), intParam(e, 1, 1));
            case 4 -> new FireflyE4(param(e, 0, 0.5));
            case 6 -> new FireflyE6(param(e, 0, 0.2), param(e, 1, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> FireflyKit::fireflySkill;
            case 8 -> FireflyKit::fireflyEnhancedBasic;
            case 9 -> FireflyKit::fireflyEnhancedSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 消耗#2生命上限的生命值, 恢复#3能量上限的能量, 造成#1%伤害, 使下一次行动提前#4。 */
    static void fireflySkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double hpCostRatio = ctx.param(1, 0.4);
        double energyRatio = ctx.param(2, 0.5);
        double advance = ctx.param(3, 0.25);
        double cost = user.getMaxHp() * hpCostRatio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
        user.gainEnergy(user.getMaxEnergy() * energyRatio);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        battle.advanceByPercent(user, advance);
        IO.println("  " + user.getName() + " costs " + String.format("%.0f", cost)
                + " HP, recovers " + String.format("%.0f", user.getMaxEnergy() * energyRatio) + " energy");
    }

    /** 强化普攻 底火斩击: 回复#2生命上限的生命值, 造成#1%火属性伤害。 */
    static void fireflyEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double healRatio = ctx.param(1, 0.2);
        user.heal(user.getMaxHp() * healRatio);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    /** 强化战技 死星过载: 回复#3生命上限的生命值, 为目标添加火属性弱点#4回合,
     *  造成 (#5×击破特攻+#1) 攻击力伤害, 相邻目标 (#6×击破特攻+#2) 攻击力伤害. */
    static void fireflyEnhancedSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainBeMult = ctx.param(0, 1.0);
        double sideBeMult = ctx.param(1, 0.5);
        double healRatio = ctx.param(2, 0.25);
        int weaknessTurns = ctx.intParam(3, 2);
        double mainFlat = ctx.param(4, 0.2);
        double sideFlat = ctx.param(5, 0.1);
        user.heal(user.getMaxHp() * healRatio);
        double be = user.getAttribute(AttributeType.BREAKING_EFFECT) != null
                ? user.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
        if (main instanceof Enemy enemy) {
            enemy.addTemporaryWeakness(Element.FIRE, weaknessTurns);
        }
        ctx.dealDamage(battle, user, main, mainBeMult * be + mainFlat);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideBeMult * be + sideFlat);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " 死星过载 hits with BE-scaled damage!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** α模组-偏时迸发: 「完全燃烧」状态下, 攻击没有火属性弱点的敌人也能削减韧性 (55%效果)。 */
    static class FireflyAlpha implements Trace {
        private final double ratio;

        FireflyAlpha(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "α模组-偏时迸发";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("α模组", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(ratio,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** β模组-自限装甲: 完全燃烧状态下, 击破特攻≥200%时, 攻击弱点击破状态目标将削韧值转化为超击破伤害。 */
    static class FireflyBeta implements Trace {
        private final double threshold1;
        private final double threshold2;
        private final double ratio1;
        private final double ratio2;

        FireflyBeta(double threshold1, double threshold2, double ratio1, double ratio2) {
            this.threshold1 = threshold1;
            this.threshold2 = threshold2;
            this.ratio1 = ratio1;
            this.ratio2 = ratio2;
        }

        @Override
        public String getName() {
            return "β模组-自限装甲";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double ratio = be >= threshold2 ? ratio2 : be >= threshold1 ? ratio1 : 0;
            if (ratio > 0) {
                Buff buff = new Buff("β模组", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.SUPER_BREAK_DAMAGE_BOOST, DoubleValue.Modifier.pure(ratio,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    /** γ模组-过载核心: 攻击力>1800时, 每超过10点攻击力使击破特攻提高0.8%。 */
    static class FireflyGamma implements Trace {
        private final double threshold;
        private final double step;
        private final double perStep;

        FireflyGamma(double threshold, double step, double perStep) {
            this.threshold = threshold;
            this.step = step;
            this.perStep = perStep;
        }

        @Override
        public String getName() {
            return "γ模组-过载核心";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            double bonus = atk > threshold ? Math.floor((atk - threshold) / step) * perStep : 0;
            owner.removeBuff("γ模组");
            Buff buff = new Buff("γ模组", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 我曾安眠，赤染之茧: 施放强化战技时无视目标15%防御。 */
    static class FireflyE1 implements Trace {
        private final double defIgnore;

        FireflyE1(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 自破碎的天空坠落: 完全燃烧状态下施放强化普攻/强化战技消灭目标或击破弱点时, 立即获得1个额外回合。 */
    static class FireflyE2 implements Trace {
        private final int ignored;
        private final int cooldown;
        private int turnsLeft = 0;

        FireflyE2(int ignored, int cooldown) {
            this.ignored = ignored;
            this.cooldown = cooldown;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (turnsLeft > 0) {
                turnsLeft--;
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (turnsLeft > 0 || !owner.isEnhanced()) {
                return;
            }
            boolean killOrBreak = targets.stream().anyMatch(t -> t.isDeath()
                    || t instanceof Enemy e && e.isBroken());
            if (type == SkillType.COMMON || type == SkillType.SKILL) {
                if (killOrBreak) {
                    turnsLeft = cooldown;
                    battle.advanceByPercent(owner, 1.0);
                    IO.println("  [星魂] " + owner.getName() + " gains an extra turn!");
                }
            }
        }
    }

    /** 星魂4 我会看见，飞萤之火: 完全燃烧状态下, 效果抵抗提高50%。 */
    static class FireflyE4 implements Trace {
        private final double resistance;

        FireflyE4(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 绽放在终竟的明天: 完全燃烧状态下火属性抗性穿透提高20%, 弱点击破效率提高50%。 */
    static class FireflyE6 implements Trace {
        private final double pen;
        private final double efficiency;

        FireflyE6(double pen, double efficiency) {
            this.pen = pen;
            this.efficiency = efficiency;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(efficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }
}
