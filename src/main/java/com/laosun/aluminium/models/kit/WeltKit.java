package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
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
 * 瓦尔特 (Welt, cid 1004) — 虚数属性 虚无.
 */
public final class WeltKit implements CharacterKit {

    @Override
    public int cid() {
        return 1004;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new WeltPunishment(param(byId, 1004101, 0, 1.0), param(byId, 1004101, 1, 0.12),
                        intParam(byId, 1004101, 2, 2)),
                new WeltJudgement(param(byId, 1004102, 0, 10)),
                new WeltVerdict(param(byId, 1004103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new WeltE1(param(e, 0, 0.5), param(e, 1, 0.8), intParam(e, 2, 2));
            case 2 -> new WeltE2(param(e, 0, 3));
            case 4 -> new WeltE4(param(e, 0, 0.35));
            case 6 -> new WeltE6();
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> WeltKit::weltSkill;
            case 3 -> WeltKit::weltUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对指定敌方单体造成3段伤害, 每段有X%基础概率使目标减速. */
    static void weltSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double slowChance = ctx.param(1, 0.65);
        double slowValue = ctx.param(2, 0.1);
        int slowTurns = ctx.intParam(3, 2);
        int hits = 3; // 数据: 1段主目标 + 2段随机
        List<Enemy> alive = battle.getAliveEnemies();
        for (int i = 0; i < hits; i++) {
            if (alive.isEmpty()) {
                return;
            }
            CanHit target = i == 0 ? targets.getFirst() : alive.get((int) (Math.random() * alive.size()));
            ctx.dealDamage(battle, user, target, multiplier);
            battle.breakToughness(user, target, ctx.stanceSingle() / hits);
            if (battle.checkEffectHit(user, target, slowChance)) {
                ctx.applySlow(battle, user, target, slowValue, slowTurns);
            }
        }
    }

    /** 终结技: 对敌方全体造成伤害, 使目标禁锢 (行动延后) 并减速. */
    static void weltUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double delay = ctx.param(1, 0.32);
        double imprisonChance = ctx.param(2, 1.0);
        double slowValue = ctx.param(3, 0.1);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.isDeath() || enemy.getControlState() != null) {
                continue;
            }
            if (battle.checkEffectHit(user, enemy, imprisonChance)) {
                Buff imprison = new Buff("Imprisonment", Buff.Category.DEBUFF, user, enemy, 1)
                        .control(Buff.ControlType.IMPRISONED)
                        .delayOnExpire(delay);
                battle.applyBuff(enemy, imprison);
                enemy.setControlState(Buff.ControlType.IMPRISONED);
                ctx.applySlow(battle, user, enemy, slowValue, 1);
                IO.println("  " + enemy.getName() + " is IMPRISONED!");
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 惩戒: 施放终结技时，有100%基础概率使目标受到的伤害提高12%，持续2回合。 */
    static class WeltPunishment implements Trace {
        private final double chance;
        private final double vuln;
        private final int turns;

        WeltPunishment(double chance, double vuln, int turns) {
            this.chance = chance;
            this.vuln = vuln;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "惩戒";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit target : targets) {
                if (battle.checkEffectHit(owner, target, chance)) {
                    Buff buff = new Buff("惩戒", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    /** 审判: 施放终结技时，额外恢复10点能量。 */
    static class WeltJudgement implements Trace {
        private final double energy;

        WeltJudgement(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "审判";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains "
                        + String.format("%.0f", energy) + " energy");
            }
        }
    }

    /** 裁决: 对被弱点击破的敌方目标造成的伤害提高20%。 */
    static class WeltVerdict implements Trace {
        private final double bonus;

        WeltVerdict(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "裁决";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender instanceof Enemy enemy && enemy.isBroken() ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 名的传承: 终结技后，接下来2次普攻/战技额外造成附加伤害。 */
    static class WeltE1 implements Trace {
        private final double basicRatio;
        private final double skillRatio;
        private int remaining = 0;

        WeltE1(double basicRatio, double skillRatio, int count) {
            this.basicRatio = basicRatio;
            this.skillRatio = skillRatio;
            this.remaining = count;
        }

        @Override
        public String getName() {
            return "名的传承";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                remaining = 2;
                IO.println("  [星魂] " + owner.getName() + " is enhanced: next 2 attacks deal additional damage");
                return;
            }
            if ((type == SkillType.COMMON || type == SkillType.SKILL) && remaining > 0 && !targets.isEmpty()) {
                double ratio = type == SkillType.COMMON ? basicRatio : skillRatio;
                double mult = KitSupport.actionMultiplier(owner, type) * ratio;
                battle.dealAttackDamage(owner, targets.getFirst(), mult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
                remaining--;
                IO.println("  [星魂] " + owner.getName() + " deals additional damage (" + remaining + " left)");
            }
        }
    }

    /** 星魂2 星的凝聚: 触发天赋时恢复3点能量。 */
    static class WeltE2 implements Trace {
        private final double energy;

        WeltE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星的凝聚";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                boolean slowed = targets.stream().anyMatch(t -> t.hasBuffNamed("Slow") || t.hasBuffNamed("Slow+"));
                if (slowed) {
                    owner.gainEnergy(energy);
                    IO.println("  [星魂] " + owner.getName() + " gains "
                            + String.format("%.0f", energy) + " energy (talent)");
                }
            }
        }
    }

    /** 星魂4 义的名号: 施放战技时，使目标速度降低的基础概率提高35%。 */
    static class WeltE4 implements Trace {
        private final double chanceBonus;

        WeltE4(double chanceBonus) {
            this.chanceBonus = chanceBonus;
        }

        @Override
        public String getName() {
            return "义的名号";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            // 战技减速基础概率 65% + 35% = 100%.
            for (CanHit target : targets) {
                if (target.isDeath() || target.hasBuffNamed("Slow")) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, 1.0)) {
                    Buff slow = new Buff("Slow", Buff.Category.DEBUFF, owner, target, 2)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-0.1,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, slow);
                    IO.println("  [星魂] " + target.getName() + " is slowed (义的名号)");
                }
            }
        }
    }

    /** 星魂6 光明的未来: 施放战技时，对随机敌方单体额外造成1次伤害。 */
    static class WeltE6 implements Trace {
        @Override
        public String getName() {
            return "光明的未来";
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
}
