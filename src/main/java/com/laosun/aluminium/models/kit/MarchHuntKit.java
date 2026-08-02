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
 * 三月七•巡猎 (March 7th • Hunt, cid 1224) — 虚数属性 巡猎.
 */
public final class MarchHuntKit implements CharacterKit {

    @Override
    public int cid() {
        return 1224;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MarchShock(param(byId, 1224101, 0, 0.25)),
                new MarchLinglong(),
                new MarchWave(param(byId, 1224103, 0, 0.6), param(byId, 1224103, 1, 0.36),
                        intParam(byId, 1224103, 2, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MarchHuntE1(param(e, 0, 0.1));
            case 2 -> new MarchHuntE2(param(e, 0, 0.6), intParam(e, 1, 5), intParam(e, 2, 1), param(e, 3, 15));
            case 4 -> new MarchHuntE4(param(e, 0, 5));
            case 6 -> new MarchHuntE6(param(e, 0, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 1 -> MarchHuntKit::marchHuntBasic;
            case 2 -> MarchHuntKit::marchHuntSkill;
            case 3 -> MarchHuntKit::marchHuntUlt;
            case 8 -> MarchHuntKit::marchEnhancedBasic;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 普攻 荡涤妖邪琉璃剑: 对目标造成#1%伤害, 随后获得#2点充能。 */
    static void marchHuntBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, ctx.firstParam());
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof MarchShock shock) {
                    shock.gainCharge(battle, character, ctx.intParam(1, 1));
                }
            }
        }
    }

