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
 * 黑塔 (Herta, cid 1013) — 冰属性 智识.
 */
public final class HertaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1013;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HertaEfficiency(param(byId, 1013101, 0, 0.25)),
                new HertaPuppet(param(byId, 1013102, 0, 0.35)),
                new HertaFrost(param(byId, 1013103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HertaE1(param(e, 0, 0.5), param(e, 1, 0.4));
            case 2 -> new HertaE2(param(e, 0, 0.03), intParam(e, 1, 5));
            case 4 -> new HertaE4(param(e, 0, 0.1));
            case 6 -> new HertaE6(param(e, 0, 0.25), intParam(e, 1, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 2 ? HertaKit::hertaSkill : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对敌方全体造成伤害, 对生命值百分比≥X%的目标伤害提高. */
    static void hertaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double hpThreshold = ctx.param(1, 0.5);
        double bonus = ctx.param(2, 0.2);
        for (Enemy enemy : battle.getAliveEnemies()) {
            double mult = multiplier;
            if (enemy.getHpPercent() >= hpThreshold) {
                mult *= 1 + bonus;
            }
            ctx.dealDamage(battle, user, enemy, mult);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 效率: 施放战技时，对目标造成的伤害提高效果额外再提高25%。 */
    static class HertaEfficiency implements Trace {
        private final double extra;

        HertaEfficiency(double extra) {
            this.extra = extra;
        }

        @Override
        public String getName() {
            return "效率";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                for (CanHit target : targets) {
                    if (target.isDeath()) {
                        continue;
                    }
                    Buff buff = new Buff("效率", Buff.Category.DEBUFF, owner, target, 2)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(extra,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    /** 人偶: 抵抗控制类负面状态的概率提高35%。 */
    static class HertaPuppet implements Trace {
        private final double resistance;

        HertaPuppet(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "人偶";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("人偶", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 冰结: 施放终结技时，对冻结状态下的敌人造成的伤害提高20%。 */
    static class HertaFrost implements Trace {
        private final double bonus;

        HertaFrost(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "冰结";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA && defender.getControlState() == Buff.ControlType.FROZEN
                    ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 落井当下石: 普攻对生命值≤50%的目标额外造成40%攻击力的冰属性伤害。 */
    static class HertaE1 implements Trace {
        private final double hpThreshold;
        private final double atkRatio;

        HertaE1(double hpThreshold, double atkRatio) {
            this.hpThreshold = hpThreshold;
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "落井当下石";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && !targets.isEmpty()) {
                CanHit target = targets.getFirst();
                if (target.getHpPercent() <= hpThreshold) {
                    battle.dealAttackDamage(owner, target, atkRatio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
                    IO.println("  [星魂] " + owner.getName() + " deals extra ice damage to the weakened target");
                }
            }
        }
    }

    /** 星魂2 得胜必追击: 天赋每触发1次，暴击率提高3%，最多叠加5层。 */
    static class HertaE2 implements Trace {
        private final double critPerStack;
        private final int maxStacks;
        private int stacks = 0;

        HertaE2(double critPerStack, int maxStacks) {
            this.critPerStack = critPerStack;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "得胜必追击";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            stacks = Math.min(maxStacks, stacks + 1);
            owner.removeBuff("得胜必追击");
            Buff buff = new Buff("得胜必追击", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critPerStack * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " crit +"
                    + String.format("%.0f", critPerStack * stacks * 100) + "%");
        }
    }

    /** 星魂4 打人要打脸: 天赋触发时造成的伤害提高10% (代理: 对低血量目标)。 */
    static class HertaE4 implements Trace {
        private final double bonus;

        HertaE4(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "打人要打脸";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getHpPercent() <= 0.5 ? 1 + bonus : 1.0;
        }
    }

    /** 星魂6 世上没人能负我: 施放终结技后，攻击力提高25%，持续1回合。 */
    static class HertaE6 implements Trace {
        private final double atkPercent;
        private final int turns;

        HertaE6(double atkPercent, int turns) {
            this.atkPercent = atkPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "世上没人能负我";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                owner.removeBuff("世上没人能负我");
                Buff buff = new Buff("世上没人能负我", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [星魂] " + owner.getName() + " gains +"
                        + String.format("%.0f", atkPercent * 100) + "% ATK");
            }
        }
    }
}
