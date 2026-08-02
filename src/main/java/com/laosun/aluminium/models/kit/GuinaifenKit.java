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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 桂乃芬 (Guinaifen, cid 1210) — 火属性 虚无.
 *
 * <p>核心机制【吞火】: 敌方目标的灼烧状态触发伤害后, 有#1基础概率陷入【吞火】
 * (受到的伤害提高#4, 持续#5回合, 最多叠加#6层), 由行迹 缘竿 代理天赋实现。
 */
public final class GuinaifenKit implements CharacterKit {

    @Override
    public int cid() {
        return 1210;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new GuinaifenAcrobat(param(byId, 1210101, 0, 0.8)),
                new GuinaifenAdvance(param(byId, 1210102, 0, 0.25)),
                new GuinaifenBlade(param(byId, 1210103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new GuinaifenE1(param(e, 0, 1.0), param(e, 1, 0.1), intParam(e, 2, 2));
            case 2 -> new GuinaifenE2(param(e, 0, 0.4));
            case 4 -> new GuinaifenE4(intParam(e, 0, 2));
            case 6 -> new GuinaifenE6(intParam(e, 0, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> GuinaifenKit::guinaifenSkill;
            case 3 -> GuinaifenKit::guinaifenUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对指定敌方单体造成#1%攻击力的火属性伤害, 对相邻目标造成#2%攻击力的火属性伤害,
     *  并有#3基础概率使目标与相邻目标陷入灼烧状态 (#4%攻击力的火属性持续伤害, #5回合).
     *  星魂2: 敌方处于灼烧状态时, 灼烧伤害倍率提升#6。 */
    static void guinaifenSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMultiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(1, 0.2);
        double burnChance = ctx.param(2, 1.0);
        double dotRatio = ctx.param(3, 0.83904);
        int burnTurns = ctx.intParam(4, 2);
        double burnRatio = burnRatio(user, dotRatio);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        applyBurnIfAble(battle, ctx, user, main, burnChance, burnRatio, burnTurns);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
                applyBurnIfAble(battle, ctx, user, enemy, burnChance, burnRatio, burnTurns);
            }
        }
    }

    /** 终结技: 对敌方全体造成#1%攻击力的火属性伤害; 若目标处于灼烧状态,
     *  使其当前承受的灼烧状态立即产生相当于原伤害#2的伤害 (引爆持续伤害). */
    static void guinaifenUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double detonateRatio = ctx.param(1, 0.72);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.hasDotOfElement(Element.FIRE)) {
                ctx.detonateDots(battle, user, enemy, detonateRatio);
                IO.println("  " + enemy.getName() + "'s burn is detonated ("
                        + String.format("%.0f%%", detonateRatio * 100) + ")");
            }
        }
    }

    /** 战技/普攻的灼烧伤害倍率 (星魂2 提升#1, 敌方处于灼烧状态时生效). */
    static double burnRatio(CanHit user, double base) {
        if (user instanceof Character character && character.getEidolonLevel() >= 2) {
            return base * 1.4;
        }
        return base;
    }

    static void applyBurnIfAble(Battle battle, SkillContext ctx, CanHit user, CanHit target,
                                double chance, double dotRatio, int turns) {
        if (target.isDeath() || target.hasDotOfElement(Element.FIRE)) {
            return;
        }
        if (battle.checkEffectHit(user, target, chance)) {
            ctx.applyBurn(battle, user, target, dotRatio, turns);
            IO.println("  " + target.getName() + " is burning");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 缘竿: 普攻有#1基础概率使敌方目标陷入与战技相同的灼烧状态。
     * 兼作天赋 古来君子养艺人 的代理: 敌方受到的灼烧状态触发伤害后, 有#1基础概率陷入
     * 【吞火】(受到的伤害提高#4, 持续#5回合, 最多叠加#6层; 星魂6 增加#1层上限)。
     */
    static class GuinaifenAcrobat implements Trace {
        private final double chance;
        private final Map<CanHit, Integer> stacks = new HashMap<>();

        GuinaifenAcrobat(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "缘竿";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON) {
                return;
            }
            double dotRatio = 0.83904;
            com.laosun.aluminium.models.SkillData skill = KitSupport.skillData(owner, SkillType.SKILL);
            if (skill != null && skill.getSkills() != null && !skill.getSkills().isEmpty()
                    && skill.getSkills().getFirst().size() >= 4) {
                dotRatio = skill.getSkills().getFirst().get(3);
            }
            for (CanHit target : targets) {
                if (target.isDeath() || target.hasDotOfElement(Element.FIRE)) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, chance)) {
                    target.applyDot(new Buff.Dot("Burn (灼烧)", owner, target,
                            owner.getAttribute(AttributeType.ATTACK) != null
                                    ? owner.getAttribute(AttributeType.ATTACK).get() * burnRatio(owner, dotRatio) : 0,
                            Element.FIRE, 2));
                    IO.println("  [行迹] " + target.getName() + " is burning (普攻, 缘竿)");
                }
            }
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            if (victim.isDeath()) {
                return;
            }
            double baseChance = 1.0;
            double vuln = 0.04;
            int turns = 3;
            int maxStacks = 3;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() >= 6) {
                List<Double> t = talent.getSkills().getFirst();
                baseChance = t.get(0);
                vuln = t.get(3);
                turns = (int) Math.round(t.get(4));
                maxStacks = (int) Math.round(t.get(5));
            }
            if (owner.getEidolonLevel() >= 6) {
                maxStacks++;
            }
            if (battle.checkEffectHit(owner, victim, baseChance)) {
                int next = Math.min(maxStacks, stacks.getOrDefault(victim, 0) + 1);
                stacks.put(victim, next);
                victim.removeBuff("吞火");
                Buff buff = new Buff("吞火", Buff.Category.DEBUFF, owner, victim, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln * next,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(victim, buff);
                IO.println("  [行迹] " + victim.getName() + " gains 【吞火】 " + next + "/"
                        + maxStacks + " stacks (+" + String.format("%.0f%%", vuln * next * 100)
                        + " damage taken)");
            }
        }
    }

    /** 投狭: 战斗开始时，桂乃芬的行动提前#1。 */
    static class GuinaifenAdvance implements Trace {
        private final double advance;

        GuinaifenAdvance(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "投狭";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, advance);
            IO.println("  [行迹] " + owner.getName() + " advances "
                    + String.format("%.0f%%", advance * 100) + " at battle start (投狭)");
        }
    }

    /** 逾锋: 对陷入灼烧状态的敌方目标造成的伤害提高#1。 */
    static class GuinaifenBlade implements Trace {
        private final double bonus;

        GuinaifenBlade(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "逾锋";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasDotOfElement(Element.FIRE) ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 倒立吃面条: 施放战技时，有#1基础概率使受到攻击的敌方目标效果抵抗降低#2，持续#3回合。 */
    static class GuinaifenE1 implements Trace {
        private final double chance;
        private final double resistanceDown;
        private final int turns;

        GuinaifenE1(double chance, double resistanceDown, int turns) {
            this.chance = chance;
            this.resistanceDown = resistanceDown;
            this.turns = turns;
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
                if (target.isDeath()) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, chance)) {
                    Buff buff = new Buff("倒立吃面条", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(-resistanceDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    /** 星魂2 刷牙吹口哨: 敌方目标处于灼烧状态时，桂乃芬的普攻与战技对其施加的灼烧状态的
     *  伤害倍率提升#1。 (数值由战技行为与行迹 缘竿 按星魂等级读取.) */
    static class GuinaifenE2 implements Trace {
        @SuppressWarnings("unused")
        private final double bonus;

        GuinaifenE2(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 金枪锁咽喉: 桂乃芬施加的灼烧状态每触发一次伤害，使自身恢复#1点能量。 */
    static class GuinaifenE4 implements Trace {
        private final double energy;

        GuinaifenE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (burn tick, 金枪锁咽喉)");
        }
    }

    /** 星魂6 徒手接子弹: 使【吞火】的可叠加层数增加#1层。
     *  (上限由行迹 缘竿 按星魂等级读取.) */
    static class GuinaifenE6 implements Trace {
        @SuppressWarnings("unused")
        private final int extraStacks;

        GuinaifenE6(int extraStacks) {
            this.extraStacks = extraStacks;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
