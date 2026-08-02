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
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 景元 (Jing Yuan, cid 1204) — 雷属性 智识.
 */
public final class JingYuanKit implements CharacterKit {

    @Override
    public int cid() {
        return 1204;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new JingYuanBreakFormation(intParam(byId, 1204101, 0, 6), param(byId, 1204101, 1, 0.25)),
                new JingYuanPrepare(param(byId, 1204102, 0, 15)),
                new JingYuanDispatch(param(byId, 1204103, 0, 0.1), intParam(byId, 1204103, 1, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new JingYuanE1(param(e, 0, 0.25));
            case 2 -> new JingYuanE2(param(e, 0, 0.2), intParam(e, 1, 2));
            case 4 -> new JingYuanE4(param(e, 0, 2));
            case 6 -> new JingYuanE6(param(e, 0, 0.12), intParam(e, 1, 3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        // 战技/终结技由通用 AoEAttack 处理 (伤害 + 全体削韧);
        // 天赋【神君】与星魂全部由行迹 破阵 的钩子代理.
        return null;
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 破阵: 若【神君】下回合的攻击段数≥6, 则其下回合的暴击伤害提高25%。
     * 同时代理天赋 斩勘神形 (战斗开始召唤【神君】, 初始3段攻击; 每段攻击
     * 造成景元攻击力33%的雷属性伤害并波及相邻目标; 行动结束后段数恢复至初始状态)
     * 以及战技/终结技增加的【神君】攻击段数。
     */
    static class JingYuanBreakFormation implements Trace {
        private final int critStacks;
        private final double critBonus;
        private int lordStacks = 0;

        JingYuanBreakFormation(int critStacks, double critBonus) {
            this.critStacks = critStacks;
            this.critBonus = critBonus;
        }

        @Override
        public String getName() {
            return "破阵";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            // 天赋: 【神君】初始拥有 3 段攻击段数.
            lordStacks = talentBaseStacks(owner, 3);
            IO.println("  [行迹] 【神君】 joins the battle with " + lordStacks + " hits");
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                // 战技: 增加2段【神君】下回合的攻击段数.
                lordStacks += skillStacks(owner, SkillType.SKILL, 2);
                IO.println("  [行迹] 【神君】stacks: " + lordStacks);
            } else if (type == SkillType.ULTRA) {
                // 终结技: 增加3段.
                lordStacks += skillStacks(owner, SkillType.ULTRA, 3);
                IO.println("  [行迹] 【神君】stacks: " + lordStacks);
            }
        }

        /**
         * 【神君】行动: 每段攻击对随机敌方单体造成伤害并波及相邻目标
         * (以景元回合开始代理其独立行动条, 见规则16 近似说明).
         */
        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (lordStacks <= 0) {
                return;
            }
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            double hitMult = talentParams(talent, 1, 0.33);
            double adjacentRatio = talentParams(talent, 4, 0.25);
            int maxStacks = (int) Math.round(talentParams(talent, 5, 10));
            int hits = Math.min(maxStacks, lordStacks);
            double damageFactor = lordStacks >= critStacks ? 1 + critBonus : 1;
            JingYuanE1 e1 = findTrace(owner, JingYuanE1.class);
            double adjacentFactor = e1 != null ? 2 : 1;
            IO.println("  [行迹] 【神君】 strikes " + hits + " times"
                    + (lordStacks >= critStacks ? " (破阵 crit +"
                    + String.format("%.0f%%", critBonus * 100) + ")" : ""));
            for (int i = 0; i < hits; i++) {
                Enemy main = alive.get((int) (Math.random() * alive.size()));
                battle.dealAttackDamage(owner, main, hitMult * damageFactor, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
                battle.breakToughness(owner, main, 0.5);
                for (Enemy adjacent : battle.getAliveEnemies()) {
                    if (adjacent != main) {
                        battle.dealAttackDamage(owner, adjacent,
                                hitMult * adjacentRatio * adjacentFactor * damageFactor, 0,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                        Element.THUNDER));
                        battle.breakToughness(owner, adjacent, 0.25);
                    }
                }
                // 星魂4: 【神君】每段攻击后, 景元恢复2点能量.
                JingYuanE4 e4 = findTrace(owner, JingYuanE4.class);
                if (e4 != null) {
                    owner.gainEnergy(e4.energy);
                }
                // 星魂6: 每段攻击后使目标陷入易伤状态, 最多叠加3层.
                JingYuanE6 e6 = findTrace(owner, JingYuanE6.class);
                if (e6 != null) {
                    main.removeBuff("神君易伤");
                    Buff vuln = new Buff("神君易伤", Buff.Category.DEBUFF, owner, main, 1)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(
                                    Math.min(e6.maxStacks, i + 1) * e6.vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(main, vuln);
                }
            }
            // 星魂2: 【神君】行动后, 景元的普攻/战技/终结技造成的伤害提高.
            JingYuanE2 e2 = findTrace(owner, JingYuanE2.class);
            if (e2 != null) {
                owner.removeBuff("戎戈动地");
                Buff buff = new Buff("戎戈动地", Buff.Category.BUFF, owner, owner, e2.turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(e2.bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
            // 行动结束后速度与攻击段数恢复至初始状态.
            lordStacks = talentBaseStacks(owner, 3);
        }

        private static int talentBaseStacks(Character owner, int fallback) {
            return (int) Math.round(talentParams(KitSupport.skillData(owner, SkillType.TALENT), 3, fallback));
        }

        private static int skillStacks(Character owner, SkillType type, int fallback) {
            SkillData data = KitSupport.skillData(owner, type);
            if (data != null && data.getSkills() != null && !data.getSkills().isEmpty()
                    && data.getSkills().getFirst().size() > 1) {
                return (int) Math.round(data.getSkills().getFirst().get(1));
            }
            return fallback;
        }

        private static double talentParams(SkillData data, int index, double fallback) {
            if (data != null && data.getSkills() != null && !data.getSkills().isEmpty()
                    && data.getSkills().getFirst().size() > index) {
                return data.getSkills().getFirst().get(index);
            }
            return fallback;
        }

        private static <T extends Trace> T findTrace(Character owner, Class<T> type) {
            for (Trace trace : owner.getTraces()) {
                if (type.isInstance(trace)) {
                    return type.cast(trace);
                }
            }
            return null;
        }
    }

    /** 绸缪: 战斗开始时，立即恢复15点能量。 */
    static class JingYuanPrepare implements Trace {
        private final double energy;

        JingYuanPrepare(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "绸缪";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (绸缪)");
        }
    }

    /** 遣将: 施放战技后，暴击率提升10%，持续2回合。 */
    static class JingYuanDispatch implements Trace {
        private final double critChance;
        private final int turns;

        JingYuanDispatch(double critChance, int turns) {
            this.critChance = critChance;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "遣将";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            owner.removeBuff("遣将");
            Buff buff = new Buff("遣将", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critChance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " crit chance +"
                    + String.format("%.0f%%", critChance * 100) + " (" + turns + " turns)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 星流霆击碎昆冈: 【神君】对相邻目标的伤害倍率额外提高, 数值等同于对主目标伤害倍率的25%。 */
    static class JingYuanE1 implements Trace {
        private final double bonus;

        JingYuanE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 戎戈动地开天阵: 【神君】行动后，景元的普攻、战技、终结技造成的伤害提高20%，持续2回合。 */
    static class JingYuanE2 implements Trace {
        private final double bonus;
        private final int turns;

        JingYuanE2(double bonus, int turns) {
            this.bonus = bonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 刃卷横云落玉沙: 【神君】每段攻击后，景元恢复2点能量。 */
    static class JingYuanE4 implements Trace {
        private final double energy;

        JingYuanE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 威灵有应破敌雠: 【神君】每段攻击后使目标陷入易伤状态, 最多叠加3层。 */
    static class JingYuanE6 implements Trace {
        private final double vuln;
        private final int maxStacks;

        JingYuanE6(double vuln, int maxStacks) {
            this.vuln = vuln;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
