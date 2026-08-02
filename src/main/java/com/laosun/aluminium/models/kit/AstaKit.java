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
 * 艾丝妲 (Asta, cid 1009) — 火属性 同谐.
 */
public final class AstaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1009;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AstaSpark(param(byId, 1009101, 0, 0.8), intParam(byId, 1009101, 1, 3),
                        param(byId, 1009101, 2, 0.5)),
                new AstaIgnite(param(byId, 1009102, 0, 0.18)),
                new AstaConstellation(param(byId, 1009103, 0, 0.06)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AstaE1();
            case 2 -> new AstaE2(0.07, 5, 3); // 代理天赋 天象学 的蓄能数值
            case 4 -> new AstaE4(intParam(e, 0, 2), param(e, 1, 0.15));
            case 6 -> new AstaE6(intParam(e, 0, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 3 ? AstaKit::astaUlt : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 我方全体速度提高X点, 持续若干回合. */
    static void astaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double speed = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            Buff buff = new Buff("星空祝言", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speed,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 火花: 施放普攻时，有80%基础概率使敌方目标陷入灼烧状态3回合。 */
    static class AstaSpark implements Trace {
        private final double chance;
        private final int turns;
        private final double atkRatio;

        AstaSpark(double chance, int turns, double atkRatio) {
            this.chance = chance;
            this.turns = turns;
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "火花";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                for (CanHit target : targets) {
                    if (!target.isDeath() && target instanceof Enemy) {
                        if (battle.checkEffectHit(owner, target, chance)) {
                            double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                                    ? owner.getAttribute(AttributeType.ATTACK).get() * atkRatio : 0;
                            target.applyDot(new Buff.Dot("Burn (灼烧)", owner, target, dotDamage, Element.FIRE, turns));
                            IO.println("  [行迹] " + target.getName() + " is burning ("
                                    + String.format("%.0f", dotDamage) + " fire DoT x" + turns + ")");
                        }
                    }
                }
            }
        }
    }

    /** 点燃: 艾丝妲在场时，我方全体的火属性伤害提高18%。 */
    static class AstaIgnite implements Trace {
        private final double bonus;

        AstaIgnite(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "点燃";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("点燃", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.FIRE_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] " + owner.getName() + ": party fire damage +"
                    + String.format("%.0f", bonus * 100) + "%");
        }
    }

    /** 星座: 艾丝妲每拥有1层蓄能，自身防御力提高6%。 */
    static class AstaConstellation implements Trace {
        private final double defPerStack;
        private int stacks = 0;

        AstaConstellation(double defPerStack) {
            this.defPerStack = defPerStack;
        }

        @Override
        public String getName() {
            return "星座";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            stacks = Math.min(5, stacks + 1);
            owner.removeBuff("星座");
            Buff buff = new Buff("星座", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(defPerStack * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " stacks: " + stacks + " (DEF +"
                    + String.format("%.0f", defPerStack * stacks * 100) + "%)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 星有无言之歌: 施放战技时，对敌方随机单体额外造成1次伤害。 */
    static class AstaE1 implements Trace {
        @Override
        public String getName() {
            return "星有无言之歌";
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
            battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                    mult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
            IO.println("  [星魂] " + owner.getName() + " deals an extra hit!");
        }
    }

    /** 星魂2 月见圆缺之意: 施放终结技时，艾丝妲下回合不会减少蓄能层数。
     *  兼作天赋 天象学 的蓄能代理: 每层使我方全体攻击力提高7%, 上限5层,
     *  每回合开始时层数减少3层 (星魂6 使减少量降低1). */
    static class AstaE2 implements Trace {
        private final double atkPerStack;
        private final int maxStacks;
        private final int decay;
        private int stacks = 0;

        AstaE2(double atkPerStack, int maxStacks, int decay) {
            this.atkPerStack = atkPerStack;
            this.maxStacks = maxStacks;
            this.decay = decay;
        }

        @Override
        public String getName() {
            return "月见圆缺之意";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                stacks = Math.min(maxStacks, stacks + 1);
                refresh(battle, owner);
            } else if (type == SkillType.ULTRA) {
                owner.removeBuff("蓄能不衰");
                Buff buff = new Buff("蓄能不衰", Buff.Category.BUFF, owner, owner, 2);
                battle.applyBuff(owner, buff);
                IO.println("  [星魂] " + owner.getName() + ": 下回合不会减少蓄能层数");
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (owner.hasBuffNamed("蓄能不衰")) {
                owner.removeBuff("蓄能不衰");
                IO.println("  [星魂] " + owner.getName() + ": 蓄能层数未减少 (月见圆缺之意)");
                return;
            }
            int reduce = owner.hasBuffNamed("眠于银河之下") ? decay - 1 : decay;
            stacks = Math.max(0, stacks - reduce);
            refresh(battle, owner);
            IO.println("  [星魂] " + owner.getName() + ": 蓄能层数减少" + reduce);
        }

        private void refresh(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("天象学");
            }
            if (stacks <= 0) {
                return;
            }
            double atk = atkPerStack * stacks;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("天象学", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atk,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [星魂] " + owner.getName() + ": 蓄能" + stacks + "层, 我方全体攻击力+"
                    + String.format("%.1f", atk * 100) + "%");
        }
    }

    /** 星魂4 极光显现之时: 蓄能层数≥2时，能量恢复效率提高15%。 */
    static class AstaE4 implements Trace {
        private final int stackThreshold;
        private final double regenBonus;
        private int skillCasts = 0;

        AstaE4(int stackThreshold, double regenBonus) {
            this.stackThreshold = stackThreshold;
            this.regenBonus = regenBonus;
        }

        @Override
        public String getName() {
            return "极光显现之时";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                skillCasts++;
                refresh(battle, owner);
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            boolean active = owner.hasBuffNamed("极光显现");
            boolean shouldBe = skillCasts >= stackThreshold;
            if (active && !shouldBe) {
                owner.removeBuff("极光显现");
            } else if (!active && shouldBe) {
                Buff buff = new Buff("极光显现", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(regenBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    /** 星魂6 眠于银河之下: 天赋每回合减少的蓄能层数降低1。
     *  与星魂2 月见圆缺之意 的蓄能系统联动 (蓄能减少量 3 → 2). */
    static class AstaE6 implements Trace {
        AstaE6(int ignored) {
            // 数值已并入 AstaE2 的衰减逻辑 (每回合减少量 -1).
        }

        @Override
        public String getName() {
            return "眠于银河之下";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("眠于银河之下", Buff.Category.BUFF, owner, owner, -1);
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + ": 蓄能层数每回合减少量降低1");
        }
    }
}
