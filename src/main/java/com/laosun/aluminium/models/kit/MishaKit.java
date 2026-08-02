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
 * 米沙 (Misha, cid 1312) — 冰属性 毁灭.
 */
public final class MishaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1312;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MishaRelease(param(byId, 1312101, 0, 0.8)),
                new MishaLock(param(byId, 1312102, 0, 0.6)),
                new MishaTransmission(param(byId, 1312103, 0, 0.3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MishaE1(intParam(e, 0, 1), intParam(e, 1, 5));
            case 2 -> new MishaE2(param(e, 0, 0.16), intParam(e, 1, 3), param(e, 2, 0.24));
            case 4 -> new MishaE4(param(e, 0, 0.06));
            case 6 -> new MishaE6(intParam(e, 0, 1), param(e, 1, 0.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> MishaKit::mishaSkill;
            case 3 -> MishaKit::mishaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 增加#3段下一次终结技的攻击段数, 造成#1%伤害并对相邻目标造成#2%伤害. */
    static void mishaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(1, 0.4);
        int segments = ctx.intParam(2, 1);
        MishaKit.addSegments(user, segments);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " adds " + segments + " ult segment(s) ("
                + MishaKit.segments(user) + "/10)");
    }

    /** 终结技: #1初始段数 (受累积段数影响) 的弹射攻击, 每段前有#3基础概率使目标冻结1回合. */
    static void mishaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.param(1, 0.36);
        double freezeChance = ctx.param(2, 0.12);
        double dotRatio = ctx.param(3, 0.18);
        int maxSegments = ctx.intParam(4, 10);
        int segments = Math.min(maxSegments, MishaKit.segments(user));
        List<Enemy> alive = battle.getAliveEnemies();
        for (int i = 0; i < segments; i++) {
            if (alive.isEmpty()) {
                return;
            }
            CanHit target = i == 0 ? targets.getFirst() : alive.get((int) (Math.random() * alive.size()));
            ctx.dealDamage(battle, user, target, multiplier);
            battle.breakToughness(user, target, ctx.stanceSingle() / segments);
            if (!target.isDeath() && target.getControlState() == null
                    && battle.checkEffectHit(user, target, freezeChance)) {
                double dot = user.getAttribute(AttributeType.ATTACK) != null
                        ? user.getAttribute(AttributeType.ATTACK).get() * dotRatio : 0;
                Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, user, target, 1)
                        .control(Buff.ControlType.FROZEN)
                        .dot(dot, Element.ICE, 1);
                battle.applyBuff(target, freeze);
                target.setControlState(Buff.ControlType.FROZEN);
                IO.println("  " + target.getName() + " is FROZEN!");
            }
        }
        MishaKit.resetSegments(user);
        IO.println("  " + user.getName() + " fires " + segments + " segments (要迟到了!)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 释放: 终结技首段攻击前, 使目标陷入冻结状态的基础概率提高80%。 */
    static class MishaRelease implements Trace {
        private final double bonus;

        MishaRelease(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "释放";
        }
    }

    /** 锁接: 施放终结技时, 效果命中提高60%。 */
    static class MishaLock implements Trace {
        private final double ehr;

        MishaLock(double ehr) {
            this.ehr = ehr;
        }

        @Override
        public String getName() {
            return "锁接";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("锁接", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_HIT_RATE, DoubleValue.Modifier.pure(ehr,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 传冲: 对陷入冻结状态的敌方目标造成伤害时, 暴击伤害提升30%。 */
    static class MishaTransmission implements Trace {
        private final double cdmg;

        MishaTransmission(double cdmg) {
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "传冲";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getControlState() == Buff.ControlType.FROZEN ? 1 + cdmg : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 飘忽的幻影: 施放终结技时, 场上每有1个敌方目标, 攻击段数+1, 最多+5。 */
    static class MishaE1 implements Trace {
        private final int perEnemy;
        private final int maxExtra;

        MishaE1(int perEnemy, int maxExtra) {
            this.perEnemy = perEnemy;
            this.maxExtra = maxExtra;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            int extra = Math.min(maxExtra, battle.getAliveEnemies().size() * perEnemy);
            MishaKit.addSegments(owner, extra);
            IO.println("  [星魂] " + owner.getName() + " gains " + extra + " extra ult segments");
        }
    }

    /** 星魂2 青春的怅望: 终结技每段攻击前, 有24%基础概率使目标防御力降低16%, 持续3回合。 */
    static class MishaE2 implements Trace {
        private final double defDown;
        private final int turns;
        private final double chance;

        MishaE2(double defDown, int turns, double chance) {
            this.defDown = defDown;
            this.turns = turns;
            this.chance = chance;
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
            for (CanHit target : targets) {
                if (!target.isDeath() && battle.checkEffectHit(owner, target, chance)) {
                    Buff debuff = new Buff("星魂2", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }
    }

    /** 星魂4 亲挚的音容: 终结技每段攻击的伤害倍率提高6%。 */
    static class MishaE4 implements Trace {
        private final double bonus;

        MishaE4(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + bonus : 1.0;
        }
    }

    /** 星魂6 久已生疏的憧憬: 施放终结技时, 自身造成的伤害提高30%, 且下一次战技后恢复1个战技点。 */
    static class MishaE6 implements Trace {
        private final int skillPoints;
        private final double dmgBoost;
        private boolean nextSkillReturnsSp = false;

        MishaE6(int skillPoints, double dmgBoost) {
            this.skillPoints = skillPoints;
            this.dmgBoost = dmgBoost;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                nextSkillReturnsSp = true;
                owner.removeBuff("星魂6");
                Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            } else if (type == SkillType.SKILL && nextSkillReturnsSp) {
                nextSkillReturnsSp = false;
                battle.addSkillPoints(skillPoints);
                IO.println("  [星魂] " + owner.getName() + " restores " + skillPoints + " skill point");
            }
        }
    }

    // ─── 终结技段数代理 (天赋 擒纵机构: 我方每消耗1个战技点, 段数+1) ──────

    private static final java.util.Map<CanHit, Integer> SEGMENTS = new java.util.concurrent.ConcurrentHashMap<>();

    static int segments(CanHit owner) {
        return SEGMENTS.getOrDefault(owner, 3);
    }

    static void addSegments(CanHit owner, int amount) {
        SEGMENTS.put(owner, Math.min(10, segments(owner) + amount));
    }

    static void resetSegments(CanHit owner) {
        SEGMENTS.put(owner, 3);
    }
}
