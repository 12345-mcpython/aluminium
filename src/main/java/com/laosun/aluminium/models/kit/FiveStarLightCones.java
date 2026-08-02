package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Trace;

import java.util.List;

/**
 * 五星光锥被动 (22 系列与 23 系列).
 *
 * <p>Each cone's passive is implemented as a {@link Trace} attached to the
 * equipped character at build time. Values are read from the weapon's
 * level-1 {@code skill_value} params, mirroring the game descriptions.
 */
public final class FiveStarLightCones {

    private FiveStarLightCones() {
    }

    /**
     * The hand-written passive for a FiveStarLightCones light cone ID, or {@code null}
     * if the cone falls back to the generic interpreter.
     */
    public static Trace forWeapon(int wid, List<Double> params) {
        double p0 = param(params, 0, 0);
        double p1 = param(params, 1, 0);
        double p2 = param(params, 2, 0);
        double p3 = param(params, 3, 0);
        double p4 = param(params, 4, 0);
        double p5 = param(params, 5, 0);
        double p6 = param(params, 6, 0);
        return switch (wid) {
            case 22000 -> new Ctx.NewbieTask(p1);                             // 新手任务开始前
            case 22001 -> new Ctx.HeyOverHere(p1, (int) p2);                  // 嘿，我在这儿
            case 22002 -> new Ctx.TomorrowJourney(p1, (int) p2);              // 为了明日的旅途
            case 22003 -> new Ctx.HuntMelody(p1, (int) p2);                   // 忍事录•音律狩猎
            case 22004 -> new Ctx.CosmicDeal(p1);                             // 宇宙大生意
            case 22005 -> new Ctx.ForeverMeal(p1, (int) p2);                  // 永远的迷境饭
            case 22006 -> new Ctx.PinkTomorrow(p1, p2);                       // 飞向粉色的明天
            case 22007 -> new Ctx.FutureTogether(p1, (int) p2);               // 未来，有我们一起
            case 23000 -> new Ctx.NightTrain(p0, p1);                         // 银河铁道之夜
            case 23001 -> new Ctx.Night(p1, p2, p3, (int) p4);                // 于夜色中
            case 23002 -> new Ctx.Irreplaceable(p1, p2);                      // 无可取代的东西
            case 23003 -> new Ctx.BattleNotOver(p1, (int) p2);                // 但战斗还未结束
            case 23004 -> new Ctx.WorldName(p0, p1, p2);                      // 以世界之名
            case 23005 -> new Ctx.MomentVictory(p2);                          // 制胜的瞬间
            case 23006 -> new Ctx.Waiting(p0, p2, (int) p3, (int) p4);        // 只需等待
            case 23007 -> new Ctx.Rain(p1, p2, (int) p3, p4);                 // 雨一直下
            case 23008 -> new Ctx.Coffin(p1, p2, (int) p3);                   // 棺的回响
            case 23009 -> new Ctx.Unreachable(p2);                            // 到不了的彼岸
            case 23010 -> new Ctx.BeforeDawn(p1, p2);                         // 拂晓之前
            case 23011 -> new Ctx.ClosedEyes(p1, p2, (int) p4);               // 她已闭上双眼
            case 23012 -> new Ctx.SleepMud(p1, (int) p2, (int) p3);           // 如泥酣眠
            case 23013 -> new Ctx.Season(p2);                                 // 时节不居
            case 23014 -> new Ctx.SwordBody((int) p1, p2, p3);                // 此身为剑
            case 23015 -> new Ctx.Brighter((int) p1, (int) p2, p3, p4);       // 比阳光更明亮的
            case 23016 -> new Ctx.Happy(p1, p2, (int) p3);                    // 烦恼着，幸福着
            case 23017 -> new Ctx.NightFright(p1, p2, (int) p3, (int) p4);    // 惊魂夜
            case 23018 -> new Ctx.MomentEye(p1, p2);                          // 片刻，留在眼底
            case 23019 -> new Ctx.Mirror(p1, (int) p2, p3, p4);               // 镜中故我
            case 23020 -> new Ctx.Baptism(p1, (int) p2, p3, p4, (int) p5);    // 纯粹思维的洗礼
            case 23021 -> new Ctx.GameWorld(p1, (int) p2, (int) p3, p4, (int) p5); // 游戏尘寰
            case 23022 -> new Ctx.Recast(p1, p2, (int) p3);                   // 重塑时光之忆
            case 23023 -> new Ctx.FateUnfair(p1, (int) p2, p3, p4, (int) p5); // 命运从未公平
            case 23024 -> new Ctx.FadingShore(p1, p2);                        // 行于流逝的岸
            case 23025 -> new Ctx.DreamReturn(p1, p2, (int) p3);              // 梦应归于何处
            case 23026 -> new Ctx.GlitterNight(p0, (int) p1, p2, p3, (int) p4); // 夜色流光溢彩
            case 23027 -> new Ctx.SecondLife(p1, p2);                         // 驶向第二次生命
            case 23028 -> new Ctx.Hope(p1, p2, p3, (int) p4, p5, (int) p6);   // 偏偏希望无价
            case 23029 -> new Ctx.InnumerableSprings(p1, p2, (int) p3, p4, p5); // 那无数个春天
            case 23030 -> new Ctx.SunsetDance((int) p1, p2);                  // 落日时起舞
            case 23031 -> new Ctx.HuntLight(p1, (int) p2);                    // 我将，巡征追猎
            case 23032 -> new Ctx.Scent(p1, p2, p3, (int) p4);                // 唯有香如故
            case 23033 -> new Ctx.Ninja(p1, p2);                              // 忍法帖•缭乱破魔
            case 23034 -> new Ctx.Flight(p0, p1, (int) p2, (int) p3, (int) p4); // 回到大地的飞行
            case 23035 -> new Ctx.Homeward(p1, p2, (int) p3, (int) p4);       // 长路终有归途
            case 23036 -> new Ctx.GoldenWeave((int) p1, p2, p3);               // 将光阴织成黄金
            case 23037 -> new Ctx.Unaskable(p2, p3, (int) p4);                // 向着不可追问处
            case 23038 -> new Ctx.FlowerTime(p1, (int) p2, p3, p4, (int) p5); // 如果时间是一朵花
            case 23039 -> new Ctx.FireBlood(p1, p2, p3, p4);                  // 血火啊，燃烧前路
            case 23040 -> new Ctx.FarewellPrettier(p1, (int) p2, p3);         // 让告别，更美一些
            case 23041 -> new Ctx.OnceFlame(p1, p2, (int) p3, p4);            // 生命当付之一炬
            case 23042 -> new Ctx.RainbowSky(p1, p3, (int) p4, p5);           // 愿虹光永驻天空
            case 23043 -> new Ctx.LiesWind(p1, p2, (int) p3, p4, p5, p6);     // 谎言在风中飘扬
            case 23044 -> new Ctx.DawnBurn(p1, p2);                           // 黎明恰如此燃烧
            case 23045 -> new Ctx.Coronation(p5, p1, p2, (int) p3, p4);       // 没有回报的加冕
            case 23046 -> new Ctx.HellIdeal(p2, p3, (int) p4);                // 理想燃烧的地狱
            case 23047 -> new Ctx.OceanSong(p1, (int) p2, p3, (int) p4, p5, (int) p6); // 海洋为何而歌
            case 23048 -> new Ctx.GoldBlood((int) p1, p3, (int) p4);          // 金血铭刻的时代
            case 23049 -> new Ctx.NightStar(p1, p2, p3);                      // 致长夜的星光
            case 23050 -> new Ctx.HerFlame(p1);                               // 勿忘她的火焰
            case 23051 -> new Ctx.Mountains(p4, p5, p1, p2, (int) p3);        // 纵然山河万程
            case 23052 -> new Ctx.Eternity(p1, p2, p3);                       // 爱如此刻永恒
            case 23053 -> new Ctx.FlowerWorld(p1, (int) p2, p3, (int) p4, p5, (int) p6); // 花花世界迷人眼
            case 23054 -> new Ctx.SheSees(p1, p2, (int) p3, p4, p5);          // 当她决定看见
            case 23056 -> new Ctx.LieEnd((int) p1, (int) p2, p3, p4);         // 一场谎言的终幕
            case 23057 -> new Ctx.GalaxyCity(p1, p2, (int) p3);               // 欢迎来到银河城
            case 23058 -> new Ctx.FlowerSeason(p1, (int) p2, p3, p4, p5, p6); // 邂逅于下一个花季
            case 23059 -> new Ctx.Inferno(p1, (int) p2, p3, p4);              // 灼尽炼狱的新骸
            case 23060 -> new Ctx.StarNight(p1, (int) p2, p3, (int) p4, p5, p6); // 当一颗星照亮夜空
            case 23061 -> new Ctx.Spark(p1, (int) p2, (int) p3, p4);          // 星火悄然闪耀
            case 23062 -> new Ctx.SeeMe(p2, (int) p3, p4, p5);                // 所见即我
            default -> null;
        };
    }

    private static double param(List<Double> params, int index, double fallback) {
        return params != null && index < params.size() ? params.get(index) : fallback;
    }

    private static final class Ctx {
        // ═══ 22*** 五星光锥 (活动赠送) ═════════════════════════════════

