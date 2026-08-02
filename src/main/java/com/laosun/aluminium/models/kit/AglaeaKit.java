package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 阿格莱雅 (Aglaea, cid 1402) — 雷属性 记忆 (忆灵: 衣匠).
 * 战技/终结技 (Summon/Enhance) 由通用执行器处理 (引擎自动召唤忆灵并进入强化状态).
 */
public final class AglaeaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1402;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new AglaeaShortSight(param(byId, 1402101, 0, 7.2), param(byId, 1402101, 1, 3.6)),
                new AglaeaWeave(intParam(byId, 1402102, 0, 1)),
                new AglaeaSun(param(byId, 1402103, 0, 0.5), param(byId, 1402103, 1, 0.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new AglaeaE1(param(e, 0, 0.15), param(e, 1, 20));
            case 2 -> new AglaeaE2(param(e, 0, 0.14), intParam(e, 1, 3));
            case 4 -> new AglaeaE4(intParam(e, 0, 1), intParam(e, 1, 2));
            case 6 -> new AglaeaE6(param(e, 0, 0.2), param(e, 1, 0.1), param(e, 2, 0.3), param(e, 3, 0.6));
            default -> null;
        };
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 短视之惩: 处于【至高之姿】状态时, 阿格莱雅与衣匠的攻击力提高 (等同于速度的7.2%+3.6%)。 */
    static class AglaeaShortSight implements Trace {
        private final double selfSpeedRatio;
        private final double servantSpeedRatio;

        AglaeaShortSight(double selfSpeedRatio, double servantSpeedRatio) {
            this.selfSpeedRatio = selfSpeedRatio;
            this.servantSpeedRatio = servantSpeedRatio;
        }

        @Override
        public String getName() {
            return "短视之惩";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("短视之惩", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(0.0,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 织运之竭: 衣匠消失时, 忆灵天赋的速度提高层数最多保留1层。 */
    static class AglaeaWeave implements Trace {
        private final int maxLayers;

        AglaeaWeave(int maxLayers) {
            this.maxLayers = maxLayers;
        }

        @Override
        public String getName() {
            return "织运之竭";
        }
    }

    /** 飞驰之阳: 战斗开始时, 若自身能量不足50%, 恢复能量至50%。 */
    static class AglaeaSun implements Trace {
        private final double threshold;
        private final double target;

        AglaeaSun(double threshold, double target) {
            this.threshold = threshold;
            this.target = target;
        }

        @Override
        public String getName() {
            return "飞驰之阳";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double max = owner.getMaxEnergy();
            if (owner.getEnergy() < max * threshold) {
                owner.gainEnergy(Math.max(0, max * target - owner.getEnergy()));
                IO.println("  [行迹] " + owner.getName() + " energy restored to 50%");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 飘曳金星的行列: 处于【间隙织线】状态的敌人受到的伤害提高15%。 */
    static class AglaeaE1 implements Trace {
        private final double vuln;
        private final double energy;

        AglaeaE1(double vuln, double energy) {
            this.vuln = vuln;
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂1", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂2 行舟命运的眼睑: 阿格莱雅与衣匠造成伤害时无视目标14%防御力。 */
    static class AglaeaE2 implements Trace {
        private final double defIgnore;
        private final int maxStacks;

        AglaeaE2(double defIgnore, int maxStacks) {
            this.defIgnore = defIgnore;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 大理石内的闪烁: 忆灵天赋速度提高层数上限+1。 */
    static class AglaeaE4 implements Trace {
        private final int layerBonus;
        private final int ignored;

        AglaeaE4(int layerBonus, int ignored) {
            this.layerBonus = layerBonus;
            this.ignored = ignored;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 盈虚无常的金线: 至高之姿状态下, 自身与衣匠雷属性抗性穿透提高20%。 */
    static class AglaeaE6 implements Trace {
        private final double pen;
        private final double speed160;
        private final double speed240;
        private final double speed320;

        AglaeaE6(double pen, double speed160, double speed240, double speed320) {
            this.pen = pen;
            this.speed160 = speed160;
            this.speed240 = speed240;
            this.speed320 = speed320;
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
}
