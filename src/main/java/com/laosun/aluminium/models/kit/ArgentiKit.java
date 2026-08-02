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
 * 银枝 (Argenti, cid 1302) — 物理属性 智识.
 */
public final class ArgentiKit implements CharacterKit {

    @Override
    public int cid() {
        return 1302;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new ArgentiPiety(intParam(byId, 1302101, 0, 1)),
                new ArgentiGenerosity(param(byId, 1302102, 0, 2)),
                new ArgentiCourage(param(byId, 1302103, 0, 0.5), param(byId, 1302103, 1, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new ArgentiE1(param(e, 0, 0.04));
            case 2 -> new ArgentiE2(intParam(e, 0, 3), param(e, 1, 0.4), intParam(e, 2, 1));
            case 4 -> new ArgentiE4(intParam(e, 0, 2), intParam(e, 1, 2));
            case 6 -> new ArgentiE6(param(e, 0, 0.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 3 -> ArgentiKit::argentiUlt;
            case 14 -> ArgentiKit::argentiMaxUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 消耗#2点能量, 对敌方全体造成#1%物理属性伤害. */
    static void argentiUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    /** 二段终结技: 消耗180点能量, 对敌方全体造成#1%伤害, 并额外造成#2次随机单体#3%伤害. */
    static void argentiMaxUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int extraHits = ctx.intParam(1, 6);
        double hitMultiplier = ctx.param(2, 0.57);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        for (int i = 0; i < extraHits; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            Enemy target = alive.get((int) (Math.random() * alive.size()));
            battle.dealAttackDamage(user, target, hitMultiplier, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.PHYSICAL));
        }
        IO.println("  " + user.getName() + " fires " + extraHits + " extra hits!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 虔诚: 回合开始时，立即获得1层【升格】。
     *  兼作天赋 崇高的客体 的代理: 施放普攻/战技/终结技时, 每击中1个敌方目标恢复3点能量并获得1层【升格】。 */
    static class ArgentiPiety implements Trace {
        private final int stacks;

        ArgentiPiety(int stacks) {
            this.stacks = stacks;
        }

        @Override
        public String getName() {
            return "虔诚";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            int current = ArgentiKit.promotionStacks(owner);
            setPromotionStacks(owner, current + stacks);
            refreshPromotion(battle, owner);
            IO.println("  [行迹] " + owner.getName() + " gains " + stacks + " 【升格】");
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            refreshPromotion(battle, owner);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            int hit = 0;
            for (CanHit target : targets) {
                if (!target.isDeath()) {
                    hit++;
                }
            }
            if (hit > 0) {
                double energy = 3;
                addPromotion(owner, hit);
                refreshPromotion(battle, owner);
                owner.gainEnergy(energy * hit);
                IO.println("  [行迹] " + owner.getName() + " hits " + hit + " targets: +"
                        + String.format("%.0f", energy * hit) + " energy, +" + hit + " 【升格】");
            }
        }
    }

    /** 慷慨: 在敌方目标进入战斗时，自身立即恢复2点能量。 */
    static class ArgentiGenerosity implements Trace {
        private final double energy;

        ArgentiGenerosity(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "慷慨";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (慷慨)");
        }
    }

    /** 勇气: 对当前生命值≤50%的敌方目标造成的伤害提高15%。 */
    static class ArgentiCourage implements Trace {
        private final double hpThreshold;
        private final double bonus;

        ArgentiCourage(double hpThreshold, double bonus) {
            this.hpThreshold = hpThreshold;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "勇气";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getHpPercent() <= hpThreshold ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 每层【升格】额外使暴击伤害提高4%。 */
    static class ArgentiE1 implements Trace {
        private final double cdmgPerStack;

        ArgentiE1(double cdmgPerStack) {
            this.cdmgPerStack = cdmgPerStack;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            ArgentiKit.refreshPromotion(battle, owner);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            ArgentiKit.refreshPromotion(battle, owner);
        }
    }

    /** 星魂2: 施放终结技时若敌方≥3个, 攻击力提高40%持续1回合。 */
    static class ArgentiE2 implements Trace {
        private final int enemyThreshold;
        private final double atkBonus;
        private final int turns;

        ArgentiE2(int enemyThreshold, double atkBonus, int turns) {
            this.enemyThreshold = enemyThreshold;
            this.atkBonus = atkBonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            if (battle.getAliveEnemies().size() >= enemyThreshold) {
                owner.removeBuff("星魂2");
                Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    /** 星魂4: 战斗开始时获得2层【升格】, 天赋可叠加上限提高2层。 */
    static class ArgentiE4 implements Trace {
        private final int startStacks;
        private final int capBonus;

        ArgentiE4(int startStacks, int capBonus) {
            this.startStacks = startStacks;
            this.capBonus = capBonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            setPromotionStacks(owner, startStacks);
            ArgentiKit.refreshPromotion(battle, owner);
            IO.println("  [星魂] " + owner.getName() + " starts with " + startStacks + " 【升格】");
        }
    }

    /** 星魂6: 施放终结技时无视敌方30%防御力。 */
    static class ArgentiE6 implements Trace {
        private final double defIgnore;

        ArgentiE6(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 【升格】层数代理 (天赋 崇高的客体: 每击中1个目标获得1层, 暴击率+1%每层, 上限10层) ──

    private static final java.util.Map<CanHit, Integer> PROMOTION = new java.util.concurrent.ConcurrentHashMap<>();

    static int promotionStacks(CanHit owner) {
        return PROMOTION.getOrDefault(owner, 0);
    }

    static void setPromotionStacks(CanHit owner, int stacks) {
        PROMOTION.put(owner, Math.max(0, stacks));
    }

    static void addPromotion(CanHit owner, int amount) {
        setPromotionStacks(owner, promotionStacks(owner) + amount);
    }

    static void refreshPromotion(Battle battle, Character owner) {
        int cap = 10;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof ArgentiE4 e4) {
                cap += e4.capBonus;
            }
        }
        int stacks = Math.min(cap, promotionStacks(owner));
        double crit = stacks * 0.01;
        double cdmg = 0;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof ArgentiE1 e1) {
                cdmg = stacks * e1.cdmgPerStack;
            }
        }
        owner.removeBuff("升格");
        Buff buff = new Buff("升格", Buff.Category.BUFF, owner, owner, -1)
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        if (cdmg > 0) {
            buff.stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                    DoubleValue.Modifier.ModifierSource.BUFF));
        }
        battle.applyBuff(owner, buff);
        IO.println("  【升格】 " + owner.getName() + ": " + stacks + " stacks (crit +"
                + String.format("%.0f", crit * 100) + "%)");
    }
}
