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
 * 三月七 (March 7th, cid 1001) — 冰属性 存护.
 */
public final class March7thKit implements CharacterKit {

    @Override
    public int cid() {
        return 1001;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MarchPurify(),
                new MarchReinforce(),
                new MarchIceSpell(param(byId, 1001103, 0, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MarchE1(param(e, 0, 6));
            case 2 -> new MarchE2(param(e, 0, 0.24), intParam(e, 1, 3), param(e, 2, 320));
            case 4 -> new MarchE4(param(e, 0, 0.3));
            case 6 -> new MarchE6(param(e, 0, 0.04), param(e, 1, 106));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> March7thKit::marchShield;
            case 3 -> March7thKit::marchUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 为友方目标提供等同于自身防御力X%+固定值的护盾, 持续若干回合. */
    static void marchShield(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double defRatio = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        double flat = ctx.param(3, 0);
        double def = user.getAttribute(AttributeType.DEFENCE) != null
                ? user.getAttribute(AttributeType.DEFENCE).get() : 0;
        double shield = def * defRatio + flat;
        for (CanHit target : ctx.friendlyTargets(battle, user)) {
            battle.applyShield(target, shield, user);
            target.setShieldTurns(turns);
        }
    }

    /** 终结技: 对敌方全体造成伤害, 并有X%基础概率使目标冻结. */
    static void marchUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double freezeChance = ctx.param(1, 0.5);
        int freezeTurns = ctx.intParam(2, 1);
        double dotRatio = ctx.param(3, 0.3);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.isDeath() || enemy.getControlState() != null) {
                continue;
            }
            if (battle.checkEffectHit(user, enemy, freezeChance)) {
                double dot = user.getAttribute(AttributeType.ATTACK) != null
                        ? user.getAttribute(AttributeType.ATTACK).get() * dotRatio : 0;
                Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, user, enemy, freezeTurns)
                        .control(Buff.ControlType.FROZEN)
                        .dot(dot, Element.ICE, freezeTurns);
                battle.applyBuff(enemy, freeze);
                enemy.setControlState(Buff.ControlType.FROZEN);
                IO.println("  " + enemy.getName() + " is FROZEN!");
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 纯洁: 施放战技时，解除指定我方单体的1个负面效果。 */
    static class MarchPurify implements Trace {
        @Override
        public String getName() {
            return "纯洁";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            // Cleanse one designated ally (the ally with the most debuffs).
            CanHit toCleanse = null;
            long mostDebuffs = 0;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                long debuffs = ally.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                if (debuffs > mostDebuffs) {
                    mostDebuffs = debuffs;
                    toCleanse = ally;
                }
            }
            if (toCleanse != null) {
                for (Buff buff : toCleanse.getBuffs()) {
                    if (buff.getCategory() == Buff.Category.DEBUFF) {
                        battle.removeBuff(toCleanse, buff);
                        IO.println("  [行迹] " + buff.getName() + " dispelled from " + toCleanse.getName());
                        return;
                    }
                }
            }
        }
    }

    /** 加护: 战技提供的护盾持续时间增加1回合。 */
    static class MarchReinforce implements Trace {
        @Override
        public String getName() {
            return "加护";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    if (ally.getShield() > 0) {
                        ally.setShieldTurns(ally.getShieldTurns() + 1);
                        IO.println("  [行迹] " + ally.getName() + "'s shield extended (+1 turn)");
                    }
                }
            }
        }
    }

    /** 冰咒: 施放终结技时，冻结敌方目标的基础概率提高15%。 */
    static class MarchIceSpell implements Trace {
        private final double bonus;

        MarchIceSpell(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "冰咒";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
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

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 记忆中的你: 终结技每冻结1个目标，恢复6点能量。 */
    static class MarchE1 implements Trace {
        private final double energy;

        MarchE1(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "记忆中的你";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                long frozen = targets.stream().filter(t -> t.getControlState() == Buff.ControlType.FROZEN).count();
                if (frozen > 0) {
                    owner.gainEnergy(energy * frozen);
                    IO.println("  [星魂] " + owner.getName() + " restores "
                            + String.format("%.0f", energy * frozen) + " energy (" + frozen + " frozen)");
                }
            }
        }
    }

    /** 星魂2 记忆中的它: 战斗开始为生命值最低的我方目标提供护盾。 */
    static class MarchE2 implements Trace {
        private final double defRatio;
        private final int turns;
        private final double flat;

        MarchE2(double defRatio, int turns, double flat) {
            this.defRatio = defRatio;
            this.turns = turns;
            this.flat = flat;
        }

        @Override
        public String getName() {
            return "记忆中的它";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            List<Character> allies = battle.getAliveCharacters();
            if (allies.isEmpty()) {
                return;
            }
            Character lowest = allies.stream().min(java.util.Comparator.comparingDouble(Character::getHpPercent))
                    .orElse(owner);
            double shield = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() * defRatio + flat : flat;
            battle.applyShield(lowest, shield, owner);
            lowest.setShieldTurns(turns);
            IO.println("  [星魂] " + owner.getName() + " shields " + lowest.getName()
                    + " for " + String.format("%.0f", shield));
        }
    }

    /** 星魂4 不愿再失去: 反击造成伤害提高，数值等同于三月七防御力的30%。 */
    static class MarchE4 implements Trace {
        private final double defRatio;

        MarchE4(double defRatio) {
            this.defRatio = defRatio;
        }

        @Override
        public String getName() {
            return "不愿再失去";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath() || attacker == null) {
                return;
            }
            // The counter (反击) triggers after being attacked.
            double counterDamage = owner.getAttribute(AttributeType.DEFENCE) != null
                    ? owner.getAttribute(AttributeType.DEFENCE).get() * defRatio : 0;
            battle.dealAttackDamage(owner, attacker, 0, counterDamage,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
            IO.println("  [星魂] " + owner.getName() + " counter-attacks " + attacker.getName());
        }
    }

    /** 星魂6 就这样，一直…: 护盾保护下的我方目标每回合开始回复4%生命上限+106。 */
    static class MarchE6 implements Trace {
        private final double hpRatio;
        private final double flat;

        MarchE6(double hpRatio, double flat) {
            this.hpRatio = hpRatio;
            this.flat = flat;
        }

        @Override
        public String getName() {
            return "就这样，一直…";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.getShield() > 0) {
                    double heal = ally.getMaxHp() * hpRatio + flat;
                    ally.heal(heal);
                    IO.println("  [星魂] " + ally.getName() + " recovers "
                            + String.format("%.0f", heal) + " HP (shielded)");
                }
            }
        }
    }
}
