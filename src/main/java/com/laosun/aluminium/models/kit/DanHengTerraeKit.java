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
 * 丹恒•腾荒 (Dan Heng • Permansor Terrae, cid 1414) — 物理属性 存护.
 */
public final class DanHengTerraeKit implements CharacterKit {

    @Override
    public int cid() {
        return 1414;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new DanHengTerraeShenxiu(param(byId, 1414101, 0, 0.15)),
                new DanHengTerraeWeirui(param(byId, 1414102, 0, 0.4), param(byId, 1414102, 1, 6),
                        param(byId, 1414102, 2, 0.15)),
                new DanHengTerraeZhengrong(param(byId, 1414103, 0, 0.4), param(byId, 1414103, 1, 0.05),
                        param(byId, 1414103, 2, 100), intParam(byId, 1414103, 3, 3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new DanHengTerraeE1(intParam(e, 0, 1), param(e, 1, 0.18), intParam(e, 2, 3));
            case 2 -> new DanHengTerraeE2(intParam(e, 0, 2), param(e, 1, 2), param(e, 2, 2));
            case 4 -> new DanHengTerraeE4(param(e, 0, 0.2));
            case 6 -> new DanHengTerraeE6(param(e, 0, 0.2), param(e, 1, 3.3), param(e, 2, 0.12));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> DanHengTerraeKit::danHengTerraeSkill;
            case 3 -> DanHengTerraeKit::danHengTerraeUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使指定我方单体成为【同袍】, 为我方全体提供#1攻击力+#2护盾, 持续#3回合。 */
    static void danHengTerraeSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkRatio = ctx.firstParam();
        double flat = ctx.param(1, 100);
        int turns = ctx.intParam(2, 3);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("同袍");
            }
            Buff mark = new Buff("同袍", Buff.Category.BUFF, user, target, -1);
            battle.applyBuff(target, mark);
            double atk = user.getAttribute(AttributeType.ATTACK) != null
                    ? user.getAttribute(AttributeType.ATTACK).get() : 0;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                battle.applyShield(ally, atk * atkRatio + flat, user);
                ally.setShieldTurns(turns);
            }
            IO.println("  " + target.getName() + " becomes 同袍, party shielded by "
                    + String.format("%.0f", atk * atkRatio + flat));
        }
    }

    /** 终结技: 对敌方全体造成#1%伤害, 为我方全体提供#4攻击力+#5护盾, 使【龙灵】获得强化#3次行动。 */
    static void danHengTerraeUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double dragonMult = ctx.param(1, 0.4);
        int enhancedActions = ctx.intParam(2, 2);
        double shieldRatio = ctx.param(3, 0.14);
        double shieldFlat = ctx.param(4, 100);
        int shieldTurns = ctx.intParam(5, 3);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        double atk = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            battle.applyShield(ally, atk * shieldRatio + shieldFlat, user);
            ally.setShieldTurns(shieldTurns);
        }
        user.removeBuff("龙灵强化");
        Buff buff = new Buff("龙灵强化", Buff.Category.BUFF, user, user, enhancedActions);
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " 亢龙无悔: 龙灵 enhanced for "
                + enhancedActions + " actions");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 神秀: 施放战技时, 使成为【同袍】的目标攻击力提高 (等同于丹恒•腾荒15%攻击力)。 */
    static class DanHengTerraeShenxiu implements Trace {
        private final double atkRatio;

        DanHengTerraeShenxiu(double atkRatio) {
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "神秀";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("同袍")) {
                    double atk = owner.getAttribute(AttributeType.ATTACK) != null
                            ? owner.getAttribute(AttributeType.ATTACK).get() * atkRatio : 0;
                    Buff buff = new Buff("神秀", Buff.Category.BUFF, owner, ally, 3)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atk,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }

    /** 葳蕤: 战斗开始时行动提前40%; 【同袍】施放攻击时恢复6点能量。 */
    static class DanHengTerraeWeirui implements Trace {
        private final double advance;
        private final double energy;
        private final double dragonAdvance;

        DanHengTerraeWeirui(double advance, double energy, double dragonAdvance) {
            this.advance = advance;
            this.energy = energy;
            this.dragonAdvance = dragonAdvance;
        }

        @Override
        public String getName() {
            return "葳蕤";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, advance);
            IO.println("  [行迹] " + owner.getName() + " advances " + String.format("%.0f", advance * 100)
                    + "% at battle start");
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor != owner && actor.hasBuffNamed("同袍")) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (同袍 attack)");
            }
        }
    }

    /** 峥嵘: 【龙灵】行动时, 为护盾最低的我方目标提供#2攻击力+#3护盾。 */
    static class DanHengTerraeZhengrong implements Trace {
        private final double companionRatio;
        private final double shieldRatio;
        private final double flat;
        private final int cap;

        DanHengTerraeZhengrong(double companionRatio, double shieldRatio, double flat, int cap) {
            this.companionRatio = companionRatio;
            this.shieldRatio = shieldRatio;
            this.flat = flat;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "峥嵘";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 蜕却旧鳞的荒龙: 施放终结技时恢复1个战技点, 【同袍】全属性抗性穿透提高18%持续3回合。 */
    static class DanHengTerraeE1 implements Trace {
        private final int skillPoints;
        private final double pen;
        private final int turns;

        DanHengTerraeE1(int skillPoints, double pen, int turns) {
            this.skillPoints = skillPoints;
            this.pen = pen;
            this.turns = turns;
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
            battle.addSkillPoints(skillPoints);
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("同袍")) {
                    Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }

    /** 星魂2 守望开拓的赤子: 终结技的强化龙灵行动效果额外增加2次。 */
    static class DanHengTerraeE2 implements Trace {
        private final int extraActions;
        private final double companionRatio;
        private final double shieldRatio;

        DanHengTerraeE2(int extraActions, double companionRatio, double shieldRatio) {
            this.extraActions = extraActions;
            this.companionRatio = companionRatio;
            this.shieldRatio = shieldRatio;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 金石立誓，此身为舟: 【同袍】受到的伤害降低20%。 */
    static class DanHengTerraeE4 implements Trace {
        private final double reduction;

        DanHengTerraeE4(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 草木微尘皆入梦中: 场上存在【同袍】时, 敌方全体受到的伤害提高20%。 */
    static class DanHengTerraeE6 implements Trace {
        private final double vuln;
        private final double companionRatio;
        private final double defIgnore;

        DanHengTerraeE6(double vuln, double companionRatio, double defIgnore) {
            this.vuln = vuln;
            this.companionRatio = companionRatio;
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂6", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }
}
