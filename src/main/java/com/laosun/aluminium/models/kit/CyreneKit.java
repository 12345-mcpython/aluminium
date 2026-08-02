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
 * 昔涟 (Cyrene, cid 1415) — 冰属性 记忆 (忆灵: 德谬歌).
 */
public final class CyreneKit implements CharacterKit {

    @Override
    public int cid() {
        return 1415;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new CyreneNetChild(),
                new CyreneTraveler(intParam(byId, 1415102, 0, 2), intParam(byId, 1415102, 1, 3),
                        intParam(byId, 1415102, 2, 6)),
                new CyreneTrinity(param(byId, 1415103, 0, 180), param(byId, 1415103, 1, 0.02),
                        param(byId, 1415103, 2, 60), param(byId, 1415103, 3, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new CyreneE1(intParam(e, 0, 6), intParam(e, 1, 12));
            case 2 -> new CyreneE2(intParam(e, 0, 12), param(e, 1, 0), param(e, 2, 0.06), param(e, 3, 0.24));
            case 4 -> new CyreneE4(param(e, 0, 0.06), intParam(e, 1, 24));
            case 6 -> new CyreneE6(param(e, 0, 1), param(e, 1, 0.2), param(e, 2, 0.24));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 1 -> CyreneKit::cyreneBasic;
            case 2 -> CyreneKit::cyreneSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 普攻: 获得#2点【追忆】, 对目标造成#1生命上限的冰属性伤害. */
    static void cyreneBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        int memory = ctx.intParam(1, 1);
        CyreneKit.addMemory(user, memory);
        battle.dealAttackDamageBase(user, target, user.getMaxHp() * multiplier,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
        battle.breakToughness(user, target, ctx.stanceSingle());
    }

    /** 战技: 获得#3点【追忆】并展开结界#2回合 (我方每造成1次伤害, 额外造成#1真实伤害)。 */
    static void cyreneSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double trueRatio = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        int memory = ctx.intParam(2, 3);
        CyreneKit.addMemory(user, memory);
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns): party true damage +"
                + String.format("%.0f", trueRatio * 100) + "%");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 记忆的净子: 队友的忆灵被召唤时获得【未来】。 */
    static class CyreneNetChild implements Trace {
        @Override
        public String getName() {
            return "记忆的净子";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor != owner && !actor.getSummons().isEmpty()) {
                CyreneKit.addMemory(owner, 1);
                IO.println("  [行迹] " + owner.getName() + " gains 1 【追忆】 (队友召唤忆灵)");
            }
        }
    }

    /** 岁月的旅人: 队伍中黄金裔/记忆命途角色越多, 战斗开始时获得越多【追忆】。 */
    static class CyreneTraveler implements Trace {
        private final int one;
        private final int two;
        private final int three;

        CyreneTraveler(int one, int two, int three) {
            this.one = one;
            this.two = two;
            this.three = three;
        }

        @Override
        public String getName() {
            return "岁月的旅人";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            long allies = battle.getAliveCharacters().stream()
                    .filter(c -> c.getCid() != 1415 && c.getCid() >= 1400)
                    .count();
            int memory = allies >= 3 ? three : allies == 2 ? two : allies == 1 ? one : 0;
            if (memory > 0) {
                CyreneKit.addMemory(owner, memory);
                IO.println("  [行迹] " + owner.getName() + " gains " + memory + " 【追忆】");
            }
        }
    }

    /** 三相的因果: 速度≥180时, 我方全体造成的伤害提高20%, 之后每超过1点速度冰属性抗性穿透提高2%。 */
    static class CyreneTrinity implements Trace {
        private final double speedThreshold;
        private final double perSpeed;
        private final double maxExtra;
        private final double partyDmg;

        CyreneTrinity(double speedThreshold, double perSpeed, double maxExtra, double partyDmg) {
            this.speedThreshold = speedThreshold;
            this.perSpeed = perSpeed;
            this.maxExtra = maxExtra;
            this.partyDmg = partyDmg;
        }

        @Override
        public String getName() {
            return "三相的因果";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double speed = owner.getAttribute(AttributeType.SPEED) != null
                    ? owner.getAttribute(AttributeType.SPEED).get() : 0;
            if (speed >= speedThreshold) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("三相的因果", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyDmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                double pen = Math.min(maxExtra, speed - speedThreshold) * perSpeed;
                Buff buff = new Buff("三相的因果·穿透", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [行迹] 三相的因果: party DMG +" + String.format("%.0f", partyDmg * 100) + "%");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 空白，在诗篇的起点: 德谬歌触发忆灵技时获得6点【追忆】。 */
    static class CyreneE1 implements Trace {
        private final int memory;
        private final int extraBounces;

        CyreneE1(int memory, int extraBounces) {
            this.memory = memory;
            this.extraBounces = extraBounces;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 十三种光彩，倒映明天: 进入战斗时额外获得12点【追忆】。 */
    static class CyreneE2 implements Trace {
        private final int startMemory;
        private final double ignored;
        private final double trueDamageStep;
        private final double trueDamageCap;

        CyreneE2(int startMemory, double ignored, double trueDamageStep, double trueDamageCap) {
            this.startMemory = startMemory;
            this.ignored = ignored;
            this.trueDamageStep = trueDamageStep;
            this.trueDamageCap = trueDamageCap;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            CyreneKit.addMemory(owner, startMemory);
            IO.println("  [星魂] " + owner.getName() + " gains " + startMemory + " 【追忆】");
        }
    }

    /** 星魂4 请笑着，写就下一页: 德谬歌每施放1次【花与箭的舞曲】, 弹射伤害倍率提高6%, 最多24层。 */
    static class CyreneE4 implements Trace {
        private final double perCast;
        private final int maxStacks;

        CyreneE4(double perCast, int maxStacks) {
            this.perCast = perCast;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 记忆，化作涟漪片片♪: 首次施放终结技时, 我方全体行动提前100%。 */
    static class CyreneE6 implements Trace {
        private final double firstAdvance;
        private final double defDown;
        private final double advance;

        CyreneE6(double firstAdvance, double defDown, double advance) {
            this.firstAdvance = firstAdvance;
            this.defDown = defDown;
            this.advance = advance;
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
            for (Character ally : battle.getAliveCharacters()) {
                battle.advanceByPercent(ally, firstAdvance);
            }
            IO.println("  [星魂] " + owner.getName() + ": party acts immediately (星魂6)");
        }
    }

    // ─── 【追忆】代理 (天赋 众愿啊，汇流如歌) ────────────────────────────

    private static final java.util.Map<CanHit, Integer> MEMORY = new java.util.concurrent.ConcurrentHashMap<>();

    static int memory(CanHit owner) {
        return MEMORY.getOrDefault(owner, 0);
    }

    static void addMemory(CanHit owner, int amount) {
        MEMORY.put(owner, Math.min(30, memory(owner) + amount));
    }
}
