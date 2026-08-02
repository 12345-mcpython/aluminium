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
 * 忘归人 (Fugue, cid 1225) — 火属性 虚无.
 */
public final class FugueKit implements CharacterKit {

    @Override
    public int cid() {
        return 1225;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new FugueLight(param(byId, 1225101, 0, 0.15)),
                new FugueMountain(param(byId, 1225102, 0, 0.3), intParam(byId, 1225102, 1, 1)),
                new FugueJade(param(byId, 1225103, 0, 0.06), intParam(byId, 1225103, 1, 2),
                        intParam(byId, 1225103, 2, 2), param(byId, 1225103, 3, 2.2),
                        param(byId, 1225103, 4, 0.12)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new FugueE1(param(e, 0, 0.5));
            case 2 -> new FugueE2(param(e, 0, 3), param(e, 1, 0.24));
            case 4 -> new FugueE4(param(e, 0, 0.2));
            case 6 -> new FugueE6(param(e, 0, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> FugueKit::fugueSkill;
            case 3 -> FugueKit::fugueUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 有道祥见，衔书摇风: 使指定我方单体获得【狐祈】 (击破特攻提高#2, 持续#1回合),
     * 并使自身进入【炽灼】状态 (持续#1回合, 期间普攻强化为技能8)。【狐祈】仅对战技最新的
     * 施放目标生效; 星魂6 使【狐祈】对我方全体生效。
     */
    static void fugueSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        int turns = ctx.intParam(0, 3);
        double breakBoost = ctx.param(1, 0.15);
        boolean allAllies = character.getTraces().stream().anyMatch(t -> t instanceof FugueE6);
        if (!allAllies) {
            CanHit holder = allies.getFirst();
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally != holder) {
                    ally.removeBuff("狐祈");
                }
            }
        }
        for (CanHit ally : (allAllies ? battle.getAlivePlayerUnits() : List.of(allies.getFirst()))) {
            ally.removeBuff("狐祈");
            Buff blessing = new Buff("狐祈", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, blessing);
        }
        // 使自身进入【炽灼】状态, 期间普攻获得强化.
        character.removeBuff("炽灼");
        battle.applyBuff(character, new Buff("炽灼", Buff.Category.BUFF, character, character, turns));
        battle.enterEnhancedState(character);
        IO.println("  " + character.getName() + " grants 【狐祈】 and enters 【炽灼】 ("
                + turns + " turns)");
    }

    /** 终结技 阳极照世，离火满缀: 对敌方全体造成#1%伤害, 本次攻击无视弱点属性削减韧性
     *  (以临时火弱点植入代理), 击破弱点时触发火属性的弱点击破效果。 */
    static void fugueUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mult = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamage(user, enemy, mult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.FIRE));
            if (enemy.isBroken()) {
                continue;
            }
            if (!enemy.isWeakTo(Element.FIRE)) {
                enemy.addTemporaryWeakness(Element.FIRE, 2);
            }
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 青丘重光: 我方目标造成弱点击破后额外使敌方目标行动延后#1。
     * 兼作天赋 善盈后福，德气流布 与战技防御降低效果的代理: 忘归人在场时, 我方攻击处于
     * 弱点击破状态的敌方目标后, 将本次攻击的削韧值转化为#1的超击破伤害 (以固定削韧值代理);
     * 【炽灼】状态下, 我方目标每次施放攻击时, 忘归人有#3的基础概率使受到攻击的敌方目标
     * 防御力降低#4, 持续#5回合。【云火昭】 (可被再次削减的额外韧性) 无法由引擎表达, 见 desc。
     */
    static class FugueLight implements Trace {
        private final double delay;

        FugueLight(double delay) {
            this.delay = delay;
        }

        @Override
        public String getName() {
            return "青丘重光";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            battle.delayByPercent(enemy, delay);
            IO.println("  [行迹] " + enemy.getName() + " delayed "
                    + String.format("%.0f%%", delay * 100) + " (青丘重光)");
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!isAttack(type) || targets == null || targets.isEmpty()) {
                return;
            }
            applyFightEffects(battle, owner, actor, targets.getFirst());
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && owner.isEnhanced()) {
                return;
            }
            if (!isAttack(type) || targets == null || targets.isEmpty()) {
                return;
            }
            applyFightEffects(battle, owner, owner, targets.getFirst());
        }

        private void applyFightEffects(Battle battle, Character owner, CanHit attacker, CanHit target) {
            if (target.getCamp() == owner.getCamp() || target.isDeath()) {
                return;
            }
            // 【炽灼】状态下, 攻击使目标防御力降低 (基础概率#3).
            if (owner.hasBuffNamed("炽灼") && battle.checkEffectHit(owner, target, 1.0)) {
                target.removeBuff("炽灼降防");
                Buff debuff = new Buff("炽灼降防", Buff.Category.DEBUFF, owner, target, 2)
                        .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-0.08,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(target, debuff);
            }
            // 天赋: 我方攻击处于弱点击破状态的敌方目标后, 将削韧值转化为#1的超击破伤害.
            if (target instanceof Enemy enemy && enemy.isBroken() && attacker instanceof Character) {
                battle.dealSuperBreakDamage(attacker, enemy, 0.5);
            }
        }
    }

