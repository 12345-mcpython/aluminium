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
 * 托帕 (Topaz, cid 1112) — 火属性 巡猎.
 */
public final class TopazKit implements CharacterKit {

    @Override
    public int cid() {
        return 1112;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new TopazOverdraft(),
                new TopazFinance(param(byId, 1112102, 0, 0.15)),
                new TopazAdjust(param(byId, 1112103, 0, 10)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new TopazE1(param(e, 0, 0.25), intParam(e, 1, 2));
            case 2 -> new TopazE2(param(e, 0, 5));
            case 4 -> new TopazE4(param(e, 0, 0.2));
            case 6 -> new TopazE6(intParam(e, 0, 1), param(e, 1, 0.1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> TopazKit::topazSkill;
            case 3 -> TopazKit::topazUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使目标陷入【负债证明】状态 (受到的追加攻击伤害提高#2), 账账造成#1%伤害. */
    static void topazSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double followUpTaken = ctx.param(1, 0.25);
        // 使目标陷入【负债证明】状态.
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("负债证明");
        }
        Buff mark = new Buff("负债证明", Buff.Category.DEBUFF, user, target, -1)
                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(followUpTaken,
                        DoubleValue.Modifier.ModifierSource.DEBUFF));
        battle.applyBuff(target, mark);
        // 账账对目标造成伤害.
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        IO.println("  " + target.getName() + " is marked with 负债证明");
    }

    /** 终结技: 使账账进入【涨幅惊人！】状态, 伤害倍率提高#1, 持续#4回合. */
    static void topazUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplierBonus = ctx.firstParam();
        int turns = ctx.intParam(3, 2);
        user.removeBuff("涨幅惊人");
        Buff buff = new Buff("涨幅惊人", Buff.Category.BUFF, user, user, turns);
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + ": 账账 enters 涨幅惊人! (+"
                + String.format("%.0f", multiplierBonus * 100) + "% follow-up damage, "
                + turns + " turns)");
    }

    /** 账账追加攻击的伤害倍率 (涨幅惊人 状态下 +#1). */
    static double numbyMultiplier(CanHit user, double base) {
        if (user instanceof Character character && character.hasBuffNamed("涨幅惊人")) {
            com.laosun.aluminium.models.SkillData ult = KitSupport.skillData(character, SkillType.ULTRA);
            if (ult != null && ult.getSkills() != null && !ult.getSkills().isEmpty()
                    && !ult.getSkills().getFirst().isEmpty()) {
                return base * (1 + ult.getSkills().getFirst().getFirst());
            }
        }
        return base;
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 透支: 托帕施放普攻造成伤害时，被视为发动了追加攻击。
     * 账账是传统召唤物而非忆灵 (HSR.md §4.2): 无独立单位, 以追加攻击代理。
     */
    static class TopazOverdraft implements Trace {
        @Override
        public String getName() {
            return "透支";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && !targets.isEmpty()) {
                // 账账追加攻击 (传统召唤物代理); 涨幅惊人 状态下伤害倍率提高.
                double mult = numbyMultiplier(owner, 0.6);
                battle.dealAttackDamage(owner, targets.getFirst(), mult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.FIRE));
                IO.println("  [行迹] 账账 follows up on " + targets.getFirst().getName());
            }
        }
    }

    /** 金融动荡: 托帕和账账对拥有火属性弱点的敌方目标造成的伤害提高15%。 */
    static class TopazFinance implements Trace {
        private final double bonus;

        TopazFinance(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "金融动荡";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender instanceof Enemy enemy && enemy.isWeakTo(Element.FIRE) ? 1 + bonus : 1.0;
        }
    }

    /** 技术性调整: 当账账处于【涨幅惊人！】状态施放攻击后，额外使托帕恢复10点能量。 */
    static class TopazAdjust implements Trace {
        private final double energy;

        TopazAdjust(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "技术性调整";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            // Proxy: after Numby's follow-up attack, restore energy.
            if (type == SkillType.COMMON && !targets.isEmpty()) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains " + String.format("%.0f", energy)
                        + " energy (账账 attacked)");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 追加攻击使【负债证明】目标陷入【被执行】，追加攻击暴击伤害提高25%。 */
    static class TopazE1 implements Trace {
        private final double bonus;
        private final int maxStacks;

        TopazE1(double bonus, int maxStacks) {
            this.bonus = bonus;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || targets.isEmpty()) {
                return;
            }
            // Proxy: follow-up attacks mark the target, boosting follow-up crit damage.
            Buff mark = new Buff("被执行", Buff.Category.DEBUFF, owner, targets.getFirst(), 2)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(targets.getFirst(), mark);
        }
    }

    /** 星魂2: 账账自身行动并发动攻击后，托帕恢复5点能量。 */
    static class TopazE2 implements Trace {
        private final double energy;

        TopazE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                owner.gainEnergy(energy);
                log(owner.getName() + " gains " + String.format("%.0f", energy) + " energy");
            }
        }
    }

    /** 星魂4: 账账自身回合开始时，托帕的行动提前20%。 */
    static class TopazE4 implements Trace {
        private final double advance;

        TopazE4(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                battle.advanceByPercent(owner, advance);
                log(owner.getName() + "'s next action advances " + String.format("%.0f", advance * 100) + "%");
            }
        }
    }

    /** 星魂6: 账账攻击次数增加1次，攻击时火属性抗性穿透提高10%。 */
    static class TopazE6 implements Trace {
        private final int extraAttacks;
        private final double penetration;

        TopazE6(int extraAttacks, double penetration) {
            this.extraAttacks = extraAttacks;
            this.penetration = penetration;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && !targets.isEmpty()) {
                owner.removeBuff("星魂6");
                Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                for (int i = 0; i < extraAttacks; i++) {
                    battle.dealAttackDamage(owner, targets.getFirst(), numbyMultiplier(owner, 0.5), 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.FIRE));
                }
            }
        }
    }

    private static void log(String message) {
        IO.println("  [星魂] " + message);
    }
}