    /** 战技 师父，请喝茶！: 使除自身以外的我方指定单体成为【师父】, 【师父】的速度提高#1。
     *  仅战技最新的施放目标被视为【师父】。 */
    static void marchHuntSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        CanHit master = allies.getFirst();
        if (master == user) {
            IO.println("  " + user.getName() + " cannot make herself 【师父】!");
            return;
        }
        double speedRatio = ctx.param(0, 0.06);
        int turns = ctx.intParam(2, 1);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != master) {
                ally.removeBuff("师父");
                ally.removeBuff("师恩");
            }
        }
        master.removeBuff("师父");
        master.removeBuff("师恩");
        battle.applyBuff(master, new Buff("师父", Buff.Category.BUFF, user, master, -1));
        Buff speed = new Buff("师恩", Buff.Category.BUFF, user, master, turns)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedRatio,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(master, speed);
        IO.println("  " + master.getName() + " becomes 【师父】 (SPD +"
                + String.format("%.1f%%", speedRatio * 100) + ", " + turns + " turn)");
    }

    /** 终结技 盖世女侠三月七: 对目标造成#1%伤害, 使下一次强化普攻的初始段数增加#2段,
     *  额外造成伤害的固定概率提高#3。 */
    static void marchHuntUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, ctx.firstParam());
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof MarchShock shock) {
                    shock.boostNextEnhancedBasic(ctx.intParam(1, 2), ctx.param(2, 0.2));
                }
            }
        }
    }

    /**
     * 强化普攻 一扎眉攒，二扎心: 初始造成#4段伤害, 每段#1%伤害, 每段后以#2的固定概率再造成1段,
     * 最多额外#3段 (以固定段数代理: 初始段数 + 最大额外段数)。强化普攻无法恢复战技点。
     */
    static void marchEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        CanHit target = targets.getFirst();
        double hitMult = ctx.param(0, 0.4);
        int initialHits = ctx.intParam(3, 3);
        int maxExtra = ctx.intParam(2, 3);
        MarchShock shock = null;
        for (Trace trace : character.getTraces()) {
            if (trace instanceof MarchShock s) {
                shock = s;
            }
        }
        int extra = Math.min(maxExtra, shock != null ? shock.extraHits + maxExtra : maxExtra);
        int total = initialHits + extra;
        // 星魂6: 终结技后的下一次强化普攻暴击伤害提高#1.
        for (Trace trace : character.getTraces()) {
            if (trace instanceof MarchHuntE6 e6) {
                e6.consumeCritBoost(battle, character);
            }
        }
        for (int i = 0; i < total; i++) {
            if (target.isDeath()) {
                break;
            }
            ctx.dealDamage(battle, user, target, hitMult);
            battle.breakToughness(user, target, ctx.stanceSingle() / total);
        }
        if (shock != null) {
            shock.enhancedBasicUsed(battle, character);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 惊鸿: 战斗开始时行动提前#1。
     * 兼作天赋 师父，我悟了！ 的代理: 【师父】施放攻击或终结技后三月七每次获得最多1点充能;
     * 充能≥#1点时三月七立即行动, 造成的伤害提高#2, 普攻得到强化 (进入强化状态, 普攻 → 技能8);
     * 施放强化普攻后消耗#1点充能, 充能上限#3点。
     */
    static class MarchShock implements Trace {
        private final double advance;
        private int charge = 0;
        private int extraHits = 0;
        private double extraChance = 0;
        private boolean justEnhanced = false;

        MarchShock(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "惊鸿";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, advance);
            charge = 0;
            IO.println("  [行迹] " + owner.getName() + " advances "
                    + String.format("%.0f%%", advance * 100) + " (惊鸿)");
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            justEnhanced = false;
            if (type == SkillType.ULTRA) {
                return;
            }
            if (type == SkillType.COMMON && owner.isEnhanced()) {
                justEnhanced = true;
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!isAttack(type) || !actor.hasBuffNamed("师父")) {
                return;
            }
            gainCharge(battle, owner, 1);
        }

        /** 普攻/师父行动获得充能; 充能≥7时立即行动并强化普攻. */
        void gainCharge(Battle battle, Character owner, int amount) {
            charge = Math.min(10, charge + amount);
            if (charge >= 7 && !owner.isEnhanced()) {
                battle.enterEnhancedState(owner);
                battle.advanceByPercent(owner, 1.0);
                owner.removeBuff("师父我悟了");
                Buff buff = new Buff("师父我悟了", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(0.4,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [天赋] " + owner.getName() + " 悟了! 充能" + charge
                        + "点: 立即行动, 普攻强化!");
            } else {
                IO.println("  [天赋] " + owner.getName() + " 充能 " + charge + "/10");
            }
        }

        /** 终结技强化下一次强化普攻. */
        void boostNextEnhancedBasic(int extra, double chance) {
            extraHits += extra;
            extraChance += chance;
        }

        /** 强化普攻结束后: 消耗7点充能并退出强化状态. */
        void enhancedBasicUsed(Battle battle, Character owner) {
            charge = Math.max(0, charge - 7);
            extraHits = 0;
            extraChance = 0;
            if (charge < 7) {
                battle.exitEnhancedState(owner);
                owner.removeBuff("师父我悟了");
            }
        }
    }

    /** 玲珑: 三月七能够削减具有【师父】属性弱点的敌方目标的韧性; 击破弱点时触发虚数属性的
     *  弱点击破效果 (以战斗中虚数弱点植入代理, 见 desc)。 */
    static class MarchLinglong implements Trace {
        @Override
        public String getName() {
            return "玲珑";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                enemy.addTemporaryWeakness(Element.IMAGINARY, 99);
            }
            IO.println("  [行迹] " + owner.getName() + ": enemies gain imaginary weakness (玲珑)");
        }
    }

    /** 斡波: 施放强化普攻后, 使【师父】的暴击伤害提高#1, 击破特攻提高#2, 持续#3回合。 */
    static class MarchWave implements Trace {
        private final double critDamage;
        private final double breakEffect;
        private final int turns;

        MarchWave(double critDamage, double breakEffect, int turns) {
            this.critDamage = critDamage;
            this.breakEffect = breakEffect;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "斡波";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            boolean enhancedBasic = type == SkillType.COMMON && owner.isEnhanced();
            if (!enhancedBasic) {
                for (Trace trace : owner.getTraces()) {
                    if (trace instanceof MarchShock shock && shock.justEnhanced) {
                        enhancedBasic = true;
                    }
                }
            }
            if (!enhancedBasic) {
                return;
            }
            CanHit master = battle.getAlivePlayerUnits().stream()
                    .filter(u -> u.hasBuffNamed("师父")).findFirst().orElse(null);
            if (master == null) {
                return;
            }
            master.removeBuff("斡波");
            Buff buff = new Buff("斡波", Buff.Category.BUFF, owner, master, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakEffect,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(master, buff);
            IO.println("  [行迹] 【师父】 " + master.getName() + " gains crit dmg +"
                    + String.format("%.0f%%", critDamage * 100) + " / break effect +"
                    + String.format("%.0f%%", breakEffect * 100) + " (斡波)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 初花学剑动星芒: 场上存在【师父】时, 三月七的速度提高#1。 */
    static class MarchHuntE1 implements Trace {
        private final double speedPercent;

        MarchHuntE1(double speedPercent) {
            this.speedPercent = speedPercent;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean hasMaster = battle.getAlivePlayerUnits().stream()
                    .anyMatch(u -> u != owner && u.hasBuffNamed("师父"));
            if (!hasMaster) {
                return;
            }
            owner.removeBuff("初花学剑动星芒");
            Buff buff = new Buff("初花学剑动星芒", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 白刃耀雪舞骇浪: 【师父】施放普攻或战技攻击敌方目标后, 三月七立即发动追加攻击,
     *  造成#1%虚数伤害, 并获得#3点充能 (该效果每回合最多触发1次)。 */
    static class MarchHuntE2 implements Trace {
        private final double mult;
        private final int maxTurns;
        private final int chargeGain;
        private final double extraChance;
        private boolean usedThisTurn = false;

        MarchHuntE2(double mult, int maxTurns, int chargeGain, double extraChance) {
            this.mult = mult;
            this.maxTurns = maxTurns;
            this.chargeGain = chargeGain;
            this.extraChance = extraChance;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            usedThisTurn = false;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (usedThisTurn || !actor.hasBuffNamed("师父")) {
                return;
            }
            if (type != SkillType.COMMON && type != SkillType.SKILL) {
                return;
            }
            if (targets == null || targets.isEmpty()) {
                return;
            }
            CanHit main = targets.getFirst();
            if (main.isDeath() || main.getCamp() == owner.getCamp()) {
                return;
            }
            usedThisTurn = true;
            battle.dealAttackDamage(owner, main, mult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                            Element.IMAGINARY));
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof MarchShock shock) {
                    shock.gainCharge(battle, owner, chargeGain);
                }
            }
            IO.println("  [星魂] " + owner.getName() + " follows up on " + main.getName()
                    + " (师父 acted, 白刃耀雪)");
        }
    }

    /** 星魂4 头脑机灵本领强: 回合开始时, 恢复#1点能量。 */
    static class MarchHuntE4 implements Trace {
        private final double energy;

        MarchHuntE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (头脑机灵)");
        }
    }

    /** 星魂6 天下第一本姑娘: 施放终结技后, 下一次强化普攻造成的暴击伤害提高#1。 */
    static class MarchHuntE6 implements Trace {
        private final double critDamage;
        private boolean nextEnhancedCrit = false;

        MarchHuntE6(double critDamage) {
            this.critDamage = critDamage;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                nextEnhancedCrit = true;
            }
        }

        void consumeCritBoost(Battle battle, Character owner) {
            if (!nextEnhancedCrit) {
                return;
            }
            nextEnhancedCrit = false;
            owner.removeBuff("天下第一本姑娘");
            Buff buff = new Buff("天下第一本姑娘", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 是否为攻击类行动 (普攻/战技/终结技). */
    static boolean isAttack(SkillType type) {
        return type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA;
    }
}