    /** 涂山玄设: 使自身击破特攻提高#1; 施放首次战技后立即恢复#2个战技点。 */
    static class FugueMountain implements Trace {
        private final double breakEffect;
        private final int skillPoints;
        private boolean firstSkill = true;

        FugueMountain(double breakEffect, int skillPoints) {
            this.breakEffect = breakEffect;
            this.skillPoints = skillPoints;
        }

        @Override
        public String getName() {
            return "涂山玄设";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("涂山玄设");
            Buff buff = new Buff("涂山玄设", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakEffect,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL && firstSkill) {
                firstSkill = false;
                battle.addSkillPoints(skillPoints);
                IO.println("  [行迹] " + owner.getName() + " restores " + skillPoints
                        + " skill point (涂山玄设, first skill)");
            }
        }
    }

    /** 玑星太素: 当有敌方目标的弱点被击破时, 使除自身以外的队友击破特攻提高#1; 若忘归人的击破特攻
     *  大于等于#4, 该效果额外提高#5; 持续#2回合, 最多叠加#3层。 */
    static class FugueJade implements Trace {
        private final double breakBoost;
        private final int turns;
        private final int maxStacks;
        private final double threshold;
        private final double extraBoost;

        FugueJade(double breakBoost, int turns, int maxStacks, double threshold, double extraBoost) {
            this.breakBoost = breakBoost;
            this.turns = turns;
            this.maxStacks = maxStacks;
            this.threshold = threshold;
            this.extraBoost = extraBoost;
        }

        @Override
        public String getName() {
            return "玑星太素";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            double ownBreak = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double bonus = breakBoost + (ownBreak >= threshold ? extraBoost : 0);
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally == owner) {
                    continue;
                }
                double current = 0;
                for (Buff buff : ally.getBuffs()) {
                    if ("玑星太素".equals(buff.getName()) && buff.getModifiers() != null
                            && !buff.getModifiers().isEmpty()) {
                        current = buff.getModifiers().getFirst().modifier().getValue();
                        break;
                    }
                }
                int stacks = Math.min(maxStacks, 1 + (int) Math.round(current / Math.max(1e-6, bonus)));
                ally.removeBuff("玑星太素");
                Buff buff = new Buff("玑星太素", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(
                                stacks * bonus, DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] " + owner.getName() + ": allies break effect +"
                    + String.format("%.1f%%", bonus * 100) + " (玑星太素)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 狐尘已去，云驾有期: 持有【狐祈】的我方目标弱点击破效率提高#1。 */
    static class FugueE1 implements Trace {
        private final double breakEfficiency;

        FugueE1(double breakEfficiency) {
            this.breakEfficiency = breakEfficiency;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("狐祈")) {
                    ally.removeBuff("狐尘已去");
                    Buff buff = new Buff("狐尘已去", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(
                                    breakEfficiency, DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }

    /** 星魂2 瑞应之來，必昭有德: 当有敌方目标的弱点被击破时, 为忘归人恢复#1点能量;
     *  施放终结技后, 我方全体行动提前#2。 */
    static class FugueE2 implements Trace {
        private final double energy;
        private final double advance;

        FugueE2(double energy, double advance) {
            this.energy = energy;
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (瑞应之來)");
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                battle.advanceByPercent(ally, advance);
            }
            IO.println("  [星魂] " + owner.getName() + ": party advances "
                    + String.format("%.0f%%", advance * 100) + " (瑞应之來)");
        }
    }

    /** 星魂4 自我离形，而今几姓: 持有【狐祈】的我方目标造成的击破伤害提高#1
     *  (以击破特攻buff代理, 见 desc)。 */
    static class FugueE4 implements Trace {
        private final double breakBoost;

        FugueE4(double breakBoost) {
            this.breakBoost = breakBoost;
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
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("狐祈")) {
                    ally.removeBuff("自我离形");
                    Buff buff = new Buff("自我离形", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakBoost,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }

    /** 星魂6 肇素未来，晦明兴灭: 忘归人的弱点击破效率提高#1; 处于【炽灼】状态时【狐祈】效果
     *  对我方全体生效 (由战技行为代理)。 */
    static class FugueE6 implements Trace {
        private final double breakEfficiency;

        FugueE6(double breakEfficiency) {
            this.breakEfficiency = breakEfficiency;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("肇素未来");
            Buff buff = new Buff("肇素未来", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(breakEfficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 是否为攻击类行动 (普攻/战技/终结技). */
    static boolean isAttack(SkillType type) {
        return type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA;
    }
}
