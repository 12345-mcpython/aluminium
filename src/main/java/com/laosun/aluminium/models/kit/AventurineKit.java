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
 * 砂金 (Aventurine, cid 1304) — 虚数属性 存护.
 */
public final class AventurineKit implements CharacterKit {

    @Override
    public int cid() {
        return 1304;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AventurineLeverage(param(byId, 1304101, 0, 0.02), param(byId, 1304101, 1, 0.48),
                        param(byId, 1304101, 2, 1600)),
                new AventurineHotHands(intParam(byId, 1304102, 0, 3), param(byId, 1304102, 1, 1.0)),
                new AventurineBingo(param(byId, 1304103, 0, 0.072), param(byId, 1304103, 1, 96),
                        intParam(byId, 1304103, 2, 3), param(byId, 1304103, 3, 0.072),
                        param(byId, 1304103, 4, 96)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AventurineE1(param(e, 0, 0.2), param(e, 1, 1.0), intParam(e, 2, 3));
            case 2 -> new AventurineE2(param(e, 0, 1.2), param(e, 1, 0.12), intParam(e, 2, 3));
            case 4 -> new AventurineE4(param(e, 0, 0.4), intParam(e, 1, 2), intParam(e, 2, 3));
            case 6 -> new AventurineE6(param(e, 0, 0.5), param(e, 1, 1.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 1 -> AventurineKit::aventurineBasic;
            case 2 -> AventurineKit::aventurineSkill;
            case 3 -> AventurineKit::aventurineUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 普攻: 造成等同于砂金#1防御力的虚数属性伤害. */
    static void aventurineBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double def = user.getAttribute(AttributeType.DEFENCE) != null
                ? user.getAttribute(AttributeType.DEFENCE).get() : 0;
        battle.dealAttackDamageBase(user, target, def * ctx.firstParam(),
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                        user.getElement()));
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    /** 战技: 为我方全体提供等同于砂金#1防御力+#2伤害的护盾【坚垣筹码】, 持续#3回合. */
    static void aventurineSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double defRatio = ctx.firstParam();
        double flat = ctx.param(1, 80);
        int turns = ctx.intParam(2, 3);
        double def = user.getAttribute(AttributeType.DEFENCE) != null
                ? user.getAttribute(AttributeType.DEFENCE).get() : 0;
        double shield = def * defRatio + flat;
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            battle.applyShield(ally, shield, user);
            ally.setShieldTurns(turns);
        }
        IO.println("  " + user.getName() + " shields the party for "
                + String.format("%.0f", shield) + " (坚垣筹码)");
    }

    /** 终结技: 随机获得1到#1点【盲注】, 使目标陷入【惊惶】#4回合, 造成#2防御力虚数伤害. */
    static void aventurineUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        int maxBet = ctx.intParam(0, 7);
        double defRatio = ctx.param(1, 1.62);
        double critTaken = ctx.param(2, 0.09);
        int turns = ctx.intParam(3, 3);
        AventurineKit.addBlindBet(user, 1 + (int) (Math.random() * maxBet));
        double def = user.getAttribute(AttributeType.DEFENCE) != null
                ? user.getAttribute(AttributeType.DEFENCE).get() : 0;
        battle.dealAttackDamageBase(user, target, def * defRatio,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA,
                        user.getElement()));
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (!target.isDeath()) {
            Buff debuff = new Buff("惊惶", Buff.Category.DEBUFF, user, target, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(critTaken,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, debuff);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 杠杆: 防御力>1600时, 每超过100点防御力使暴击率提高2%, 最多提高48%。 */
    static class AventurineLeverage implements Trace {
        private final double per100;
        private final double cap;
        private final double threshold;

        AventurineLeverage(double per100, double cap, double threshold) {
            this.per100 = per100;
            this.cap = cap;
            this.threshold = threshold;
        }

        @Override
        public String getName() {
            return "杠杆";
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
            double def = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
            double crit = def > threshold ? Math.min(cap, (def - threshold) / 100 * per100) : 0;
            owner.removeBuff("杠杆");
            Buff buff = new Buff("杠杆", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 热手: 战斗开始时, 为我方全体提供等同于战技护盾量#2倍的护盾, 持续#1回合。 */
    static class AventurineHotHands implements Trace {
        private final int turns;
        private final double ratio;

        AventurineHotHands(int turns, double ratio) {
            this.turns = turns;
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "热手";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double def = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
            double shield = def * 0.16 * ratio + 80 * ratio;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                battle.applyShield(ally, shield, owner);
                ally.setShieldTurns(turns);
            }
            IO.println("  [行迹] 热手: party shield " + String.format("%.0f", shield));
        }
    }

    /** 宾果！: 持有护盾的队友发动追加攻击后积攒1点【盲注】(每回合最多3次);
     *  砂金发动天赋追加攻击后为我方全体提供护盾。 */
    static class AventurineBingo implements Trace {
        private final double defRatio;
        private final double flat;
        private final int maxTriggers;
        private final double extraDefRatio;
        private final double extraFlat;
        private int triggers = 0;

        AventurineBingo(double defRatio, double flat, int maxTriggers,
                        double extraDefRatio, double extraFlat) {
            this.defRatio = defRatio;
            this.flat = flat;
            this.maxTriggers = maxTriggers;
            this.extraDefRatio = extraDefRatio;
            this.extraFlat = extraFlat;
        }

        @Override
        public String getName() {
            return "宾果！";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            triggers = 0;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner || triggers >= maxTriggers) {
                return;
            }
            // 代理: 持有护盾的队友施放攻击后, 为砂金积攒1点【盲注】.
            if (actor.getShield() > 0 && type != SkillType.ULTRA) {
                triggers++;
                AventurineKit.addBlindBet(owner, 1);
                IO.println("  [行迹] 宾果！: " + owner.getName() + " gains 1 盲注 ("
                        + AventurineKit.blindBet(owner) + "/7)");
                if (AventurineKit.blindBet(owner) >= 7) {
                    followUp(battle, owner);
                }
            }
        }

        /** 天赋代理: 消耗7点【盲注】发动7段追加攻击 (每段#3防御力伤害). */
        void followUp(Battle battle, Character owner) {
            int segments = 7;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof AventurineE4 e4) {
                    segments += e4.extraSegments;
                }
            }
            AventurineKit.consumeBlindBet(owner, 7);
            double def = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
            for (int i = 0; i < segments; i++) {
                List<Enemy> alive = battle.getAliveEnemies();
                if (alive.isEmpty()) {
                    return;
                }
                battle.dealAttackDamageBase(owner, alive.get((int) (Math.random() * alive.size())),
                        def * 0.125,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                Element.IMAGINARY));
            }
            // 天赋追加攻击后为我方全体提供护盾.
            double shield = def * defRatio + flat;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                battle.applyShield(ally, shield, owner);
                ally.setShieldTurns(3);
            }
            IO.println("  [行迹] 宾果！: " + owner.getName() + " fires " + segments
                    + "-segment follow-up and shields the party!");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 囚徒博弈: 持有护盾的我方目标暴击伤害提高20%; 终结技后为我方全体提供护盾。 */
    static class AventurineE1 implements Trace {
        private final double cdmg;
        private final double shieldRatio;
        private final int turns;

        AventurineE1(double cdmg, double shieldRatio, int turns) {
            this.cdmg = cdmg;
            this.shieldRatio = shieldRatio;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            double def = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
            double shield = (def * 0.16 + 80) * shieldRatio;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                battle.applyShield(ally, shield, owner);
                ally.setShieldTurns(turns);
            }
        }
    }

