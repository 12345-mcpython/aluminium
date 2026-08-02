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

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 知更鸟 (Robin, cid 1309) — 物理属性 同谐.
 */
public final class RobinKit implements CharacterKit {

    @Override
    public int cid() {
        return 1309;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new RobinColoratura(param(byId, 1309101, 0, 0.25)),
                new RobinImprovisation(param(byId, 1309102, 0, 0.25)),
                new RobinSequence(param(byId, 1309103, 0, 5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new RobinE1(param(e, 0, 0.24));
            case 2 -> new RobinE2(param(e, 0, 0.16), param(e, 1, 1));
            case 4 -> new RobinE4(param(e, 0, 0.5));
            case 6 -> new RobinE6(intParam(e, 0, 8), param(e, 1, 4.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> RobinKit::robinSkill;
            case 3 -> RobinKit::robinUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 我方全体造成的伤害提高#1%, 持续#2回合. */
    static void robinSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double dmgBoost = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("翎之咏叹调", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + ": party DMG +" + String.format("%.0f", dmgBoost * 100)
                + "% (" + turns + " turns)");
    }

    /** 终结技: 进入【协奏】状态#2倒计时回合, 队友立即行动, 我方全体攻击力提高#1+#3. */
    static void robinUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkRatio = ctx.firstParam();
        int countdown = ctx.intParam(1, 90);
        double atkFlat = ctx.param(2, 50);
        double extraMult = ctx.param(3, 0.72);
        int turns = countdown / 90 > 0 ? countdown / 90 : 2;
        double atkBonus = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * atkRatio + atkFlat : atkFlat;
        user.removeBuff("协奏");
        battle.applyBuff(user, new Buff("协奏", Buff.Category.BUFF, user, user, turns));
        // 队友立即行动 + 攻击力提高.
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != user) {
                battle.advanceByPercent(ally, 1.0);
                Buff buff = new Buff("协奏·攻", Buff.Category.BUFF, user, ally, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
        IO.println("  " + user.getName() + " enters 协奏 (concert): party ATK +"
                + String.format("%.0f", atkBonus) + ", allies act immediately!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 华彩花腔: 战斗开始时，自身行动提前25%。 */
    static class RobinColoratura implements Trace {
        private final double advance;

        RobinColoratura(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "华彩花腔";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, advance);
            IO.println("  [行迹] " + owner.getName() + " advances "
                    + String.format("%.0f", advance * 100) + "% at battle start");
        }
    }

    /** 即兴装饰: 处于【协奏】状态时，我方全体发动追加攻击造成的暴击伤害提高25%。 */
    static class RobinImprovisation implements Trace {
        private final double cdmg;

        RobinImprovisation(double cdmg) {
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "即兴装饰";
        }
    }

    /** 模进乐段: 施放战技时额外恢复5点能量。
     *  兼作天赋 调性合颂 的代理: 我方目标攻击后, 知更鸟额外恢复2点能量。 */
    static class RobinSequence implements Trace {
        private final double energy;

        RobinSequence(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "模进乐段";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                owner.gainEnergy(energy);
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner || type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            owner.gainEnergy(2);
            IO.println("  [行迹] " + owner.getName() + " restores 2 energy (调性合颂)");
            // 协奏状态: 队友攻击后, 知更鸟造成附加伤害.
            if (targets != null && !targets.isEmpty()) {
                RobinKit.concertExtraDamage(battle, owner, targets.getFirst());
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 微笑的国度: 处于【协奏】状态时, 我方全体全属性抗性穿透提高24%。 */
    static class RobinE1 implements Trace {
        private final double pen;

        RobinE1(double pen) {
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂2 两者的午茶: 处于【协奏】状态时, 我方全体速度提高16%。 */
    static class RobinE2 implements Trace {
        private final double speed;
        private final double extraEnergy;

        RobinE2(double speed, double extraEnergy) {
            this.speed = speed;
            this.extraEnergy = extraEnergy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂4 雨滴的钥匙: 施放终结技时解除我方全体的控制类负面状态。 */
    static class RobinE4 implements Trace {
        private final double resistance;

        RobinE4(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.getControlState() != null) {
                    ally.setControlState(null);
                }
                ally.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF && b.getControl() != null)
                        .toList()
                        .forEach(b -> battle.removeBuff(ally, b));
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 月隐的午夜: 处于【协奏】状态时, 终结技造成的物理附加伤害暴击伤害额外提高450%。 */
    static class RobinE6 implements Trace {
        private final int maxTriggers;
        private final double cdmg;

        RobinE6(int maxTriggers, double cdmg) {
            this.maxTriggers = maxTriggers;
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 协奏附加伤害代理 (终结技: 队友攻击后, 知更鸟造成1次#4%物理附加伤害) ──

    /** 队友攻击后, 处于【协奏】状态时知更鸟对目标造成附加伤害. */
    static void concertExtraDamage(Battle battle, Character owner, CanHit target) {
        if (owner.isDeath() || !owner.hasBuffNamed("协奏") || target.isDeath()
                || target.getCamp() == owner.getCamp()) {
            return;
        }
        double mult = 0.72;
        com.laosun.aluminium.models.SkillData ult = KitSupport.skillData(owner, SkillType.ULTRA);
        if (ult != null && ult.getSkills() != null && !ult.getSkills().isEmpty()
                && ult.getSkills().getFirst().size() > 3) {
            mult = ult.getSkills().getFirst().get(3);
        }
        battle.dealAttackDamage(owner, target, mult, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.PHYSICAL));
        IO.println("  [协奏] " + owner.getName() + " sings an extra note on " + target.getName());
    }
}
