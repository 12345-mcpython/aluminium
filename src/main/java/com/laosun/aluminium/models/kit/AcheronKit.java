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
 * 黄泉 (Acheron, cid 1308) — 雷属性 虚无.
 */
public final class AcheronKit implements CharacterKit {

    @Override
    public int cid() {
        return 1308;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AcheronRedDemon(param(byId, 1308101, 0, 5), intParam(byId, 1308101, 1, 3)),
                new AcheronNaraka(param(byId, 1308102, 0, 1.15), param(byId, 1308102, 1, 1.6)),
                new AcheronThunderHeart(param(byId, 1308103, 0, 0.3), intParam(byId, 1308103, 1, 3),
                        intParam(byId, 1308103, 2, 3), intParam(byId, 1308103, 3, 6),
                        param(byId, 1308103, 4, 0.25)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AcheronE1(param(e, 0, 0.18));
            case 2 -> new AcheronE2();
            case 4 -> new AcheronE4(param(e, 0, 0.08));
            case 6 -> new AcheronE6(param(e, 0, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> AcheronKit::acheronSkill;
            case 3 -> AcheronKit::acheronUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得#3点【残梦】, 为目标附上#3层【集真赤】, 造成#1%伤害并对相邻目标造成#2%伤害. */
    static void acheronSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double multiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(1, 0.3);
        int charges = ctx.intParam(2, 1);
        AcheronKit.addDream(user, charges);
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (main instanceof Enemy enemy) {
            AcheronKit.addCrimson(enemy, charges);
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " gains " + charges + " 残梦 ("
                + AcheronKit.dreamStacks(user) + "/9)");
    }

    /** 终结技: 依次发动3次【啼泽雨斩】和1次【黄泉返渡】 (代理: 对主目标#6%+3段, 全体#7%). */
    static void acheronUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double hitMult = ctx.param(0, 0.144);
        double aoeMult = ctx.param(2, 0.72);
        double mainTotal = ctx.param(5, 2.232);
        double othersTotal = ctx.param(6, 1.8);
        // 3段【啼泽雨斩】: 对主目标造成 #1×3 段伤害.
        for (int i = 0; i < 3; i++) {
            if (main.isDeath()) {
                break;
            }
            ctx.dealDamage(battle, user, main, hitMult);
        }
        // 【黄泉返渡】: 对敌方全体造成#3%伤害.
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, aoeMult);
            battle.breakToughness(user, enemy, 0.15);
        }
        // 主目标总伤害不低于 #6 (雷心 额外伤害近似计入).
        double mainLost = main.getMaxHp() - main.getCurrentHp();
        if (mainLost < main.getMaxHp() * 0.01) {
            ctx.dealDamage(battle, user, main, Math.max(0, mainTotal - hitMult * 3));
        }
        // 终结技期间无视弱点属性削减韧性 + 全属性抗性降低10%.
        for (Enemy enemy : battle.getAliveEnemies()) {
            Buff debuff = new Buff("残梦尽染", Buff.Category.DEBUFF, user, enemy, 1)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(0.1,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
        }
        AcheronKit.consumeDream(user, 9);
        IO.println("  " + user.getName() + " casts 残梦尽染，一刀缭断!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 赤鬼: 战斗开始时获得5点【残梦】, 并为随机1名敌方附上5层【集真赤】。 */
    static class AcheronRedDemon implements Trace {
        private final double startDream;
        private final int maxLayers;

        AcheronRedDemon(double startDream, int maxLayers) {
            this.startDream = startDream;
            this.maxLayers = maxLayers;
        }

        @Override
        public String getName() {
            return "赤鬼";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            AcheronKit.addDream(owner, (int) startDream);
            List<Enemy> alive = battle.getAliveEnemies();
            if (!alive.isEmpty()) {
                AcheronKit.addCrimson(alive.get((int) (Math.random() * alive.size())), (int) startDream);
            }
            IO.println("  [行迹] " + owner.getName() + " starts with " + (int) startDream + " 残梦");
        }
    }

    /** 奈落: 队伍中每有1名除黄泉之外的虚无命途角色, 普攻/战技/终结技伤害提高 (1名115%, 2名160%)。 */
    static class AcheronNaraka implements Trace {
        private final double oneNihility;
        private final double twoNihility;

        AcheronNaraka(double oneNihility, double twoNihility) {
            this.oneNihility = oneNihility;
            this.twoNihility = twoNihility;
        }

        @Override
        public String getName() {
            return "奈落";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            long nihility = battle.getAliveCharacters().stream()
                    .filter(c -> c.getCid() != 1308 && c.getCid() >= 1300)
                    .count();
            if (nihility >= 2) {
                return twoNihility;
            }
            return nihility == 1 ? oneNihility : 1.0;
        }
    }

    /** 雷心: 终结技击中持有【集真赤】的目标时, 造成的伤害提高30%, 最多叠加3层, 持续3回合。 */
    static class AcheronThunderHeart implements Trace {
        private final double perStack;
        private final int maxStacks;
        private final int turns;
        private final int extraHits;
        private final double extraMult;
        private int stacks = 0;

        AcheronThunderHeart(double perStack, int maxStacks, int turns, int extraHits, double extraMult) {
            this.perStack = perStack;
            this.maxStacks = maxStacks;
            this.turns = turns;
            this.extraHits = extraHits;
            this.extraMult = extraMult;
        }

        @Override
        public String getName() {
            return "雷心";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            boolean hitCrimson = targets.stream().anyMatch(t -> t instanceof Enemy e && crimsonStacks(e) > 0);
            if (hitCrimson) {
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("雷心");
                Buff buff = new Buff("雷心", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                // 黄泉返渡额外造成#4次随机单体#5%伤害.
                for (int i = 0; i < extraHits; i++) {
                    List<Enemy> alive = battle.getAliveEnemies();
                    if (alive.isEmpty()) {
                        return;
                    }
                    battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                            extraMult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.THUNDER));
                }
                IO.println("  [行迹] 雷心: damage +" + String.format("%.0f", perStack * stacks * 100)
                        + "% (" + stacks + "/" + maxStacks + ")");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 高天寥落真言始: 对处于负面效果的敌方目标造成伤害时暴击率提高18%。 */
    static class AcheronE1 implements Trace {
        private final double crit;

        AcheronE1(double crit) {
            this.crit = crit;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasDebuff() ? crit : 0;
        }
    }

    /** 星魂2 霆鼓俱寂，瑟风亦止: 自身回合开始时获得1点【残梦】。 */
    static class AcheronE2 implements Trace {
        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            AcheronKit.addDream(owner, 1);
            IO.println("  [星魂] " + owner.getName() + " gains 1 残梦 ("
                    + AcheronKit.dreamStacks(owner) + "/9)");
        }
    }

    /** 星魂4 亘焰燎照镜中人: 敌方目标进入战斗时, 受到的终结技伤害提高8%。 */
    static class AcheronE4 implements Trace {
        private final double vuln;

        AcheronE4(double vuln) {
            this.vuln = vuln;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂4", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂6 灾咎解桎梏: 终结技伤害全属性抗性穿透提高20%。 */
    static class AcheronE6 implements Trace {
        private final double pen;

        AcheronE6(double pen) {
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

    // ─── 【残梦】/【集真赤】代理 ────────────────────────────────────────

    private static final java.util.Map<CanHit, Integer> DREAM = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> CRIMSON = new java.util.concurrent.ConcurrentHashMap<>();

    static int dreamStacks(CanHit owner) {
        return DREAM.getOrDefault(owner, 0);
    }

    static void addDream(CanHit owner, int amount) {
        DREAM.put(owner, Math.min(9, dreamStacks(owner) + amount));
    }

    static void consumeDream(CanHit owner, int amount) {
        DREAM.put(owner, Math.max(0, dreamStacks(owner) - amount));
    }

    static int crimsonStacks(Enemy enemy) {
        return CRIMSON.getOrDefault(enemy, 0);
    }

    static void addCrimson(Enemy enemy, int amount) {
        CRIMSON.put(enemy, Math.min(9, crimsonStacks(enemy) + amount));
    }
}
