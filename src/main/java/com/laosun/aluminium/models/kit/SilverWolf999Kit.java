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
 * 银狼LV.999 (Silver Wolf LV.999, cid 1506) — 虚数属性 欢愉.
 * 终结技 (Enhance) 由引擎自动进入【无敌玩家】强化状态; 欢愉技 (skill 20/21) 由通用执行器处理.
 */
public final class SilverWolf999Kit implements CharacterKit {

    @Override
    public int cid() {
        return 1506;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SilverWolf999Speedrun(param(byId, 1506101, 0, 160), param(byId, 1506101, 1, 0.5),
                        param(byId, 1506101, 2, 1), param(byId, 1506101, 3, 0.02),
                        param(byId, 1506101, 4, 100)),
                new SilverWolf999Ending(intParam(byId, 1506102, 0, 20), intParam(byId, 1506102, 1, 40),
                        intParam(byId, 1506102, 2, 20), intParam(byId, 1506102, 3, 20)),
                new SilverWolf999Hidden(intParam(byId, 1506103, 0, 20)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SilverWolf999E1(param(e, 0, 0.2), param(e, 1, 0.2));
            case 2 -> new SilverWolf999E2(intParam(e, 0, 120));
            case 4 -> new SilverWolf999E4(intParam(e, 0, 5));
            case 6 -> new SilverWolf999E6(param(e, 0, 0.2), param(e, 1, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SilverWolf999Kit::silverWolf999Skill;
            case 8 -> SilverWolf999Kit::silverWolf999EnhancedBasic;
            case 20 -> SilverWolf999Kit::silverWolf999ElationSkill;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得#2个笑点, 对敌方全体造成#1%伤害. */
    static void silverWolf999Skill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int laugh = ctx.intParam(1, 5);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        battle.addLaughPoints(laugh);
        SilverWolf999Kit.addHiddenScore(user, laugh);
        IO.println("  " + user.getName() + " gains " + laugh + " 笑点 & 隐藏分");
    }

    /** 强化普攻 奖励关: 造成#1%伤害 (均分为100段弹射). */
    static void silverWolf999EnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double totalMult = ctx.firstParam();
        int segments = ctx.intParam(2, 100);
        double perSegment = totalMult / segments;
        for (int i = 0; i < segments; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamage(user, alive.get((int) (Math.random() * alive.size())),
                    perSegment, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                            Element.IMAGINARY));
        }
        IO.println("  " + user.getName() + " 狼尊时刻: " + segments + "-segment bounce!");
    }

    /** 欢愉技 殿堂级操作回放: 获得#1点【隐藏分】. */
    static void silverWolf999ElationSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int hidden = ctx.intParam(0, 15);
        SilverWolf999Kit.addHiddenScore(user, hidden);
        IO.println("  " + user.getName() + " gains " + hidden + " 【隐藏分】");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 假结局速通攻略: 速度≥160时, 使自身欢愉度提高50%, 每超过1点速度欢愉度提高2%。 */
    static class SilverWolf999Speedrun implements Trace {
        private final double speedThreshold;
        private final double base;
        private final double step;
        private final double perStep;
        private final double maxExtra;

        SilverWolf999Speedrun(double speedThreshold, double base, double step, double perStep, double maxExtra) {
            this.speedThreshold = speedThreshold;
            this.base = base;
            this.step = step;
            this.perStep = perStep;
            this.maxExtra = maxExtra;
        }

        @Override
        public String getName() {
            return "假结局速通攻略";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            double speed = owner.getAttribute(AttributeType.SPEED) != null
                    ? owner.getAttribute(AttributeType.SPEED).get() : 0;
            double bonus = 0;
            if (speed >= speedThreshold) {
                bonus = base + Math.min(maxExtra, speed - speedThreshold) / step * perStep;
            }
            owner.removeBuff("假结局速通攻略");
            Buff buff = new Buff("假结局速通攻略", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 真结局解锁条件: 若施放欢愉技计入的笑点≥20个, 额外获得20点【隐藏分】。 */
    static class SilverWolf999Ending implements Trace {
        private final int threshold1;
        private final int threshold2;
        private final int score1;
        private final int score2;

        SilverWolf999Ending(int threshold1, int threshold2, int score1, int score2) {
            this.threshold1 = threshold1;
            this.threshold2 = threshold2;
            this.score1 = score1;
            this.score2 = score2;
        }

        @Override
        public String getName() {
            return "真结局解锁条件";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ELATION) {
                return;
            }
            double laugh = battle.getLaughPoints();
            int score = 0;
            if (laugh >= threshold2) {
                score = score1 + score2;
            } else if (laugh >= threshold1) {
                score = score1;
            }
            if (score > 0) {
                SilverWolf999Kit.addHiddenScore(owner, score);
                IO.println("  [行迹] " + owner.getName() + " gains " + score + " 【隐藏分】 (真结局)");
            }
        }
    }

    /** 隐藏关卡全成就: 进入【无敌玩家】状态后, 获得20点【隐藏分】。 */
    static class SilverWolf999Hidden implements Trace {
        private final int score;

        SilverWolf999Hidden(int score) {
            this.score = score;
        }

        @Override
        public String getName() {
            return "隐藏关卡全成就";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA && owner.isEnhanced()) {
                SilverWolf999Kit.addHiddenScore(owner, score);
                IO.println("  [行迹] " + owner.getName() + " enters 无敌玩家: +" + score + " 【隐藏分】");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 以太编辑：星魂+1: 结界中的敌方目标受到的伤害提高20%。 */
    static class SilverWolf999E1 implements Trace {
        private final double keepRatio;
        private final double vuln;

        SilverWolf999E1(double keepRatio, double vuln) {
            this.keepRatio = keepRatio;
            this.vuln = vuln;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂1", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂2 是机制，不是BUG: 进入无敌玩家状态后, 每120点隐藏分获得1个额外回合。 */
    static class SilverWolf999E2 implements Trace {
        private final int scorePerTurn;

        SilverWolf999E2(int scorePerTurn) {
            this.scorePerTurn = scorePerTurn;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 我来，我见，我..秒了: 崩坏级伤害演示的欢愉伤害额外计入5倍笑点。 */
    static class SilverWolf999E4 implements Trace {
        private final int laughMultiplier;

        SilverWolf999E4(int laughMultiplier) {
            this.laughMultiplier = laughMultiplier;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 我独自满级！: 敌方目标进入战斗时被植入全属性弱点。 */
    static class SilverWolf999E6 implements Trace {
        private final double resDown;
        private final double enhancedBonus;

        SilverWolf999E6(double resDown, double enhancedBonus) {
            this.resDown = resDown;
            this.enhancedBonus = enhancedBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                for (Element element : Element.values()) {
                    enemy.addTemporaryWeakness(element, 1);
                }
            }
            IO.println("  [星魂] 禁限弱点: all enemies gain all weaknesses");
        }
    }

    // ─── 【隐藏分】代理 (天赋 有我在，把把都是顺风局) ────────────────────

    private static final java.util.Map<CanHit, Integer> HIDDEN_SCORE = new java.util.concurrent.ConcurrentHashMap<>();

    static int hiddenScore(CanHit owner) {
        return HIDDEN_SCORE.getOrDefault(owner, 0);
    }

    static void addHiddenScore(CanHit owner, int amount) {
        HIDDEN_SCORE.put(owner, Math.min(300, hiddenScore(owner) + amount));
    }
}
