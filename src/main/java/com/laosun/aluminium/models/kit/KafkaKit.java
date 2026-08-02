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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 卡芙卡 (Kafka, cid 1005) — 雷属性 虚无.
 */
public final class KafkaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1005;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new KafkaTorment(),
                new KafkaPlunder(param(byId, 1005102, 0, 5)),
                new KafkaThorns(param(byId, 1005103, 0, 0.3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new KafkaE1(param(e, 0, 1.0), param(e, 1, 0.3), intParam(e, 2, 2));
            case 2 -> new KafkaE2(param(e, 0, 0.25));
            case 4 -> new KafkaE4(param(e, 0, 2));
            case 6 -> new KafkaE6(param(e, 0, 1.56), intParam(e, 1, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> KafkaKit::kafkaSkill;
            case 3 -> KafkaKit::kafkaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对主目标及相邻目标造成伤害, 并引爆主目标承受的全部持续伤害. */
    static void kafkaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMultiplier = ctx.firstParam();
        double detonateRatio = ctx.param(1, 0.6);
        double sideMultiplier = ctx.param(2, 0.3);
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        ctx.detonateDots(battle, user, main, detonateRatio);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 终结技: 对敌方全体造成伤害, 并以X%基础概率使目标陷入触电状态. */
    static void kafkaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double shockChance = ctx.param(1, 1.0);
        int shockTurns = ctx.intParam(2, 2);
        double dotRatio = ctx.param(3, 1.16);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.isDeath() || enemy.hasDotOfElement(Element.THUNDER)) {
                continue;
            }
            if (battle.checkEffectHit(user, enemy, shockChance)) {
                ctx.applyShock(battle, user, enemy, dotRatio, shockTurns);
                IO.println("  " + enemy.getName() + " is shocked!");
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 折磨: 施放终结技时，使目标承受的所有持续伤害立即产生伤害。 */
    static class KafkaTorment implements Trace {
        @Override
        public String getName() {
            return "折磨";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit target : targets) {
                if (target.isDeath() || target.getDots().isEmpty()) {
                    continue;
                }
                IO.println("  [行迹] " + owner.getName() + " detonates all DoTs on " + target.getName());
                for (Buff.Dot dot : new ArrayList<>(target.getDots())) {
                    battle.applyDotDamage(target, dot);
                }
            }
        }
    }

    /** 掠夺: 触电状态下的敌方目标被消灭时，额外恢复5点能量。 */
    static class KafkaPlunder implements Trace {
        private final double energy;

        KafkaPlunder(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "掠夺";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (victim instanceof Enemy && victim.hasDotOfElement(Element.THUNDER)) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains "
                        + String.format("%.0f", energy) + " energy (shock victim slain)");
            }
        }
    }

    /** 荆棘: 终结技使敌方目标陷入触电状态的基础概率提高30%。 */
    static class KafkaThorns implements Trace {
        private final double bonus;

        KafkaThorns(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "荆棘";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit target : targets) {
                if (target.isDeath() || target.hasDotOfElement(Element.THUNDER)) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, bonus)) {
                    double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                            ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                    target.applyDot(new Buff.Dot("Shock (触电)", owner, target, dotDamage, Element.THUNDER, 2));
                    IO.println("  [行迹] " + target.getName() + " is shocked");
                }
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 无穷动: 追加攻击使目标受到的持续伤害提高30%，持续2回合。 */
    static class KafkaE1 implements Trace {
        private final double chance;
        private final double dotTaken;
        private final int turns;

        KafkaE1(double chance, double dotTaken, int turns) {
            this.chance = chance;
            this.dotTaken = dotTaken;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "无穷动！无穷";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                for (CanHit target : targets) {
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff buff = new Buff("无穷动", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.DOT_TAKEN, DoubleValue.Modifier.pure(dotTaken,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, buff);
                    }
                }
            }
        }
    }

    /** 星魂2 狂想者: 我方全体造成的持续伤害提高25%。 */
    static class KafkaE2 implements Trace {
        private final double bonus;

        KafkaE2(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "狂想者，呜咽";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("狂想者", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.DOT_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [星魂] party DoT damage +" + String.format("%.0f", bonus * 100) + "%");
        }
    }

    /** 星魂4 把宣叙呈献给: 触电状态产生伤害时，为卡芙卡恢复2点能量。 */
    static class KafkaE4 implements Trace {
        private final double energy;

        KafkaE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "把宣叙呈献给";
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " gains "
                    + String.format("%.0f", energy) + " energy (shock tick)");
        }
    }

    /** 星魂6 回旋: 触电状态伤害倍率提高156%，持续时间增加1回合。 */
    static class KafkaE6 implements Trace {
        private final double damageRatio;
        private final int extraTurns;

        KafkaE6(double damageRatio, int extraTurns) {
            this.damageRatio = damageRatio;
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "回旋，悄悄地";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit target : targets) {
                for (Buff.Dot dot : new ArrayList<>(target.getDots())) {
                    if (dot.getSource() == owner && dot.getElement() == Element.THUNDER) {
                        Buff.Dot boosted = new Buff.Dot(dot.getName(), owner, target,
                                dot.getDamage() * (1 + damageRatio), Element.THUNDER,
                                dot.getDuration() + extraTurns);
                        target.getDots().remove(dot);
                        target.getDots().add(boosted);
                        IO.println("  [星魂] " + target.getName() + "'s shock boosted to "
                                + String.format("%.0f", boosted.getDamage()) + " ("
                                + boosted.getDuration() + " turns)");
                    }
                }
            }
        }
    }
}
