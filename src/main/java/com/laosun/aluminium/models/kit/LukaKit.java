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
 * 卢卡 (Luka, cid 1111) — 物理属性 虚无.
 */
public final class LukaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1111;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new LukaOverload(),
                new LukaBrake(param(byId, 1111102, 0, 3)),
                new LukaCrush(param(byId, 1111103, 0, 0.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new LukaE1(param(e, 0, 0.15), intParam(e, 1, 2));
            case 2 -> new LukaE2(intParam(e, 0, 1));
            case 4 -> new LukaE4(param(e, 0, 0.05), intParam(e, 1, 4));
            case 6 -> new LukaE6(param(e, 0, 0.08));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> LukaKit::lukaSkill;
            case 3 -> LukaKit::lukaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 造成#1%物理伤害, 有#2基础概率使目标裂伤#5回合
     *  (每回合受到#3生命上限的物理持续伤害, 不超过卢卡攻击力的#4). */
    static void lukaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double chance = ctx.param(1, 1.0);
        double bleedRatio = ctx.param(2, 0.24);
        double capRatio = ctx.param(3, 1.3);
        int turns = ctx.intParam(4, 3);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (!target.isDeath() && !target.hasDotOfElement(Element.PHYSICAL)
                && battle.checkEffectHit(user, target, chance)) {
            applyBleed(battle, user, target, bleedRatio, capRatio, turns);
        }
        grantFightingWill(user);
    }

    /** 终结技: 获得#5层【斗志】, 有#2基础概率使目标受到的伤害提高#3 (持续#4回合),
     *  随后造成#1%物理伤害. */
    static void lukaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double chance = ctx.param(1, 1.0);
        double vuln = ctx.param(2, 0.12);
        int vulnTurns = ctx.intParam(3, 3);
        int will = ctx.intParam(4, 2);
        for (int i = 0; i < will; i++) {
            grantFightingWill(user);
        }
        if (!target.isDeath() && battle.checkEffectHit(user, target, chance)) {
            Buff debuff = new Buff("易伤", Buff.Category.DEBUFF, user, target, vulnTurns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, debuff);
            IO.println("  " + target.getName() + " takes +"
                    + String.format("%.0f", vuln * 100) + "% damage (" + vulnTurns + " turns)");
        }
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    /** 裂伤: 每回合受到#3生命上限的物理持续伤害, 上限为卢卡攻击力的#4. */
    static void applyBleed(Battle battle, CanHit user, CanHit target, double ratio, double capRatio, int turns) {
        double dot = Math.min(target.getMaxHp() * ratio,
                user.getAttribute(AttributeType.ATTACK) != null
                        ? user.getAttribute(AttributeType.ATTACK).get() * capRatio : Double.MAX_VALUE);
        target.applyDot(new Buff.Dot("Bleed (裂伤)", user, target, dot, Element.PHYSICAL, turns));
        IO.println("  " + target.getName() + " is bleeding (" + String.format("%.0f", dot)
                + " physical DoT x" + turns + ")");
    }

    /** 斗志: 施放战技/终结技后获得层数 (代理天赋 战斗意志, 由星魂4 提供攻击力加成). */
    static void grantFightingWill(CanHit user) {
        IO.println("  " + user.getName() + " gains a 斗志 stack");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 动能过载: 施放战技时，立即解除敌方目标1个增益效果。 */
    static class LukaOverload implements Trace {
        @Override
        public String getName() {
            return "动能过载";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                target.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.BUFF)
                        .findFirst()
                        .ifPresent(buff -> {
                            battle.removeBuff(target, buff);
                            IO.println("  [行迹] " + target.getName() + "'s " + buff.getName() + " dispelled");
                        });
            }
        }
    }

    /** 循环制动: 每获得1层【斗志】，额外恢复3点能量。 */
    static class LukaBrake implements Trace {
        private final double energy;
        private int stacks = 0;

        LukaBrake(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "循环制动";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                stacks++;
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains " + String.format("%.0f", energy)
                        + " energy (斗志 stack " + stacks + ")");
            }
        }
    }

    /** 粉碎斗志: 施放强化普攻时，每段攻击有50%固定概率额外施放1段攻击。 */
    static class LukaCrush implements Trace {
        private final double chance;

        LukaCrush(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "粉碎斗志";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && !targets.isEmpty() && Math.random() < chance) {
                battle.dealAttackDamage(owner, targets.getFirst(), 0.5, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
                IO.println("  [行迹] " + owner.getName() + " throws an extra punch!");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 行动时若指定目标处于裂伤状态，造成的伤害提高15%，持续2回合。 */
    static class LukaE1 implements Trace {
        private final double bonus;
        private final int turns;

        LukaE1(double bonus, int turns) {
            this.bonus = bonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (targets.isEmpty() || !targets.getFirst().hasDotOfElement(Element.PHYSICAL)) {
                return;
            }
            owner.removeBuff("星魂1");
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2: 战技击中的目标弱点为物理属性时，获得1层斗志。 */
    static class LukaE2 implements Trace {
        private final int stacks;

        LukaE2(int stacks) {
            this.stacks = stacks;
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
            boolean physicalWeak = targets.stream().anyMatch(t -> t instanceof Enemy e && e.isWeakTo(Element.PHYSICAL));
            if (physicalWeak) {
                log(owner.getName() + " gains " + stacks + " 斗志 stack");
            }
        }
    }

    /** 星魂4: 每获得1层斗志，攻击力提高5%，最多4层。 */
    static class LukaE4 implements Trace {
        private final double atkPerStack;
        private final int maxStacks;
        private int stacks = 0;

        LukaE4(double atkPerStack, int maxStacks) {
            this.atkPerStack = atkPerStack;
            this.maxStacks = maxStacks;
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
            stacks = Math.min(maxStacks, stacks + 1);
            owner.removeBuff("斗志");
            Buff buff = new Buff("斗志", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPerStack * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            log(owner.getName() + " 斗志 stacks: " + stacks);
        }
    }

    /** 星魂6: 强化普攻击中裂伤目标后，使裂伤立即产生8%伤害。 */
    static class LukaE6 implements Trace {
        private final double detonateRatio;

        LukaE6(double detonateRatio) {
            this.detonateRatio = detonateRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || targets.isEmpty()) {
                return;
            }
            CanHit target = targets.getFirst();
            for (Buff.Dot dot : target.getDots()) {
                if (dot.getElement() == Element.PHYSICAL) {
                    battle.dealAttackDamage(owner, target, 0, dot.getDamage() * detonateRatio,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.PHYSICAL));
                    log(target.getName() + "'s bleed detonates");
                }
            }
        }
    }

    private static void log(String message) {
        IO.println("  [星魂] " + message);
    }
}
