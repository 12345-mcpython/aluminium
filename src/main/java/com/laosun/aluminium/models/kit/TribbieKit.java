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
 * 缇宝 (Tribbie, cid 1403) — 量子属性 同谐.
 */
public final class TribbieKit implements CharacterKit {

    @Override
    public int cid() {
        return 1403;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new TribbieLamb(param(byId, 1403101, 0, 0.72), intParam(byId, 1403101, 1, 3),
                        intParam(byId, 1403101, 2, 3)),
                new TribbieGlassBall(param(byId, 1403102, 0, 0.09)),
                new TribbiePebble(param(byId, 1403103, 0, 30), param(byId, 1403103, 1, 1.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new TribbieE1(param(e, 0, 0.24));
            case 2 -> new TribbieE2(param(e, 0, 1.2), intParam(e, 1, 1));
            case 4 -> new TribbieE4(param(e, 0, 0.18));
            case 6 -> new TribbieE6(param(e, 0, 7.29));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> TribbieKit::tribbieSkill;
            case 3 -> TribbieKit::tribbieUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得【神启】#2回合, 我方全体全属性抗性穿透提高#1%。 */
    static void tribbieSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double pen = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        user.removeBuff("神启");
        battle.applyBuff(user, new Buff("神启", Buff.Category.BUFF, user, user, turns));
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("神启·穿透", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " gains 神启 (" + turns + " turns): party RES PEN +"
                + String.format("%.0f", pen * 100) + "%");
    }

    /** 终结技: 开启结界#4回合 (敌方受到的伤害提高#2), 对敌方全体造成#1生命上限的伤害. */
    static void tribbieUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double vuln = ctx.param(1, 0.15);
        double extraRatio = ctx.param(2, 0.06);
        int turns = ctx.intParam(3, 2);
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("结界·易伤");
            Buff debuff = new Buff("结界·易伤", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * multiplier,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.QUANTUM));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 城墙外的羊羔儿…: 施放天赋的追加攻击后, 造成的伤害提高72%, 最多3层, 持续3回合。 */
    static class TribbieLamb implements Trace {
        private final double perStack;
        private final int maxStacks;
        private final int turns;
        private int stacks = 0;

        TribbieLamb(double perStack, int maxStacks, int turns) {
            this.perStack = perStack;
            this.maxStacks = maxStacks;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "城墙外的羊羔儿…";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor != owner && type == SkillType.ULTRA) {
                // 天赋 好忙好忙的缇宝 代理: 队友施放终结技后, 缇宝发动追加攻击.
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("城墙外的羊羔儿");
                Buff buff = new Buff("城墙外的羊羔儿", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                double mult = 0.09;
                com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
                if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                        && !talent.getSkills().getFirst().isEmpty()) {
                    mult = talent.getSkills().getFirst().getFirst();
                }
                for (Enemy enemy : battle.getAliveEnemies()) {
                    battle.dealAttackDamageBase(owner, enemy, owner.getMaxHp() * mult,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                    Element.QUANTUM));
                }
                IO.println("  [行迹] " + owner.getName() + " follows up on ally ult (好忙好忙的缇宝)");
            }
        }
    }

    /** 长翅膀的玻璃球！: 结界持续期间, 缇宝生命上限提高 (等同于我方全体生命上限之和的9%)。 */
    static class TribbieGlassBall implements Trace {
        private final double ratio;

        TribbieGlassBall(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "长翅膀的玻璃球！";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double total = 0;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                total += ally.getMaxHp();
            }
            Buff buff = new Buff("长翅膀的玻璃球", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(ratio,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] 长翅膀的玻璃球: max HP +" + String.format("%.1f%%", ratio * 100));
        }
    }

    /** 岔路旁的小石子？: 战斗开始时恢复30点能量; 其他目标攻击后每击中1个目标恢复1.5点能量。 */
    static class TribbiePebble implements Trace {
        private final double startEnergy;
        private final double perHit;

        TribbiePebble(double startEnergy, double perHit) {
            this.startEnergy = startEnergy;
            this.perHit = perHit;
        }

        @Override
        public String getName() {
            return "岔路旁的小石子？";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(startEnergy);
            IO.println("  [行迹] " + owner.getName() + " starts with +"
                    + String.format("%.0f", startEnergy) + " energy");
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner || targets == null) {
                return;
            }
            int hit = (int) targets.stream().filter(t -> !t.isDeath()).count();
            if (hit > 0) {
                owner.gainEnergy(perHit * hit);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 拾起砂糖的祭典: 结界期间我方攻击后, 对附加伤害目标额外造成24%真实伤害。 */
    static class TribbieE1 implements Trace {
        private final double trueDamageRatio;

        TribbieE1(double trueDamageRatio) {
            this.trueDamageRatio = trueDamageRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!owner.hasBuffNamed("结界") || targets == null) {
                return;
            }
            for (CanHit target : targets) {
                if (!target.isDeath() && target.getCamp() != owner.getCamp()) {
                    battle.applyTrueDamage(target, target.getMaxHp() * 0.01 * trueDamageRatio,
                            "星魂1");
                }
            }
        }
    }

    /** 星魂2 探访佳梦的向导: 结界附加伤害提高至120%。 */
    static class TribbieE2 implements Trace {
        private final double ratio;
        private final int extraHits;

        TribbieE2(double ratio, int extraHits) {
            this.ratio = ratio;
            this.extraHits = extraHits;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 心意相契的安宁: 【神启】持续期间, 我方全体造成伤害时无视18%防御力。 */
    static class TribbieE4 implements Trace {
        private final double defIgnore;

        TribbieE4(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 星月满天的明日: 施放终结技后对敌方全体发动天赋追加攻击。 */
    static class TribbieE6 implements Trace {
        private final double followUpBonus;

        TribbieE6(double followUpBonus) {
            this.followUpBonus = followUpBonus;
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
            double mult = 0.09 * (1 + followUpBonus);
            for (Enemy enemy : battle.getAliveEnemies()) {
                battle.dealAttackDamageBase(owner, enemy, owner.getMaxHp() * mult,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                Element.QUANTUM));
            }
            IO.println("  [星魂] " + owner.getName() + " follows up after ult (星魂6)");
        }
    }
}
