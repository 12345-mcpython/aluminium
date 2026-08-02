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
 * 符玄 (Fu Xuan, cid 1208) — 量子属性 存护.
 *
 * <p>伤害技能按 生命上限 结算; 战技开启【穷观阵】结界 (以符玄身上的同名 Buff 代理),
 * 并使我方全体获得【鉴知】。天赋 避厄/自愈 与复活代理在行迹中实现。
 */
public final class FuXuanKit implements CharacterKit {

    @Override
    public int cid() {
        return 1208;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new FuXuanAstrolabe(intParam(byId, 1208101, 0, 20)),
                new FuXuanHealing(param(byId, 1208102, 0, 0.05), param(byId, 1208102, 1, 133)),
                new FuXuanResist());
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new FuXuanE1(param(e, 0, 0.3));
            case 2 -> new FuXuanE2(param(e, 0, 0.7));
            case 4 -> new FuXuanE4(intParam(e, 0, 5));
            case 6 -> new FuXuanE6(param(e, 0, 0.02), param(e, 1, 1.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> FuXuanKit::fuXuanSkill;
            case 3 -> FuXuanKit::fuXuanUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 开启【穷观阵】#3回合; 处于【穷观阵】的我方全体获得【鉴知】:
     *  生命上限提高 (数值等同于符玄生命上限的#4), 暴击率提高#5。 */
    static void fuXuanSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int turns = ctx.intParam(2, 3);
        double hpRatio = ctx.param(3, 0.03);
        double critChance = ctx.param(4, 0.06);
        user.removeBuff("穷观阵");
        Buff matrix = new Buff("穷观阵", Buff.Category.BUFF, user, user, turns);
        battle.applyBuff(user, matrix);
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            ally.removeBuff("鉴知");
            Buff insight = new Buff("鉴知", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.pure(user.getMaxHp() * hpRatio,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critChance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, insight);
        }
        IO.println("  " + user.getName() + " opens 【穷观阵】 for " + turns
                + " turns: party gains 【鉴知】 (max HP +" + String.format("%.1f%%", hpRatio * 100)
                + " of Fu Xuan HP, crit +" + String.format("%.0f%%", critChance * 100) + ")");
    }

    /** 终结技: 对敌方全体造成等同于符玄#1生命上限的量子属性伤害。 */
    static void fuXuanUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * multiplier,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.QUANTUM));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 太乙式盘: 【穷观阵】开启时，符玄施放战技将额外恢复#1点能量。
     * 兼作天赋 乾清坤夷，否极泰来 的代理: 为我方全体附加【避厄】(受到的伤害降低10%);
     * 当前生命值≤50%时回复已损失生命值的80% (初始1次触发, 终结技增加1次, 最多2次)。
     */
    static class FuXuanAstrolabe implements Trace {
        private final double energy;
        private int healCharges = 1;

