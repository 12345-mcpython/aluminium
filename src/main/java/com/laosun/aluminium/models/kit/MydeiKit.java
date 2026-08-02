package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
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
 * 万敌 (Mydei, cid 1404) — 虚数属性 毁灭.
 */
public final class MydeiKit implements CharacterKit {

    @Override
    public int cid() {
        return 1404;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MydeiWater(intParam(byId, 1404101, 0, 3)),
                new MydeiUsurper(),
                new MydeiCloak(param(byId, 1404103, 0, 4000), param(byId, 1404103, 1, 4000),
                        param(byId, 1404103, 2, 0.012), param(byId, 1404103, 3, 0.025),
                        param(byId, 1404103, 4, 0.0075)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MydeiE1(param(e, 0, 0.3));
            case 2 -> new MydeiE2(param(e, 0, 0.15), param(e, 1, 0.4), param(e, 2, 40));
            case 4 -> new MydeiE4(param(e, 0, 0.1), param(e, 1, 0.3));
            case 6 -> new MydeiE6(param(e, 0, 100));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> MydeiKit::mydeiSkill;
            case 3 -> MydeiKit::mydeiUlt;
            case 9 -> MydeiKit::mydeiKingSlayer;
            case 11 -> MydeiKit::mydeiGodSlayer;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 消耗#3当前生命值, 对主目标造成#1生命上限伤害, 相邻目标#2伤害. */
    static void mydeiSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.25);
        double hpCostRatio = ctx.param(2, 0.5);
        payHpCost(user, hpCostRatio);
        MydeiKit.addCharge(user, (int) Math.round((1 - user.getHpPercent()) * 100));
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * mainMult,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.IMAGINARY));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * sideMult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.IMAGINARY));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 终结技: 回复#3生命上限生命并积攒#5点充能, 造成#1/#2生命上限伤害, 使目标陷入嘲讽#4回合. */
    static void mydeiUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.6);
        double healRatio = ctx.param(2, 0.15);
        int tauntTurns = ctx.intParam(3, 2);
        int charge = ctx.intParam(4, 20);
        user.heal(user.getMaxHp() * healRatio);
        MydeiKit.addCharge(user, charge);
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * mainMult,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
        battle.breakToughness(user, main, ctx.stanceSingle());
        applyTaunt(battle, user, main, tauntTurns);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * sideMult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
                applyTaunt(battle, user, enemy, tauntTurns);
            }
        }
    }

    /** 弑王成王 (自动施放): 消耗#3当前生命值, 造成#1/#2生命上限伤害. */
    static void mydeiKingSlayer(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.33);
        double hpCostRatio = ctx.param(2, 0.35);
        payHpCost(user, hpCostRatio);
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * mainMult,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * sideMult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 弑神登神: 消耗150点充能, 造成#1/#2生命上限伤害 (自动施放). */
    static void mydeiGodSlayer(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.84);
        int chargeCost = ctx.intParam(2, 150);
        MydeiKit.consumeCharge(user, chargeCost);
        battle.dealAttackDamageBase(user, main, user.getMaxHp() * mainMult,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * sideMult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.IMAGINARY));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    static void payHpCost(CanHit user, double ratio) {
        double cost = user.getCurrentHp() * ratio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
    }

    static void applyTaunt(Battle battle, CanHit user, CanHit target, int turns) {
        if (target.isDeath()) {
            return;
        }
        Buff buff = new Buff("嘲讽", Buff.Category.DEBUFF, user, target, turns);
        battle.applyBuff(target, buff);
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 水与泥土: 【血仇】状态下万敌受到致命攻击时不会退出【血仇】状态 (单场3次)。 */
    static class MydeiWater implements Trace {
        private final int uses;

        MydeiWater(int uses) {
            this.uses = uses;
        }

        @Override
        public String getName() {
            return "水与泥土";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            // 血仇致命保护代理: 受致命攻击时回复50%生命上限 (近似 血仇 状态保护).
            if (owner.isDeath() && owner.hasBuffNamed("血仇")) {
                owner.revive(owner.getMaxHp() * 0.5);
                IO.println("  [行迹] " + owner.getName() + " survives (血仇保护)");
            }
        }
    }

    /** 三十僭主: 【血仇】状态下万敌免疫控制类负面状态。 */
    static class MydeiUsurper implements Trace {
        @Override
        public String getName() {
            return "三十僭主";
        }
    }

    /** 血祥罩衫: 生命上限>4000时, 每超过100点生命值使暴击率提高1.2%。 */
    static class MydeiCloak implements Trace {
        private final double threshold;
        private final double maxCount;
        private final double critPer100;
        private final double chargeRatio;
        private final double healRatio;

        MydeiCloak(double threshold, double maxCount, double critPer100, double chargeRatio, double healRatio) {
            this.threshold = threshold;
            this.maxCount = maxCount;
            this.critPer100 = critPer100;
            this.chargeRatio = chargeRatio;
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "血祥罩衫";
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
            double hp = owner.getMaxHp();
            double bonus = hp > threshold ? Math.min(maxCount, hp - threshold) / 100 * critPer100 : 0;
            owner.removeBuff("血祥罩衫");
            Buff buff = new Buff("血祥罩衫", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 寒风雕琢不屈的脊梁: 【弑神登神】对主目标伤害倍率提高30%。 */
    static class MydeiE1 implements Trace {
        private final double bonus;

        MydeiE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 纷争见证尸骸的喉鸣: 【血仇】状态期间无视15%防御力。 */
    static class MydeiE2 implements Trace {
        private final double defIgnore;
        private final double healChargeRatio;
        private final double chargeCap;

        MydeiE2(double defIgnore, double healChargeRatio, double chargeCap) {
            this.defIgnore = defIgnore;
            this.healChargeRatio = healChargeRatio;
            this.chargeCap = chargeCap;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 号角惊觉缄默的狂狮: 【血仇】状态期间暴击伤害提高30%。 */
    static class MydeiE4 implements Trace {
        private final double healRatio;
        private final double cdmg;

        MydeiE4(double healRatio, double cdmg) {
            this.healRatio = healRatio;
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 往日攀陟沉积的血山: 进入战斗时立刻进入【血仇】状态, 【弑神登神】所需充能降低至100点。 */
    static class MydeiE6 implements Trace {
        private final double chargeCost;

        MydeiE6(double chargeCost) {
            this.chargeCost = chargeCost;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("血仇");
            battle.applyBuff(owner, new Buff("血仇", Buff.Category.BUFF, owner, owner, -1));
            IO.println("  [星魂] " + owner.getName() + " enters 血仇 at battle start");
        }
    }

    // ─── 【血仇】/【充能】代理 (天赋 以血还血) ──────────────────────────

    private static final java.util.Map<CanHit, Integer> CHARGE = new java.util.concurrent.ConcurrentHashMap<>();

    static int charge(CanHit owner) {
        return CHARGE.getOrDefault(owner, 0);
    }

    static void addCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.min(200, charge(owner) + amount));
    }

    static void consumeCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.max(0, charge(owner) - amount));
    }
}
