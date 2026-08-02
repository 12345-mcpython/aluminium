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
 * 虎克 (Hook, cid 1109) — 火属性 毁灭.
 */
public final class HookKit implements CharacterKit {

    @Override
    public int cid() {
        return 1109;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HookInnocence(param(byId, 1109101, 0, 0.05)),
                new HookPurity(param(byId, 1109102, 0, 0.35)),
                new HookPlayFire(param(byId, 1109103, 0, 5), param(byId, 1109103, 1, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HookE1(param(e, 0, 0.2));
            case 2 -> new HookE2(intParam(e, 0, 1));
            case 4 -> new HookE4(param(e, 0, 1.0));
            case 6 -> new HookE6(param(e, 0, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HookKit::hookSkill;
            case 3 -> HookKit::hookUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 造成#1%火属性伤害, 有#2基础概率使目标陷入灼烧状态#3回合
     *  (每回合受到#4攻击力的火属性持续伤害). */
    static void hookSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        double burnChance = ctx.param(1, 1.0);
        int burnTurns = ctx.intParam(2, 2);
        double dotRatio = ctx.param(3, 0.25);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (!main.isDeath() && !main.hasDotOfElement(Element.FIRE)
                && battle.checkEffectHit(user, main, burnChance)) {
            ctx.applyBurn(battle, user, main, dotRatio, burnTurns);
            IO.println("  " + main.getName() + " is burning");
        }
        // 终结技强化: 同时对主目标相邻的目标造成伤害 (强化战技).
        if (user.hasBuffNamed("强化战技")) {
            user.removeBuff("强化战技");
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main) {
                    ctx.dealDamage(battle, user, enemy, multiplier);
                    battle.breakToughness(user, enemy, ctx.stanceSpread());
                }
            }
            IO.println("  " + user.getName() + " 强化战技 hits adjacent targets!");
        }
    }

    /** 终结技: 造成#1%火属性伤害, 下一次施放的战技得到强化 (可攻击相邻目标). */
    static void hookUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        user.removeBuff("强化战技");
        Buff buff = new Buff("强化战技", Buff.Category.BUFF, user, user, -1);
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + "'s next Skill is enhanced (强化战技)!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 童真: 触发天赋时，回复等同于虎克生命上限5%的生命值。
     *  兼作天赋 怒火冲天 的代理: 攻击灼烧状态下的目标时, 追加1次X%攻击力的火属性伤害。 */
    static class HookInnocence implements Trace {
        private final double healRatio;

        HookInnocence(double healRatio) {
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "童真";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            boolean hitBurning = false;
            for (CanHit target : targets) {
                if (target.isDeath() || !target.hasDotOfElement(Element.FIRE)) {
                    continue;
                }
                hitBurning = true;
                // 天赋代理: 追加 1 次 X% 攻击力的火属性伤害.
                double ratio = 0.5;
                com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
                if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                        && !talent.getSkills().getFirst().isEmpty()) {
                    ratio = talent.getSkills().getFirst().getFirst();
                }
                battle.dealAttackDamage(owner, target, ratio, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.FIRE));
                IO.println("  [行迹] " + owner.getName() + " ignites the burning foe again!");
            }
            if (hitBurning) {
                owner.heal(owner.getMaxHp() * healRatio);
                IO.println("  [行迹] " + owner.getName() + " recovers "
                        + String.format("%.0f", owner.getMaxHp() * healRatio) + " HP (童真)");
            }
        }
    }

    /** 无邪: 抵抗控制类负面状态的概率提高35%。 */
    static class HookPurity implements Trace {
        private final double resistance;

        HookPurity(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "无邪";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("无邪", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 玩火: 施放终结技后，虎克行动提前20%并额外恢复5点能量。 */
    static class HookPlayFire implements Trace {
        private final double energy;
        private final double advance;

        HookPlayFire(double energy, double advance) {
            this.energy = energy;
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "玩火";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                owner.gainEnergy(energy);
                battle.advanceByPercent(owner, advance);
                IO.println("  [行迹] " + owner.getName() + " advances "
                        + String.format("%.0f", advance * 100) + "% and gains "
                        + String.format("%.0f", energy) + " energy");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 战技强化后造成的伤害提高20%。 */
    static class HookE1 implements Trace {
        private final double bonus;

        HookE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.SKILL ? 1 + bonus : 1.0;
        }
    }

    /** 星魂2: 战技使目标陷入的灼烧状态持续时间增加1回合。 */
    static class HookE2 implements Trace {
        private final int extraTurns;

        HookE2(int extraTurns) {
            this.extraTurns = extraTurns;
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
                for (Buff.Dot dot : target.getDots()) {
                    if (dot.getElement() == Element.FIRE) {
                        dot.setDuration(dot.getDuration() + extraTurns);
                    }
                }
            }
        }
    }

    /** 星魂4: 触发天赋时，100%基础概率使相邻目标也陷入灼烧状态。 */
    static class HookE4 implements Trace {
        private final double chance;

        HookE4(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL || targets.isEmpty()) {
                return;
            }
            CanHit main = targets.getFirst();
            if (!main.hasDotOfElement(Element.FIRE)) {
                return;
            }
            double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() * 0.3 : 0;
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main && battle.checkEffectHit(owner, enemy, chance)) {
                    enemy.applyDot(new Buff.Dot("Burn (灼烧)", owner, enemy, dotDamage, Element.FIRE, 2));
                    IO.println("  [星魂] " + enemy.getName() + " is burning");
                }
            }
        }
    }

    /** 星魂6: 对灼烧状态下的敌方目标造成的伤害提高20%。 */
    static class HookE6 implements Trace {
        private final double bonus;

        HookE6(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasDotOfElement(Element.FIRE) ? 1 + bonus : 1.0;
        }
    }
}
