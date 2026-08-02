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
 * 佩拉 (Pela, cid 1106) — 冰属性 虚无.
 */
public final class PelaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1106;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new PelaStrike(param(byId, 1106101, 0, 0.2)),
                new PelaStrategy(param(byId, 1106102, 0, 0.1)),
                new PelaPursuit(param(byId, 1106103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new PelaE1(param(e, 0, 5));
            case 2 -> new PelaE2(param(e, 0, 0.1), intParam(e, 1, 2));
            case 4 -> new PelaE4(param(e, 0, 1.0), param(e, 1, 0.12), intParam(e, 2, 2));
            case 6 -> new PelaE6(param(e, 0, 0.4));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> PelaKit::pelaSkill;
            case 3 -> PelaKit::pelaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 解除指定敌方单体#2个增益效果, 同时造成#1%冰属性伤害. */
    static void pelaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        int dispels = ctx.intParam(1, 1);
        // 解除增益效果; 成功解除时触发行迹 追歼 (下一次攻击伤害提高).
        boolean dispelled = false;
        for (int i = 0; i < dispels; i++) {
            Buff toRemove = target.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.BUFF)
                    .findFirst().orElse(null);
            if (toRemove != null) {
                battle.removeBuff(target, toRemove);
                IO.println("  " + toRemove.getName() + " dispelled from " + target.getName());
                dispelled = true;
            } else {
                break;
            }
        }
        if (dispelled) {
            markEmpowered(user);
        }
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    /** 终结技: 有#1基础概率使敌方单体陷入【通解】状态 (防御力降低#2, 持续#3回合),
     *  同时对敌方全体造成#4%冰属性伤害. */
    static void pelaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double chance = ctx.firstParam();
        double defDown = ctx.param(1, 0.3);
        int turns = ctx.intParam(2, 2);
        double multiplier = ctx.param(3, 0.6);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (battle.checkEffectHit(user, enemy, chance)) {
                Buff debuff = new Buff("通解", Buff.Category.DEBUFF, user, enemy, turns)
                        .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
                IO.println("  " + enemy.getName() + " is under 通解 (DEF -"
                        + String.format("%.0f", defDown * 100) + "%)");
            }
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    /** 追歼 empowered flag: 战技解除增益后, 下一次攻击伤害提高20%. */
    private static final java.util.Map<CanHit, Boolean> EMPOWERED = new java.util.concurrent.ConcurrentHashMap<>();

    static void markEmpowered(CanHit user) {
        EMPOWERED.put(user, true);
    }

    static boolean consumeEmpowered(CanHit user) {
        Boolean flag = EMPOWERED.get(user);
        if (Boolean.TRUE.equals(flag)) {
            EMPOWERED.put(user, false);
            return true;
        }
        return false;
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 痛击: 对处于负面效果的敌方目标造成的伤害提高20%。
     *  兼作天赋 弱点洞察 的代理: 施放攻击后若目标处于负面效果, 额外恢复5点能量。 */
    static class PelaStrike implements Trace {
        private final double bonus;

        PelaStrike(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "痛击";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasDebuff() ? 1 + bonus : 1.0;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            double energy = 5;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                energy = talent.getSkills().getFirst().getFirst();
            }
            if (targets.stream().anyMatch(CanHit::hasDebuff)) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains "
                        + String.format("%.0f", energy) + " energy (弱点洞察)");
            }
        }
    }

    /** 秘策: 佩拉在场时，我方全体的效果命中提高10%。 */
    static class PelaStrategy implements Trace {
        private final double bonus;

        PelaStrategy(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "秘策";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("秘策", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.EFFECT_HIT_RATE, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 追歼: 施放战技解除增益效果时，下一次攻击造成的伤害提高20%。 */
    static class PelaPursuit implements Trace {
        private final double bonus;

        PelaPursuit(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "追歼";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            if (type != SkillType.SKILL && consumeEmpowered(owner)) {
                return 1 + bonus;
            }
            return 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 敌方目标被消灭时，恢复5点能量。 */
    static class PelaE1 implements Trace {
        private final double energy;

        PelaE1(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (victim instanceof Enemy) {
                owner.gainEnergy(energy);
            }
        }
    }

    /** 星魂2: 施放战技解除增益效果时，速度提高10%，持续2回合。 */
    static class PelaE2 implements Trace {
        private final double speedPercent;
        private final int turns;

        PelaE2(double speedPercent, int turns) {
            this.speedPercent = speedPercent;
            this.turns = turns;
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
            Buff buff = new Buff("急智", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4: 战技有100%基础概率使目标冰属性抗性降低12%，持续2回合。 */
    static class PelaE4 implements Trace {
        private final double chance;
        private final double vulnerability;
        private final int turns;

        PelaE4(double chance, double vulnerability, int turns) {
            this.chance = chance;
            this.vulnerability = vulnerability;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (battle.checkEffectHit(owner, target, chance)) {
                    Buff buff = new Buff("冰抗降低", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vulnerability,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    /** 星魂6: 施放攻击后，若目标处于负面效果，对其造成40%攻击力的冰属性附加伤害。 */
    static class PelaE6 implements Trace {
        private final double atkRatio;

        PelaE6(double atkRatio) {
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (targets.isEmpty()) {
                return;
            }
            CanHit target = targets.getFirst();
            if (target.hasDebuff()) {
                battle.dealAttackDamage(owner, target, atkRatio, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
                IO.println("  [星魂] " + owner.getName() + " deals bonus ice damage");
            }
        }
    }
}
