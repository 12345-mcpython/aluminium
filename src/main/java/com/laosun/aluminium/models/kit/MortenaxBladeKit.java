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
 * 千冶•刃 (Mortenax Blade, cid 1507) — 火属性 毁灭.
 */
public final class MortenaxBladeKit implements CharacterKit {

    @Override
    public int cid() {
        return 1507;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MortenaxBone(param(byId, 1507101, 0, 0.75), intParam(byId, 1507101, 1, 80)),
                new MortenaxSoul(param(byId, 1507102, 0, 10), param(byId, 1507102, 1, 0.5),
                        param(byId, 1507102, 2, 0.5)),
                new MortenaxHeart(param(byId, 1507103, 0, 0.5), param(byId, 1507103, 1, 0.75),
                        param(byId, 1507103, 2, 0.75)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MortenaxE1(param(e, 0, 0.2), param(e, 1, 0.15));
            case 2 -> new MortenaxE2(param(e, 0, 0.75), intParam(e, 1, 7));
            case 4 -> new MortenaxE4(param(e, 0, 0.5));
            case 6 -> new MortenaxE6(param(e, 0, 1.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 1 -> MortenaxBladeKit::mortenaxBasic;
            case 2 -> MortenaxBladeKit::mortenaxSkill;
            case 3 -> MortenaxBladeKit::mortenaxUlt;
            case 8 -> MortenaxBladeKit::mortenaxEnhancedBasic;
            case 14 -> MortenaxBladeKit::mortenaxFinale;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 普攻: 造成#1生命上限伤害, 并使目标陷入嘲讽1回合. */
    static void mortenaxBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * multiplier,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.FIRE));
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (!main.isDeath()) {
            battle.applyBuff(main, new Buff("嘲讽", Buff.Category.DEBUFF, user, main, 1));
        }
    }

    /** 战技: 消耗#4生命上限的生命值, 对敌方全体造成#1生命上限伤害并额外造成#2次#3伤害. */
    static void mortenaxSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int extraHits = ctx.intParam(1, 4);
        double hitMult = ctx.param(2, 0.12);
        double hpCostRatio = ctx.param(3, 0.1);
        double cost = user.getMaxHp() * hpCostRatio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * multiplier,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.FIRE));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        for (int i = 0; i < extraHits; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamageBase(user, alive.get((int) (Math.random() * alive.size())),
                    user.getMaxHp() * hitMult,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.FIRE));
        }
        IO.println("  " + user.getName() + " 刃下归葬: HP cost + AoE + " + extraHits + " extra hits");
    }

    /** 终结技: 使敌方全体陷入【煞火缠身】(#7减防, #4易伤, #8回合), 消耗#1生命上限展开结界. */
    static void mortenaxUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double hpCostRatio = ctx.firstParam();
        double crit = ctx.param(1, 0.2);
        double cdmg = ctx.param(2, 0.3);
        double vuln = ctx.param(3, 0.3);
        double defDown = ctx.param(6, 0.2);
        int turns = ctx.intParam(7, 2);
        double cost = user.getMaxHp() * hpCostRatio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("煞火缠身");
            Buff debuff = new Buff("煞火缠身", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF))
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
        }
        user.removeBuff("无量忿怒");
        Buff buff = new Buff("无量忿怒", Buff.Category.BUFF, user, user, -1)
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                        DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " 骸骨当炉: 煞火缠身 + 无量忿怒!");
    }

    /** 强化普攻 淬锋: 造成#1生命上限伤害, 使目标陷入嘲讽1回合. */
    static void mortenaxEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * multiplier,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.FIRE));
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (!main.isDeath()) {
            battle.applyBuff(main, new Buff("嘲讽", Buff.Category.DEBUFF, user, main, 1));
        }
    }

    /** 千冶铸一，万劫烬灭: 对敌方全体造成#1生命上限伤害. */
    static void mortenaxFinale(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * multiplier,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.FIRE));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " 千冶铸一，万劫烬灭!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 百炼骨: 战斗开始时或结界解除时, 若能量不足75%则立刻恢复至75%。 */
    static class MortenaxBone implements Trace {
        private final double threshold;
        private final int overflowCap;

        MortenaxBone(double threshold, int overflowCap) {
            this.threshold = threshold;
            this.overflowCap = overflowCap;
        }

        @Override
        public String getName() {
            return "百炼骨";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double max = owner.getMaxEnergy();
            if (owner.getEnergy() < max * threshold) {
                owner.gainEnergy(Math.max(0, max * threshold - owner.getEnergy()));
                IO.println("  [行迹] " + owner.getName() + " energy restored to 75%");
            }
        }
    }

    /** 千锻魂: 结界持续期间, 被敌方攻击的概率提高, 受到的伤害降低50%。 */
    static class MortenaxSoul implements Trace {
        private final double energy;
        private final double reduction;
        private final double healBonus;

        MortenaxSoul(double energy, double reduction, double healBonus) {
            this.energy = energy;
            this.reduction = reduction;
            this.healBonus = healBonus;
        }

        @Override
        public String getName() {
            return "千锻魂";
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return owner.hasBuffNamed("无量忿怒") ? 2.0 : 1.0;
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("千锻魂", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 万淬心: 结界持续期间, 我方目标造成的伤害提高50%。 */
    static class MortenaxHeart implements Trace {
        private final double partyDmg;
        private final double ultDmg;
        private final double selfDmg;

        MortenaxHeart(double partyDmg, double ultDmg, double selfDmg) {
            this.partyDmg = partyDmg;
            this.ultDmg = ultDmg;
            this.selfDmg = selfDmg;
        }

        @Override
        public String getName() {
            return "万淬心";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("万淬心", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyDmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 我殁之前，我仍未成形: 结界持续期间, 使敌方全体全属性抗性降低20%。 */
    static class MortenaxE1 implements Trace {
        private final double resDown;
        private final double delay;

        MortenaxE1(double resDown, double delay) {
            this.resDown = resDown;
            this.delay = delay;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂1", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂2 心若死灰，而炉火未熄: 我方目标造成的追加攻击伤害提高75%。 */
    static class MortenaxE2 implements Trace {
        private final double followUpBonus;
        private final int chargeCap;

        MortenaxE2(double followUpBonus, int chargeCap) {
            this.followUpBonus = followUpBonus;
            this.chargeCap = chargeCap;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 锻打遗恨，后剑骨自成: 【万淬心】使我方目标造成的伤害额外提高50%。 */
    static class MortenaxE4 implements Trace {
        private final double extraDmg;

        MortenaxE4(double extraDmg) {
            this.extraDmg = extraDmg;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(extraDmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 神若当殒，自誓弑寿瘟: 【千冶铸一，万劫烬灭】伤害倍率提高为原倍率的150%。 */
    static class MortenaxE6 implements Trace {
        private final double ultMult;

        MortenaxE6(double ultMult) {
            this.ultMult = ultMult;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + ultMult : 1.0;
        }
    }

    // ─── 【充能】代理 (天赋 因果尽偿) ────────────────────────────────────

    private static final java.util.Map<CanHit, Integer> CHARGE = new java.util.concurrent.ConcurrentHashMap<>();

    static int charge(CanHit owner) {
        return CHARGE.getOrDefault(owner, 0);
    }

    static void addCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.min(9, charge(owner) + amount));
    }

    /** 天赋代理: 结界持续期间, 我方目标每次攻击敌方后使目标陷入【煞火缠身】并获得1点充能. */
    static void emberCharge(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (!owner.hasBuffNamed("无量忿怒") || targets == null) {
            return;
        }
        MortenaxBladeKit.addCharge(owner, 1);
        if (MortenaxBladeKit.charge(owner) >= 9) {
            MortenaxBladeKit.addCharge(owner, -9);
            owner.gainEnergy(15);
            // 额外施放1次战技 (视为追加攻击).
            double mult = 0.36;
            for (Enemy enemy : battle.getAliveEnemies()) {
                battle.dealAttackDamageBase(owner, enemy, owner.getMaxHp() * mult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.FIRE));
            }
            IO.println("  [天赋] 因果尽偿: extra skill as follow-up!");
        }
    }
}
