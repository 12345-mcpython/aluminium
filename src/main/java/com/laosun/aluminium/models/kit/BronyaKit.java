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
 * 布洛妮娅 (Bronya, cid 1101) — 风属性 同谐.
 */
public final class BronyaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1101;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new BronyaCommand(),
                new BronyaPosition(intParam(byId, 1101102, 0, 2), param(byId, 1101102, 1, 0.2)),
                new BronyaForces(param(byId, 1101103, 0, 0.1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new BronyaE1(param(e, 0, 0.5));
            case 2 -> new BronyaE2(param(e, 0, 0.3));
            case 4 -> new BronyaE4(param(e, 0, 0.8));
            case 6 -> new BronyaE6(intParam(e, 0, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> BronyaKit::bronyaSkill;
            case 3 -> BronyaKit::bronyaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 解除指定我方单体1个负面效果, 使其立即行动并提高伤害. */
    static void bronyaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double dmgBoost = ctx.firstParam();
        int turns = ctx.intParam(2, 1);
        double advance = ctx.param(3, 1.0);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            // 解除指定我方单体的1个负面效果.
            target.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                    .findFirst()
                    .ifPresent(buff -> {
                        battle.removeBuff(target, buff);
                        IO.println("  " + buff.getName() + " dispelled from " + target.getName());
                    });
            // 造成的伤害提高, 持续#3回合.
            target.removeBuff("作战指令");
            Buff buff = new Buff("作战指令", Buff.Category.BUFF, user, target, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            // 使该目标立即行动 (对自身施放时不触发).
            if (target != user) {
                battle.advanceByPercent(target, advance);
                IO.println("  " + target.getName() + " acts immediately!");
            }
        }
    }

    /** 终结技: 我方全体攻击力提高, 并提高等同于布洛妮娅X%暴击伤害+Y%的暴击伤害. */
    static void bronyaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkPercent = ctx.firstParam();
        double cdmgRatio = ctx.param(1, 0.12);
        double cdmgFlat = ctx.param(2, 0.12);
        int turns = ctx.intParam(3, 2);
        double cdmg = user.getAttribute(AttributeType.CRIT_ATTACK) != null
                ? user.getAttribute(AttributeType.CRIT_ATTACK).get() * cdmgRatio + cdmgFlat : cdmgFlat;
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            Buff atk = new Buff("作战指令", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, atk);
            Buff crit = new Buff("战意", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, crit);
        }
        IO.println("  " + user.getName() + ": party ATK +" + String.format("%.0f", atkPercent * 100)
                + "%, Crit DMG +" + String.format("%.0f", cdmg * 100) + "%");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 号令: 普攻的暴击率提高至100%。兼作天赋 战场教令 的代理: 施放普攻后下一次行动提前15%。 */
    static class BronyaCommand implements Trace {
        @Override
        public String getName() {
            return "号令";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.COMMON ? 1.0 : 0;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                double advance = KitSupport.skillData(owner, SkillType.TALENT) != null
                        && !KitSupport.skillData(owner, SkillType.TALENT).getSkills().isEmpty()
                        && !KitSupport.skillData(owner, SkillType.TALENT).getSkills().getFirst().isEmpty()
                        ? KitSupport.skillData(owner, SkillType.TALENT).getSkills().getFirst().getFirst() : 0.15;
                battle.advanceByPercent(owner, advance);
                IO.println("  [行迹] " + owner.getName() + "'s next action advances "
                        + String.format("%.0f", advance * 100) + "% (战场教令)");
            }
        }
    }

    /** 阵地: 战斗开始时，我方全体防御力提高20%，持续2回合。 */
    static class BronyaPosition implements Trace {
        private final int turns;
        private final double defPercent;

        BronyaPosition(int turns, double defPercent) {
            this.turns = turns;
            this.defPercent = defPercent;
        }

        @Override
        public String getName() {
            return "阵地";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("阵地", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(defPercent,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 军势: 布洛妮娅在场时，我方全体造成的伤害提高10%。 */
    static class BronyaForces implements Trace {
        private final double bonus;

        BronyaForces(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "军势";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("军势", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 施放战技时，有50%固定概率恢复1个战技点，1回合冷却。 */
    static class BronyaE1 implements Trace {
        private final double chance;
        private int cooldown = 0;

        BronyaE1(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            cooldown = 0;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL && cooldown == 0 && Math.random() < chance) {
                battle.addSkillPoints(1);
                cooldown = 1;
                IO.println("  [星魂] " + owner.getName() + " recovers 1 skill point");
            }
        }
    }

    /** 星魂2: 战技指定目标行动后速度提高30%，持续1回合。 */
    static class BronyaE2 implements Trace {
        private final double speedPercent;

        BronyaE2(double speedPercent) {
            this.speedPercent = speedPercent;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL || targets.isEmpty()) {
                return;
            }
            Buff buff = new Buff("疾风", Buff.Category.BUFF, owner, targets.getFirst(), 1)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(targets.getFirst(), buff);
        }
    }

    /** 星魂4: 其他角色对风属性弱点目标施放普攻后，布洛妮娅立即追加攻击，每回合1次。 */
    static class BronyaE4 implements Trace {
        private final double ratio;
        private boolean usedThisTurn = false;

        BronyaE4(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            usedThisTurn = false;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (usedThisTurn || type != SkillType.COMMON || targets.isEmpty() || actor.isDeath()) {
                return;
            }
            CanHit target = targets.getFirst();
            if (target instanceof Enemy enemy && enemy.isWeakTo(Element.WIND)) {
                usedThisTurn = true;
                double mult = KitSupport.actionMultiplier(actor, SkillType.COMMON) * ratio;
                battle.dealAttackDamage(owner, enemy, mult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.WIND));
                IO.println("  [星魂] " + owner.getName() + " follows up on " + enemy.getName());
            }
        }
    }

    /** 星魂6: 战技对指定我方目标造成的伤害提高效果的持续时间增加1回合。 */
    static class BronyaE6 implements Trace {
        private final int extraTurns;

        BronyaE6(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                for (Buff buff : new java.util.ArrayList<>(target.getBuffs())) {
                    if ("作战指令".equals(buff.getName())) {
                        buff.setDuration(buff.getDuration() + extraTurns);
                        IO.println("  [星魂] " + target.getName() + "'s 作战指令 extended (+"
                                + extraTurns + " turn)");
                    }
                }
            }
        }
    }
}
