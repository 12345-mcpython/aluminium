package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
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
 * 大丽花 (The Dahlia, cid 1321) — 火属性 虚无.
 */
public final class DahliaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1321;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new DahliaFuneral(param(byId, 1321101, 0, 0.24), intParam(byId, 1321101, 1, 1),
                        param(byId, 1321101, 2, 0.5), intParam(byId, 1321101, 3, 3)),
                new DahliaMourning(intParam(byId, 1321102, 0, 2)),
                new DahliaRenewal(param(byId, 1321103, 0, 0.5), param(byId, 1321103, 1, 0.1),
                        param(byId, 1321103, 2, 0.3), intParam(byId, 1321103, 3, 2),
                        param(byId, 1321103, 4, 20)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new DahliaE1(param(e, 0, 0.25), param(e, 1, 10), param(e, 2, 300), param(e, 3, 0.4));
            case 2 -> new DahliaE2(param(e, 0, 0.2), intParam(e, 1, 3));
            case 4 -> new DahliaE4(param(e, 0, 0.12), intParam(e, 1, 2), intParam(e, 2, 5));
            case 6 -> new DahliaE6(param(e, 0, 1.5), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> DahliaKit::dahliaSkill;
            case 3 -> DahliaKit::dahliaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 开启结界#2回合, 我方全体弱点击破效率提高#3, 随后对目标及相邻目标造成#1%伤害。 */
    static void dahliaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        double efficiency = ctx.param(2, 0.5);
        CanHit main = targets.getFirst();
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("结界·破", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(efficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, multiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns): party break efficiency +"
                + String.format("%.0f", efficiency * 100) + "%");
    }

    /** 终结技: 使敌方全体陷入【败谢】#2回合 (防御力降低#3), 并造成#1%伤害 (全体均分). */
    static void dahliaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 4);
        double defDown = ctx.param(2, 0.08);
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("败谢");
            Buff debuff = new Buff("败谢", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " withers the enemies (败谢, DEF -"
                + String.format("%.0f", defDown * 100) + "%)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 又一场葬礼: 进入战斗时, 使其他角色的击破特攻提高 (等同于大丽花24%击破特攻+50%), 持续1回合。 */
    static class DahliaFuneral implements Trace {
        private final double beRatio;
        private final int turns;
        private final double flat;
        private final int retriggerTurns;

        DahliaFuneral(double beRatio, int turns, double flat, int retriggerTurns) {
            this.beRatio = beRatio;
            this.turns = turns;
            this.flat = flat;
            this.retriggerTurns = retriggerTurns;
        }

        @Override
        public String getName() {
            return "又一场葬礼";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double bonus = be * beRatio + flat;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally != owner) {
                    Buff buff = new Buff("又一场葬礼", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
            IO.println("  [行迹] 又一场葬礼: allies break effect +" + String.format("%.0f", bonus * 100) + "%");
        }
    }

    /** 致哀，故人: 施放天赋的追加攻击时, 为我方恢复1个战技点 (每2次可触发1次)。 */
    static class DahliaMourning implements Trace {
        private final int interval;
        private int followUps = 0;

        DahliaMourning(int interval) {
            this.interval = interval;
        }

        @Override
        public String getName() {
            return "致哀，故人";
        }
    }

    /** 弃旧，恋新: 我方目标为敌方目标添加弱点时, 速度提高30%持续2回合。 */
    static class DahliaRenewal implements Trace {
        private final double energyCapRatio;
        private final double energyRatio;
        private final double speed;
        private final int turns;
        private final double fixedToughness;

        DahliaRenewal(double energyCapRatio, double energyRatio, double speed, int turns, double fixedToughness) {
            this.energyCapRatio = energyCapRatio;
            this.energyRatio = energyRatio;
            this.speed = speed;
            this.turns = turns;
            this.fixedToughness = fixedToughness;
        }

        @Override
        public String getName() {
            return "弃旧，恋新";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 当一朵花含苞待放: 天赋的【共舞者】超击破伤害倍率对我方全体生效。 */
    static class DahliaE1 implements Trace {
        private final double fixedRatio;
        private final double minFixed;
        private final double maxFixed;
        private final double extraRatio;

        DahliaE1(double fixedRatio, double minFixed, double maxFixed, double extraRatio) {
            this.fixedRatio = fixedRatio;
            this.minFixed = minFixed;
            this.maxFixed = maxFixed;
            this.extraRatio = extraRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 新生，鲜灵，爱怜: 大丽花在场时, 敌方全体全属性抗性降低20%。 */
    static class DahliaE2 implements Trace {
        private final double resDown;
        private final int turns;

        DahliaE2(double resDown, int turns) {
            this.resDown = resDown;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂2", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂4 可惜花蕊为虫剥蚀: 天赋追加攻击伤害次数+5, 施放时使敌方全体受到的伤害提高12%持续2回合。 */
    static class DahliaE4 implements Trace {
        private final double vuln;
        private final int turns;
        private final int extraHits;

        DahliaE4(double vuln, int turns, int extraHits) {
            this.vuln = vuln;
            this.turns = turns;
            this.extraHits = extraHits;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 然而它总是，致命美丽: 【共舞者】的击破特攻提高150%, 施放天赋追加攻击时共舞者行动提前20%。 */
    static class DahliaE6 implements Trace {
        private final double beBonus;
        private final double advance;

        DahliaE6(double beBonus, double advance) {
            this.beBonus = beBonus;
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