    /** 星魂2 有限理性: 施放普攻时使目标全属性抗性降低12%, 持续3回合。 */
    static class AventurineE2 implements Trace {
        private final double ignored;
        private final double resDown;
        private final int turns;

        AventurineE2(double ignored, double resDown, int turns) {
            this.ignored = ignored;
            this.resDown = resDown;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || targets.isEmpty()) {
                return;
            }
            CanHit target = targets.getFirst();
            if (!target.isDeath() && battle.checkEffectHit(owner, target, 1.0)) {
                Buff debuff = new Buff("星魂2", Buff.Category.DEBUFF, owner, target, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(target, debuff);
            }
        }
    }

    /** 星魂4 意外绞刑: 触发天赋追加攻击时, 防御力提高40%持续2回合, 追加攻击段数+3。 */
    static class AventurineE4 implements Trace {
        private final double defBonus;
        private final int defTurns;
        private final int extraSegments;

        AventurineE4(double defBonus, int defTurns, int extraSegments) {
            this.defBonus = defBonus;
            this.defTurns = defTurns;
            this.extraSegments = extraSegments;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 猎鹿游戏: 每有1个队友持有护盾, 砂金造成的伤害提高50%, 最高150%。 */
    static class AventurineE6 implements Trace {
        private final double perShielded;
        private final double cap;

        AventurineE6(double perShielded, double cap) {
            this.perShielded = perShielded;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            long shielded = battle.getAlivePlayerUnits().stream().filter(u -> u.getShield() > 0).count();
            return 1 + Math.min(cap, shielded * perShielded);
        }
    }

    // ─── 【盲注】计数器 (天赋 枪口以右 代理) ─────────────────────────────

    private static final java.util.Map<CanHit, Integer> BLIND_BET = new java.util.concurrent.ConcurrentHashMap<>();

    static int blindBet(CanHit owner) {
        return BLIND_BET.getOrDefault(owner, 0);
    }

    static void addBlindBet(CanHit owner, int amount) {
        BLIND_BET.put(owner, Math.min(10, blindBet(owner) + amount));
    }

    static void consumeBlindBet(CanHit owner, int amount) {
        BLIND_BET.put(owner, Math.max(0, blindBet(owner) - amount));
    }
}
