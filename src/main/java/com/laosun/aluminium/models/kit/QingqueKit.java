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

import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 青雀 (Qingque, cid 1201) — 量子属性 智识.
 */
public final class QingqueKit implements CharacterKit {

    @Override
    public int cid() {
        return 1201;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new QingqueTileBattle(),
                new QingqueListen(param(byId, 1201102, 0, 0.1)),
                new QingqueRobbing(param(byId, 1201103, 0, 0.1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new QingqueE1(param(e, 0, 0.1));
            case 2 -> new QingqueE2(param(e, 0, 1));
            case 4 -> new QingqueE4(param(e, 0, 0.24));
            case 6 -> new QingqueE6();
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> QingqueKit::qingqueSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 抽取琼玉牌, 使自身造成的伤害提高, 可叠加4层.
     * 引擎在战技 (Enhance) 执行后自动进入强化状态 (普攻 → 杠上开花!).
     */
    static void qingqueSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double boostPerCast = ctx.param(1, 0.14);
        int maxStacks = ctx.intParam(2, 4);
        // 层数 = 已有【海底捞月】buff 的层数 + 1 (叠加层数记录在 buff 值上).
        int currentStacks = 0;
        for (Buff buff : user.getBuffs()) {
            if ("海底捞月".equals(buff.getName())
                    && buff.getModifiers() != null && !buff.getModifiers().isEmpty()) {
                currentStacks = (int) Math.round(buff.getModifiers().getFirst().modifier().getValue()
                        / Math.max(1e-6, boostPerCast));
                break;
            }
        }
        int stacks = Math.min(maxStacks, currentStacks + 1);
        user.removeBuff("海底捞月");
        Buff buff = new Buff("海底捞月", Buff.Category.BUFF, user, user, 1)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(
                        boostPerCast * stacks, DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " draws tiles: damage +"
                + String.format("%.0f%%", boostPerCast * stacks * 100) + " (" + stacks + "/" + maxStacks + ")");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 争番: 施放战技时，恢复1个战技点。该效果单场战斗中只能触发1次。 */
    static class QingqueTileBattle implements Trace {
        private boolean used = false;

        @Override
        public String getName() {
            return "争番";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL || used) {
                return;
            }
            used = true;
            battle.addSkillPoints(1);
            IO.println("  [行迹] " + owner.getName() + " restores 1 skill point (争番, once per battle)");
        }
    }

    /** 听牌: 战技使自身造成的伤害提高效果额外提高10%。 */
    static class QingqueListen implements Trace {
        private final double bonus;

        QingqueListen(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "听牌";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            owner.removeBuff("听牌");
            Buff buff = new Buff("听牌", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " gains +" + String.format("%.0f%%", bonus * 100)
                    + " damage (听牌)");
        }
    }

    /** 抢杠: 施放强化普攻后，青雀的速度提高10%，持续1回合。 */
    static class QingqueRobbing implements Trace {
        private final double speedPercent;

        QingqueRobbing(double speedPercent) {
            this.speedPercent = speedPercent;
        }

        @Override
        public String getName() {
            return "抢杠";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || !owner.isEnhanced()) {
                return;
            }
            owner.removeBuff("抢杠");
            Buff buff = new Buff("抢杠", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " speeds up after 杠上开花! (抢杠)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 散勇化骁摸幺鱼: 施放终结技造成的伤害提高10%。 */
    static class QingqueE1 implements Trace {
        private final double bonus;

        QingqueE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + bonus : 1.0;
        }
    }

    /** 星魂2 棋枰作枕好入眠: 青雀每次触发抽牌时，立即恢复1点能量。 */
    static class QingqueE2 implements Trace {
        private final double energy;

        QingqueE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            double tiles = 0;
            if (type == SkillType.SKILL) {
                // 战技抽取 param[0] 张琼玉牌.
                tiles = KitSupport.actionMultiplier(owner, SkillType.SKILL);
            } else if (type == SkillType.ULTRA) {
                // 终结技获得4张相同花色的琼玉牌.
                tiles = 4;
            }
            if (tiles > 0) {
                owner.gainEnergy(energy * tiles);
                IO.println("  [星魂] " + owner.getName() + " draws "
                        + String.format("%.0f", tiles) + " tiles: +"
                        + String.format("%.0f", energy * tiles) + " energy");
            }
        }
    }

    /**
     * 星魂4 帝垣翔鳞和绝张: 施放战技后，有24%的固定概率获得【不求人】状态，
     * 期间施放普攻/强化普攻后立即进行1次等同于普攻伤害100%的追加攻击。
     */
    static class QingqueE4 implements Trace {
        private final double chance;

        QingqueE4(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                if (Math.random() < chance) {
                    owner.removeBuff("不求人");
                    battle.applyBuff(owner, new Buff("不求人", Buff.Category.BUFF, owner, owner, 1));
                    IO.println("  [星魂] " + owner.getName() + " gains 【不求人】!");
                }
            } else if (type == SkillType.COMMON && owner.hasBuffNamed("不求人")) {
                if (targets == null || targets.isEmpty()) {
                    return;
                }
                CanHit main = targets.getFirst();
                if (main.isDeath()) {
                    return;
                }
                double mult = KitSupport.actionMultiplier(owner, SkillType.COMMON);
                battle.dealAttackDamage(owner, main, mult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.QUANTUM));
                owner.removeBuff("不求人");
                IO.println("  [星魂] " + owner.getName() + " follows up on " + main.getName() + " (不求人)");
            }
        }
    }

    /** 星魂6 虚心平意候枭卢: 施放强化普攻后，恢复1个战技点。 */
    static class QingqueE6 implements Trace {
        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && owner.isEnhanced()) {
                battle.addSkillPoints(1);
                IO.println("  [星魂] " + owner.getName() + " restores 1 skill point (杠上开花!)");
            }
        }
    }
}
