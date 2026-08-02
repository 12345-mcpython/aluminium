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
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 杰帕德 (Gepard, cid 1104) — 冰属性 存护.
 */
public final class GepardKit implements CharacterKit {

    @Override
    public int cid() {
        return 1104;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new GepardRighteous(param(byId, 1104101, 0, 3.0)),
                new GepardCommand(),
                new GepardBattleIntent(param(byId, 1104103, 0, 0.35)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new GepardE1(param(e, 0, 0.35));
            case 2 -> new GepardE2(param(e, 0, 0.2), intParam(e, 1, 1));
            case 4 -> new GepardE4(param(e, 0, 0.2));
            case 6 -> new GepardE6(param(e, 0, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> GepardKit::gepardSkill;
            case 3 -> GepardKit::gepardUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 造成伤害, 有#2基础概率使目标冻结#3回合 (冻结期间每回合受到#4攻击力的冰属性附加伤害). */
    static void gepardSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double freezeChance = ctx.param(1, 0.65);
        int freezeTurns = ctx.intParam(2, 1);
        double dotRatio = ctx.param(3, 0.3);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (target.isDeath() || target.getControlState() != null) {
            return;
        }
        if (battle.checkEffectHit(user, target, freezeChance)) {
            double dot = user.getAttribute(AttributeType.ATTACK) != null
                    ? user.getAttribute(AttributeType.ATTACK).get() * dotRatio : 0;
            Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, user, target, freezeTurns)
                    .control(Buff.ControlType.FROZEN)
                    .dot(dot, Element.ICE, freezeTurns);
            battle.applyBuff(target, freeze);
            target.setControlState(Buff.ControlType.FROZEN);
            IO.println("  " + target.getName() + " is FROZEN!");
        }
    }

    /** 终结技: 为我方全体提供等同于杰帕德#1防御力+#3的护盾, 持续#2回合. */
    static void gepardUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double defRatio = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        double flat = ctx.param(2, 150);
        double def = user.getAttribute(AttributeType.DEFENCE) != null
                ? user.getAttribute(AttributeType.DEFENCE).get() : 0;
        double shield = def * defRatio + flat;
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            battle.applyShield(ally, shield, user);
            ally.setShieldTurns(turns);
        }
        IO.println("  " + user.getName() + " shields the party for "
                + String.format("%.0f", shield));
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 刚正: 杰帕德被敌方攻击的概率提高 (仇恨倍率来自数据). */
    static class GepardRighteous implements Trace {
        private final double aggro;

        GepardRighteous(double aggro) {
            this.aggro = aggro;
        }

        @Override
        public String getName() {
            return "刚正";
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return aggro;
        }
    }

    /** 统领: 【不屈之身】触发后，杰帕德的能量立即恢复至100%。
     *  兼作天赋 不屈之身 的代理: 受到致命攻击时不会倒下, 回复25%生命上限 (每场战斗1次);
     *  星魂6 使回复量额外增加50%生命上限并立即行动。 */
    static class GepardCommand implements Trace {
        private boolean triggered = false;

        @Override
        public String getName() {
            return "统领";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (triggered || !owner.isDeath()) {
                return;
            }
            triggered = true;
            boolean e6 = owner.hasBuffNamed("星魂6");
            double heal = owner.getMaxHp() * 0.25 + (e6 ? owner.getMaxHp() * 0.5 : 0);
            owner.revive(heal);
            owner.gainEnergy(owner.getMaxEnergy());
            IO.println("  [行迹] " + owner.getName() + " survives (不屈之身) at "
                    + String.format("%.0f", heal) + " HP, energy restored to full!");
            if (e6) {
                battle.advanceByPercent(owner, 1.0);
                IO.println("  [星魂] " + owner.getName() + " acts immediately!");
            }
        }
    }

    /** 战意: 杰帕德提高等同于自身当前防御力35%的攻击力，每回合开始时刷新。 */
    static class GepardBattleIntent implements Trace {
        private final double defRatio;

        GepardBattleIntent(double defRatio) {
            this.defRatio = defRatio;
        }

        @Override
        public String getName() {
            return "战意";
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
            owner.removeBuff("战意");
            double atk = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() * defRatio : 0;
            Buff buff = new Buff("战意", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atk,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 战技使目标陷入冻结状态的基础概率提高35%。 */
    static class GepardE1 implements Trace {
        private final double bonus;

        GepardE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (target.isDeath() || target.getControlState() != null) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, bonus)) {
                    Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, owner, target, 1)
                            .control(Buff.ControlType.FROZEN);
                    battle.applyBuff(target, freeze);
                    target.setControlState(Buff.ControlType.FROZEN);
                }
            }
        }
    }

    /** 星魂2: 冻结状态解除后，目标速度降低20%，持续1回合。 */
    static class GepardE2 implements Trace {
        private final double slow;
        private final int turns;

        GepardE2(double slow, int turns) {
            this.slow = slow;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (target.getControlState() == Buff.ControlType.FROZEN) {
                    Buff slowBuff = new Buff("Slow", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-slow,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, slowBuff);
                }
            }
        }
    }

    /** 星魂4: 杰帕德在场时，我方全体的效果抵抗提高20%。 */
    static class GepardE4 implements Trace {
        private final double bonus;

        GepardE4(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("坚壁", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6: 触发天赋时杰帕德立即行动，回复量额外增加50%生命上限。
     *  标记由行迹 统领 的 不屈之身 代理读取。 */
    static class GepardE6 implements Trace {
        private final double healRatio;

        GepardE6(double healRatio) {
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1);
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + ": 不屈之身回复量+"
                    + String.format("%.0f", healRatio * 100) + "%生命上限, 触发后立即行动");
        }
    }
}