        /** 新手任务开始前: 攻击防御力被降低的敌方目标后, 恢复#2能量. */
        static final class NewbieTask implements Trace {
            private final double energy;

            NewbieTask(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "新手任务开始前";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                boolean hitDefDown = targets.stream()
                        .anyMatch(t -> !t.isDeath() && t.getBuffs().stream()
                                .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                                .flatMap(b -> b.getModifiers() == null ? java.util.stream.Stream.empty()
                                        : b.getModifiers().stream())
                                .anyMatch(m -> m.attribute() == AttributeType.DEFENCE
                                        && m.modifier().getValue() < 0));
                if (hitDefDown) {
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 嘿，我在这儿: 施放战技时, 治疗量提高#2, 持续#3回合. */
        static final class HeyOverHere implements Trace {
            private final double healBoost;
            private final int turns;

            HeyOverHere(double healBoost, int turns) {
                this.healBoost = healBoost;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "嘿，我在这儿";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                owner.removeBuff("嘿，我在这儿");
                Buff buff = new Buff("嘿，我在这儿", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 为了明日的旅途: 施放终结技后, 造成的伤害提高#2, 持续#3回合. */
        static final class TomorrowJourney implements Trace {
            private final double bonus;
            private final int turns;

            TomorrowJourney(double bonus, int turns) {
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "为了明日的旅途";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("为了明日的旅途");
                Buff buff = new Buff("为了明日的旅途", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 忍事录•音律狩猎: 损失或回复生命后, 暴击伤害提高#2, 持续#3回合 (每回合1次). */
        static final class HuntMelody implements Trace {
            private final double cdmg;
            private final int turns;
            private boolean usedThisTurn = false;
            private double lastHp = -1;

            HuntMelody(double cdmg, int turns) {
                this.cdmg = cdmg;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "忍事录•音律狩猎";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                lastHp = owner.getCurrentHp();
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
                // 回复生命 (与上次回合开始记录的生命值比较).
                if (lastHp >= 0 && owner.getCurrentHp() > lastHp) {
                    refresh(battle, owner);
                }
                lastHp = owner.getCurrentHp();
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (damage > 0) {
                    refresh(battle, owner);
                }
            }

            private void refresh(Battle battle, Character owner) {
                if (usedThisTurn) {
                    return;
                }
                usedThisTurn = true;
                owner.removeBuff("忍事录•音律狩猎");
                Buff buff = new Buff("忍事录•音律狩猎", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 宇宙大生意: 敌方目标每拥有1个不同属性的弱点, 对其伤害提高#2 (最多计入7个). */
        static final class CosmicDeal implements Trace {
            private final double perWeakness;

            CosmicDeal(double perWeakness) {
                this.perWeakness = perWeakness;
            }

            @Override
            public String getName() {
                return "宇宙大生意";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (!(defender instanceof Enemy enemy)) {
                    return 1.0;
                }
                int weaknesses = Math.min(7, enemy.getWeaknesses().size());
                return 1 + weaknesses * perWeakness;
            }
        }

        /** 永远的迷境饭: 施放战技后, 攻击力提高#2, 最多叠加#3层. */
        static final class ForeverMeal implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            ForeverMeal(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "永远的迷境饭";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("永远的迷境饭");
                Buff buff = new Buff("永远的迷境饭", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 飞向粉色的明天: 开拓者•记忆装备时, 我方全体伤害提高#2, 强化普攻伤害提高#3. */
        static final class PinkTomorrow implements Trace {
            private final double partyBonus;
            private final double enhancedBasicBonus;

            PinkTomorrow(double partyBonus, double enhancedBasicBonus) {
                this.partyBonus = partyBonus;
                this.enhancedBasicBonus = enhancedBasicBonus;
            }

            @Override
            public String getName() {
                return "飞向粉色的明天";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                if (owner.getCid() != 8006) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("飞向粉色的明天", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (owner.getCid() != 8006 || type != SkillType.COMMON) {
                    return 1.0;
                }
                return owner.isEnhanced() ? 1 + enhancedBasicBonus : 1.0;
            }
        }

        /** 未来，有我们一起: 施放终结技后, 我方全体欢愉度提高#2, 持续#3回合. */
        static final class FutureTogether implements Trace {
            private final double elation;
            private final int turns;

            FutureTogether(double elation, int turns) {
                this.elation = elation;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "未来，有我们一起";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("未来，有我们一起", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(elation,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        // ═══ 23*** 五星光锥 ═══════════════════════════════════════════

        /** 银河铁道之夜: 每有1个敌方目标攻击力+#2 (最多5层); 击破弱点后伤害+#1 (1回合). */
        static final class NightTrain implements Trace {
            private final double breakBonus;
            private final double perEnemy;

            NightTrain(double breakBonus, double perEnemy) {
                this.breakBonus = breakBonus;
                this.perEnemy = perEnemy;
            }

            @Override
            public String getName() {
                return "银河铁道之夜";
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
                int stacks = Math.min(5, battle.getAliveEnemies().size());
                owner.removeBuff("银河铁道之夜·攻");
                Buff buff = new Buff("银河铁道之夜·攻", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perEnemy * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                owner.removeBuff("银河铁道之夜");
                Buff buff = new Buff("银河铁道之夜", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(breakBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 于夜色中: 速度>100时每超出#1点, 普攻/战技伤害+#2, 终结技暴伤+#3 (最多#4层). */
        static final class Night implements Trace {
            private final double perSpeed;
            private final double basicSkill;
            private final double ultCdmg;
            private final int maxStacks;

            Night(double perSpeed, double basicSkill, double ultCdmg, int maxStacks) {
                this.perSpeed = perSpeed;
                this.basicSkill = basicSkill;
                this.ultCdmg = ultCdmg;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "于夜色中";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double speed = owner.getAttribute(AttributeType.SPEED) != null
                        ? owner.getAttribute(AttributeType.SPEED).get() : 0;
                int stacks = (int) Math.min(maxStacks, Math.max(0, (speed - 100) / perSpeed));
                if (type == SkillType.COMMON || type == SkillType.SKILL) {
                    return 1 + stacks * basicSkill;
                }
                if (type == SkillType.ULTRA) {
                    return 1 + stacks * ultCdmg;
                }
                return 1.0;
            }
        }

        /** 无可取代的东西: 击杀或受击后回复#1攻击生命并伤害+#2 (到下回合结束, 每回合1次). */
        static final class Irreplaceable implements Trace {
            private final double healRatio;
            private final double bonus;
            private boolean usedThisTurn = false;

            Irreplaceable(double healRatio, double bonus) {
                this.healRatio = healRatio;
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "无可取代的东西";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            private void trigger(Battle battle, Character owner) {
                if (usedThisTurn) {
                    return;
                }
                usedThisTurn = true;
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                owner.heal(atk * healRatio);
                owner.removeBuff("无可取代的东西");
                Buff buff = new Buff("无可取代的东西", Buff.Category.BUFF, owner, owner, 2)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (victim instanceof Enemy) {
                    trigger(battle, owner);
                }
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                trigger(battle, owner);
            }
        }

        /** 但战斗还未结束: 每施放2次终结技回复1战技点; 战技后下一个行动的队友伤害+#1. */
        static final class BattleNotOver implements Trace {
            private final double allyBonus;
            private final int allyTurns;
            private int ultCount = 0;

            BattleNotOver(double allyBonus, int allyTurns) {
                this.allyBonus = allyBonus;
                this.allyTurns = allyTurns;
            }

            @Override
            public String getName() {
                return "但战斗还未结束";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    ultCount++;
                    if (ultCount % 2 == 0) {
                        battle.addSkillPoints(1);
                    }
                } else if (type == SkillType.SKILL) {
                    for (Signal signal : battle.getQueueSnapshot()) {
                        CanHit next = signal.getCanHit();
                        if (next != owner && next.getCamp() == owner.getCamp() && !next.isDeath()) {
                            Buff buff = new Buff("但战斗还未结束", Buff.Category.BUFF, owner, next, allyTurns)
                                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(allyBonus,
                                            DoubleValue.Modifier.ModifierSource.BUFF));
                            battle.applyBuff(next, buff);
                            return;
                        }
                    }
                }
            }
        }

        /** 以世界之名: 对负面效果目标伤害+#1; 战技时效果命中+#2, 攻击力+#3. */
        static final class WorldName implements Trace {
            private final double debuffBonus;
            private final double ehr;
            private final double atk;

            WorldName(double debuffBonus, double ehr, double atk) {
                this.debuffBonus = debuffBonus;
                this.ehr = ehr;
                this.atk = atk;
            }

            @Override
            public String getName() {
                return "以世界之名";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasDebuff() ? 1 + debuffBonus : 1.0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                owner.removeBuff("以世界之名");
                Buff buff = new Buff("以世界之名", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atk,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.EFFECT_HIT_RATE, DoubleValue.Modifier.pure(ehr,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 制胜的瞬间: 受到攻击概率提高; 受击后防御额外+#1 (持续到回合结束). */
        static final class MomentVictory implements Trace {
            private final double defBonus;

            MomentVictory(double defBonus) {
                this.defBonus = defBonus;
            }

            @Override
            public String getName() {
                return "制胜的瞬间";
            }

            @Override
            public double aggroMultiplier(Battle battle, Character owner) {
                return 2.0;
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                owner.removeBuff("制胜的瞬间");
                Buff buff = new Buff("制胜的瞬间", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(defBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 只需等待: 攻击后速度+#2 (最多#3层); 击中时使目标陷入游丝 (视作触电, 每回合#1攻击力雷伤害, #4回合). */
        static final class Waiting implements Trace {
            private final double dotRatio;
            private final double perSpeed;
            private final int maxStacks;
            private final int dotTurns;

            Waiting(double dotRatio, double perSpeed, int maxStacks, int dotTurns) {
                this.dotRatio = dotRatio;
                this.perSpeed = perSpeed;
                this.maxStacks = maxStacks;
                this.dotTurns = dotTurns;
            }

            @Override
            public String getName() {
                return "只需等待";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                int current = 0;
                for (Buff b : owner.getBuffs()) {
                    if ("只需等待·速".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                        current = (int) Math.round(b.getModifiers().getFirst().modifier().getValue() / perSpeed);
                    }
                }
                int stacks = Math.min(maxStacks, current + 1);
                owner.removeBuff("只需等待·速");
                Buff speed = new Buff("只需等待·速", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(perSpeed * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, speed);
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("游丝")) {
                        continue;
                    }
                    double atk = owner.getAttribute(AttributeType.ATTACK) != null
                            ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                    target.applyDot(new Buff.Dot("游丝", owner, target, atk * dotRatio,
                            Element.THUNDER, dotTurns));
                    Buff mark = new Buff("游丝", Buff.Category.DEBUFF, owner, target, dotTurns);
                    battle.applyBuff(target, mark);
                }
            }
        }

        /** 雨一直下: 对≥#3个负面效果目标伤害时暴击率+#4; 攻击后#1概率对随机1个未持有以太编码的受击目标施加 (易伤#2, 1回合). */
        static final class Rain implements Trace {
            private final double chance;
            private final double vuln;
            private final int debuffCount;
            private final double crit;

            Rain(double chance, double vuln, int debuffCount, double crit) {
                this.chance = chance;
                this.vuln = vuln;
                this.debuffCount = debuffCount;
                this.crit = crit;
            }

            @Override
            public String getName() {
                return "雨一直下";
            }

            @Override
            public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
                long debuffs = defender.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                return debuffs >= debuffCount ? crit : 0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("以太编码")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("以太编码", Buff.Category.DEBUFF, owner, target, 1)
                                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                        return;
                    }
                }
            }
        }

        /** 棺的回响: 攻击每击中1名不同目标恢复#2能量 (每次攻击最多#3次); 终结技后全队速度+#1点 (1回合). */
        static final class Coffin implements Trace {
            private final double speedFlat;
            private final double perTarget;
            private final int maxTargets;

            Coffin(double speedFlat, double perTarget, int maxTargets) {
                this.speedFlat = speedFlat;
                this.perTarget = perTarget;
                this.maxTargets = maxTargets;
            }

            @Override
            public String getName() {
                return "棺的回响";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        Buff buff = new Buff("棺的回响", Buff.Category.BUFF, owner, ally, 1)
                                .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speedFlat,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                    return;
                }
                if (type != SkillType.COMMON && type != SkillType.SKILL) {
                    return;
                }
                long distinct = targets.stream().filter(t -> !t.isDeath()).distinct().count();
                owner.gainEnergy(Math.min(maxTargets, (int) distinct) * perTarget);
            }
        }

        /** 到不了的彼岸: 受击后伤害+#1, 施放攻击后解除. */
        static final class Unreachable implements Trace {
            private final double bonus;

            Unreachable(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "到不了的彼岸";
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                owner.removeBuff("到不了的彼岸");
                Buff buff = new Buff("到不了的彼岸", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("到不了的彼岸");
            }
        }

        /** 拂晓之前: 战技/终结技伤害+#1; 施放战技/终结技后获得梦身, 追加攻击消耗梦身伤害+#2. */
        static final class BeforeDawn implements Trace {
            private final double skillUlt;
            private final double followUp;

            BeforeDawn(double skillUlt, double followUp) {
                this.skillUlt = skillUlt;
                this.followUp = followUp;
            }

            @Override
            public String getName() {
                return "拂晓之前";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type == SkillType.SKILL || type == SkillType.ULTRA) {
                    return 1 + skillUlt;
                }
                if (type == SkillType.TALENT) {
                    return owner.hasBuffNamed("梦身") ? 1 + followUp : 1.0;
                }
                return 1.0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL || type == SkillType.ULTRA) {
                    owner.removeBuff("梦身");
                    Buff buff = new Buff("梦身", Buff.Category.BUFF, owner, owner, -1);
                    battle.applyBuff(owner, buff);
                } else if (type == SkillType.TALENT) {
                    owner.removeBuff("梦身");
                }
            }
        }

        /** 她已闭上双眼: 生命降低时全队伤害+#1 (2回合); 战斗开始回复#2已损失生命. */
        static final class ClosedEyes implements Trace {
            private final double partyBonus;
            private final double healRatio;
            private final int turns;

            ClosedEyes(double partyBonus, double healRatio, int turns) {
                this.partyBonus = partyBonus;
                this.healRatio = healRatio;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "她已闭上双眼";
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("她已闭上双眼", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.heal((ally.getMaxHp() - ally.getCurrentHp()) * healRatio);
                }
            }
        }

        /** 如泥酣眠: 普攻/战技后暴击率+#1 (1回合, 每#3回合可触发1次). */
        static final class SleepMud implements Trace {
            private final double crit;
            private final int buffTurns;
            private final int cooldown;
            private int cdLeft = 0;

            SleepMud(double crit, int buffTurns, int cooldown) {
                this.crit = crit;
                this.buffTurns = buffTurns;
                this.cooldown = cooldown;
            }

            @Override
            public String getName() {
                return "如泥酣眠";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (cdLeft > 0) {
                    cdLeft--;
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if ((type != SkillType.COMMON && type != SkillType.SKILL) || cdLeft > 0) {
                    return;
                }
                cdLeft = cooldown;
                owner.removeBuff("如泥酣眠");
                Buff buff = new Buff("如泥酣眠", Buff.Category.BUFF, owner, owner, buffTurns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 时节不居: 提供治疗时记录治疗量; 任意我方攻击后对随机1个受击目标造成记录治疗量#1的附加伤害 (每回合1次). */
        static final class Season implements Trace {
            private final double ratio;
            private double lastHpSum = -1;
            private boolean usedThisTurn = false;

            Season(double ratio) {
                this.ratio = ratio;
            }

            @Override
            public String getName() {
                return "时节不居";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
                lastHpSum = 0;
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    lastHpSum += ally.getCurrentHp();
                }
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                if (usedThisTurn || lastHpSum < 0 || targets == null || targets.isEmpty()) {
                    return;
                }
                double sum = 0;
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    sum += ally.getCurrentHp();
                }
                double healed = Math.max(0, sum - lastHpSum);
                if (healed <= 0) {
                    return;
                }
                List<CanHit> hit = new java.util.ArrayList<>();
                for (CanHit t : targets) {
                    if (!t.isDeath()) {
                        hit.add(t);
                    }
                }
                if (hit.isEmpty()) {
                    return;
                }
                usedThisTurn = true;
                battle.dealAttackDamageBase(owner, hit.get((int) (Math.random() * hit.size())),
                        healed * ratio, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, owner.getElement()));
            }
        }

        /** 此身为剑: 队友行动后获得月蚀 (最多#1层, 每层下次攻击伤害+#2, 叠满额外无视#3防御); 施放攻击后解除. */
        static final class SwordBody implements Trace {
            private final int maxStacks;
            private final double perStack;
            private final double defIgnore;
            private int stacks = 0;

            SwordBody(int maxStacks, double perStack, double defIgnore) {
                this.maxStacks = maxStacks;
                this.perStack = perStack;
                this.defIgnore = defIgnore;
            }

            @Override
            public String getName() {
                return "此身为剑";
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                stacks = Math.min(maxStacks, stacks + 1);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return 1.0;
                }
                return 1 + perStack * stacks + (stacks >= maxStacks ? defIgnore : 0);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                    stacks = 0;
                }
            }
        }

        /** 比阳光更明亮的: 普攻获得龙吟 (#1回合, 最多#2层, 每层攻击+#3, 能量恢复效率+#4). */
        static final class Brighter implements Trace {
            private final int turns;
            private final int maxStacks;
            private final double perAtk;
            private final double perErr;
            private int stacks = 0;

            Brighter(int turns, int maxStacks, double perAtk, double perErr) {
                this.turns = turns;
                this.maxStacks = maxStacks;
                this.perAtk = perAtk;
                this.perErr = perErr;
            }

            @Override
            public String getName() {
                return "比阳光更明亮的";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("龙吟");
                Buff buff = new Buff("龙吟", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perAtk * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(perErr * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 烦恼着，幸福着: 追加攻击伤害+#1; 追加攻击后目标获得温驯 (最多#3层), 我方击中温驯目标时每层暴伤+#2. */
        static final class Happy implements Trace {
            private final double followUpBonus;
            private final double perStackCdmg;
            private final int maxStacks;

            Happy(double followUpBonus, double perStackCdmg, int maxStacks) {
                this.followUpBonus = followUpBonus;
                this.perStackCdmg = perStackCdmg;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "烦恼着，幸福着";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double mult = 1.0;
                if (type == SkillType.TALENT) {
                    mult *= 1 + followUpBonus;
                }
                for (Buff b : defender.getBuffs()) {
                    if ("温驯".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                        double stacks = b.getModifiers().getFirst().modifier().getValue() / perStackCdmg;
                        mult *= 1 + perStackCdmg * stacks;
                    }
                }
                return mult;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.TALENT) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath()) {
                        continue;
                    }
                    int current = 0;
                    for (Buff b : target.getBuffs()) {
                        if ("温驯".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                            current = (int) Math.round(b.getModifiers().getFirst().modifier().getValue() / perStackCdmg);
                        }
                    }
                    int stacks = Math.min(maxStacks, current + 1);
                    target.removeBuff("温驯");
                    Buff debuff = new Buff("温驯", Buff.Category.DEBUFF, owner, target, -1)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(perStackCdmg * stacks,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }

        /** 惊魂夜: 我方施放终结技时治疗生命最低的我方目标#1生命上限, 并使其攻击+#2 (最多#3层, #4回合). */
        static final class NightFright implements Trace {
            private final double healRatio;
            private final double perAtk;
            private final int maxStacks;
            private final int turns;

            NightFright(double healRatio, double perAtk, int maxStacks, int turns) {
                this.healRatio = healRatio;
                this.perAtk = perAtk;
                this.maxStacks = maxStacks;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "惊魂夜";
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                CanHit lowest = null;
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    if (ally.isDeath()) {
                        continue;
                    }
                    if (lowest == null || ally.getHpPercent() < lowest.getHpPercent()) {
                        lowest = ally;
                    }
                }
                if (lowest == null) {
                    return;
                }
                lowest.heal(lowest.getMaxHp() * healRatio);
                int current = 0;
                for (Buff b : lowest.getBuffs()) {
                    if ("惊魂夜·攻".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                        current = (int) Math.round(b.getModifiers().getFirst().modifier().getValue() / perAtk);
                    }
                }
                int stacks = Math.min(maxStacks, current + 1);
                lowest.removeBuff("惊魂夜·攻");
                Buff buff = new Buff("惊魂夜·攻", Buff.Category.BUFF, owner, lowest, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perAtk * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(lowest, buff);
            }
        }

        /** 片刻，留在眼底: 终结技伤害每点能量上限+#1 (最多计入#2点). */
        static final class MomentEye implements Trace {
            private final double perEnergy;
            private final double cap;

            MomentEye(double perEnergy, double cap) {
                this.perEnergy = perEnergy;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "片刻，留在眼底";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.ULTRA) {
                    return 1.0;
                }
                return 1 + Math.min(cap, owner.getMaxEnergy()) * perEnergy;
            }
        }

        /** 镜中故我: 终结技后全队伤害+#1 (3回合), 击破特攻≥#3时恢复1战技点; 战斗开始全队+#4能量. */
        static final class Mirror implements Trace {
            private final double partyBonus;
            private final int turns;
            private final double beThreshold;
            private final double energy;

            Mirror(double partyBonus, int turns, double beThreshold, double energy) {
                this.partyBonus = partyBonus;
                this.turns = turns;
                this.beThreshold = beThreshold;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "镜中故我";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("镜中故我", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                        ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
                if (be >= beThreshold) {
                    battle.addSkillPoints(1);
                }
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.gainEnergy(energy);
                }
            }
        }

        /** 纯粹思维的洗礼: 目标每有1个负面效果暴击伤害额外+#1 (最多#2层); 终结技后获得论辩 (伤害+#3, #5回合). */
        static final class Baptism implements Trace {
            private final double perDebuff;
            private final int maxDebuffs;
            private final double debateBonus;
            private final double defIgnore;
            private final int turns;

            Baptism(double perDebuff, int maxDebuffs, double debateBonus, double defIgnore, int turns) {
                this.perDebuff = perDebuff;
                this.maxDebuffs = maxDebuffs;
                this.debateBonus = debateBonus;
                this.defIgnore = defIgnore;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "纯粹思维的洗礼";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                long debuffs = defender.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                return 1 + Math.min(maxDebuffs, (int) debuffs) * perDebuff;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("论辩");
                Buff buff = new Buff("论辩", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(debateBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 游戏尘寰: 战斗开始获得假面 (#5回合, 队友暴击率+#4, 暴击伤害+#1); 每次普攻获得1层彩焰, 达到#3层后刷新假面 (#2回合). */
        static final class GameWorld implements Trace {
            private final double partyCdmg;
            private final int flameTurns;
            private final int flameCap;
            private final double partyCrit;
            private final int maskTurns;
            private int flames = 0;

            GameWorld(double partyCdmg, int flameTurns, int flameCap, double partyCrit, int maskTurns) {
                this.partyCdmg = partyCdmg;
                this.flameTurns = flameTurns;
                this.flameCap = flameCap;
                this.partyCrit = partyCrit;
                this.maskTurns = maskTurns;
            }

            @Override
            public String getName() {
                return "游戏尘寰";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                applyMask(battle, owner, maskTurns);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON) {
                    return;
                }
                flames++;
                if (flames >= flameCap) {
                    flames = 0;
                    applyMask(battle, owner, flameTurns);
                }
            }

            private void applyMask(Battle battle, Character owner, int turns) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("假面", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(partyCrit,
                                    DoubleValue.Modifier.ModifierSource.BUFF))
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(partyCdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 重塑时光之忆: 对陷入风化/灼烧/触电/裂伤目标造成伤害时分别获得1层先知 (最多#3层, 每层攻击+#1, 持续伤害无视#2防御). */
        static final class Recast implements Trace {
            private final double perAtk;
            private final double defIgnore;
            private final int maxStacks;

            Recast(double perAtk, double defIgnore, int maxStacks) {
                this.perAtk = perAtk;
                this.defIgnore = defIgnore;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "重塑时光之忆";
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
                int stacks = Math.min(maxStacks, 4);
                owner.removeBuff("先知");
                Buff buff = new Buff("先知", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perAtk * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                int found = 0;
                if (defender.hasDotOfElement(Element.WIND)) found++;
                if (defender.hasDotOfElement(Element.FIRE)) found++;
                if (defender.hasDotOfElement(Element.THUNDER)) found++;
                if (defender.hasDotOfElement(Element.PHYSICAL)) found++;
                refresh(battle, owner);
                return 1.0;
            }
        }

        /** 命运从未公平: 战斗开始暴击伤害+#1 (2回合近似); 追加攻击击中时#3概率使目标易伤#4 (#5回合). */
        static final class FateUnfair implements Trace {
            private final double cdmg;
            private final int cdmgTurns;
            private final double chance;
            private final double vuln;
            private final int vulnTurns;

            FateUnfair(double cdmg, int cdmgTurns, double chance, double vuln, int vulnTurns) {
                this.cdmg = cdmg;
                this.cdmgTurns = cdmgTurns;
                this.chance = chance;
                this.vuln = vuln;
                this.vulnTurns = vulnTurns;
            }

            @Override
            public String getName() {
                return "命运从未公平";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("命运从未公平", Buff.Category.BUFF, owner, owner, cdmgTurns)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.TALENT) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("命运从未公平·易伤")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("命运从未公平·易伤", Buff.Category.DEBUFF, owner, target, vulnTurns)
                                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 行于流逝的岸: 击中使目标陷入泡影 (1回合); 对泡影目标伤害+#1, 终结技额外+#2. */
        static final class FadingShore implements Trace {
            private final double bonus;
            private final double ultExtra;

            FadingShore(double bonus, double ultExtra) {
                this.bonus = bonus;
                this.ultExtra = ultExtra;
            }

            @Override
            public String getName() {
                return "行于流逝的岸";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("泡影")) {
                        continue;
                    }
                    Buff mark = new Buff("泡影", Buff.Category.DEBUFF, owner, target, 1);
                    battle.applyBuff(target, mark);
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (!defender.hasBuffNamed("泡影")) {
                    return 1.0;
                }
                return type == SkillType.ULTRA ? 1 + bonus + ultExtra : 1 + bonus;
            }
        }

        /** 梦应归于何处: 击破伤害时使目标陷入溃败 (#3回合, 受击破伤害+#1, 速度降低#2). */
        static final class DreamReturn implements Trace {
            private final double bonus;
            private final double slow;
            private final int turns;

            DreamReturn(double bonus, double slow, int turns) {
                this.bonus = bonus;
                this.slow = slow;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "梦应归于何处";
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                Buff debuff = new Buff("溃败", Buff.Category.DEBUFF, owner, enemy, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.DEBUFF))
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-slow,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
            }
        }

        /** 夜色流光溢彩: 我方每次攻击时获得1层歌咏 (每层能量恢复效率+#1, 最多#2层); 终结技时移除歌咏并获得华彩 (攻击力+#4, 全队伤害+#3, #5回合). */
        static final class GlitterNight implements Trace {
            private final double perErr;
            private final int maxStacks;
            private final double partyBonus;
            private final double atkBonus;
            private final int turns;
            private int stacks = 0;

            GlitterNight(double perErr, int maxStacks, double partyBonus, double atkBonus, int turns) {
                this.perErr = perErr;
                this.maxStacks = maxStacks;
                this.partyBonus = partyBonus;
                this.atkBonus = atkBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "夜色流光溢彩";
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("歌咏");
                Buff buff = new Buff("歌咏", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(perErr * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                stacks = 0;
                owner.removeBuff("歌咏");
                owner.removeBuff("华彩");
                Buff buff = new Buff("华彩", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff party = new Buff("华彩·队", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, party);
                }
            }
        }

        /** 驶向第二次生命: 击破特攻≥#1时速度+#2. */
        static final class SecondLife implements Trace {
            private final double beThreshold;
            private final double speedBonus;

            SecondLife(double beThreshold, double speedBonus) {
                this.beThreshold = beThreshold;
                this.speedBonus = speedBonus;
            }

            @Override
            public String getName() {
                return "驶向第二次生命";
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
                double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                        ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
                boolean active = owner.hasBuffNamed("驶向第二次生命");
                if (be >= beThreshold && !active) {
                    Buff buff = new Buff("驶向第二次生命", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                } else if (be < beThreshold && active) {
                    owner.removeBuff("驶向第二次生命");
                }
            }
        }

        /** 偏偏希望无价: 暴击伤害>#1时每超出#2追加攻击伤害+#3 (最多#4层); 战斗开始和普攻后终结技/追加无视#5防御 (#6回合). */
        static final class Hope implements Trace {
            private final double cdmgThreshold;
            private final double perStep;
            private final double perBonus;
            private final int maxStacks;
            private final double defIgnore;
            private final int turns;

            Hope(double cdmgThreshold, double perStep, double perBonus, int maxStacks, double defIgnore, int turns) {
                this.cdmgThreshold = cdmgThreshold;
                this.perStep = perStep;
                this.perBonus = perBonus;
                this.maxStacks = maxStacks;
                this.defIgnore = defIgnore;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "偏偏希望无价";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.TALENT) {
                    return 1.0;
                }
                double cdmg = owner.getAttribute(AttributeType.CRIT_ATTACK) != null
                        ? owner.getAttribute(AttributeType.CRIT_ATTACK).get() : 0;
                int stacks = (int) Math.min(maxStacks, Math.max(0, (cdmg - cdmgThreshold) / perStep));
                return 1 + stacks * perBonus;
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                apply(battle, owner);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON) {
                    apply(battle, owner);
                }
            }

            private void apply(Battle battle, Character owner) {
                owner.removeBuff("偏偏希望无价");
                Buff buff = new Buff("偏偏希望无价", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 那无数个春天: 攻击后#1概率使目标卸甲 (易伤#2, #3回合); 若目标有持续伤害, #4概率升级为穷寇 (额外#5). */
        static final class InnumerableSprings implements Trace {
            private final double chance;
            private final double vuln;
            private final int turns;
            private final double upgradeChance;
            private final double extraVuln;

            InnumerableSprings(double chance, double vuln, int turns, double upgradeChance, double extraVuln) {
                this.chance = chance;
                this.vuln = vuln;
                this.turns = turns;
                this.upgradeChance = upgradeChance;
                this.extraVuln = extraVuln;
            }

            @Override
            public String getName() {
                return "那无数个春天";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("卸甲") || target.hasBuffNamed("穷寇")) {
                        continue;
                    }
                    if (!battle.checkEffectHit(owner, target, chance)) {
                        continue;
                    }
                    boolean upgrade = (target.hasDotOfElement(Element.WIND)
                            || target.hasDotOfElement(Element.FIRE)
                            || target.hasDotOfElement(Element.THUNDER)
                            || target.hasDotOfElement(Element.PHYSICAL))
                            && battle.checkEffectHit(owner, target, upgradeChance);
                    String name = upgrade ? "穷寇" : "卸甲";
                    double value = upgrade ? vuln + extraVuln : vuln;
                    Buff debuff = new Buff(name, Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(value,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }

        /** 落日时起舞: 受到攻击概率大幅提高; 终结技后获得火舞 (2回合, 最多#1层, 每层追加攻击伤害+#2). */
        static final class SunsetDance implements Trace {
            private final int maxStacks;
            private final double perStack;
            private int stacks = 0;

            SunsetDance(int maxStacks, double perStack) {
                this.maxStacks = maxStacks;
                this.perStack = perStack;
            }

            @Override
            public String getName() {
                return "落日时起舞";
            }

            @Override
            public double aggroMultiplier(Battle battle, Character owner) {
                return 2.0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("火舞");
                Buff buff = new Buff("火舞", Buff.Category.BUFF, owner, owner, 2);
                battle.applyBuff(owner, buff);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.TALENT ? 1 + perStack * stacks : 1.0;
            }
        }

        /** 我将，巡征追猎: 追加攻击获得1层流光 (最多#2层, 每层终结技无视#1防御); 回合开始移除1层. */
        static final class HuntLight implements Trace {
            private final double defIgnore;
            private final int maxStacks;
            private int stacks = 0;

            HuntLight(double defIgnore, int maxStacks) {
                this.defIgnore = defIgnore;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "我将，巡征追猎";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.TALENT) {
                    stacks = Math.min(maxStacks, stacks + 1);
                } else if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                    stacks = Math.max(0, stacks - 1);
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA ? 1 + stacks * defIgnore : 1.0;
            }
        }

        /** 唯有香如故: 终结技攻击后使目标陷入忘忧 (#4回合, 易伤#1, 击破特攻≥#2时额外#3). */
        static final class Scent implements Trace {
            private final double vuln;
            private final double beThreshold;
            private final double extraVuln;
            private final int turns;

            Scent(double vuln, double beThreshold, double extraVuln, int turns) {
                this.vuln = vuln;
                this.beThreshold = beThreshold;
                this.extraVuln = extraVuln;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "唯有香如故";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                        ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
                double value = vuln + (be >= beThreshold ? extraVuln : 0);
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("忘忧")) {
                        continue;
                    }
                    Buff debuff = new Buff("忘忧", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(value,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }

        /** 忍法帖•缭乱破魔: 进入战斗恢复#1能量; 终结技后获得雷遁, 施放2次普攻后行动提前#2并移除; 终结技重置. */
        static final class Ninja implements Trace {
            private final double startEnergy;
            private final double advance;
            private int basicCount = 0;

            Ninja(double startEnergy, double advance) {
                this.startEnergy = startEnergy;
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "忍法帖•缭乱破魔";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                owner.gainEnergy(startEnergy);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    basicCount = 0;
                    owner.removeBuff("雷遁");
                    Buff buff = new Buff("雷遁", Buff.Category.BUFF, owner, owner, 1);
                    battle.applyBuff(owner, buff);
                } else if (type == SkillType.COMMON) {
                    if (owner.hasBuffNamed("雷遁")) {
                        basicCount++;
                        if (basicCount >= 2) {
                            owner.removeBuff("雷遁");
                            battle.advanceByPercent(owner, advance);
                        }
                    }
                }
            }
        }

        /** 回到大地的飞行: 对单体队友施放战技/终结技后恢复#1能量, 目标获得圣咏 (#4回合, 最多#3层, 每层伤害+#2); 每施放#5次恢复1战技点. */
        static final class Flight implements Trace {
            private final double energy;
            private final double perStack;
            private final int maxStacks;
            private final int turns;
            private final int spEvery;
            private int count = 0;

            Flight(double energy, double perStack, int maxStacks, int turns, int spEvery) {
                this.energy = energy;
                this.perStack = perStack;
                this.maxStacks = maxStacks;
                this.turns = turns;
                this.spEvery = spEvery;
            }

            @Override
            public String getName() {
                return "回到大地的飞行";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                CanHit target = null;
                if (targets != null) {
                    for (CanHit t : targets) {
                        if (t != owner && t.getCamp() == owner.getCamp() && !t.isDeath()) {
                            target = t;
                            break;
                        }
                    }
                }
                if (target == null) {
                    return;
                }
                owner.gainEnergy(energy);
                int current = 0;
                for (Buff b : target.getBuffs()) {
                    if ("圣咏".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                        current = (int) Math.round(b.getModifiers().getFirst().modifier().getValue() / perStack);
                    }
                }
                int stacks = Math.min(maxStacks, current + 1);
                target.removeBuff("圣咏");
                Buff buff = new Buff("圣咏", Buff.Category.BUFF, owner, target, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(target, buff);
                count++;
                if (count % spEvery == 0) {
                    battle.addSkillPoints(1);
                }
            }
        }

        /** 长路终有归途: 击破弱点时#1概率使目标焚灼 (击破伤害+#2, #3回合, 最多#4层). */
        static final class Homeward implements Trace {
            private final double chance;
            private final double perStack;
            private final int turns;
            private final int maxStacks;

            Homeward(double chance, double perStack, int turns, int maxStacks) {
                this.chance = chance;
                this.perStack = perStack;
                this.turns = turns;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "长路终有归途";
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                if (!battle.checkEffectHit(owner, enemy, chance)) {
                    return;
                }
                int current = 0;
                for (Buff b : enemy.getBuffs()) {
                    if ("焚灼".equals(b.getName()) && !b.getModifiers().isEmpty()) {
                        current = (int) Math.round(b.getModifiers().getFirst().modifier().getValue() / perStack);
                    }
                }
                int stacks = Math.min(maxStacks, current + 1);
                enemy.removeBuff("焚灼");
                Buff debuff = new Buff("焚灼", Buff.Category.DEBUFF, owner, enemy, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
            }
        }

        /** 将光阴织成黄金: 攻击后获得织锦 (最多#1层, 每层暴击伤害+#3); 叠满时每层额外普攻伤害+#2. */
        static final class GoldenWeave implements Trace {
            private final int maxStacks;
            private final double basicExtra;
            private final double perCdmg;
            private int stacks = 0;

            GoldenWeave(int maxStacks, double basicExtra, double perCdmg) {
                this.maxStacks = maxStacks;
                this.basicExtra = basicExtra;
                this.perCdmg = perCdmg;
            }

            @Override
            public String getName() {
                return "将光阴织成黄金";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA
                        && type != SkillType.TALENT) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
            }

            @Override
            public void onSummonAction(Battle battle, Character owner,
                                       com.laosun.aluminium.models.Summon summon,
                                       SkillType type, List<? extends CanHit> targets) {
                stacks = Math.min(maxStacks, stacks + 1);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double mult = 1 + perCdmg * stacks;
                if (type == SkillType.COMMON && stacks >= maxStacks) {
                    mult += basicExtra * stacks;
                }
                return mult;
            }
        }

        /** 向着不可追问处: 终结技时战技和终结技伤害+#2 (#3回合); 能量上限≥#1时恢复1战技点. */
        static final class Unaskable implements Trace {
            private final double energyThreshold;
            private final double bonus;
            private final int turns;

            Unaskable(double energyThreshold, double bonus, int turns) {
                this.energyThreshold = energyThreshold;
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "向着不可追问处";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                if (owner.getMaxEnergy() >= energyThreshold) {
                    battle.addSkillPoints(1);
                }
                owner.removeBuff("向着不可追问处");
                Buff buff = new Buff("向着不可追问处", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 如果时间是一朵花: 追加攻击后额外恢复#1能量并获得谕示 (#2回合, 全队暴击伤害+#3); 进入战斗恢复#4能量并获得谕示 (#5回合). */
        static final class FlowerTime implements Trace {
            private final double energy;
            private final int turns;
            private final double partyCdmg;
            private final double startEnergy;
            private final int startTurns;

            FlowerTime(double energy, int turns, double partyCdmg, double startEnergy, int startTurns) {
                this.energy = energy;
                this.turns = turns;
                this.partyCdmg = partyCdmg;
                this.startEnergy = startEnergy;
                this.startTurns = startTurns;
            }

            @Override
            public String getName() {
                return "如果时间是一朵花";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                owner.gainEnergy(startEnergy);
                apply(battle, owner, startTurns);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.TALENT) {
                    return;
                }
                owner.gainEnergy(energy);
                apply(battle, owner, turns);
            }

            private void apply(Battle battle, Character owner, int duration) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("谕示", Buff.Category.BUFF, owner, ally, duration)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(partyCdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 血火啊，燃烧前路: 战技/终结技时消耗#1生命上限的生命, 本次伤害+#2, 消耗高于#3点额外+#4 (生命不低于1). */
        static final class FireBlood implements Trace {
            private final double hpRatio;
            private final double bonus;
            private final double extraThreshold;
            private final double extraBonus;

            FireBlood(double hpRatio, double bonus, double extraThreshold, double extraBonus) {
                this.hpRatio = hpRatio;
                this.bonus = bonus;
                this.extraThreshold = extraThreshold;
                this.extraBonus = extraBonus;
            }

            @Override
            public String getName() {
                return "血火啊，燃烧前路";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                double cost = owner.getMaxHp() * hpRatio;
                double actual = Math.min(cost, Math.max(0, owner.getCurrentHp() - 1));
                owner.takeDamage(actual);
                owner.removeBuff("血火啊，燃烧前路");
                Buff buff = new Buff("血火啊，燃烧前路", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(
                                bonus + (cost > extraThreshold ? extraBonus : 0),
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 让告别，更美一些: 损失生命时获得冥花 (#2回合, 伤害无视#1防御 — 近似增伤); 忆灵消失时行动提前#3. */
        static final class FarewellPrettier implements Trace {
            private final double defIgnore;
            private final int turns;
            private final double advance;
            private boolean advanced = false;

            FarewellPrettier(double defIgnore, int turns, double advance) {
                this.defIgnore = defIgnore;
                this.turns = turns;
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "让告别，更美一些";
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (damage <= 0) {
                    return;
                }
                owner.removeBuff("冥花");
                Buff buff = new Buff("冥花", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    advanced = false;
                }
            }
        }

        /** 生命当付之一炬: 回合开始恢复#4能量; 对拥有装备者添加的弱点的目标伤害+#3; 攻击使目标防御降低#1 (#2回合). */
        static final class OnceFlame implements Trace {
            private final double defDown;
            private final double bonus;
            private final int defTurns;
            private final double energy;

            OnceFlame(double defDown, double bonus, int defTurns, double energy) {
                this.defDown = defDown;
                this.bonus = bonus;
                this.defTurns = defTurns;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "生命当付之一炬";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                owner.gainEnergy(energy);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (!(defender instanceof Enemy enemy)) {
                    return 1.0;
                }
                for (Element e : Element.values()) {
                    if (enemy.getTemporaryWeaknessTurns(e) > 0) {
                        return 1 + bonus;
                    }
                }
                return 1.0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("生命当付之一炬·防")) {
                        continue;
                    }
                    Buff debuff = new Buff("生命当付之一炬·防", Buff.Category.DEBUFF, owner, target, defTurns)
                            .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }

        /** 愿虹光永驻天空: 普攻/战技/终结技时全队消耗#1当前生命并累计; 忆灵行动时对随机1个受击目标造成#4倍消耗量附加伤害, 并使敌方全体易伤#2 (#3回合). */
        static final class RainbowSky implements Trace {
            private final double hpCost;
            private final double vuln;
            private final int vulnTurns;
            private final double extraRatio;
            private double consumed = 0;

            RainbowSky(double hpCost, double vuln, int vulnTurns, double extraRatio) {
                this.hpCost = hpCost;
                this.vuln = vuln;
                this.vulnTurns = vulnTurns;
                this.extraRatio = extraRatio;
            }

            @Override
            public String getName() {
                return "愿虹光永驻天空";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    double cost = ally.getCurrentHp() * hpCost;
                    ally.takeDamage(Math.min(cost, Math.max(0, ally.getCurrentHp() - 1)));
                    consumed += cost;
                }
            }

            @Override
            public void onSummonAction(Battle battle, Character owner,
                                       com.laosun.aluminium.models.Summon summon,
                                       SkillType type, List<? extends CanHit> targets) {
                if (consumed > 0 && targets != null) {
                    List<CanHit> hit = new java.util.ArrayList<>();
                    for (CanHit t : targets) {
                        if (!t.isDeath()) {
                            hit.add(t);
                        }
                    }
                    if (!hit.isEmpty()) {
                        battle.dealAttackDamageBase(summon, hit.get((int) (Math.random() * hit.size())),
                                consumed * extraRatio, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        summon.getElement()));
                        consumed = 0;
                    }
                }
                for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                    Buff debuff = new Buff("愿虹光永驻天空", Buff.Category.DEBUFF, owner, enemy, vulnTurns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }

        /** 谎言在风中飘扬: 攻击后#1概率使目标茫然 (防御-#2, #3回合); 速度≥#6时#4概率使目标失窃 (防御-#5, 替换). */
        static final class LiesWind implements Trace {
            private final double chance1;
            private final double defDown1;
            private final int turns;
            private final double chance2;
            private final double defDown2;
            private final double speedThreshold;

            LiesWind(double chance1, double defDown1, int turns, double chance2, double defDown2, double speedThreshold) {
                this.chance1 = chance1;
                this.defDown1 = defDown1;
                this.turns = turns;
                this.chance2 = chance2;
                this.defDown2 = defDown2;
                this.speedThreshold = speedThreshold;
            }

            @Override
            public String getName() {
                return "谎言在风中飘扬";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                double speed = owner.getAttribute(AttributeType.SPEED) != null
                        ? owner.getAttribute(AttributeType.SPEED).get() : 0;
                boolean fast = speed >= speedThreshold;
                for (CanHit target : targets) {
                    if (target.isDeath()) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, Math.min(1.0, chance1))) {
                        target.removeBuff("茫然");
                        Buff debuff = new Buff("茫然", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown1,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                    if (fast && battle.checkEffectHit(owner, target, Math.min(1.0, chance2))) {
                        target.removeBuff("失窃");
                        Buff debuff = new Buff("失窃", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown2,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 黎明恰如此燃烧: 造成伤害无视#1防御; 终结技后获得烈阳 (回合开始移除, 伤害+#2). */
        static final class DawnBurn implements Trace {
            private final double defIgnore;
            private final double sunBonus;

            DawnBurn(double defIgnore, double sunBonus) {
                this.defIgnore = defIgnore;
                this.sunBonus = sunBonus;
            }

            @Override
            public String getName() {
                return "黎明恰如此燃烧";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("烈阳");
                Buff buff = new Buff("烈阳", Buff.Category.BUFF, owner, owner, 1);
                battle.applyBuff(owner, buff);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double mult = 1 + defIgnore;
                if (owner.hasBuffNamed("烈阳")) {
                    mult *= 1 + sunBonus;
                }
                return mult;
            }
        }

        /** 没有回报的加冕: 终结技时攻击力+#1, 能量上限≥#3时恢复能量上限#5的能量并攻击力额外+#2 (#4回合). */
        static final class Coronation implements Trace {
            private final double bonus1;
            private final double bonus2;
            private final double energyCap;
            private final int turns;
            private final double energyRatio;

            Coronation(double bonus1, double bonus2, double energyCap, int turns, double energyRatio) {
                this.bonus1 = bonus1;
                this.bonus2 = bonus2;
                this.energyCap = energyCap;
                this.turns = turns;
                this.energyRatio = energyRatio;
            }

            @Override
            public String getName() {
                return "没有回报的加冕";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                double atk = bonus1;
                if (owner.getMaxEnergy() >= energyCap) {
                    owner.gainEnergy(owner.getMaxEnergy() * energyRatio);
                    atk += bonus2;
                }
                owner.removeBuff("没有回报的加冕");
                Buff buff = new Buff("没有回报的加冕", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atk,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 理想燃烧的地狱: 战斗开始时若战技点上限≥6则攻击力+#1; 每次战技后攻击+#2 (最多#3次). */
        static final class HellIdeal implements Trace {
            private final double startAtk;
            private final double perSkill;
            private final int maxStacks;
            private int stacks = 0;

            HellIdeal(double startAtk, double perSkill, int maxStacks) {
                this.startAtk = startAtk;
                this.perSkill = perSkill;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "理想燃烧的地狱";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                if (Battle.MAX_SKILL_POINTS >= 6) {
                    Buff buff = new Buff("理想燃烧的地狱·始", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(startAtk,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("理想燃烧的地狱");
                Buff buff = new Buff("理想燃烧的地狱", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perSkill * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 海洋为何而歌: 目标陷入装备者负面效果时#1概率使其魂迷 (#2回合); 对魂迷目标每有1个负面效果持续伤害+#3 (最多#4层); 攻击魂迷目标时攻击者速度+#5 (#6回合). */
        static final class OceanSong implements Trace {
            private final double chance;
            private final int turns;
            private final double perDot;
            private final int maxStacks;
            private final double allySpeed;
            private final int speedTurns;

            OceanSong(double chance, int turns, double perDot, int maxStacks, double allySpeed, int speedTurns) {
                this.chance = chance;
                this.turns = turns;
                this.perDot = perDot;
                this.maxStacks = maxStacks;
                this.allySpeed = allySpeed;
                this.speedTurns = speedTurns;
            }

            @Override
            public String getName() {
                return "海洋为何而歌";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("魂迷") || !target.hasDebuff()) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff mark = new Buff("魂迷", Buff.Category.DEBUFF, owner, target, turns);
                        battle.applyBuff(target, mark);
                    }
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (!defender.hasBuffNamed("魂迷")) {
                    return 1.0;
                }
                long debuffs = defender.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                return 1 + Math.min(maxStacks, (int) debuffs) * perDot;
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                if (targets == null) {
                    return;
                }
                boolean hitConfused = targets.stream().anyMatch(t -> t.hasBuffNamed("魂迷"));
                if (hitConfused && actor.getCamp() == owner.getCamp()) {
                    Buff buff = new Buff("海洋为何而歌·速", Buff.Category.BUFF, owner, actor, speedTurns)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(allySpeed,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(actor, buff);
                }
            }
        }

        /** 金血铭刻的时代: 终结技后恢复#1个战技点; 对单体队友施放战技后其战技伤害+#2 (#3回合). */
        static final class GoldBlood implements Trace {
            private final int sp;
            private final double skillBonus;
            private final int turns;

            GoldBlood(int sp, double skillBonus, int turns) {
                this.sp = sp;
                this.skillBonus = skillBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "金血铭刻的时代";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    battle.addSkillPoints(sp);
                } else if (type == SkillType.SKILL && targets != null) {
                    for (CanHit t : targets) {
                        if (t != owner && t.getCamp() == owner.getCamp() && !t.isDeath()) {
                            t.removeBuff("金血铭刻的时代");
                            Buff buff = new Buff("金血铭刻的时代", Buff.Category.BUFF, owner, t, turns)
                                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(skillBonus,
                                            DoubleValue.Modifier.ModifierSource.BUFF));
                            battle.applyBuff(t, buff);
                            return;
                        }
                    }
                }
            }
        }

        /** 致长夜的星光: 忆灵施放技能时获得夜色 (装备者和忆灵伤害+#2, 全队忆灵伤害无视#1防御 — 近似增伤). */
        static final class NightStar implements Trace {
            private final double defIgnore;
            private final double bonus;
            private final double energy;

            NightStar(double defIgnore, double bonus, double energy) {
                this.defIgnore = defIgnore;
                this.bonus = bonus;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "致长夜的星光";
            }

            @Override
            public void onSummonAction(Battle battle, Character owner,
                                       com.laosun.aluminium.models.Summon summon,
                                       SkillType type, List<? extends CanHit> targets) {
                owner.removeBuff("夜色");
                Buff buff = new Buff("夜色", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff party = new Buff("夜色·队", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, party);
                }
            }
        }

        /** 勿忘她的火焰: 进入战斗时装备者和另一位队友击破伤害+#1. */
        static final class HerFlame implements Trace {
            private final double bonus;

            HerFlame(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "勿忘她的火焰";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                boolean first = true;
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    if (ally == owner) {
                        Buff buff = new Buff("勿忘她的火焰", Buff.Category.BUFF, owner, owner, -1)
                                .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(owner, buff);
                    } else if (first) {
                        first = false;
                        Buff buff = new Buff("勿忘她的火焰", Buff.Category.BUFF, owner, ally, -1)
                                .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 纵然山河万程: 终结技时全队回复#1攻击生命, 额外为生命最低角色回复#2攻击生命, 并获得卫戍 (#5回合, 伤害+#3, 有召唤物额外+#4). */
        static final class Mountains implements Trace {
            private final double partyHeal;
            private final double lowestHeal;
            private final double bonus;
            private final double summonBonus;
            private final int turns;

            Mountains(double partyHeal, double lowestHeal, double bonus, double summonBonus, int turns) {
                this.partyHeal = partyHeal;
                this.lowestHeal = lowestHeal;
                this.bonus = bonus;
                this.summonBonus = summonBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "纵然山河万程";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                CanHit lowest = null;
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.heal(atk * partyHeal);
                    if (lowest == null || ally.getHpPercent() < lowest.getHpPercent()) {
                        lowest = ally;
                    }
                }
                if (lowest != null) {
                    lowest.heal(atk * lowestHeal);
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    double value = bonus;
                    if (ally instanceof Character c && !c.getSummons().isEmpty()) {
                        value += summonBonus;
                    }
                    Buff buff = new Buff("卫戍", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(value,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 爱如此刻永恒: 忆灵对队友施放忆灵技时获得空白 (敌方易伤+#2); 对敌方施放时获得诗行 (全队暴击伤害+#1); 同时持有时效果提高#3. */
        static final class Eternity implements Trace {
            private final double cdmg;
            private final double vuln;
            private final double boost;
            private boolean blank = false;
            private boolean poem = false;

            Eternity(double cdmg, double vuln, double boost) {
                this.cdmg = cdmg;
                this.vuln = vuln;
                this.boost = boost;
            }

            @Override
            public String getName() {
                return "爱如此刻永恒";
            }

            @Override
            public void onSummonAction(Battle battle, Character owner,
                                       com.laosun.aluminium.models.Summon summon,
                                       SkillType type, List<? extends CanHit> targets) {
                boolean toAlly = targets != null && targets.stream()
                        .anyMatch(t -> t.getCamp() == owner.getCamp());
                if (toAlly) {
                    blank = true;
                    double value = vuln * (poem ? 1 + boost : 1);
                    for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                        Buff debuff = new Buff("空白", Buff.Category.DEBUFF, owner, enemy, 1)
                                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(value,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(enemy, debuff);
                    }
                } else {
                    poem = true;
                    double value = cdmg * (blank ? 1 + boost : 1);
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        Buff buff = new Buff("诗行", Buff.Category.BUFF, owner, ally, 1)
                                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(value,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 花花世界迷人眼: 每次普攻获得1层推流计数, 同回合战技消耗≥#6次时全队欢愉度+#3 (近似). */
        static final class FlowerWorld implements Trace {
            private final double spBonus;
            private final int spMax;
            private final double partyElation;
            private final int maxStacks;
            private final double perSp;
            private final int spSameTurn;
            private int skillsThisTurn = 0;

            FlowerWorld(double spBonus, int spMax, double partyElation, int maxStacks, double perSp, int spSameTurn) {
                this.spBonus = spBonus;
                this.spMax = spMax;
                this.partyElation = partyElation;
                this.maxStacks = maxStacks;
                this.perSp = perSp;
                this.spSameTurn = spSameTurn;
            }

            @Override
            public String getName() {
                return "花花世界迷人眼";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                skillsThisTurn = 0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL) {
                    skillsThisTurn++;
                }
                if (skillsThisTurn >= spSameTurn) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        Buff buff = new Buff("推流", Buff.Category.BUFF, owner, ally, 1)
                                .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(partyElation,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type == SkillType.ELATION) {
                    return 1 + Math.min(maxStacks, skillsThisTurn) * perSp;
                }
                return 1.0;
            }
        }

        /** 当她决定看见: 进入战斗或对队友施放终结技时获得上上签 (#3回合, 全队暴击率+#1, 暴击伤害+#2, 装备者能量恢复效率+#4); 进入战斗恢复#5能量. */
        static final class SheSees implements Trace {
            private final double crit;
            private final double cdmg;
            private final int turns;
            private final double err;
            private final double energy;

            SheSees(double crit, double cdmg, int turns, double err, double energy) {
                this.crit = crit;
                this.cdmg = cdmg;
                this.turns = turns;
                this.err = err;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "当她决定看见";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                owner.gainEnergy(energy);
                apply(battle, owner);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    apply(battle, owner);
                }
            }

            private void apply(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("上上签", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                    DoubleValue.Modifier.ModifierSource.BUFF))
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                Buff errBuff = new Buff("上上签·能", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(err,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, errBuff);
            }
        }

        /** 一场谎言的终幕: 战斗开始或每累计#1次追加攻击获得影噬 (#2回合, 攻击力+#3, 敌方全体易伤#4). */
        static final class LieEnd implements Trace {
            private final int followUpInterval;
            private final int turns;
            private final double atkBonus;
            private final double vuln;
            private int followUps = 0;

            LieEnd(int followUpInterval, int turns, double atkBonus, double vuln) {
                this.followUpInterval = followUpInterval;
                this.turns = turns;
                this.atkBonus = atkBonus;
                this.vuln = vuln;
            }

            @Override
            public String getName() {
                return "一场谎言的终幕";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                apply(battle, owner);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.TALENT) {
                    return;
                }
                followUps++;
                if (followUps % followUpInterval == 0) {
                    apply(battle, owner);
                }
            }

            private void apply(Battle battle, Character owner) {
                owner.removeBuff("影噬");
                Buff buff = new Buff("影噬", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                    Buff debuff = new Buff("影噬·敌", Buff.Category.DEBUFF, owner, enemy, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }

        /** 欢迎来到银河城: 欢愉伤害无视#1防御; 对自身施放终结技时获得#2点笑点 (每#3次普攻重置). */
        static final class GalaxyCity implements Trace {
            private final double defIgnore;
            private final double laugh;
            private final int resetBasics;
            private int basics = 0;
            private boolean used = false;

            GalaxyCity(double defIgnore, double laugh, int resetBasics) {
                this.defIgnore = defIgnore;
                this.laugh = laugh;
                this.resetBasics = resetBasics;
            }

            @Override
            public String getName() {
                return "欢迎来到银河城";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA && !used) {
                    used = true;
                    battle.addLaughPoints(laugh);
                } else if (type == SkillType.COMMON) {
                    basics++;
                    if (basics >= resetBasics) {
                        basics = 0;
                        used = false;
                    }
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ELATION ? 1 + defIgnore : 1.0;
            }
        }

        /** 邂逅于下一个花季: 能量上限>#4时每超出10点能量恢复效率额外+#5 (最多计入#6点); 欢愉技时敌方全体易伤#1 (#2回合). */
        static final class FlowerSeason implements Trace {
            private final double vuln;
            private final int vulnTurns;
            private final double baseErr;
            private final double energyCap;
            private final double perTen;
            private final double maxOver;

            FlowerSeason(double vuln, int vulnTurns, double baseErr, double energyCap, double perTen, double maxOver) {
                this.vuln = vuln;
                this.vulnTurns = vulnTurns;
                this.baseErr = baseErr;
                this.energyCap = energyCap;
                this.perTen = perTen;
                this.maxOver = maxOver;
            }

            @Override
            public String getName() {
                return "邂逅于下一个花季";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                double over = Math.min(maxOver, Math.max(0, owner.getMaxEnergy() - energyCap));
                double err = baseErr + over / 10.0 * perTen;
                Buff buff = new Buff("邂逅于下一个花季·能", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(err,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                    Buff debuff = new Buff("邂逅于下一个花季", Buff.Category.DEBUFF, owner, enemy, vulnTurns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }

        /** 灼尽炼狱的新骸: 回合开始固定恢复#1能量 (每波次1次); 战技攻击后使目标陷入炼狱 (#2回合, 受到暴击伤害提高#3, 来自装备者额外#4 — 近似易伤). */
        static final class Inferno implements Trace {
            private final double energy;
            private final int turns;
            private final double cdmgTaken;
            private final double ownerExtra;
            private boolean energyUsed = false;

            Inferno(double energy, int turns, double cdmgTaken, double ownerExtra) {
                this.energy = energy;
                this.turns = turns;
                this.cdmgTaken = cdmgTaken;
                this.ownerExtra = ownerExtra;
            }

            @Override
            public String getName() {
                return "灼尽炼狱的新骸";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (!energyUsed) {
                    energyUsed = true;
                    owner.gainEnergy(energy);
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("炼狱")) {
                        continue;
                    }
                    Buff debuff = new Buff("炼狱", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(cdmgTaken + ownerExtra,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, debuff);
                }
            }
        }

        /** 当一颗星照亮夜空: 伤害无视#6防御; 施放助战技时恢复#1能量并获得启航 (2回合, 最多#2层, 每层助战技伤害+#3); 达到#4层时每层终结技伤害+#5. */
        static final class StarNight implements Trace {
            private final double energy;
            private final int maxStacks;
            private final double perStack;
            private final int ultThreshold;
            private final double perUlt;
            private final double defIgnore;
            private int stacks = 0;

            StarNight(double energy, int maxStacks, double perStack, int ultThreshold, double perUlt, double defIgnore) {
                this.energy = energy;
                this.maxStacks = maxStacks;
                this.perStack = perStack;
                this.ultThreshold = ultThreshold;
                this.perUlt = perUlt;
                this.defIgnore = defIgnore;
            }

            @Override
            public String getName() {
                return "当一颗星照亮夜空";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.gainEnergy(energy);
                owner.removeBuff("启航");
                Buff buff = new Buff("启航", Buff.Category.BUFF, owner, owner, 2);
                battle.applyBuff(owner, buff);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double mult = 1 + defIgnore;
                if (type == SkillType.ELATION) {
                    mult *= 1 + perStack * stacks;
                } else if (type == SkillType.ULTRA && stacks >= ultThreshold) {
                    mult *= 1 + perUlt * stacks;
                }
                return mult;
            }
        }

        /** 星火悄然闪耀: 任意角色同回合累计消耗≥#2战技点时获得闪耀王冠 (#3回合, 全队伤害无视#4防御 — 近似增伤, 装备者战技伤害+#1). */
        static final class Spark implements Trace {
            private final double skillBonus;
            private final int spThreshold;
            private final int turns;
            private final double partyBonus;
            private int skillsThisTurn = 0;

            Spark(double skillBonus, int spThreshold, int turns, double partyBonus) {
                this.skillBonus = skillBonus;
                this.spThreshold = spThreshold;
                this.turns = turns;
                this.partyBonus = partyBonus;
            }

            @Override
            public String getName() {
                return "星火悄然闪耀";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                skillsThisTurn = 0;
            }

            @Override
            public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                     List<? extends CanHit> targets) {
                if (type == SkillType.SKILL && actor.getCamp() == owner.getCamp()) {
                    skillsThisTurn++;
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                skillsThisTurn++;
                if (skillsThisTurn < spThreshold) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("闪耀王冠", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(partyBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                Buff ownerBuff = new Buff("闪耀王冠·技", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SKILL_DAMAGE_BOOST, DoubleValue.Modifier.pure(skillBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, ownerBuff);
            }
        }

        /** 所见即我: 终结技时每消耗1点能量终结技伤害+#1 (最多#4); 进入战斗或终结技时获得王之娱乐 (#2回合, 全队暴击伤害+#3). */
        static final class SeeMe implements Trace {
            private final double perEnergy;
            private final int turns;
            private final double partyCdmg;
            private final double maxBonus;

            SeeMe(double perEnergy, int turns, double partyCdmg, double maxBonus) {
                this.perEnergy = perEnergy;
                this.turns = turns;
                this.partyCdmg = partyCdmg;
                this.maxBonus = maxBonus;
            }

            @Override
            public String getName() {
                return "所见即我";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.ULTRA) {
                    return 1.0;
                }
                return 1 + Math.min(maxBonus, owner.getMaxEnergy() * perEnergy);
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                apply(battle, owner);
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    apply(battle, owner);
                }
            }

            private void apply(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("王之娱乐", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(partyCdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }
    }
}
