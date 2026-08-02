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
 * 娜塔莎 (Natasha, cid 1105) — 物理属性 丰饶.
 */
public final class NatashaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1105;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new NatashaSoothing(),
                new NatashaDoctor(param(byId, 1105102, 0, 0.1)),
                new NatashaRegimen(intParam(byId, 1105103, 0, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new NatashaE1(param(e, 0, 0.3), param(e, 1, 0.15), param(e, 2, 400));
            case 2 -> new NatashaE2(param(e, 0, 0.3), intParam(e, 1, 1), param(e, 2, 0.06), param(e, 3, 160));
            case 4 -> new NatashaE4(param(e, 0, 5));
            case 6 -> new NatashaE6(param(e, 0, 0.4));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 2 ? NatashaKit::natashaSkill : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 为指定我方单体回复#1生命上限+#4的生命值, 并附上持续回复效果#3回合
     *  (每回合回复#2生命上限+#5). */
    static void natashaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        double healRatio = ctx.param(0, 0.07);
        double hotRatio = ctx.param(1, 0.048);
        int turns = ctx.intParam(2, 2);
        double flat = ctx.param(3, 70);
        double hotFlat = ctx.param(4, 48);
        for (CanHit ally : allies) {
            battle.healTarget(user, ally, user.getMaxHp() * healRatio + flat);
            // 持续回复效果 (#3回合, 每回合回复#2生命上限+#5); 行迹 调理 延长1回合.
            Buff hot = new Buff("持续回复", Buff.Category.BUFF, user, ally, turns)
                    .heal(user.getMaxHp() * hotRatio + hotFlat);
            battle.applyBuff(ally, hot);
            IO.println("  " + ally.getName() + " recovers "
                    + String.format("%.0f", user.getMaxHp() * healRatio + flat)
                    + " HP + HoT (" + turns + " turns)");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 舒缓: 施放战技时，解除指定我方单体的1个负面效果。 */
    static class NatashaSoothing implements Trace {
        @Override
        public String getName() {
            return "舒缓";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
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

    /** 医者: 娜塔莎提供的治疗量提高10%。 */
    static class NatashaDoctor implements Trace {
        private final double bonus;

        NatashaDoctor(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "医者";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("医者", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 调理: 施放战技产生的持续回复效果延长1回合。 */
    static class NatashaRegimen implements Trace {
        private final int extraTurns;

        NatashaRegimen(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "调理";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                for (Buff buff : new java.util.ArrayList<>(ally.getBuffs())) {
                    if ("持续回复".equals(buff.getName())) {
                        buff.setDuration(buff.getDuration() + extraTurns);
                        IO.println("  [行迹] " + ally.getName() + "'s HoT extended (+"
                                + extraTurns + " turn)");
                    }
                }
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 受到攻击后生命值≤30%时，治疗15%生命上限+400，单场战斗1次。 */
    static class NatashaE1 implements Trace {
        private final double hpThreshold;
        private final double healRatio;
        private final double flat;
        private boolean used = false;

        NatashaE1(double hpThreshold, double healRatio, double flat) {
            this.hpThreshold = hpThreshold;
            this.healRatio = healRatio;
            this.flat = flat;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (used || owner.isDeath() || owner.getHpPercent() > hpThreshold) {
                return;
            }
            used = true;
            owner.heal(owner.getMaxHp() * healRatio + flat);
            IO.println("  [星魂] " + owner.getName() + " heals for "
                    + String.format("%.0f", owner.getMaxHp() * healRatio + flat));
        }
    }

    /** 星魂2: 终结技使生命值≤30%的我方目标获得1回合持续治疗。 */
    static class NatashaE2 implements Trace {
        private final double hpThreshold;
        private final int turns;
        private final double healRatio;
        private final double flat;

        NatashaE2(double hpThreshold, int turns, double healRatio, double flat) {
            this.hpThreshold = hpThreshold;
            this.turns = turns;
            this.healRatio = healRatio;
            this.flat = flat;
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
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.getHpPercent() <= hpThreshold) {
                    Buff hot = new Buff("持续治疗", Buff.Category.BUFF, owner, ally, turns)
                            .heal(owner.getMaxHp() * healRatio + flat);
                    battle.applyBuff(ally, hot);
                    IO.println("  [星魂] " + ally.getName() + " gains HoT");
                }
            }
        }
    }

    /** 星魂4: 受到攻击后，额外恢复5点能量。 */
    static class NatashaE4 implements Trace {
        private final double energy;

        NatashaE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            owner.gainEnergy(energy);
        }
    }

    /** 星魂6: 普攻额外造成等同于生命上限40%的物理伤害。 */
    static class NatashaE6 implements Trace {
        private final double hpRatio;

        NatashaE6(double hpRatio) {
            this.hpRatio = hpRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && !targets.isEmpty()) {
                battle.dealAttackDamage(owner, targets.getFirst(), 0, owner.getMaxHp() * hpRatio,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.PHYSICAL));
                IO.println("  [星魂] " + owner.getName() + " deals bonus physical damage");
            }
        }
    }
}
