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
 * 希露瓦 (Serval, cid 1103) — 雷属性 智识.
 */
public final class ServalKit implements CharacterKit {

    @Override
    public int cid() {
        return 1103;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new ServalRock(param(byId, 1103101, 0, 0.2)),
                new ServalElectric(param(byId, 1103102, 0, 15)),
                new ServalFrenzy(param(byId, 1103103, 0, 0.2), intParam(byId, 1103103, 1, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new ServalE1(param(e, 0, 0.6));
            case 2 -> new ServalE2(param(e, 0, 4));
            case 4 -> new ServalE4(param(e, 0, 1.0));
            case 6 -> new ServalE6(param(e, 0, 0.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return skillId == 2 ? ServalKit::servalSkill : null;
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对主目标及相邻目标造成伤害, 有#3基础概率使目标触电#4回合. */
    static void servalSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMultiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(1, 0.3);
        double shockChance = ctx.param(2, 0.8);
        int shockTurns = ctx.intParam(3, 2);
        double dotRatio = ctx.param(4, 0.4);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        applyShockIfAble(battle, ctx, user, main, shockChance, dotRatio, shockTurns);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
                applyShockIfAble(battle, ctx, user, enemy, shockChance, dotRatio, shockTurns);
            }
        }
    }

    static void applyShockIfAble(Battle battle, SkillContext ctx, CanHit user, CanHit target,
                                 double chance, double dotRatio, int turns) {
        if (target.isDeath() || target.hasDotOfElement(Element.THUNDER)) {
            return;
        }
        if (battle.checkEffectHit(user, target, chance)) {
            ctx.applyShock(battle, user, target, dotRatio, turns);
            IO.println("  " + target.getName() + " is shocked");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 摇滚: 施放战技时，使目标陷入触电状态的基础概率提高20%。
     *  兼作天赋 电光火石 的代理: 施放攻击后，对所有触电状态下的敌方目标造成X%攻击力的雷属性附加伤害。 */
    static class ServalRock implements Trace {
        private final double bonus;

        ServalRock(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "摇滚";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasDotOfElement(Element.THUNDER)) {
                        continue;
                    }
                    // 战技触电基础概率 80% + 行迹 20% = 100%.
                    if (battle.checkEffectHit(owner, target, 0.8 + bonus)) {
                        double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                                ? owner.getAttribute(AttributeType.ATTACK).get() * 0.4 : 0;
                        target.applyDot(new Buff.Dot("Shock (触电)", owner, target, dotDamage, Element.THUNDER, 2));
                        IO.println("  [行迹] " + target.getName() + " is shocked");
                    }
                }
            }
            // 天赋代理: 攻击后对所有触电目标造成 X% 攻击力的雷属性附加伤害.
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                double ratio = 0.36;
                com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
                if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                        && !talent.getSkills().getFirst().isEmpty()) {
                    ratio = talent.getSkills().getFirst().getFirst();
                }
                for (Enemy enemy : battle.getAliveEnemies()) {
                    if (enemy.hasDotOfElement(Element.THUNDER)) {
                        battle.dealAttackDamage(owner, enemy, ratio, 0,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        Element.THUNDER));
                    }
                }
            }
        }
    }

    /** 电音: 战斗开始时，立即恢复15点能量。 */
    static class ServalElectric implements Trace {
        private final double energy;

        ServalElectric(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "电音";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " starts with +"
                    + String.format("%.0f", energy) + " energy");
        }
    }

    /** 狂热: 消灭敌方目标后，攻击力提高20%，持续2回合。 */
    static class ServalFrenzy implements Trace {
        private final double atkPercent;
        private final int turns;

        ServalFrenzy(double atkPercent, int turns) {
            this.atkPercent = atkPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "狂热";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Enemy)) {
                return;
            }
            owner.removeBuff("狂热");
            Buff buff = new Buff("狂热", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 普攻对1个随机相邻目标造成60%普攻伤害。 */
    static class ServalE1 implements Trace {
        private final double ratio;

        ServalE1(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || targets.isEmpty()) {
                return;
            }
            List<Enemy> others = battle.getAliveEnemies().stream()
                    .filter(e -> e != targets.getFirst()).toList();
            if (others.isEmpty()) {
                return;
            }
            double mult = KitSupport.actionMultiplier(owner, SkillType.COMMON) * ratio;
            battle.dealAttackDamage(owner, others.get((int) (Math.random() * others.size())),
                    mult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.THUNDER));
        }
    }

    /** 星魂2: 每触发1次天赋的附加伤害，恢复4点能量。 */
    static class ServalE2 implements Trace {
        private final double energy;

        ServalE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " gains "
                    + String.format("%.0f", energy) + " energy (shock tick)");
        }
    }

    /** 星魂4: 终结技使未触电目标陷入与战技相同的触电状态。 */
    static class ServalE4 implements Trace {
        private final double chance;

        ServalE4(double chance) {
            this.chance = chance;
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
            for (CanHit target : targets) {
                if (target.isDeath() || target.hasDotOfElement(Element.THUNDER)) {
                    continue;
                }
                if (battle.checkEffectHit(owner, target, chance)) {
                    double dotDamage = owner.getAttribute(AttributeType.ATTACK) != null
                            ? owner.getAttribute(AttributeType.ATTACK).get() * 0.4 : 0;
                    target.applyDot(new Buff.Dot("Shock (触电)", owner, target, dotDamage, Element.THUNDER, 2));
                    IO.println("  [星魂] " + target.getName() + " is shocked");
                }
            }
        }
    }

    /** 星魂6: 对触电状态下的敌方目标造成的伤害提高30%。 */
    static class ServalE6 implements Trace {
        private final double bonus;

        ServalE6(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasDotOfElement(Element.THUNDER) ? 1 + bonus : 1.0;
        }
    }
}
