package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
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
 * 加拉赫 (Gallagher, cid 1301) — 火属性 丰饶.
 */
public final class GallagherKit implements CharacterKit {

    @Override
    public int cid() {
        return 1301;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new GallagherNewRecipe(param(byId, 1301101, 0, 0.5), param(byId, 1301101, 1, 0.75)),
                new GallagherYeast(),
                new GallagherCheers());
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new GallagherE1(param(e, 0, 20), param(e, 1, 0.5));
            case 2 -> new GallagherE2(intParam(e, 0, 1), param(e, 1, 0.3), intParam(e, 2, 2));
            case 4 -> new GallagherE4(intParam(e, 0, 1));
            case 6 -> new GallagherE6(param(e, 0, 0.2), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> GallagherKit::gallagherSkill;
            case 3 -> GallagherKit::gallagherUlt;
            case 8 -> GallagherKit::gallagherEnhancedBasic;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 为指定我方单体回复#1点生命值 (固定值). */
    static void gallagherSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double flat = ctx.firstParam();
        for (CanHit ally : KitSupport.friendlyTargets(battle, user, targets)) {
            battle.healTarget(user, ally, flat);
            IO.println("  " + ally.getName() + " recovers " + String.format("%.0f", flat) + " HP");
        }
    }

    /** 终结技: 使敌方全体陷入【酩酊】#2回合, 造成#1%火属性伤害, 并将下一次普攻强化为【酒花奔涌】. */
    static void gallagherUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            enemy.removeBuff("酩酊");
            Buff debuff = new Buff("酩酊", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(0.06,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
            IO.println("  " + enemy.getName() + " is 酩酊 (break damage taken +6%)");
        }
        user.removeBuff("酒花奔涌");
        battle.applyBuff(user, new Buff("酒花奔涌", Buff.Category.BUFF, user, user, -1));
    }

    /** 强化普攻 酒花奔涌: 造成#1%火属性伤害, 使目标攻击力降低#2, 持续#3回合. */
    static void gallagherEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double atkDown = ctx.param(1, 0.1);
        int turns = ctx.intParam(2, 2);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (!target.isDeath() && battle.checkEffectHit(user, target, 1.0)) {
            Buff debuff = new Buff("攻击力降低", Buff.Category.DEBUFF, user, target, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(-atkDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, debuff);
        }
        user.removeBuff("酒花奔涌");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 崭新配方: 治疗量提高, 数值等同于击破特攻的50%, 最多提高75%. */
    static class GallagherNewRecipe implements Trace {
        private final double ratio;
        private final double cap;

        GallagherNewRecipe(double ratio, double cap) {
            this.ratio = ratio;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "崭新配方";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double bonus = Math.min(cap, be * ratio);
            Buff buff = new Buff("崭新配方", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " healing +"
                    + String.format("%.1f%%", bonus * 100) + " (崭新配方)");
        }
    }

    /** 天然酵母: 施放终结技后，立刻使自己行动提前100%。 */
    static class GallagherYeast implements Trace {
        @Override
        public String getName() {
            return "天然酵母";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                battle.advanceByPercent(owner, 1.0);
                IO.println("  [行迹] " + owner.getName() + " acts immediately after the ultimate!");
            }
        }
    }

    /** 敬请干杯: 攻击【酩酊】目标时, 天赋的生命回复效果对队友也生效 (代理: 攻击酩酊目标后治疗全队). */
    static class GallagherCheers implements Trace {
        @Override
        public String getName() {
            return "敬请干杯";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.ULTRA) {
                return;
            }
            boolean hitDrunk = targets.stream().anyMatch(t -> t.hasBuffNamed("酩酊"));
            if (hitDrunk) {
                double heal = 80;
                com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
                if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                        && talent.getSkills().getFirst().size() > 1) {
                    heal = talent.getSkills().getFirst().get(1);
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.heal(heal);
                }
                IO.println("  [行迹] 敬请干杯: party recovers " + String.format("%.0f", heal) + " HP");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 盐与犬: 进入战斗后恢复20点能量, 效果抵抗提高50%。 */
    static class GallagherE1 implements Trace {
        private final double energy;
        private final double resistance;

        GallagherE1(double energy, double resistance) {
            this.energy = energy;
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 狮子之尾: 施放战技时解除1个负面效果, 效果抵抗提高30%持续2回合。 */
    static class GallagherE2 implements Trace {
        private final int cleanseCount;
        private final double resistance;
        private final int turns;

        GallagherE2(int cleanseCount, double resistance, int turns) {
            this.cleanseCount = cleanseCount;
            this.resistance = resistance;
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
            for (CanHit ally : KitSupport.friendlyTargets(battle, owner, targets)) {
                for (int i = 0; i < cleanseCount; i++) {
                    Buff toRemove = ally.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                            .findFirst().orElse(null);
                    if (toRemove != null) {
                        battle.removeBuff(ally, toRemove);
                        IO.println("  [星魂] " + toRemove.getName() + " dispelled from " + ally.getName());
                    } else {
                        break;
                    }
                }
                Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂4 临终的轻语: 终结技的【酩酊】状态持续时间延长1回合。 */
    static class GallagherE4 implements Trace {
        private final int extraTurns;

        GallagherE4(int extraTurns) {
            this.extraTurns = extraTurns;
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
            for (Enemy enemy : battle.getAliveEnemies()) {
                for (Buff buff : enemy.getBuffs()) {
                    if ("酩酊".equals(buff.getName())) {
                        buff.setDuration(buff.getDuration() + extraTurns);
                    }
                }
            }
        }
    }

    /** 星魂6 血与沙: 击破特攻提高20%, 弱点击破效率提高20%。 */
    static class GallagherE6 implements Trace {
        private final double breaking;
        private final double efficiency;

        GallagherE6(double breaking, double efficiency) {
            this.breaking = breaking;
            this.efficiency = efficiency;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breaking,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(efficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }
}
