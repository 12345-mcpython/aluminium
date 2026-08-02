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
 * 姬子 (Himeko, cid 1003) — 火属性 智识.
 */
public final class HimekoKit implements CharacterKit {

    @Override
    public int cid() {
        return 1003;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HimekoSpark(param(byId, 1003101, 0, 0.5), intParam(byId, 1003101, 1, 2),
                        param(byId, 1003101, 2, 0.3)),
                new HimekoScorching(param(byId, 1003102, 0, 0.2)),
                new HimekoBeacon(param(byId, 1003103, 0, 0.8), param(byId, 1003103, 1, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HimekoE1(param(e, 0, 0.2), intParam(e, 1, 2));
            case 2 -> new HimekoE2(param(e, 0, 0.5), param(e, 1, 0.15));
            case 4 -> new HimekoE4(param(e, 0, 1));
            case 6 -> new HimekoE6(param(e, 0, 0.4));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 3 ? HimekoKit::himekoUlt : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 对敌方全体造成伤害, 每消灭1个目标额外恢复能量. */
    static void himekoUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double energyPerKill = ctx.param(1, 5);
        List<Enemy> alive = battle.getAliveEnemies();
        int kills = 0;
        for (Enemy enemy : new ArrayList<>(alive)) {
            if (enemy.isDeath()) {
                continue;
            }
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (enemy.isDeath()) {
                kills++;
            }
        }
        if (kills > 0) {
            user.gainEnergy(energyPerKill * kills);
            IO.println("  " + user.getName() + " gains " + String.format("%.0f", energyPerKill * kills)
                    + " energy (" + kills + " kills)");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 星火: 施放攻击后，有50%基础概率使敌方目标陷入灼烧状态2回合。 */
    static class HimekoSpark implements Trace {
        private final double chance;
        private final int turns;
        private final double atkRatio;

        HimekoSpark(double chance, int turns, double atkRatio) {
            this.chance = chance;
            this.turns = turns;
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "星火";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
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

    /** 灼热: 战技对灼烧状态下的敌方目标造成的伤害提高20%。 */
    static class HimekoScorching implements Trace {
        private final double bonus;

        HimekoScorching(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "灼热";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.SKILL && defender.hasDotOfElement(Element.FIRE) ? 1 + bonus : 1.0;
        }
    }

    /** 道标: 若当前生命值百分比 ≥ 80%，暴击率提高15%。 */
    static class HimekoBeacon implements Trace {
        private final double hpThreshold;
        private final double critBonus;

        HimekoBeacon(double hpThreshold, double critBonus) {
            this.hpThreshold = hpThreshold;
            this.critBonus = critBonus;
        }

        @Override
        public String getName() {
            return "道标";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            boolean active = owner.hasBuffNamed("道标");
            boolean shouldBeActive = owner.getHpPercent() >= hpThreshold;
            if (active && !shouldBeActive) {
                owner.removeBuff("道标");
            } else if (!active && shouldBeActive) {
                Buff buff = new Buff("道标", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 童年: 追加攻击触发后，速度提高20%持续2回合。 */
    static class HimekoE1 implements Trace {
        private final double speedPercent;
        private final int turns;

        HimekoE1(double speedPercent, int turns) {
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "童年";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            owner.removeBuff("童年");
            Buff buff = new Buff("童年", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " speeds up after follow-up");
        }
    }

    /** 星魂2 邂逅: 对生命值≤50%的目标造成的伤害提高15%。 */
    static class HimekoE2 implements Trace {
        private final double hpThreshold;
        private final double bonus;

        HimekoE2(double hpThreshold, double bonus) {
            this.hpThreshold = hpThreshold;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "邂逅";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.getHpPercent() <= hpThreshold ? 1 + bonus : 1.0;
        }
    }

    /** 星魂4 投入: 战技造成弱点击破时额外获得1点充能，满3点触发追加攻击。 */
    static class HimekoE4 implements Trace {
        private final double chargeGain;
        private int charge = 0;

        HimekoE4(double chargeGain) {
            this.chargeGain = chargeGain;
        }

        @Override
        public String getName() {
            return "投入";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            boolean broke = targets.stream().anyMatch(t -> t instanceof Enemy e && e.isBroken());
            if (broke) {
                charge += chargeGain;
                IO.println("  [星魂] " + owner.getName() + " gains charge (" + charge + "/3)");
                if (charge >= 3) {
                    charge = 0;
                    double mult = KitSupport.actionMultiplier(owner, SkillType.TALENT);
                    for (Enemy enemy : battle.getAliveEnemies()) {
                        battle.dealAttackDamage(owner, enemy, mult, 0,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                        owner.getElement()));
                    }
                    IO.println("  [星魂] " + owner.getName() + " launches follow-up attack! (乘胜追击)");
                }
            }
        }
    }

    /** 星魂6 开拓！: 终结技额外造成2次伤害，对随机目标各造成原伤害40%。 */
    static class HimekoE6 implements Trace {
        private final double ratio;

        HimekoE6(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "开拓！";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            double mult = KitSupport.actionMultiplier(owner, SkillType.ULTRA) * ratio;
            for (int i = 0; i < 2; i++) {
                List<Enemy> alive = battle.getAliveEnemies();
                if (alive.isEmpty()) {
                    return;
                }
                battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                        mult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
            }
            IO.println("  [星魂] " + owner.getName() + " fires 2 extra hits!");
        }
    }
}
