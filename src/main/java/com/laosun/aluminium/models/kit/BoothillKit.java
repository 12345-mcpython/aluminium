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
 * 波提欧 (Boothill, cid 1315) — 物理属性 巡猎.
 */
public final class BoothillKit implements CharacterKit {

    @Override
    public int cid() {
        return 1315;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new BoothillGhost(param(byId, 1315101, 0, 0.1), param(byId, 1315101, 1, 0.3),
                        param(byId, 1315101, 2, 0.5), param(byId, 1315101, 3, 1.5)),
                new BoothillSerpent(param(byId, 1315102, 0, 0.3)),
                new BoothillPointBlank(param(byId, 1315103, 0, 10)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new BoothillE1(param(e, 0, 0.16));
            case 2 -> new BoothillE2(intParam(e, 0, 1), param(e, 1, 0.3), intParam(e, 2, 2));
            case 4 -> new BoothillE4(param(e, 0, 0.12), param(e, 1, 0.12));
            case 6 -> new BoothillE6(param(e, 0, 0.4), param(e, 1, 0.7));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> BoothillKit::boothillSkill;
            case 3 -> BoothillKit::boothillUlt;
            case 8 -> BoothillKit::boothillEnhancedBasic;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使目标及自身进入【绝命对峙】#3回合, 普攻获得强化且无法施放战技. */
    static void boothillSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        int turns = ctx.intParam(2, 2);
        target.removeBuff("绝命对峙");
        Buff debuff = new Buff("绝命对峙", Buff.Category.DEBUFF, user, target, turns)
                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(0.15,
                        DoubleValue.Modifier.ModifierSource.DEBUFF));
        battle.applyBuff(target, debuff);
        user.removeBuff("绝命对峙·自身");
        Buff self = new Buff("绝命对峙·自身", Buff.Category.BUFF, user, user, turns);
        battle.applyBuff(user, self);
        IO.println("  " + target.getName() + " and " + user.getName() + " enter 绝命对峙!");
    }

    /** 终结技: 为目标添加物理弱点#3回合, 造成#1%伤害, 并使其行动延后#2。 */
    static void boothillUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double delay = ctx.param(1, 0.3);
        int weaknessTurns = ctx.intParam(2, 2);
        if (target instanceof Enemy enemy) {
            enemy.addTemporaryWeakness(Element.PHYSICAL, weaknessTurns);
        }
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (!target.isDeath()) {
            battle.delayByPercent(target, delay);
        }
        IO.println("  " + target.getName() + " gains physical weakness and is delayed!");
    }

    /** 强化普攻 击锤连弩: 对目标造成#1%物理伤害 (仅能以对峙目标为目标). */
    static void boothillEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 幽灵装填: 暴击率/暴击伤害提高, 数值等同于击破特攻的10%/50%, 最多30%/150%。 */
    static class BoothillGhost implements Trace {
        private final double critPerBe;
        private final double critCap;
        private final double cdmgPerBe;
        private final double cdmgCap;

        BoothillGhost(double critPerBe, double critCap, double cdmgPerBe, double cdmgCap) {
            this.critPerBe = critPerBe;
            this.critCap = critCap;
            this.cdmgPerBe = cdmgPerBe;
            this.cdmgCap = cdmgCap;
        }

        @Override
        public String getName() {
            return "幽灵装填";
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
            double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double crit = Math.min(critCap, be * critPerBe);
            double cdmg = Math.min(cdmgCap, be * cdmgPerBe);
            owner.removeBuff("幽灵装填");
            Buff buff = new Buff("幽灵装填", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 蛇之上行: 波提欧处于【绝命对峙】时, 受到未处于【绝命对峙】目标的伤害降低30%。 */
    static class BoothillSerpent implements Trace {
        private final double reduction;

        BoothillSerpent(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "蛇之上行";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("蛇之上行", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 抵近射击: 处于【绝命对峙】并获得【优势口袋】时, 恢复10点能量。 */
    static class BoothillPointBlank implements Trace {
        private final double energy;

        BoothillPointBlank(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "抵近射击";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            // 星魂1: 战斗开始时获得1层【优势口袋】.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof BoothillE1) {
                    BoothillKit.addPockets(owner, 1);
                }
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            // 天赋 轮中五豆 代理: 强化普攻击破弱点/消灭目标时获得1层【优势口袋】.
            if (type == SkillType.COMMON && owner.hasBuffNamed("绝命对峙·自身")
                    && targets != null && !targets.isEmpty()) {
                BoothillKit.gainPocketOnBreak(battle, owner, targets.getFirst());
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 扬尘孤星: 战斗开始时获得1层【优势口袋】, 造成伤害时无视敌方16%防御力。 */
    static class BoothillE1 implements Trace {
        private final double defIgnore;

        BoothillE1(double defIgnore) {
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

    /** 星魂2 里程碑贩子: 处于【绝命对峙】并获得【优势口袋】时, 恢复1个战技点并击破特攻提高30%持续2回合。 */
    static class BoothillE2 implements Trace {
        private final int skillPoints;
        private final double beBonus;
        private final int turns;

        BoothillE2(int skillPoints, double beBonus, int turns) {
            this.skillPoints = skillPoints;
            this.beBonus = beBonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 冷肉名厨: 处于【绝命对峙】的敌方目标受到波提欧攻击时, 受到的伤害额外提高12%。 */
    static class BoothillE4 implements Trace {
        private final double dmgTaken;
        private final double reduction;

        BoothillE4(double dmgTaken, double reduction) {
            this.dmgTaken = dmgTaken;
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasBuffNamed("绝命对峙") ? 1 + dmgTaken : 1.0;
        }
    }

    /** 星魂6 撬棍旅馆的浣熊: 触发天赋造成击破伤害时, 对目标额外造成40%击破伤害。 */
    static class BoothillE6 implements Trace {
        private final double mainRatio;
        private final double sideRatio;

        BoothillE6(double mainRatio, double sideRatio) {
            this.mainRatio = mainRatio;
            this.sideRatio = sideRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 【优势口袋】层数代理 (天赋 轮中五豆) ────────────────────────────

    private static final java.util.Map<CanHit, Integer> POCKETS = new java.util.concurrent.ConcurrentHashMap<>();

    static int pockets(CanHit owner) {
        return POCKETS.getOrDefault(owner, 0);
    }

    static void addPockets(CanHit owner, int amount) {
        POCKETS.put(owner, Math.min(6, pockets(owner) + amount));
    }

    /** 天赋代理: 强化普攻击破目标弱点或消灭目标时获得1层【优势口袋】。 */
    static void gainPocketOnBreak(Battle battle, Character owner, CanHit target) {
        boolean brokeOrKilled = target.isDeath()
                || target instanceof Enemy enemy && enemy.isBroken();
        if (brokeOrKilled && owner.hasBuffNamed("绝命对峙·自身")) {
            addPockets(owner, 1);
            IO.println("  [天赋] " + owner.getName() + " gains 1 【优势口袋】 ("
                    + pockets(owner) + "/6)");
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof BoothillPointBlank pointBlank) {
                    owner.gainEnergy(pointBlank.energy);
                }
                if (trace instanceof BoothillE2 e2) {
                    battle.addSkillPoints(e2.skillPoints);
                    owner.removeBuff("星魂2·BE");
                    Buff buff = new Buff("星魂2·BE", Buff.Category.BUFF, owner, owner, e2.turns)
                            .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(e2.beBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }
    }
}
