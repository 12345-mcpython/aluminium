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
 * 遐蝶 (Castorice, cid 1407) — 量子属性 记忆 (忆灵: 死龙).
 * 终结技 (Summon) 由通用执行器处理 (引擎自动召唤忆灵死龙).
 */
public final class CastoriceKit implements CharacterKit {

    @Override
    public int cid() {
        return 1407;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new CastoriceTide(param(byId, 1407101, 0, 1), param(byId, 1407101, 1, 0.12)),
                new CastoriceTorch(param(byId, 1407102, 0, 0.5), param(byId, 1407102, 1, 0.4),
                        param(byId, 1407102, 2, 1)),
                new CastoriceWestWind(param(byId, 1407103, 0, 0.3), intParam(byId, 1407103, 1, 6)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new CastoriceE1(param(e, 0, 0.8), param(e, 1, 0.5), param(e, 2, 1.2), param(e, 3, 1.4));
            case 2 -> new CastoriceE2(intParam(e, 0, 2), intParam(e, 1, 2), param(e, 2, 0.3));
            case 4 -> new CastoriceE4(param(e, 0, 0.2));
            case 6 -> new CastoriceE6(param(e, 0, 0.2), intParam(e, 1, 3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> CastoriceKit::castoriceSkill;
            case 9 -> CastoriceKit::castoriceEnhancedSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 消耗我方全体#1当前生命值, 对主目标造成#2生命上限伤害, 相邻目标#3伤害. */
    static void castoriceSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double costRatio = ctx.firstParam();
        double mainMult = ctx.param(1, 0.25);
        double sideMult = ctx.param(2, 0.15);
        // 消耗我方全体当前生命值 (死龙除外).
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != user && !ally.getName().contains("死龙")) {
                double cost = ally.getCurrentHp() * costRatio;
                if (ally.getCurrentHp() > cost) {
                    ally.takeDamage(cost);
                } else if (ally.getCurrentHp() > 1) {
                    ally.takeDamage(ally.getCurrentHp() - 1);
                }
            }
        }
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * mainMult,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.QUANTUM));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * sideMult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.QUANTUM));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 强化战技 骸爪: 消耗我方全体#1当前生命值, 对敌方全体造成#2/#3生命上限伤害. */
    static void castoriceEnhancedSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double costRatio = ctx.firstParam();
        double mult1 = ctx.param(1, 0.15);
        double mult2 = ctx.param(2, 0.25);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != user && !ally.getName().contains("死龙")) {
                double cost = ally.getCurrentHp() * costRatio;
                if (ally.getCurrentHp() > cost) {
                    ally.takeDamage(cost);
                } else if (ally.getCurrentHp() > 1) {
                    ally.takeDamage(ally.getCurrentHp() - 1);
                }
            }
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * (mult1 + mult2),
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.QUANTUM));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 收容的暗潮: 除死龙以外的我方目标接受治疗后, 将100%的治疗数值转化为【新蕊】。 */
    static class CastoriceTide implements Trace {
        private final double ratio;
        private final double capRatio;

        CastoriceTide(double ratio, double capRatio) {
            this.ratio = ratio;
            this.capRatio = capRatio;
        }

        @Override
        public String getName() {
            return "收容的暗潮";
        }
    }

    /** 倒置的火炬: 遐蝶生命值≥50%时速度提高40%; 死龙对全场造成致命伤害后速度提高100%持续1回合。 */
    static class CastoriceTorch implements Trace {
        private final double hpThreshold;
        private final double speed;
        private final double dragonSpeed;

        CastoriceTorch(double hpThreshold, double speed, double dragonSpeed) {
            this.hpThreshold = hpThreshold;
            this.speed = speed;
            this.dragonSpeed = dragonSpeed;
        }

        @Override
        public String getName() {
            return "倒置的火炬";
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
            boolean active = owner.hasBuffNamed("倒置的火炬");
            boolean shouldBe = owner.getHpPercent() >= hpThreshold;
            if (active && !shouldBe) {
                owner.removeBuff("倒置的火炬");
            } else if (!active && shouldBe) {
                Buff buff = new Buff("倒置的火炬", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    /** 西风的驻足: 死龙每次施放【燎尽黯泽的焰息】时, 造成的伤害提高30%, 最多6层。 */
    static class CastoriceWestWind implements Trace {
        private final double perStack;
        private final int maxStacks;
        private int stacks = 0;

        CastoriceWestWind(double perStack, int maxStacks) {
            this.perStack = perStack;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "西风的驻足";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            stacks = 0;
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return 1 + Math.min(maxStacks, stacks) * perStack;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 雪地的圣女，付记忆入殓: 敌方生命值≤50%时, 死龙技能伤害提高120%。 */
    static class CastoriceE1 implements Trace {
        private final double lowHp;
        private final double lowerHp;
        private final double multLow;
        private final double multLower;

        CastoriceE1(double lowHp, double lowerHp, double multLow, double multLower) {
            this.lowHp = lowHp;
            this.lowerHp = lowerHp;
            this.multLow = multLow;
            this.multLower = multLower;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 以扑翼繁花加冕: 召唤忆灵死龙后, 遐蝶获得2层【炽意】并使自身行动提前100%。 */
    static class CastoriceE2 implements Trace {
        private final int stacks;
        private final int maxStacks;
        private final double newBloomRatio;

        CastoriceE2(int stacks, int maxStacks, double newBloomRatio) {
            this.stacks = stacks;
            this.maxStacks = maxStacks;
            this.newBloomRatio = newBloomRatio;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 拥悲怜哀歌安眠: 遐蝶在场时, 我方全体受到治疗时的回复量提高20%。 */
    static class CastoriceE4 implements Trace {
        private final double healBonus;

        CastoriceE4(double healBonus) {
            this.healBonus = healBonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.HEAL_TAKEN_RATIO, DoubleValue.Modifier.pure(healBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 待流年奔涌破茧: 遐蝶与死龙造成伤害时量子属性抗性穿透提高20%。 */
    static class CastoriceE6 implements Trace {
        private final double pen;
        private final int extraBounces;

        CastoriceE6(double pen, int extraBounces) {
            this.pen = pen;
            this.extraBounces = extraBounces;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }
}
