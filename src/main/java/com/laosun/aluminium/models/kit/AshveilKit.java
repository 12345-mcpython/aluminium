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
 * 不死途 (Ashveil, cid 1504) — 雷属性 虚无.
 */
public final class AshveilKit implements CharacterKit {

    @Override
    public int cid() {
        return 1504;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AshveilCrime(intParam(byId, 1504101, 0, 1), intParam(byId, 1504101, 1, 2),
                        intParam(byId, 1504101, 2, 1), intParam(byId, 1504101, 3, 1)),
                new AshveilShadow(param(byId, 1504102, 0, 0.8), intParam(byId, 1504102, 1, 1),
                        param(byId, 1504102, 2, 0.1)),
                new AshveilWolf(param(byId, 1504103, 0, 0.4), param(byId, 1504103, 1, 0.8)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AshveilE1(param(e, 0, 0.24), param(e, 1, 0.5), param(e, 2, 0.36));
            case 2 -> new AshveilE2(intParam(e, 0, 18), param(e, 1, 0.35));
            case 4 -> new AshveilE4(param(e, 0, 0.4), intParam(e, 1, 3));
            case 6 -> new AshveilE6(param(e, 0, 0.2), param(e, 1, 0.04), intParam(e, 2, 30));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> AshveilKit::ashveilSkill;
            case 3 -> AshveilKit::ashveilUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使目标成为【饲饵】, 造成#1%伤害, 若目标已为【饲饵】额外造成#3%伤害并恢复#5个战技点. */
    static void ashveilSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        double extraMult = ctx.param(2, 0.5);
        double defDown = ctx.param(3, 0.2);
        int skillPoints = ctx.intParam(4, 1);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        boolean already = main.hasBuffNamed("饲饵");
        AshveilKit.applyBait(battle, user, main);
        if (already) {
            ctx.dealDamage(battle, user, main, extraMult);
            battle.addSkillPoints(skillPoints);
            IO.println("  " + user.getName() + " extra damage + " + skillPoints + " skill point (饲饵)");
        }
        // 场上存在【饲饵】时, 敌方全体防御力降低#4.
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("饲饵·减防");
            Buff debuff = new Buff("饲饵·减防", Buff.Category.DEBUFF, user, enemy, 2)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
        }
    }

