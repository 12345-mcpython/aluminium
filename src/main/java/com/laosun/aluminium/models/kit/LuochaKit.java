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
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 罗刹 (Luocha, cid 1203) — 虚数属性 丰饶.
 */
public final class LuochaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1203;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new LuochaSoak(intParam(byId, 1203101, 0, 1)),
                new LuochaWater(param(byId, 1203102, 0, 0.07), param(byId, 1203102, 1, 93)),
                new LuochaValley(param(byId, 1203103, 0, 0.7)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new LuochaE1(param(e, 0, 0.2));
            case 2 -> new LuochaE2(param(e, 0, 0.3), param(e, 1, 0.18), param(e, 2, 240), intParam(e, 3, 2));
            case 4 -> new LuochaE4(param(e, 0, 0.12));
            case 6 -> new LuochaE6(param(e, 0, 1.0), param(e, 1, 0.2), intParam(e, 2, 2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> LuochaKit::luochaSkill;
            case 3 -> LuochaKit::luochaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 立即为指定我方单体回复 攻击力X%+固定值 的生命值, 并获得1层【白花之刻】。
     * (通用 Restore 以生命上限结算, 不适用于罗刹.)
     */
    static void luochaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double healRatio = ctx.param(0, 0.4);
        double flat = ctx.param(1, 200);
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        CanHit target = allies.getFirst();
        double atk = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
        battle.healTarget(user, target, atk * healRatio + flat);
        addWhiteFlower(battle, user);
    }

    /**
     * 终结技: 解除敌方全体1个增益效果, 对敌方全体造成 攻击力X% 的虚数伤害,
     * 并获得1层【白花之刻】。
     */
    static void luochaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int cleanse = ctx.intParam(1, 1);
        for (Enemy enemy : battle.getAliveEnemies()) {
            // 解除指定数量的增益效果.
            int removed = 0;
            for (Buff buff : new ArrayList<>(enemy.getBuffs())) {
                if (buff.getCategory() == Buff.Category.BUFF) {
                    battle.removeBuff(enemy, buff);
                    IO.println("  " + enemy.getName() + "'s [" + buff.getName() + "] dispelled");
                    removed++;
                    if (removed >= cleanse) {
                        break;
                    }
                }
            }
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        addWhiteFlower(battle, user);
    }

    /** 白花之刻 +1: 达到2层时消耗全部并展开结界 (状态在行迹 浇灌尘身 中). */
    private static void addWhiteFlower(Battle battle, CanHit user) {
        if (!(user instanceof Character character)) {
            return;
        }
        for (Trace trace : character.getTraces()) {
            if (trace instanceof LuochaWater water) {
                water.addWhiteFlower(battle, character);
                return;
            }
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 浸池苏生: 触发战技效果时，解除指定我方单体的1个负面效果。 */
    static class LuochaSoak implements Trace {
        private final int cleanseCount;

        LuochaSoak(int cleanseCount) {
            this.cleanseCount = cleanseCount;
        }

        @Override
        public String getName() {
            return "浸池苏生";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, owner, targets);
            if (allies.isEmpty()) {
                return;
            }
            CanHit toCleanse = allies.getFirst();
            int removed = 0;
            for (Buff buff : new ArrayList<>(toCleanse.getBuffs())) {
                if (buff.getCategory() == Buff.Category.DEBUFF) {
                    battle.removeBuff(toCleanse, buff);
                    IO.println("  [行迹] " + buff.getName() + " dispelled from " + toCleanse.getName());
                    removed++;
                    if (removed >= cleanseCount) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * 浇灌尘身: 处于结界中的任意敌方目标受到我方攻击后, 除攻击者外的我方目标
     * 回复 攻击力X%+固定值 的生命值。
     * 同时代理天赋 生息的轮转: 结界状态、白花之刻计数、结界内攻击者回血,
     * 以及战技的被动自动触发 (我方目标生命≤50%时立即触发1次战技效果).
     */
    static class LuochaWater implements Trace {
        private final double allyHealRatio;
        private final double allyHealFlat;
        private int whiteFlower = 0;
        private boolean fieldActive = false;
        private int fieldTurns = 0;
        private int autoHealCooldown = 0;

        LuochaWater(double allyHealRatio, double allyHealFlat) {
            this.allyHealRatio = allyHealRatio;
            this.allyHealFlat = allyHealFlat;
        }

        @Override
        public String getName() {
            return "浇灌尘身";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (autoHealCooldown > 0) {
                autoHealCooldown--;
            }
            if (fieldActive) {
                fieldTurns--;
                if (fieldTurns <= 0) {
                    fieldActive = false;
                    IO.println("  [行迹] 结界 dissipates");
                }
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                // 天赋: 处于结界中的任意敌方目标受到攻击后, 施放攻击的我方目标立即回血.
                fieldHeal(battle, owner, owner);
            }
            checkAutoHeal(battle, owner);
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                fieldHeal(battle, owner, actor);
            }
            checkAutoHeal(battle, owner);
        }

        /** 结界内攻击者回血 (天赋) + 除攻击者外的我方目标回血 (行迹). */
        private void fieldHeal(Battle battle, Character owner, CanHit attacker) {
            if (!fieldActive) {
                return;
            }
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            // 天赋 生息的轮转: 施放攻击的我方目标立即回复 攻击力12%+60.
            double fieldRatio = 0.12;
            double fieldFlat = 60;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 3) {
                fieldRatio = talent.getSkills().getFirst().get(1);
                fieldFlat = talent.getSkills().getFirst().get(3);
            }
            battle.healTarget(owner, attacker, atk * fieldRatio + fieldFlat);
            // 行迹 浇灌尘身: 除攻击者外的我方目标也回复.
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally != attacker) {
                    battle.healTarget(owner, ally, atk * allyHealRatio + allyHealFlat);
                }
            }
        }

        /** 战技被动: 我方任意单体生命≤50%时立即触发1次战技效果, 2回合后可再次触发. */
        private void checkAutoHeal(Battle battle, Character owner) {
            if (autoHealCooldown > 0) {
                return;
            }
            CanHit lowest = battle.getAlivePlayerUnits().stream()
                    .filter(u -> u.getHpPercent() <= 0.5)
                    .min(java.util.Comparator.comparingDouble(CanHit::getHpPercent))
                    .orElse(null);
            if (lowest == null) {
                return;
            }
            double ratio = 0.4;
            double flat = 200;
            int cooldown = 2;
            SkillData skill = KitSupport.skillData(owner, SkillType.SKILL);
            if (skill != null && skill.getSkills() != null && !skill.getSkills().isEmpty()) {
                List<Double> params = skill.getSkills().getFirst();
                if (!params.isEmpty()) {
                    ratio = params.getFirst();
                }
                if (params.size() > 1) {
                    flat = params.get(1);
                }
                if (params.size() > 3) {
                    cooldown = params.get(3).intValue();
                }
            }
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            battle.healTarget(owner, lowest, atk * ratio + flat);
            autoHealCooldown = cooldown;
            IO.println("  [行迹] 白花的祈望 auto-heals " + lowest.getName()
                    + " (HP " + String.format("%.0f%%", lowest.getHpPercent() * 100) + ")");
        }

        /** 白花之刻 +1; 达到2层时展开结界 (2回合), 并触发星魂1 (结界内全体攻击力提升). */
        void addWhiteFlower(Battle battle, Character owner) {
            whiteFlower++;
            IO.println("  " + owner.getName() + " gains 【白花之刻】 (" + whiteFlower + "/2)");
            if (whiteFlower < 2) {
                return;
            }
            whiteFlower = 0;
            fieldActive = true;
            fieldTurns = 2;
            IO.println("  " + owner.getName() + " opens the 结界 (2 turns)!");
            // 星魂1: 结界生效时我方全体攻击力提高.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof LuochaE1 e1) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.removeBuff("濯洗生者");
                        Buff buff = new Buff("濯洗生者", Buff.Category.BUFF, owner, ally, 2)
                                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(e1.bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                    IO.println("  [星魂] party ATK +" + String.format("%.0f%%", e1.bonus * 100) + " (结界)");
                }
            }
        }
    }

    /** 行过幽谷: 抵抗控制类负面状态的概率提高70%。 */
    static class LuochaValley implements Trace {
        private final double resistance;

        LuochaValley(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "行过幽谷";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("行过幽谷");
            Buff buff = new Buff("行过幽谷", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 濯洗生者: 结界生效时我方全体攻击力提高20% (在 浇灌尘身 中触发). */
    static class LuochaE1 implements Trace {
        private final double bonus;

        LuochaE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 净庭赐礼: 触发战技效果时, 若目标生命<50%则治疗量提高30%, 否则提供护盾。 */
    static class LuochaE2 implements Trace {
        private final double healBoost;
        private final double shieldRatio;
        private final double shieldFlat;
        private final int shieldTurns;

        LuochaE2(double healBoost, double shieldRatio, double shieldFlat, int shieldTurns) {
            this.healBoost = healBoost;
            this.shieldRatio = shieldRatio;
            this.shieldFlat = shieldFlat;
            this.shieldTurns = shieldTurns;
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
            List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, owner, targets);
            if (allies.isEmpty()) {
                return;
            }
            CanHit target = allies.getFirst();
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            if (target.getHpPercent() < 0.5) {
                // 治疗量提高30%.
                double baseHeal = atk * 0.4 + 200;
                battle.healTarget(owner, target, baseHeal * healBoost);
                IO.println("  [星魂] " + target.getName() + "'s healing +"
                        + String.format("%.0f%%", healBoost * 100) + " (净庭赐礼)");
            } else {
                double shield = atk * shieldRatio + shieldFlat;
                battle.applyShield(target, shield, owner);
                target.setShieldTurns(shieldTurns);
                IO.println("  [星魂] " + target.getName() + " gains a shield (净庭赐礼)");
            }
        }
    }

    /** 星魂4 荆冠审判: 结界生效时使敌方目标陷入虚弱状态, 造成的伤害降低12%。 */
    static class LuochaE4 implements Trace {
        private final double weakness;

        LuochaE4(double weakness) {
            this.weakness = weakness;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean fieldActive = false;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof LuochaWater water && water.fieldActive) {
                    fieldActive = true;
                    break;
                }
            }
            if (!fieldActive) {
                return;
            }
            for (Enemy enemy : battle.getAliveEnemies()) {
                enemy.removeBuff("荆冠审判");
                Buff buff = new Buff("荆冠审判", Buff.Category.DEBUFF, owner, enemy, 2)
                        .stat(AttributeType.WEAKNESS_RATIO, DoubleValue.Modifier.pure(weakness,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
            IO.println("  [星魂] enemies weakened inside the 结界 (荆冠审判)");
        }
    }

    /** 星魂6 皆归尘土: 施放终结技时, 有100%固定概率使敌方全体全属性抗性降低20%, 持续2回合。 */
    static class LuochaE6 implements Trace {
        private final double chance;
        private final double allResDown;
        private final int turns;

        LuochaE6(double chance, double allResDown, int turns) {
            this.chance = chance;
            this.allResDown = allResDown;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (battle.checkEffectHit(owner, enemy, chance)) {
                    enemy.removeBuff("皆归尘土");
                    Buff buff = new Buff("皆归尘土", Buff.Category.DEBUFF, owner, enemy, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(allResDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, buff);
                }
            }
            // 注: 全属性抗性降低以易伤 (受到的伤害提高) 近似, 引擎无敌方抗性 debuff 通道.
            IO.println("  [星魂] enemies' all-res down (皆归尘土)");
        }
    }
}