        FuXuanAstrolabe(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "太乙式盘";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("避厄", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-0.1,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] " + owner.getName() + ": party gains 【避厄】 (-10% damage taken)");
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL && owner.hasBuffNamed("穷观阵")) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (穷观阵, 太乙式盘)");
            }
            if (type == SkillType.ULTRA) {
                healCharges = Math.min(2, healCharges + 1);
                IO.println("  [行迹] " + owner.getName() + " gains a talent heal charge ("
                        + healCharges + "/2)");
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (owner.isDeath() || healCharges <= 0 || owner.getHpPercent() > 0.5) {
                return;
            }
            healCharges--;
            double heal = (owner.getMaxHp() - owner.getCurrentHp()) * 0.8;
            owner.heal(heal);
            IO.println("  [行迹] " + owner.getName() + " recovers "
                    + String.format("%.0f", heal) + " HP (否极泰来, charges left " + healCharges + ")");
        }
    }

    /** 遁甲星舆: 施放终结技时为我方其他目标回复等同于符玄#1生命上限+#2的生命值。 */
    static class FuXuanHealing implements Trace {
        private final double hpRatio;
        private final double flat;

        FuXuanHealing(double hpRatio, double flat) {
            this.hpRatio = hpRatio;
            this.flat = flat;
        }

        @Override
        public String getName() {
            return "遁甲星舆";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally != owner) {
                    battle.healTarget(owner, ally, owner.getMaxHp() * hpRatio + flat);
                }
            }
        }
    }

    /**
     * 六壬兆堪: 【穷观阵】开启时，若敌方目标对我方施加了控制类负面状态，则我方全体抵抗
     * 本次行动中敌方目标施加的所有控制类负面状态，触发1次; 再次开启【穷观阵】后刷新。
     * (引擎无法拦截单次控制施加, 代理为每次开启【穷观阵】时为我方全体附加1回合
     * 效果抵抗100%的标记。)
     */
    static class FuXuanResist implements Trace {
        @Override
        public String getName() {
            return "六壬兆堪";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("六壬兆堪");
                Buff buff = new Buff("六壬兆堪", Buff.Category.BUFF, owner, ally, 1)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(1.0,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] " + owner.getName() + ": party resists control debuffs (六壬兆堪)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 司危: 【鉴知】使暴击伤害提高30%。 */
    static class FuXuanE1 implements Trace {
        private final double critDamage;

        FuXuanE1(double critDamage) {
            this.critDamage = critDamage;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (!ally.hasBuffNamed("鉴知")) {
                    continue;
                }
                ally.removeBuff("鉴知·暴伤");
                Buff buff = new Buff("鉴知·暴伤", Buff.Category.BUFF, owner, ally, 3)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [星魂] " + owner.getName() + ": 鉴知 also grants crit damage +"
                    + String.format("%.0f%%", critDamage * 100));
        }
    }

    /**
     * 星魂2 柔兆: 【穷观阵】开启时，若我方目标受到致命伤害，则不会陷入无法战斗状态，
     * 并立即回复其生命上限70%的生命值。单场战斗可触发1次。
     * (引擎只对符玄自身提供受击钩子, 故代理为符玄本人的致命伤免疫。)
     */
    static class FuXuanE2 implements Trace {
        private final double reviveRatio;
        private boolean used = false;

        FuXuanE2(double reviveRatio) {
            this.reviveRatio = reviveRatio;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (used || !owner.hasBuffNamed("穷观阵") || !owner.isDeath()) {
                return;
            }
            used = true;
            owner.revive(owner.getMaxHp() * reviveRatio);
            IO.println("  [星魂] " + owner.getName() + " survives the fatal blow (柔兆)!");
        }
    }

    /**
     * 星魂4 格泽: 处于【穷观阵】的我方其他目标受到攻击后，符玄恢复5点能量。
     * (引擎只对符玄自身提供受击钩子, 故代理为符玄本人受击回能。)
     */
    static class FuXuanE4 implements Trace {
        private final double energy;

        FuXuanE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.hasBuffNamed("穷观阵")) {
                owner.gainEnergy(energy);
                IO.println("  [星魂] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (穷观阵, 格泽)");
            }
        }
    }

    /**
     * 星魂6 种陵: 【穷观阵】开启时累计我方全体已损失生命值; 符玄施放终结技造成的伤害
     * 提高, 数值等同于累计已损失生命值的#1, 最高不超过符玄生命上限的#2;
     * 施放终结技后清空。 (引擎只对符玄自身提供受击钩子, 故只累计符玄本人已损失生命值。)
     */
    static class FuXuanE6 implements Trace {
        private final double ratio;
        private final double capRatio;
        private double lostHp = 0;

        FuXuanE6(double ratio, double capRatio) {
            this.ratio = ratio;
            this.capRatio = capRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.hasBuffNamed("穷观阵")) {
                lostHp = Math.min(owner.getMaxHp() * capRatio, lostHp + damage);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                lostHp = 0;
            }
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            if (type != SkillType.ULTRA || lostHp <= 0) {
                return 1.0;
            }
            // 伤害提高数值 = 累计已损失生命值 × #1 (按终结技基础 60%生命上限 折算为倍率).
            return 1 + lostHp * ratio / owner.getMaxHp();
        }
    }
}
