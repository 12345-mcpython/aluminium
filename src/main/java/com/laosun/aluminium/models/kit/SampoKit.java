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
 * 桑博 (Sampo, cid 1108) — 风属性 虚无.
 */
public final class SampoKit implements CharacterKit {

    @Override
    public int cid() {
        return 1108;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SampoTrap(intParam(byId, 1108101, 0, 1)),
                new SampoLater(param(byId, 1108102, 0, 10)),
                new SampoSpice(param(byId, 1108103, 0, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SampoE1(intParam(e, 0, 1));
            case 2 -> new SampoE2(param(e, 0, 1.0), intParam(e, 1, 1));
            case 4 -> new SampoE4(intParam(e, 0, 5), param(e, 1, 0.08));
            case 6 -> new SampoE6(param(e, 0, 0.15));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SampoKit::sampoSkill;
            case 3 -> SampoKit::sampoUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对目标造成#2%风属性伤害, 并额外造成#1次伤害, 每次对随机单体造成#2%伤害. */
    static void sampoSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int extraHits = ctx.intParam(0, 4);
        double multiplier = ctx.param(1, 0.28);
        List<Enemy> alive = battle.getAliveEnemies();
        for (int i = 0; i <= extraHits; i++) {
            if (alive.isEmpty()) {
                return;
            }
            CanHit target = i == 0 ? targets.getFirst() : alive.get((int) (Math.random() * alive.size()));
            ctx.dealDamage(battle, user, target, multiplier);
            battle.breakToughness(user, target, ctx.stanceSingle() / (extraHits + 1));
        }
    }

    /** 终结技: 对敌方全体造成#1%伤害, 有#4基础概率使目标受到的持续伤害提高#2%, 持续#3回合. */
    static void sampoUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double dotTaken = ctx.param(1, 0.2);
        int turns = ctx.intParam(2, 2);
        double chance = ctx.param(3, 1.0);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.isDeath()) {
                continue;
            }
            if (battle.checkEffectHit(user, enemy, chance)) {
                Buff debuff = new Buff("持续伤害提高", Buff.Category.DEBUFF, user, enemy, turns)
                        .stat(AttributeType.DOT_TAKEN, DoubleValue.Modifier.pure(dotTaken,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
                IO.println("  " + enemy.getName() + " takes +"
                        + String.format("%.0f", dotTaken * 100) + "% DoT damage");
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 圈套: 天赋使敌方陷入风化状态的持续时间延长1回合。 */
    static class SampoTrap implements Trace {
        private final int extraTurns;

        SampoTrap(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "圈套";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                for (CanHit target : targets) {
                    if (target.isDeath()) {
                        continue;
                    }
                    double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                            ? owner.getAttribute(AttributeType.ATTACK).get() * 0.5 : 0;
                    target.applyDot(new Buff.Dot("Wind Shear (风化)", owner, target, dotDamage, Element.WIND,
                            3 + extraTurns));
                    IO.println("  [行迹] " + target.getName() + " suffers wind shear ("
                            + (3 + extraTurns) + " turns)");
                }
            }
        }
    }

    /** 后手: 施放终结技时，额外恢复10点能量。 */
    static class SampoLater implements Trace {
        private final double energy;

        SampoLater(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "后手";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains " + String.format("%.0f", energy) + " energy");
            }
        }
    }

    /** 加料: 风化状态下的敌方目标对桑博造成的伤害降低15%。 */
    static class SampoSpice implements Trace {
        private final double reduction;

        SampoSpice(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "加料";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (attacker != null && attacker.hasDotOfElement(Element.WIND)) {
                owner.heal(damage * reduction);
                IO.println("  [行迹] " + owner.getName() + " mitigates "
                        + String.format("%.0f", damage * reduction) + " damage (wind-sheared foe)");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 战技对敌方随机单体额外造成1次伤害。 */
    static class SampoE1 implements Trace {
        private final int extraHits;

        SampoE1(int extraHits) {
            this.extraHits = extraHits;
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
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            double mult = KitSupport.actionMultiplier(owner, SkillType.SKILL);
            for (int i = 0; i < extraHits; i++) {
                battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                        mult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.WIND));
            }
        }
    }

    /** 星魂2: 风化目标被消灭时，100%基础概率使敌方全体叠加1层风化。 */
    static class SampoE2 implements Trace {
        private final double chance;
        private final int stacks;

        SampoE2(double chance, int stacks) {
            this.chance = chance;
            this.stacks = stacks;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Enemy) || !victim.hasDotOfElement(Element.WIND)) {
                return;
            }
            double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() * 0.5 : 0;
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (battle.checkEffectHit(owner, enemy, chance)) {
                    enemy.applyDot(new Buff.Dot("Wind Shear (风化)", owner, enemy, dotDamage, Element.WIND, 3));
                }
            }
            IO.println("  [星魂] " + owner.getName() + " spreads wind shear to all enemies");
        }
    }

    /** 星魂4: 战技击中风化≥5层目标时，使其风化立即产生8%伤害。 */
    static class SampoE4 implements Trace {
        private final int stackThreshold;
        private final double detonateRatio;

        SampoE4(int stackThreshold, double detonateRatio) {
            this.stackThreshold = stackThreshold;
            this.detonateRatio = detonateRatio;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                for (Buff.Dot dot : target.getDots()) {
                    if (dot.getElement() == Element.WIND) {
                        battle.dealAttackDamage(owner, target, 0, dot.getDamage() * detonateRatio,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        Element.WIND));
                        IO.println("  [星魂] " + target.getName() + "'s wind shear detonates");
                    }
                }
            }
        }
    }

    /** 星魂6: 天赋施加的风化状态伤害倍率提高15%。 */
    static class SampoE6 implements Trace {
        private final double bonus;

        SampoE6(double bonus) {
            this.bonus = bonus;
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
                for (Buff.Dot dot : new java.util.ArrayList<>(target.getDots())) {
                    if (dot.getSource() == owner && dot.getElement() == Element.WIND) {
                        Buff.Dot boosted = new Buff.Dot(dot.getName(), owner, target,
                                dot.getDamage() * (1 + bonus), Element.WIND, dot.getDuration());
                        target.getDots().remove(dot);
                        target.getDots().add(boosted);
                        IO.println("  [星魂] " + target.getName() + "'s wind shear boosted to "
                                + String.format("%.0f", boosted.getDamage()));
                    }
                }
            }
        }
    }
}