    /** 终结技: 使目标成为【饲饵】, 造成#1%伤害, 获得#2点充能并立即对【饲饵】发动强化天赋追加攻击. */
    static void ashveilUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        int charge = ctx.intParam(1, 3);
        int stackCost = ctx.intParam(2, 4);
        double extraMult = ctx.param(3, 1);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        AshveilKit.applyBait(battle, user, main);
        AshveilKit.addCharge(user, charge);
        // 强化天赋追加攻击: 每消耗4层【婪酣】额外造成1次100%伤害.
        double mult = 2.0;
        int stacks = AshveilKit.greedStacks(user);
        battle.dealAttackDamage(user, main, mult, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
        while (stacks >= stackCost) {
            stacks -= stackCost;
            battle.dealAttackDamage(user, main, extraMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
        }
        AshveilKit.setGreedStacks(user, stacks);
        IO.println("  " + user.getName() + " 飨宴: enhanced follow-up on the bait!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 罪途: 施放战技/终结技时获得#1/#2层【婪酣】。 */
    static class AshveilCrime implements Trace {
        private final int skillStacks;
        private final int ultStacks;
        private final int killPerStack;
        private final int killStacks;

        AshveilCrime(int skillStacks, int ultStacks, int killPerStack, int killStacks) {
            this.skillStacks = skillStacks;
            this.ultStacks = ultStacks;
            this.killPerStack = killPerStack;
            this.killStacks = killStacks;
        }

        @Override
        public String getName() {
            return "罪途";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                AshveilKit.addGreed(owner, skillStacks);
            } else if (type == SkillType.ULTRA) {
                AshveilKit.addGreed(owner, ultStacks);
            }
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            AshveilKit.addGreed(owner, killStacks);
            IO.println("  [行迹] " + owner.getName() + " gains " + killStacks + " 【婪酣】 ("
                    + AshveilKit.greedStacks(owner) + ")");
        }
    }

    /** 影肢: 追加攻击造成的伤害提高80%, 每有1层【婪酣】额外提高10%。 */
    static class AshveilShadow implements Trace {
        private final double baseBonus;
        private final int perStack;
        private final double perStackBonus;

        AshveilShadow(double baseBonus, int perStack, double perStackBonus) {
            this.baseBonus = baseBonus;
            this.perStack = perStack;
            this.perStackBonus = perStackBonus;
        }

        @Override
        public String getName() {
            return "影肢";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            if (type != SkillType.ULTRA && type != SkillType.COMMON) {
                return 1.0;
            }
            return 1 + baseBonus + AshveilKit.greedStacks(owner) * perStackBonus;
        }
    }

    /** 头狼: 不死途在场时, 我方目标造成的暴击伤害提高40%, 追加攻击暴击伤害额外提高80%。 */
    static class AshveilWolf implements Trace {
        private final double cdmg;
        private final double followUpCdmg;

        AshveilWolf(double cdmg, double followUpCdmg) {
            this.cdmg = cdmg;
            this.followUpCdmg = followUpCdmg;
        }

        @Override
        public String getName() {
            return "头狼";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("头狼", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 小心，满月不可外出: 不死途在场时, 敌方全体受到的伤害提高24%。 */
    static class AshveilE1 implements Trace {
        private final double vuln;
        private final double lowHpThreshold;
        private final double lowHpVuln;

        AshveilE1(double vuln, double lowHpThreshold, double lowHpVuln) {
            this.vuln = vuln;
            this.lowHpThreshold = lowHpThreshold;
            this.lowHpVuln = lowHpVuln;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂1", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂2 敲门，屋内空余窃笑: 【婪酣】叠加上限提高至18层。 */
    static class AshveilE2 implements Trace {
        private final int cap;
        private final double returnRatio;

        AshveilE2(int cap, double returnRatio) {
            this.cap = cap;
            this.returnRatio = returnRatio;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 切记，不必咀嚼真相: 施放终结技时, 攻击力提高40%持续3回合。 */
    static class AshveilE4 implements Trace {
        private final double atkBonus;
        private final int turns;

        AshveilE4(double atkBonus, int turns) {
            this.atkBonus = atkBonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 结局，或许无人生还: 场上存在【饲饵】时, 敌方全体全属性抗性降低20%。 */
    static class AshveilE6 implements Trace {
        private final double resDown;
        private final double perStackDmg;
        private final int maxStacks;

        AshveilE6(double resDown, double perStackDmg, int maxStacks) {
            this.resDown = resDown;
            this.perStackDmg = perStackDmg;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂6", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    // ─── 【饲饵】/【婪酣】/【充能】代理 (天赋 宿怨，切齿奉还) ────────────

    private static final java.util.Map<CanHit, Integer> GREED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> CHARGE = new java.util.concurrent.ConcurrentHashMap<>();

    static void applyBait(Battle battle, CanHit user, CanHit target) {
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("饲饵");
        }
        if (!target.isDeath()) {
            battle.applyBuff(target, new Buff("饲饵", Buff.Category.DEBUFF, user, target, -1));
            IO.println("  " + target.getName() + " becomes 饲饵");
        }
    }

    static int greedStacks(CanHit owner) {
        return GREED.getOrDefault(owner, 0);
    }

    static void addGreed(CanHit owner, int amount) {
        GREED.put(owner, Math.min(12, greedStacks(owner) + amount));
    }

    static void setGreedStacks(CanHit owner, int stacks) {
        GREED.put(owner, Math.max(0, stacks));
    }

    static int charge(CanHit owner) {
        return CHARGE.getOrDefault(owner, 0);
    }

    static void addCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.min(3, charge(owner) + amount));
    }

    /** 天赋代理: 【饲饵】受到我方其他目标攻击后, 不死途消耗1点充能发动追加攻击并获得1层【婪酣】。 */
    static void baitFollowUp(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (targets == null) {
            return;
        }
        for (CanHit target : targets) {
            if (target.isDeath() || !target.hasBuffNamed("饲饵")) {
                continue;
            }
            owner.gainEnergy(8);
            double mult = 2.0;
            battle.dealAttackDamage(owner, target, mult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
            AshveilKit.addGreed(owner, 1);
            IO.println("  [天赋] " + owner.getName() + " follows up on 饲饵 " + target.getName()
                    + " (+1 婪酣)");
        }
    }
}
