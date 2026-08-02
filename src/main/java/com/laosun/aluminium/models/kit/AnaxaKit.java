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
 * 那刻夏 (Anaxa, cid 1405) — 风属性 智识.
 */
public final class AnaxaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1405;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AnaxaSignifier(param(byId, 1405101, 0, 10), param(byId, 1405101, 1, 30)),
                new AnaxaBlank(param(byId, 1405102, 0, 1.4), param(byId, 1405102, 1, 0.5)),
                new AnaxaMutation(param(byId, 1405103, 0, 0.04)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AnaxaE1(param(e, 0, 0.16), intParam(e, 1, 2), intParam(e, 2, 1));
            case 2 -> new AnaxaE2(param(e, 0, 0.2));
            case 4 -> new AnaxaE4(param(e, 0, 0.3), intParam(e, 1, 2), intParam(e, 2, 2));
            case 6 -> new AnaxaE6(param(e, 0, 1.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> AnaxaKit::anaxaSkill;
            case 3 -> AnaxaKit::anaxaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对目标造成#1%伤害, 并额外造成#2次随机单体伤害 (优先未击中目标). */
    static void anaxaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int extraHits = ctx.intParam(1, 4);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        AnaxaKit.addWeakness(battle, user, main);
        for (int i = 0; i < extraHits; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            CanHit target = alive.get((int) (Math.random() * alive.size()));
            ctx.dealDamage(battle, user, target, multiplier);
            battle.breakToughness(user, target, ctx.stanceSingle() / (extraHits + 1));
            AnaxaKit.addWeakness(battle, user, target);
        }
    }

    /** 终结技: 使敌方全体陷入【升华】(添加全部属性弱点), 造成#1%伤害. */
    static void anaxaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("升华");
            Buff buff = new Buff("升华", Buff.Category.DEBUFF, user, enemy, 1);
            battle.applyBuff(enemy, buff);
            for (Element element : Element.values()) {
                enemy.addTemporaryWeakness(element, 1);
            }
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " sublimates the enemies (升华)!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 流浪的能指: 施放普攻时额外恢复10点能量; 回合开始时若场上无【质性揭露】目标, 恢复30点能量。 */
    static class AnaxaSignifier implements Trace {
        private final double basicEnergy;
        private final double turnStartEnergy;

        AnaxaSignifier(double basicEnergy, double turnStartEnergy) {
            this.basicEnergy = basicEnergy;
            this.turnStartEnergy = turnStartEnergy;
        }

        @Override
        public String getName() {
            return "流浪的能指";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                owner.gainEnergy(basicEnergy);
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean anyRevealed = battle.getAliveEnemies().stream()
                    .anyMatch(e -> e.hasBuffNamed("质性揭露"));
            if (!anyRevealed) {
                owner.gainEnergy(turnStartEnergy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", turnStartEnergy) + " energy (流浪的能指)");
            }
        }
    }

    /** 必要的留白: 队伍中1名智识角色 → 自身暴击伤害提高140%; 至少2名 → 我方全体伤害提高50%。 */
    static class AnaxaBlank implements Trace {
        private final double selfCdmg;
        private final double partyDmg;

        AnaxaBlank(double selfCdmg, double partyDmg) {
            this.selfCdmg = selfCdmg;
            this.partyDmg = partyDmg;
        }

        @Override
        public String getName() {
            return "必要的留白";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            long erudition = battle.getAliveCharacters().stream()
                    .filter(c -> c.getCid() >= 1400 && c.getCid() < 1500)
                    .count();
            if (erudition >= 2) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("必要的留白", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyDmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                IO.println("  [行迹] 必要的留白: party DMG +" + String.format("%.0f", partyDmg * 100) + "%");
            } else {
                Buff buff = new Buff("必要的留白", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(selfCdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    /** 质性的嬗变: 敌方目标每拥有1个不同属性的弱点, 那刻夏对其造成的伤害无视4%防御力 (最多7个)。 */
    static class AnaxaMutation implements Trace {
        private final double perWeakness;

        AnaxaMutation(double perWeakness) {
            this.perWeakness = perWeakness;
        }

        @Override
        public String getName() {
            return "质性的嬗变";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("质性的嬗变", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(perWeakness,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 月掩星夜的魔术师: 首次施放战技后恢复1个战技点。 */
    static class AnaxaE1 implements Trace {
        private final double defDown;
        private final int turns;
        private final int skillPoints;
        private boolean firstSkillUsed = false;

        AnaxaE1(double defDown, int turns, int skillPoints) {
            this.defDown = defDown;
            this.turns = turns;
            this.skillPoints = skillPoints;
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
            if (!firstSkillUsed) {
                firstSkillUsed = true;
                battle.addSkillPoints(skillPoints);
                IO.println("  [星魂] " + owner.getName() + " restores " + skillPoints + " skill point");
            }
        }
    }

    /** 星魂2 真实历史的自然人: 敌方目标入场时, 触发1次天赋的弱点添加效果, 并使其全属性抗性降低20%。 */
    static class AnaxaE2 implements Trace {
        private final double resDown;

        AnaxaE2(double resDown) {
            this.resDown = resDown;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂2", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂4 坠落在山谷的炽热: 施放战技时攻击力提高30%, 持续2回合, 最多2层。 */
    static class AnaxaE4 implements Trace {
        private final double atkBonus;
        private final int turns;
        private final int maxStacks;
        private int stacks = 0;

        AnaxaE4(double atkBonus, int turns, int maxStacks) {
            this.atkBonus = atkBonus;
            this.turns = turns;
            this.maxStacks = maxStacks;
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
            stacks = Math.min(maxStacks, stacks + 1);
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 万物皆在万物之中: 造成的伤害为原伤害的130%。 */
    static class AnaxaE6 implements Trace {
        private final double multiplier;

        AnaxaE6(double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return multiplier;
        }
    }

    // ─── 天赋 四分明哲，三重至高 代理 (击中后添加随机属性弱点) ──────────

    private static final Element[] ALL = Element.values();

    static void addWeakness(Battle battle, CanHit user, CanHit target) {
        if (target.isDeath() || !(target instanceof Enemy enemy)) {
            return;
        }
        for (Element element : ALL) {
            if (!enemy.isWeakTo(element)) {
                enemy.addTemporaryWeakness(element, 3);
                IO.println("  [天赋] " + enemy.getName() + " gains " + element.string + " weakness");
                return;
            }
        }
    }
}
