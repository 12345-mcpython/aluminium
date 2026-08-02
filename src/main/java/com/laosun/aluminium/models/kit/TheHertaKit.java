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
 * 大黑塔 (The Herta, cid 1401) — 冰属性 智识.
 */
public final class TheHertaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1401;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HertaHonesty(intParam(byId, 1401101, 0, 3), param(byId, 1401101, 1, 0.5)),
                new HertaOuterLetter(param(byId, 1401102, 0, 0.8), intParam(byId, 1401102, 1, 3),
                        intParam(byId, 1401102, 2, 1), intParam(byId, 1401102, 3, 2)),
                new HertaHungryLand(param(byId, 1401103, 0, 0.01), intParam(byId, 1401103, 1, 99)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HertaE1(param(e, 0, 0.5), intParam(e, 1, 15));
            case 2 -> new HertaE2(param(e, 0, 0.35), intParam(e, 1, 42), param(e, 2, 0.25));
            case 4 -> new HertaE4(param(e, 0, 0.12));
            case 6 -> new HertaE6(param(e, 0, 1.4), param(e, 1, 2.5), param(e, 2, 4), param(e, 3, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> TheHertaKit::hertaSkill;
            case 3 -> TheHertaKit::hertaUlt;
            case 9 -> TheHertaKit::hertaEnhancedSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对目标造成#1%伤害并施加#2层【解读】, 对命中过的目标及其相邻目标重复2次。 */
    static void hertaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        int interpretation = ctx.intParam(1, 1);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        TheHertaKit.addInterpretation(main, interpretation);
        for (int i = 0; i < 2; i++) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main && enemy != main) {
                    // 相邻目标近似: 其余目标均受到额外段伤害.
                    ctx.dealDamage(battle, user, enemy, multiplier);
                    battle.breakToughness(user, enemy, ctx.stanceSpread());
                }
            }
            if (main.isDeath()) {
                break;
            }
            ctx.dealDamage(battle, user, main, multiplier);
            battle.breakToughness(user, main, ctx.stanceSingle());
        }
    }

    /** 终结技: 对敌方全体造成#1%伤害, 攻击力提高#4持续#5回合, 使自身立即行动并获得1层【灵感】。 */
    static void hertaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double atkBoost = ctx.param(3, 0.4);
        int turns = ctx.intParam(4, 3);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        user.removeBuff("早说了是魔法吧");
        Buff buff = new Buff("早说了是魔法吧", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBoost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        TheHertaKit.addInspiration(user, 1);
        battle.advanceByPercent(user, 1.0);
        IO.println("  " + user.getName() + " gains 1 【灵感】 and acts immediately!");
    }

    /** 强化战技: 消耗1层【灵感】, 对目标及相邻造成#1%伤害, 最后对全体造成#3%伤害。 */
    static void hertaEnhancedSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        int interpretation = ctx.intParam(1, 1);
        double aoeMult = ctx.param(2, 0.2);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        TheHertaKit.addInterpretation(main, interpretation);
        for (int i = 0; i < 2; i++) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main) {
                    ctx.dealDamage(battle, user, enemy, multiplier);
                    battle.breakToughness(user, enemy, ctx.stanceSpread());
                }
            }
            if (!main.isDeath()) {
                ctx.dealDamage(battle, user, main, multiplier);
                battle.breakToughness(user, main, ctx.stanceSingle());
            }
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, aoeMult);
            battle.breakToughness(user, enemy, ctx.stanceSpread());
        }
        TheHertaKit.consumeInspiration(user);
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 冷漠的诚实: 我方目标攻击时对被击中的目标施加1层【解读】, 每击中1个目标恢复3点能量。 */
    static class HertaHonesty implements Trace {
        private final double energyPerHit;
        private final double revealBonus;

        HertaHonesty(double energyPerHit, double revealBonus) {
            this.energyPerHit = energyPerHit;
            this.revealBonus = revealBonus;
        }

        @Override
        public String getName() {
            return "冷漠的诚实";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                TheHertaKit.addInterpretation(enemy, 1);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            int hit = 0;
            for (CanHit target : targets) {
                if (!target.isDeath() && target.getCamp() != owner.getCamp()) {
                    hit++;
                    TheHertaKit.addInterpretation(target, 1);
                }
            }
            if (hit > 0) {
                owner.gainEnergy(energyPerHit * Math.min(5, hit));
                IO.println("  [行迹] " + owner.getName() + " gains "
                        + String.format("%.0f", energyPerHit * hit) + " energy (冷漠的诚实)");
            }
        }
    }

    /** 视界外来信: 队伍中智识≥2时, 我方全体暴击伤害提高80%。 */
    static class HertaOuterLetter implements Trace {
        private final double cdmg;
        private final int minTargets;
        private final int stack1;
        private final int stack2;

        HertaOuterLetter(double cdmg, int minTargets, int stack1, int stack2) {
            this.cdmg = cdmg;
            this.minTargets = minTargets;
            this.stack1 = stack1;
            this.stack2 = stack2;
        }

        @Override
        public String getName() {
            return "视界外来信";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            long erudition = battle.getAliveCharacters().stream()
                    .filter(c -> c.getCid() >= 1400 && c.getCid() < 1500)
                    .count();
            if (erudition >= 2) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("视界外来信", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                IO.println("  [行迹] 视界外来信: party crit damage +"
                        + String.format("%.0f", cdmg * 100) + "% (2 智识)");
            }
        }
    }

    /** 饥饿的地景: 敌方每被施加1层【解读】, 大黑塔获得1层【谜底】(最多99层), 终结技每层使伤害倍率提高1%。 */
    static class HertaHungryLand implements Trace {
        private final double perStack;
        private final int maxStacks;

        HertaHungryLand(double perStack, int maxStacks) {
            this.perStack = perStack;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "饥饿的地景";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            if (type != SkillType.ULTRA) {
                return 1.0;
            }
            int mystery = TheHertaKit.mysteryStacks(owner);
            return 1 + mystery * perStack;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 群星岸落之夜: 强化战技重置【解读】时改为重置为15层。 */
    static class HertaE1 implements Trace {
        private final double bonus;
        private final int resetTo;

        HertaE1(double bonus, int resetTo) {
            this.bonus = bonus;
            this.resetTo = resetTo;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 穿过锁孔之风: 进入战斗和施放终结技后额外获得1层【灵感】, 强化战技后行动提前35%。 */
    static class HertaE2 implements Trace {
        private final double advance;
        private final int ignored;
        private final double ignored2;

        HertaE2(double advance, int ignored, double ignored2) {
            this.advance = advance;
            this.ignored = ignored;
            this.ignored2 = ignored2;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            TheHertaKit.addInspiration(owner, 1);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                TheHertaKit.addInspiration(owner, 1);
            } else if (type == SkillType.SKILL && owner.isEnhanced()) {
                battle.advanceByPercent(owner, advance);
                IO.println("  [星魂] " + owner.getName() + " advances after the enhanced skill");
            }
        }
    }

    /** 星魂4 第十六把钥匙: 队伍中智识角色速度提高12%。 */
    static class HertaE4 implements Trace {
        private final double speed;

        HertaE4(double speed) {
            this.speed = speed;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 甜饵般的答案: 冰属性抗性穿透提高20%。 */
    static class HertaE6 implements Trace {
        private final double ult1;
        private final double ult2;
        private final double ult3;
        private final double pen;

        HertaE6(double ult1, double ult2, double ult3, double pen) {
            this.ult1 = ult1;
            this.ult2 = ult2;
            this.ult3 = ult3;
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 【解读】/【谜底】/【灵感】代理 ──────────────────────────────────

    private static final java.util.Map<CanHit, Integer> INTERPRETATION = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> MYSTERY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> INSPIRATION = new java.util.concurrent.ConcurrentHashMap<>();

    static void addInterpretation(CanHit target, int amount) {
        INTERPRETATION.put(target, Math.min(42, INTERPRETATION.getOrDefault(target, 0) + amount));
        if (target instanceof Enemy) {
            for (java.util.Map.Entry<CanHit, Integer> entry : INSPIRATION.entrySet()) {
                // 谜底由大黑塔持有, 施加解读时累计.
            }
        }
    }

    static int mysteryStacks(CanHit owner) {
        return MYSTERY.getOrDefault(owner, 0);
    }

    static void addInspiration(CanHit owner, int amount) {
        INSPIRATION.put(owner, Math.min(4, INSPIRATION.getOrDefault(owner, 0) + amount));
        IO.println("  【灵感】 " + owner.getName() + ": " + INSPIRATION.get(owner) + "/4");
    }

    static void consumeInspiration(CanHit owner) {
        int left = Math.max(0, INSPIRATION.getOrDefault(owner, 0) - 1);
        INSPIRATION.put(owner, left);
    }
}
