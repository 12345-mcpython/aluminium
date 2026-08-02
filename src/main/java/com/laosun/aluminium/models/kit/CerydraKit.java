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
 * 刻律德菈 (Cerydra, cid 1412) — 风属性 同谐.
 */
public final class CerydraKit implements CharacterKit {

    @Override
    public int cid() {
        return 1412;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new CerydraComer(param(byId, 1412101, 0, 2000), param(byId, 1412101, 1, 100),
                        param(byId, 1412101, 2, 0.18), param(byId, 1412101, 3, 3.6)),
                new CerydraSeer(param(byId, 1412102, 0, 1), intParam(byId, 1412102, 1, 1)),
                new CerydraConqueror(param(byId, 1412103, 0, 5), param(byId, 1412103, 1, 20),
                        intParam(byId, 1412103, 2, 3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new CerydraE1(param(e, 0, 0.16), param(e, 1, 0.2), intParam(e, 2, 2));
            case 2 -> new CerydraE2(param(e, 0, 0.4), param(e, 1, 1.6));
            case 4 -> new CerydraE4(param(e, 0, 2.4));
            case 6 -> new CerydraE6(param(e, 0, 0.2), param(e, 1, 3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> CerydraKit::cerydraSkill;
            case 3 -> CerydraKit::cerydraUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使指定我方单体获得【军功】, 刻律德菈获得#2点充能 (上限#3, 达#4点升级为【爵位】). */
    static void cerydraSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double nobleCdmg = ctx.firstParam();
        int chargeGain = ctx.intParam(1, 1);
        int chargeCap = ctx.intParam(2, 8);
        int upgradeAt = ctx.intParam(3, 6);
        double noblePen = ctx.param(4, 0.08);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("军功");
                ally.removeBuff("爵位");
            }
            Buff mark = new Buff("军功", Buff.Category.BUFF, user, target, -1);
            battle.applyBuff(target, mark);
            CerydraKit.addCharge(user, chargeGain);
            if (CerydraKit.charge(user) >= upgradeAt) {
                target.removeBuff("军功");
                Buff noble = new Buff("爵位", Buff.Category.BUFF, user, target, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(nobleCdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(noblePen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(target, noble);
                IO.println("  " + target.getName() + " is promoted to 爵位!");
            }
            IO.println("  " + target.getName() + " gains 军功, charge "
                    + CerydraKit.charge(user) + "/" + chargeCap);
        }
    }

    /** 终结技: 获得#2点充能, 对敌方全体造成#1%伤害. */
    static void cerydraUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int chargeGain = ctx.intParam(1, 2);
        CerydraKit.addCharge(user, chargeGain);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 来者: 攻击力>2000时, 每超过100点攻击力使暴击伤害提高18%, 最多提高360%。 */
    static class CerydraComer implements Trace {
        private final double threshold;
        private final double step;
        private final double perStep;
        private final double cap;

        CerydraComer(double threshold, double step, double perStep, double cap) {
            this.threshold = threshold;
            this.step = step;
            this.perStep = perStep;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "来者";
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
            double bonus = atk > threshold ? Math.min(cap, Math.floor((atk - threshold) / step) * perStep) : 0;
            owner.removeBuff("来者");
            Buff buff = new Buff("来者", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 见者: 暴击率提高100%。 */
    static class CerydraSeer implements Trace {
        private final double crit;
        private final int charge;

        CerydraSeer(double crit, int charge) {
            this.crit = crit;
            this.charge = charge;
        }

        @Override
        public String getName() {
            return "见者";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("见者", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 征服者: 施放战技时, 使自身和持有【军功】的队友速度提高20点持续3回合。 */
    static class CerydraConqueror implements Trace {
        private final double energy;
        private final double speed;
        private final int turns;

        CerydraConqueror(double energy, double speed, int turns) {
            this.energy = energy;
            this.speed = speed;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "征服者";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally == owner || ally.hasBuffNamed("军功") || ally.hasBuffNamed("爵位")) {
                    Buff buff = new Buff("征服者", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speed,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 夺走一切王冠: 持有【军功】的角色造成伤害时无视16%防御力。 */
    static class CerydraE1 implements Trace {
        private final double defIgnore;
        private final double nobleDefIgnore;
        private final int energy;

        CerydraE1(double defIgnore, double nobleDefIgnore, int energy) {
            this.defIgnore = defIgnore;
            this.nobleDefIgnore = nobleDefIgnore;
            this.energy = energy;
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

    /** 星魂2 凝聚万众宏愿: 持有【军功】的角色造成的伤害提高40%。 */
    static class CerydraE2 implements Trace {
        private final double markDmg;
        private final double selfDmg;

        CerydraE2(double markDmg, double selfDmg) {
            this.markDmg = markDmg;
            this.selfDmg = selfDmg;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 重塑天地人间: 终结技的伤害倍率提高240%。 */
    static class CerydraE4 implements Trace {
        private final double ultBonus;

        CerydraE4(double ultBonus) {
            this.ultBonus = ultBonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + ultBonus : 1.0;
        }
    }

    /** 星魂6 征途只在，星辰大海: 持有【军功】的角色全属性抗性穿透提高20%。 */
    static class CerydraE6 implements Trace {
        private final double pen;
        private final double extraRatio;

        CerydraE6(double pen, double extraRatio) {
            this.pen = pen;
            this.extraRatio = extraRatio;
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

    // ─── 【充能】代理 (天赋 荣光属于凯撒) ───────────────────────────────

    private static final java.util.Map<CanHit, Integer> CHARGE = new java.util.concurrent.ConcurrentHashMap<>();

    static int charge(CanHit owner) {
        return CHARGE.getOrDefault(owner, 0);
    }

    static void addCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.min(8, charge(owner) + amount));
    }

    /** 天赋代理: 持有【军功】的角色施放攻击后, 刻律德菈额外造成1次30%攻击力附加伤害。 */
    static void meritExtraDamage(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (targets == null) {
            return;
        }
        for (CanHit target : targets) {
            if (!target.isDeath() && target.getCamp() != owner.getCamp()) {
                battle.dealAttackDamage(owner, target, 0.3, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.WIND));
                IO.println("  [天赋] " + owner.getName() + " deals extra damage (荣光属于凯撒)");
            }
        }
    }
}
