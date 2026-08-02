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
 * Hand-written 光锥被动 (light cone passives) for the 20*** series (三星光锥).
 *
 * <p>Each cone's passive is implemented as a {@link Trace} attached to the
 * equipped character at build time. Values are read from the weapon's
 * level-1 {@code skill_value} params, mirroring the game descriptions.
 */
public final class LightConePassives {

    private LightConePassives() {
    }

    /**
     * The hand-written passive for the given light cone ID, or {@code null}
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
        if (wid >= 21000 && wid < 24000) {
            return fourStar(wid, params);
        }
        return switch (wid) {
            case 20000 -> new Cones.Arrows(p0, (int) p1);                       // 锋镝
            case 20001 -> new Cones.Grain(p0);                                  // 物穰
            case 20002 -> new Cones.Skyfall(p0);                                // 天倾
            case 20003 -> new Cones.Amber(p2, p1);                              // 琥珀 (永久部分在属性里)
            case 20004 -> new Cones.Abyss(p0, (int) p1);                        // 幽邃
            case 20005 -> new Cones.Hymn(p0);                                   // 齐颂
            case 20006 -> new Cones.Library(p0);                                // 智库
            case 20007 -> new Cones.Bowstring(p0, (int) p1);                    // 离弦
            case 20008 -> new Cones.Fruit(p0);                                  // 嘉果
            case 20009 -> new Cones.Ruin(p1, p0);                               // 乐圮
            case 20010 -> new Cones.Bastion(p0);                                // 戍御
            case 20011 -> new Cones.Depth(p0);                                  // 渊环
            case 20012 -> new Cones.TurningWheel(p0);                           // 轮契
            case 20013 -> new Cones.Key(p0);                                    // 灵钥
            case 20014 -> new Cones.Resist(p0, (int) p1);                       // 相抗
            case 20015 -> new Cones.Growth(p0);                                 // 蕃息
            case 20016 -> new Cones.Perish(p1, p0);                             // 俱殁
            case 20017 -> new Cones.Frontier(p0);                               // 开疆
            case 20018 -> new Cones.Conceal(p0);                                // 匿影
            case 20019 -> new Cones.Harmony(p0, (int) p1);                      // 调和
            case 20020 -> new Cones.Foresight(p0, (int) p1);                    // 睿见
            case 20021 -> new Cones.Pyre((int) p0, p1);                         // 焚影
            case 20022 -> new Cones.Recall(p0, (int) p1);                       // 溯忆
            case 20023 -> new Cones.Snicker(p0);                                // 嗤笑
            case 20024 -> new Cones.Tears(p0, p1);                              // 残泪
            default -> null;
        };
    }

    // 21***/22***/23*** 光锥被动 (永久属性在 abilityProperties 中, 这里只实现条件/触发部分).
    private static Trace fourStar(int wid, List<Double> params) {
        double p0 = param(params, 0, 0);
        double p1 = param(params, 1, 0);
        double p2 = param(params, 2, 0);
        double p3 = param(params, 3, 0);
        double p4 = param(params, 4, 0);
        double p5 = param(params, 5, 0);
        double p6 = param(params, 6, 0);
        return switch (wid) {
            case 21000 -> new Cones.PostOp(p1);                                 // 一场术后对话
            case 21001 -> new Cones.Goodnight(p0, (int) p1);                    // 晚安与睡颜
            case 21002 -> new Cones.FirstDay(p1);                               // 余生的第一天
            case 21003 -> new Cones.OnlySilence(p1);                            // 唯有沉默
            case 21004 -> new Cones.MemoryShape(p1);                            // 记忆中的模样
            case 21005 -> new Cones.Mole(p0);                                   // 鼹鼠党欢迎你
            case 21006 -> new Cones.MyBirth(p0, p1, p2);                        // 「我」的诞生
            case 21007 -> new Cones.SameFeeling(p1);                            // 同一种心情
            case 21008 -> new Cones.PreySight(p1);                              // 猎物的视线
            case 21009 -> new Cones.Landau(p1);                                 // 朗道的选择
            case 21010 -> new Cones.Swordplay(p0, (int) p1);                    // 论剑
            case 21011 -> new Cones.Planets(p0);                                // 与行星相会
            case 21012 -> new Cones.SolemnVow(p1);                              // 秘密誓心
            case 21013 -> new Cones.WorldQuiet(p0, p1);                         // 别让世界静下来
            case 21014 -> new Cones.Moment(p1, p2);                             // 此时恰好
            case 21015 -> new Cones.Resolve(p0, p1, (int) p2);                  // 决心如汗珠般闪耀
            case 21016 -> new Cones.MarketTrend(p1, p2, (int) p3);              // 宇宙市场趋势
            case 21017 -> new Cones.Follow(p0, p1);                             // 点个关注吧！
            case 21018 -> new Cones.DanceDance(p0);                             // 舞！舞！舞！
            case 21019 -> new Cones.BlueSky(p1, (int) p2);                      // 在蓝天下
            case 21020 -> new Cones.GeniusRest(p1, (int) p2);                   // 天才们的休憩
            case 21021 -> new Cones.Equivalent(p0, p1);                         // 等价交换
            case 21022 -> new Cones.Prolonged(p1);                              // 延长记号
            case 21023 -> new Cones.Groundfire(p0, p1, (int) p2);               // 我们是地火
            case 21024 -> new Cones.Springs(p0, p1);                            // 春水初生
            case 21025 -> new Cones.PastFuture(p0, (int) p1);                   // 过往未来
            case 21026 -> new Cones.WalkTime(p1);                               // 汪！散步时间！
            case 21027 -> new Cones.Breakfast(p1, (int) p2);                    // 早餐的仪式感
            case 21028 -> new Cones.WarmNight(p1);                              // 暖夜不会漫长
            case 21029 -> new Cones.SeeYou(p0);                                 // 后会有期
            case 21030 -> new Cones.Me(p1);                                     // 这就是我啦！
            case 21031 -> new Cones.Phantom(p1);                                // 重返幽冥
            case 21032 -> new Cones.MoonCarve(p0, p1, p2);                      // 镂月裁云之意
            case 21033 -> new Cones.Escape(p1);                                 // 无处可逃
            case 21034 -> new Cones.PeaceDay(p0, p1);                           // 今日亦是和平的一日
            case 21035 -> new Cones.WhatIsReal(p1, p2);                         // 何物为真
            case 21036 -> new Cones.DreamTown(p0);                              // 美梦小镇大冒险
            case 21037 -> new Cones.Winner(p1, (int) p2);                       // 最后的赢家
            case 21038 -> new Cones.FireDistance(p0, p1, p2, (int) p3, (int) p4); // 在火的远处
            case 21039 -> new Cones.FateThread(p1, p2, p3);                     // 织造命运之线
            case 21040 -> new Cones.GalaxyFall(p1, (int) p2);                   // 银河沦陷日
            case 21041 -> new Cones.Show(p0, (int) p1, (int) p2, p3, p4);       // 好戏开演
            case 21042 -> new Cones.Promise(p1, (int) p2);                      // 铭记于心的约定
            case 21043 -> new Cones.Concert(p1);                                // 两个人的演唱会
            case 21044 -> new Cones.Boundless(p1);                              // 无边曼舞
            case 21045 -> new Cones.AfterHarmony(p1, (int) p2);                 // 谐乐静默之后
            case 21046 -> new Cones.Bloom(p1);                                  // 芳华待灼
            case 21047 -> new Cones.NightShadow(p1, (int) p2);                  // 黑夜如影随行
            case 21048 -> new Cones.Montage(p1, (int) p2);                      // 梦的蒙太奇
            case 21050 -> new Cones.MorningEvening(p1, (int) p2);               // 胜利只在朝夕间
            case 21051 -> new Cones.GeniusGreet(p1, (int) p2);                  // 天才们的问候
            case 21052 -> new Cones.SweatTears(p1);                             // 多流汗，少流泪
            case 21053 -> new Cones.WishJourney(p1);                            // 愿旅途永远坦然
            case 21054 -> new Cones.NextPage(p1, (int) p2);                     // 故事的下一页
            case 21055 -> new Cones.Tomorrow(p1, p2);                           // 直到明天的明天
            case 21056 -> new Cones.ChasingWind(p0);                            // 追逐风的时候
            case 21057 -> new Cones.Flowers(p1);                                // 花儿不会忘记
            case 21058 -> new Cones.BloodLine(p1);                              // 一行往日的血
            case 21060 -> new Cones.DreamMalt(p1);                              // 氤氲麦香的梦
            case 21061 -> new Cones.Resort(p1, p2, (int) p3);                   // 假日浴场大冒险
            case 21062 -> new Cones.Farewell(p1);                               // 于那终点再见
            case 21064 -> new Cones.Mushroom(p1, (int) p2);                     // 菇菇嘎嘎历险记
            case 21065 -> new Cones.LuckyDay(p1, (int) p2);                     // 今日好手气
            case 22000 -> new Cones.NewbieTask(p1);                             // 新手任务开始前
            case 22001 -> new Cones.HeyOverHere(p1, (int) p2);                  // 嘿，我在这儿
            case 22002 -> new Cones.TomorrowJourney(p1, (int) p2);              // 为了明日的旅途
            case 22003 -> new Cones.HuntMelody(p1, (int) p2);                   // 忍事录•音律狩猎
            case 22004 -> new Cones.CosmicDeal(p1);                             // 宇宙大生意
            case 22005 -> new Cones.ForeverMeal(p1, (int) p2);                  // 永远的迷境饭
            case 22006 -> new Cones.PinkTomorrow(p1, p2);                       // 飞向粉色的明天
            case 22007 -> new Cones.FutureTogether(p1, (int) p2);               // 未来，有我们一起
            case 23000 -> new Cones.NightTrain(p0, p1);                         // 银河铁道之夜
            case 23001 -> new Cones.Night(p1, p2, p3, (int) p4);                // 于夜色中
            case 23002 -> new Cones.Irreplaceable(p1, p2);                      // 无可取代的东西
            case 23003 -> new Cones.BattleNotOver(p1, (int) p2);                // 但战斗还未结束
            case 23004 -> new Cones.WorldName(p0, p1, p2);                      // 以世界之名
            case 23005 -> new Cones.MomentVictory(p2);                          // 制胜的瞬间
            case 23006 -> new Cones.Waiting(p0, p2, (int) p3, (int) p4);        // 只需等待
            case 23007 -> new Cones.Rain(p1, p2, (int) p3, p4);                 // 雨一直下
            case 23008 -> new Cones.Coffin(p1, p2, (int) p3);                   // 棺的回响
            case 23009 -> new Cones.Unreachable(p2);                            // 到不了的彼岸
            case 23010 -> new Cones.BeforeDawn(p1, p2);                         // 拂晓之前
            case 23011 -> new Cones.ClosedEyes(p1, p2, (int) p4);               // 她已闭上双眼
            case 23012 -> new Cones.SleepMud(p1, (int) p2, (int) p3);           // 如泥酣眠
            case 23013 -> new Cones.Season(p2);                                 // 时节不居
            case 23014 -> new Cones.SwordBody((int) p1, p2, p3);                // 此身为剑
            case 23015 -> new Cones.Brighter((int) p1, (int) p2, p3, p4);       // 比阳光更明亮的
            case 23016 -> new Cones.Happy(p1, p2, (int) p3);                    // 烦恼着，幸福着
            case 23017 -> new Cones.NightFright(p1, p2, (int) p3, (int) p4);    // 惊魂夜
            case 23018 -> new Cones.MomentEye(p1, p2);                          // 片刻，留在眼底
            case 23019 -> new Cones.Mirror(p1, (int) p2, p3, p4);               // 镜中故我
            case 23020 -> new Cones.Baptism(p1, (int) p2, p3, p4, (int) p5);    // 纯粹思维的洗礼
            case 23021 -> new Cones.GameWorld(p1, (int) p2, (int) p3, p4, (int) p5); // 游戏尘寰
            case 23022 -> new Cones.Recast(p1, p2, (int) p3);                   // 重塑时光之忆
            case 23023 -> new Cones.FateUnfair(p1, (int) p2, p3, p4, (int) p5); // 命运从未公平
            case 23024 -> new Cones.FadingShore(p1, p2);                        // 行于流逝的岸
            case 23025 -> new Cones.DreamReturn(p1, p2, (int) p3);              // 梦应归于何处
            case 23026 -> new Cones.GlitterNight(p0, (int) p1, p2, p3, (int) p4); // 夜色流光溢彩
            case 23027 -> new Cones.SecondLife(p1, p2);                         // 驶向第二次生命
            case 23028 -> new Cones.Hope(p1, p2, p3, (int) p4, p5, (int) p6);   // 偏偏希望无价
            case 23029 -> new Cones.InnumerableSprings(p1, p2, (int) p3, p4, p5); // 那无数个春天
            case 23030 -> new Cones.SunsetDance((int) p1, p2);                  // 落日时起舞
            case 23031 -> new Cones.HuntLight(p1, (int) p2);                    // 我将，巡征追猎
            case 23032 -> new Cones.Scent(p1, p2, p3, (int) p4);                // 唯有香如故
            case 23033 -> new Cones.Ninja(p1, p2);                              // 忍法帖•缭乱破魔
            case 23034 -> new Cones.Flight(p0, p1, (int) p2, (int) p3, (int) p4); // 回到大地的飞行
            case 23035 -> new Cones.Homeward(p1, p2, (int) p3, (int) p4);       // 长路终有归途
            case 23036 -> new Cones.GoldenWeave((int) p1, p2, p3);               // 将光阴织成黄金
            case 23037 -> new Cones.Unaskable(p2, p3, (int) p4);                // 向着不可追问处
            case 23038 -> new Cones.FlowerTime(p1, (int) p2, p3, p4, (int) p5); // 如果时间是一朵花
            case 23039 -> new Cones.FireBlood(p1, p2, p3, p4);                  // 血火啊，燃烧前路
            case 23040 -> new Cones.FarewellPrettier(p1, (int) p2, p3);         // 让告别，更美一些
            case 23041 -> new Cones.OnceFlame(p1, p2, (int) p3, p4);            // 生命当付之一炬
            case 23042 -> new Cones.RainbowSky(p1, p3, (int) p4, p5);           // 愿虹光永驻天空
            case 23043 -> new Cones.LiesWind(p1, p2, (int) p3, p4, p5, p6);     // 谎言在风中飘扬
            case 23044 -> new Cones.DawnBurn(p1, p2);                           // 黎明恰如此燃烧
            case 23045 -> new Cones.Coronation(p5, p1, p2, (int) p3, p4);       // 没有回报的加冕
            case 23046 -> new Cones.HellIdeal(p2, p3, (int) p4);                // 理想燃烧的地狱
            case 23047 -> new Cones.OceanSong(p1, (int) p2, p3, (int) p4, p5, (int) p6); // 海洋为何而歌
            case 23048 -> new Cones.GoldBlood((int) p1, p3, (int) p4);          // 金血铭刻的时代
            case 23049 -> new Cones.NightStar(p1, p2, p3);                      // 致长夜的星光
            case 23050 -> new Cones.HerFlame(p1);                               // 勿忘她的火焰
            case 23051 -> new Cones.Mountains(p4, p5, p1, p2, (int) p3);        // 纵然山河万程
            case 23052 -> new Cones.Eternity(p1, p2, p3);                       // 爱如此刻永恒
            case 23053 -> new Cones.FlowerWorld(p1, (int) p2, p3, (int) p4, p5, (int) p6); // 花花世界迷人眼
            case 23054 -> new Cones.SheSees(p1, p2, (int) p3, p4, p5);          // 当她决定看见
            case 23056 -> new Cones.LieEnd((int) p1, (int) p2, p3, p4);         // 一场谎言的终幕
            case 23057 -> new Cones.GalaxyCity(p1, p2, (int) p3);               // 欢迎来到银河城
            case 23058 -> new Cones.FlowerSeason(p1, (int) p2, p3, p4, p5, p6); // 邂逅于下一个花季
            case 23059 -> new Cones.Inferno(p1, (int) p2, p3, p4);              // 灼尽炼狱的新骸
            case 23060 -> new Cones.StarNight(p1, (int) p2, p3, (int) p4, p5, p6); // 当一颗星照亮夜空
            case 23061 -> new Cones.Spark(p1, (int) p2, (int) p3, p4);          // 星火悄然闪耀
            case 23062 -> new Cones.SeeMe(p2, (int) p3, p4, p5);                // 所见即我
            default -> null;
        };
    }

    private static double param(List<Double> params, int index, double fallback) {
        return params != null && index < params.size() ? params.get(index) : fallback;
    }

    /** 所有 20*** 光锥被动. */
    private static final class Cones {

        /** 锋镝: 战斗开始时, 使装备者的暴击率提高#1, 持续#2回合. */
        static final class Arrows implements Trace {
            private final double crit;
            private final int turns;

            Arrows(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "锋镝";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("锋镝", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 物穰: 装备者施放战技和终结技时, 治疗量提高#1. */
        static final class Grain implements Trace {
            private final double healBoost;

            Grain(double healBoost) {
                this.healBoost = healBoost;
            }

            @Override
            public String getName() {
                return "物穰";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("物穰");
                Buff buff = new Buff("物穰", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 天倾: 使装备者普攻和战技造成的伤害提高#1. */
        static final class Skyfall implements Trace {
            private final double bonus;

            Skyfall(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "天倾";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.COMMON || type == SkillType.SKILL ? 1 + bonus : 1.0;
            }
        }

        /** 琥珀: 生命值<#2时, 防御力额外提高#3 (永久#1在属性中). */
        static final class Amber implements Trace {
            private final double extraDef;
            private final double hpThreshold;

            Amber(double extraDef, double hpThreshold) {
                this.extraDef = extraDef;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "琥珀";
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
                boolean active = owner.hasBuffNamed("琥珀");
                boolean shouldBe = owner.getHpPercent() < hpThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("琥珀");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("琥珀", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(extraDef,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 幽邃: 战斗开始时, 使装备者的效果命中提高#1, 持续#2回合. */
        static final class Abyss implements Trace {
            private final double ehr;
            private final int turns;

            Abyss(double ehr, int turns) {
                this.ehr = ehr;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "幽邃";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("幽邃", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.EFFECT_HIT_RATE, DoubleValue.Modifier.pure(ehr,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 齐颂: 进入战斗后, 使我方全体的攻击力提高#1. */
        static final class Hymn implements Trace {
            private final double atkBonus;

            Hymn(double atkBonus) {
                this.atkBonus = atkBonus;
            }

            @Override
            public String getName() {
                return "齐颂";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("齐颂", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 智库: 使装备者终结技造成的伤害提高#1. */
        static final class Library implements Trace {
            private final double bonus;

            Library(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "智库";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA ? 1 + bonus : 1.0;
            }
        }

        /** 离弦: 使装备者消灭敌方目标后, 攻击力提高#1, 持续#2回合. */
        static final class Bowstring implements Trace {
            private final double atkBonus;
            private final int turns;

            Bowstring(double atkBonus, int turns) {
                this.atkBonus = atkBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "离弦";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("离弦");
                Buff buff = new Buff("离弦", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 嘉果: 战斗开始时, 立即为我方全体恢复#1点能量. */
        static final class Fruit implements Trace {
            private final double energy;

            Fruit(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "嘉果";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.gainEnergy(energy);
                }
                IO.println("  [光锥] 嘉果: party restores " + String.format("%.0f", energy) + " energy");
            }
        }

        /** 乐圮: 使装备者对当前生命值百分比大于#1的敌方目标造成的伤害提高#2. */
        static final class Ruin implements Trace {
            private final double bonus;
            private final double hpThreshold;

            Ruin(double bonus, double hpThreshold) {
                this.bonus = bonus;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "乐圮";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() > hpThreshold ? 1 + bonus : 1.0;
            }
        }

        /** 戍御: 使装备者施放终结技时, 回复等同于自身生命上限#1的生命值. */
        static final class Bastion implements Trace {
            private final double healRatio;

            Bastion(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "戍御";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    owner.heal(owner.getMaxHp() * healRatio);
                    IO.println("  [光锥] 戍御: recovers " + String.format("%.0f", owner.getMaxHp() * healRatio));
                }
            }
        }

        /** 渊环: 使装备者对减速状态下的敌方目标造成的伤害提高#1. */
        static final class Depth implements Trace {
            private final double bonus;

            Depth(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "渊环";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasBuffNamed("Slow") || defender.hasBuffNamed("Slow+") ? 1 + bonus : 1.0;
            }
        }

        /** 轮契: 施放攻击或受到攻击后, 额外恢复#1点能量 (单个回合内不可重复触发). */
        static final class TurningWheel implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            TurningWheel(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "轮契";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (usedThisTurn || type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                usedThisTurn = true;
                owner.gainEnergy(energy);
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (!usedThisTurn) {
                    usedThisTurn = true;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 灵钥: 使装备者施放战技后额外恢复#1点能量 (单个回合内不可重复触发). */
        static final class Key implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            Key(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "灵钥";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL && !usedThisTurn) {
                    usedThisTurn = true;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 相抗: 使装备者在消灭敌方目标后, 速度提高#1, 持续#2回合. */
        static final class Resist implements Trace {
            private final double speed;
            private final int turns;

            Resist(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "相抗";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("相抗");
                Buff buff = new Buff("相抗", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 蕃息: 当装备者施放普攻后, 使下一次行动提前#1. */
        static final class Growth implements Trace {
            private final double advance;

            Growth(double advance) {
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "蕃息";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON) {
                    battle.advanceByPercent(owner, advance);
                }
            }
        }

        /** 俱殁: 装备者当前生命值百分比小于#1时, 暴击率提高#2. */
        static final class Perish implements Trace {
            private final double crit;
            private final double hpThreshold;

            Perish(double crit, double hpThreshold) {
                this.crit = crit;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "俱殁";
            }

            @Override
            public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getHpPercent() < hpThreshold ? crit : 0;
            }
        }

        /** 开疆: 当装备者击破敌方目标的弱点时, 回复等同于自身生命上限#1的生命值. */
        static final class Frontier implements Trace {
            private final double healRatio;

            Frontier(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "开疆";
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                owner.heal(owner.getMaxHp() * healRatio);
                IO.println("  [光锥] 开疆: recovers " + String.format("%.0f", owner.getMaxHp() * healRatio));
            }
        }

        /** 匿影: 施放战技后, 使装备者的下一次普攻造成等同于自身#1攻击力的附加伤害. */
        static final class Conceal implements Trace {
            private final double atkRatio;
            private boolean empowered = false;

            Conceal(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "匿影";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL) {
                    empowered = true;
                } else if (type == SkillType.COMMON && empowered && !targets.isEmpty()) {
                    empowered = false;
                    battle.dealAttackDamage(owner, targets.getFirst(), atkRatio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                    owner.getElement()));
                    IO.println("  [光锥] 匿影: extra hit!");
                }
            }
        }

        /** 调和: 进入战斗时, 我方全体速度提高#1点, 持续#2回合. */
        static final class Harmony implements Trace {
            private final double speed;
            private final int turns;

            Harmony(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "调和";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("调和", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speed,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 睿见: 当装备者施放终结技时, 攻击力提高#1, 持续#2回合. */
        static final class Foresight implements Trace {
            private final double atkBonus;
            private final int turns;

            Foresight(double atkBonus, int turns) {
                this.atkBonus = atkBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "睿见";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("睿见");
                Buff buff = new Buff("睿见", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 焚影: 装备者首次召唤忆灵时, 恢复#1个战技点, 并恢复自身#2点能量. */
        static final class Pyre implements Trace {
            private final int skillPoints;
            private final double energy;
            private boolean used = false;

            Pyre(int skillPoints, double energy) {
                this.skillPoints = skillPoints;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "焚影";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (used || owner.getSummons().isEmpty()) {
                    return;
                }
                used = true;
                battle.addSkillPoints(skillPoints);
                owner.gainEnergy(energy);
                IO.println("  [光锥] 焚影: +" + skillPoints + " skill point, +"
                        + String.format("%.0f", energy) + " energy (first summon)");
            }
        }

        /** 溯忆: 忆灵回合开始时, 装备者与忆灵获得1层【缅怀】 (伤害+#1/层, 最多#2层). */
        static final class Recall implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            Recall(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "溯忆";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    stacks = 0;
                    owner.removeBuff("缅怀");
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("缅怀");
                Buff buff = new Buff("缅怀", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [光锥] 溯忆: 【缅怀】 " + stacks + "/" + maxStacks + " stacks");
            }
        }

        /** 嗤笑: 阿哈时刻发动时, 使装备者的欢愉度提高#1, 持续到阿哈时刻结束. */
        static final class Snicker implements Trace {
            private final double elationBonus;

            Snicker(double elationBonus) {
                this.elationBonus = elationBonus;
            }

            @Override
            public String getName() {
                return "嗤笑";
            }

            @Override
            public void onAhaMoment(Battle battle, Character owner) {
                owner.removeBuff("嗤笑");
                Buff buff = new Buff("嗤笑", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(elationBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [光锥] 嗤笑: 欢愉度 +" + String.format("%.0f", elationBonus * 100) + "%");
            }
        }

        /** 残泪: 当拥有的笑点大于等于#1时, 装备者的暴击伤害提高#2. */
        static final class Tears implements Trace {
            private final double laughThreshold;
            private final double cdmg;

            Tears(double laughThreshold, double cdmg) {
                this.laughThreshold = laughThreshold;
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "残泪";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                boolean active = owner.hasBuffNamed("残泪");
                boolean shouldBe = battle.getLaughPoints() >= laughThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("残泪");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("残泪", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                    IO.println("  [光锥] 残泪: 暴击伤害 +" + String.format("%.0f", cdmg * 100) + "%");
                }
            }
        }

        // ═══ 21*** 四星光锥 ═══════════════════════════════════════════

        /** 一场术后对话: 施放终结技时治疗量提高#2. */
        static final class PostOp implements Trace {
            private final double healBoost;

            PostOp(double healBoost) {
                this.healBoost = healBoost;
            }

            @Override
            public String getName() {
                return "一场术后对话";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("一场术后对话");
                Buff buff = new Buff("一场术后对话", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 晚安与睡颜: 目标每有1个负面效果, 伤害提高#1, 最多#2层. */
        static final class Goodnight implements Trace {
            private final double perDebuff;
            private final int maxStacks;

            Goodnight(double perDebuff, int maxStacks) {
                this.perDebuff = perDebuff;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "晚安与睡颜";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                long debuffs = defender.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                return 1 + Math.min(maxStacks, debuffs) * perDebuff;
            }
        }

        /** 余生的第一天: 进入战斗后, 我方全体全属性抗性提高#2 (近似: 减伤). */
        static final class FirstDay implements Trace {
            private final double reduction;

            FirstDay(double reduction) {
                this.reduction = reduction;
            }

            @Override
            public String getName() {
                return "余生的第一天";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("余生的第一天", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 唯有沉默: 场上敌方目标数量≤2时, 暴击率提高#2. */
        static final class OnlySilence implements Trace {
            private final double crit;

            OnlySilence(double crit) {
                this.crit = crit;
            }

            @Override
            public String getName() {
                return "唯有沉默";
            }

            @Override
            public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
                return battle != null && battle.getAliveEnemies().size() <= 2 ? crit : 0;
            }
        }

        /** 记忆中的模样: 施放攻击后, 额外恢复#2点能量 (每回合1次). */
        static final class MemoryShape implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            MemoryShape(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "记忆中的模样";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (usedThisTurn || type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                usedThisTurn = true;
                owner.gainEnergy(energy);
            }
        }

        /** 鼹鼠党欢迎你: 每次攻击获得1层【淘气值】, 每层攻击力+#1 (最多5层). */
        static final class Mole implements Trace {
            private final double perStack;
            private int stacks = 0;

            Mole(double perStack) {
                this.perStack = perStack;
            }

            @Override
            public String getName() {
                return "鼹鼠党欢迎你";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                stacks = Math.min(5, stacks + 1);
                owner.removeBuff("淘气值");
                Buff buff = new Buff("淘气值", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 「我」的诞生: 追加攻击伤害+#1, 目标生命≤#2时额外+#3 (近似: 全类型). */
        static final class MyBirth implements Trace {
            private final double bonus;
            private final double hpThreshold;
            private final double extra;

            MyBirth(double bonus, double hpThreshold, double extra) {
                this.bonus = bonus;
                this.hpThreshold = hpThreshold;
                this.extra = extra;
            }

            @Override
            public String getName() {
                return "「我」的诞生";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() <= hpThreshold ? 1 + bonus + extra : 1 + bonus;
            }
        }

        /** 同一种心情: 施放战技时, 为我方全体恢复#2点能量. */
        static final class SameFeeling implements Trace {
            private final double energy;

            SameFeeling(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "同一种心情";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.gainEnergy(energy);
                    }
                }
            }
        }

        /** 猎物的视线: 造成的持续伤害提高#2. */
        static final class PreySight implements Trace {
            private final double dotBoost;

            PreySight(double dotBoost) {
                this.dotBoost = dotBoost;
            }

            @Override
            public String getName() {
                return "猎物的视线";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("猎物的视线", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.DOT_DAMAGE_BOOST, DoubleValue.Modifier.pure(dotBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 朗道的选择: 受到攻击的概率提高, 受到的伤害降低#2. */
        static final class Landau implements Trace {
            private final double reduction;

            Landau(double reduction) {
                this.reduction = reduction;
            }

            @Override
            public String getName() {
                return "朗道的选择";
            }

            @Override
            public double aggroMultiplier(Battle battle, Character owner) {
                return 2.0;
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("朗道的选择", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 论剑: 多次击中同一目标时, 每次伤害+#1, 最多#2层; 目标变化时重置. */
        static final class Swordplay implements Trace {
            private final double perStack;
            private final int maxStacks;
            private CanHit lastTarget = null;
            private int stacks = 0;

            Swordplay(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "论剑";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (targets == null || targets.isEmpty()) {
                    return;
                }
                CanHit target = targets.getFirst();
                if (target == lastTarget && !target.isDeath()) {
                    stacks = Math.min(maxStacks, stacks + 1);
                } else {
                    stacks = 1;
                    lastTarget = target;
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender == lastTarget ? 1 + perStack * stacks : 1.0;
            }
        }

        /** 与行星相会: 我方目标造成与装备者相同属性的伤害时, 伤害提高#1. */
        static final class Planets implements Trace {
            private final double bonus;

            Planets(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "与行星相会";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    if (ally.getElement() == owner.getElement()) {
                        Buff buff = new Buff("与行星相会", Buff.Category.BUFF, owner, ally, -1)
                                .stat(owner.getElement().boostAttribute, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 秘密誓心: 对当前生命值百分比≥自身的目标伤害额外提高#2. */
        static final class SolemnVow implements Trace {
            private final double extra;

            SolemnVow(double extra) {
                this.extra = extra;
            }

            @Override
            public String getName() {
                return "秘密誓心";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() >= owner.getHpPercent() ? 1 + extra : 1.0;
            }
        }

        /** 别让世界静下来: 进入战斗时恢复#2能量, 终结技伤害提高#1. */
        static final class WorldQuiet implements Trace {
            private final double ultBonus;
            private final double startEnergy;

            WorldQuiet(double ultBonus, double startEnergy) {
                this.ultBonus = ultBonus;
                this.startEnergy = startEnergy;
            }

            @Override
            public String getName() {
                return "别让世界静下来";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                owner.gainEnergy(startEnergy);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA ? 1 + ultBonus : 1.0;
            }
        }

        /** 此时恰好: 治疗量提高, 数值等同于效果抵抗的#2, 最多#3. */
        static final class Moment implements Trace {
            private final double ratio;
            private final double cap;

            Moment(double ratio, double cap) {
                this.ratio = ratio;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "此时恰好";
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
                double er = owner.getAttribute(AttributeType.EFFECT_RESISTANCE) != null
                        ? owner.getAttribute(AttributeType.EFFECT_RESISTANCE).get() : 0;
                double bonus = Math.min(cap, er * ratio);
                owner.removeBuff("此时恰好");
                Buff buff = new Buff("此时恰好", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 决心如汗珠般闪耀: 击中目标时, #1基础概率使其陷入【攻陷】(防御降低#2, #3回合). */
        static final class Resolve implements Trace {
            private final double chance;
            private final double defDown;
            private final int turns;

            Resolve(double chance, double defDown, int turns) {
                this.chance = chance;
                this.defDown = defDown;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "决心如汗珠般闪耀";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("攻陷")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("攻陷", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 宇宙市场趋势: 受到攻击后, #2基础概率使敌方目标陷入灼烧 (防御力#3, #4回合). */
        static final class MarketTrend implements Trace {
            private final double chance;
            private final double defRatio;
            private final int turns;

            MarketTrend(double chance, double defRatio, int turns) {
                this.chance = chance;
                this.defRatio = defRatio;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "宇宙市场趋势";
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (attacker == null || attacker.isDeath() || !(attacker instanceof Enemy)) {
                    return;
                }
                if (battle.checkEffectHit(owner, attacker, chance)) {
                    double def = owner.getAttribute(AttributeType.DEFENCE) != null
                            ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                    attacker.applyDot(new Buff.Dot("Burn (灼烧)", owner, attacker, def * defRatio,
                            Element.FIRE, turns));
                    IO.println("  [光锥] 宇宙市场趋势: attacker burns!");
                }
            }
        }

        /** 点个关注吧！: 普攻和战技伤害+#1, 能量满时额外+#2. */
        static final class Follow implements Trace {
            private final double bonus;
            private final double fullEnergyBonus;

            Follow(double bonus, double fullEnergyBonus) {
                this.bonus = bonus;
                this.fullEnergyBonus = fullEnergyBonus;
            }

            @Override
            public String getName() {
                return "点个关注吧！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.COMMON && type != SkillType.SKILL) {
                    return 1.0;
                }
                double extra = owner.getEnergy() >= owner.getMaxEnergy() ? fullEnergyBonus : 0;
                return 1 + bonus + extra;
            }
        }

        /** 舞！舞！舞！: 施放终结技后, 我方全体行动提前#1. */
        static final class DanceDance implements Trace {
            private final double advance;

            DanceDance(double advance) {
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "舞！舞！舞！";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        battle.advanceByPercent(ally, advance);
                    }
                }
            }
        }

        /** 在蓝天下: 消灭敌方目标后, 暴击率提高#2, 持续#3回合. */
        static final class BlueSky implements Trace {
            private final double crit;
            private final int turns;

            BlueSky(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "在蓝天下";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("在蓝天下");
                Buff buff = new Buff("在蓝天下", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 天才们的休憩: 消灭敌方目标后, 暴击伤害提高#2, 持续#3回合. */
        static final class GeniusRest implements Trace {
            private final double cdmg;
            private final int turns;

            GeniusRest(double cdmg, int turns) {
                this.cdmg = cdmg;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "天才们的休憩";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("天才们的休憩");
                Buff buff = new Buff("天才们的休憩", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 等价交换: 回合开始时, 随机为1个能量<#1的其他目标恢复#2点能量. */
        static final class Equivalent implements Trace {
            private final double threshold;
            private final double energy;

            Equivalent(double threshold, double energy) {
                this.threshold = threshold;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "等价交换";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                List<CanHit> candidates = battle.getAlivePlayerUnits().stream()
                        .filter(u -> u != owner && u.getMaxEnergy() > 0
                                && u.getEnergy() < u.getMaxEnergy() * threshold)
                        .toList();
                if (!candidates.isEmpty()) {
                    candidates.get((int) (Math.random() * candidates.size())).gainEnergy(energy);
                }
            }
        }

        /** 延长记号: 对处于触电或风化状态的敌方目标造成的伤害提高#2. */
        static final class Prolonged implements Trace {
            private final double bonus;

            Prolonged(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "延长记号";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasDotOfElement(Element.THUNDER) || defender.hasDotOfElement(Element.WIND)
                        ? 1 + bonus : 1.0;
            }
        }

        /** 我们是地火: 战斗开始全体受到伤害降低#2 (#3回合), 并回复已损失生命值#1. */
        static final class Groundfire implements Trace {
            private final double healRatio;
            private final double reduction;
            private final int turns;

            Groundfire(double healRatio, double reduction, int turns) {
                this.healRatio = healRatio;
                this.reduction = reduction;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "我们是地火";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.heal((ally.getMaxHp() - ally.getCurrentHp()) * healRatio);
                    Buff buff = new Buff("我们是地火", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 春水初生: 进入战斗后速度+#1、伤害+#2; 受到伤害后失效, 下个回合结束时恢复. */
        static final class Springs implements Trace {
            private final double speed;
            private final double dmg;

            Springs(double speed, double dmg) {
                this.speed = speed;
                this.dmg = dmg;
            }

            @Override
            public String getName() {
                return "春水初生";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                owner.removeBuff("春水初生");
            }

            private void refresh(Battle battle, Character owner) {
                if (owner.hasBuffNamed("春水初生")) {
                    return;
                }
                Buff buff = new Buff("春水初生", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 过往未来: 施放战技后, 使下一个行动的我方其他目标伤害提高#1 (#2回合). */
        static final class PastFuture implements Trace {
            private final double bonus;
            private final int turns;

            PastFuture(double bonus, int turns) {
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "过往未来";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                // 下一个行动的我方其他目标 (行动条上最近的非装备者友方).
                for (Signal signal : battle.getQueueSnapshot()) {
                    CanHit next = signal.getCanHit();
                    if (next != owner && next.getCamp() == owner.getCamp() && !next.isDeath()) {
                        Buff buff = new Buff("过往未来", Buff.Category.BUFF, owner, next, turns)
                                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(next, buff);
                        return;
                    }
                }
            }
        }

        /** 汪！散步时间！: 对处于灼烧或裂伤状态的敌方目标造成的伤害提高#2. */
        static final class WalkTime implements Trace {
            private final double bonus;

            WalkTime(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "汪！散步时间！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasDotOfElement(Element.FIRE) || defender.hasDotOfElement(Element.PHYSICAL)
                        ? 1 + bonus : 1.0;
            }
        }

        /** 早餐的仪式感: 每消灭1个敌方目标, 攻击力+#2, 最多#3层. */
        static final class Breakfast implements Trace {
            private final double perKill;
            private final int maxStacks;
            private int stacks = 0;

            Breakfast(double perKill, int maxStacks) {
                this.perKill = perKill;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "早餐的仪式感";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("早餐的仪式感");
                Buff buff = new Buff("早餐的仪式感", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perKill * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 暖夜不会漫长: 施放普攻或战技后, 为我方全体回复各自生命上限#2的生命值. */
        static final class WarmNight implements Trace {
            private final double healRatio;

            WarmNight(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "暖夜不会漫长";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON || type == SkillType.SKILL) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.heal(ally.getMaxHp() * healRatio);
                    }
                }
            }
        }

        /** 后会有期: 施放普攻或战技后, 对随机1个受到攻击的目标造成#1攻击力附加伤害. */
        static final class SeeYou implements Trace {
            private final double atkRatio;

            SeeYou(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "后会有期";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if ((type == SkillType.COMMON || type == SkillType.SKILL) && !targets.isEmpty()) {
                    List<CanHit> alive = new java.util.ArrayList<>();
                    for (CanHit t : targets) {
                        if (!t.isDeath()) {
                            alive.add(t);
                        }
                    }
                    if (!alive.isEmpty()) {
                        battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                                atkRatio, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        owner.getElement()));
                    }
                }
            }
        }

        /** 这就是我啦！: 施放终结技时, 造成的伤害值提高, 数值等同于防御力的#2. */
        static final class Me implements Trace {
            private final double defRatio;

            Me(double defRatio) {
                this.defRatio = defRatio;
            }

            @Override
            public String getName() {
                return "这就是我啦！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.ULTRA) {
                    return 1.0;
                }
                double def = owner.getAttribute(AttributeType.DEFENCE) != null
                        ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 1;
                return 1 + def * defRatio / Math.max(1, atk);
            }
        }

        /** 重返幽冥: 暴击后, #2固定概率解除被攻击目标的1个增益效果 (每次攻击1次). */
        static final class Phantom implements Trace {
            private final double chance;
            private boolean usedThisAction = false;

            Phantom(double chance) {
                this.chance = chance;
            }

            @Override
            public String getName() {
                return "重返幽冥";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                usedThisAction = false;
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || usedThisAction || Math.random() >= chance) {
                        continue;
                    }
                    Buff toRemove = target.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.BUFF)
                            .findFirst().orElse(null);
                    if (toRemove != null) {
                        battle.removeBuff(target, toRemove);
                        usedThisAction = true;
                    }
                }
            }
        }

        /** 镂月裁云之意: 战斗开始及回合开始时, 随机生效1种全队增益 (攻击/暴伤/回能, 不与上次重复). */
        static final class MoonCarve implements Trace {
            private final double atk;
            private final double cdmg;
            private final double err;
            private int lastEffect = -1;

            MoonCarve(double atk, double cdmg, double err) {
                this.atk = atk;
                this.cdmg = cdmg;
                this.err = err;
            }

            @Override
            public String getName() {
                return "镂月裁云之意";
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
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.removeBuff("镂月裁云·攻");
                    ally.removeBuff("镂月裁云·暴");
                    ally.removeBuff("镂月裁云·能");
                }
                int effect;
                do {
                    effect = (int) (Math.random() * 3);
                } while (effect == lastEffect);
                lastEffect = effect;
                AttributeType attribute = effect == 0 ? AttributeType.ATTACK
                        : effect == 1 ? AttributeType.CRIT_ATTACK : AttributeType.ENERGY_REGENERATION_RATE;
                double value = effect == 0 ? atk : effect == 1 ? cdmg : err;
                String name = effect == 0 ? "镂月裁云·攻" : effect == 1 ? "镂月裁云·暴" : "镂月裁云·能";
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff(name, Buff.Category.BUFF, owner, ally, -1)
                            .stat(attribute, DoubleValue.Modifier.pure(value,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 无处可逃: 消灭敌方目标时, 回复等同于自身#2攻击力的生命值. */
        static final class Escape implements Trace {
            private final double atkRatio;

            Escape(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "无处可逃";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                owner.heal(atk * atkRatio);
            }
        }

        /** 今日亦是和平的一日: 进入战斗后, 每点能量上限使伤害提高#1, 最多计入#2点. */
        static final class PeaceDay implements Trace {
            private final double perEnergy;
            private final double cap;

            PeaceDay(double perEnergy, double cap) {
                this.perEnergy = perEnergy;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "今日亦是和平的一日";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double bonus = Math.min(cap, owner.getMaxEnergy()) * perEnergy;
                return 1 + bonus;
            }
        }

        /** 何物为真: 施放普攻后, 回复#2生命上限+#3的生命值. */
        static final class WhatIsReal implements Trace {
            private final double healRatio;
            private final double flat;

            WhatIsReal(double healRatio, double flat) {
                this.healRatio = healRatio;
                this.flat = flat;
            }

            @Override
            public String getName() {
                return "何物为真";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON) {
                    owner.heal(owner.getMaxHp() * healRatio + flat);
                }
            }
        }

        /** 美梦小镇大冒险: 施放某类型技能后, 我方全体该类型技能伤害提高#1. */
        static final class DreamTown implements Trace {
            private final double bonus;
            private SkillType lastType = null;

            DreamTown(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "美梦小镇大冒险";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                    lastType = type;
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == lastType ? 1 + bonus : 1.0;
            }
        }

        /** 最后的赢家: 暴击后获得1层【好运】 (暴击伤害+#2/层, 最多#3层, 回合结束移除). */
        static final class Winner implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            Winner(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "最后的赢家";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                stacks = 0;
                owner.removeBuff("好运");
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("好运");
                Buff buff = new Buff("好运", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 在火的远处: 单次受击损失生命>#1时, 回复生命上限#3并伤害提高#2 (#4回合, 每#5回合1次). */
        static final class FireDistance implements Trace {
            private final double threshold;
            private final double dmg;
            private final double healRatio;
            private final int turns;
            private final int cooldown;
            private int cdLeft = 0;

            FireDistance(double threshold, double dmg, double healRatio, int turns, int cooldown) {
                this.threshold = threshold;
                this.dmg = dmg;
                this.healRatio = healRatio;
                this.turns = turns;
                this.cooldown = cooldown;
            }

            @Override
            public String getName() {
                return "在火的远处";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (cdLeft > 0) {
                    cdLeft--;
                }
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (cdLeft > 0 || owner.isDeath()) {
                    return;
                }
                if (damage > owner.getMaxHp() * threshold) {
                    cdLeft = cooldown;
                    owner.heal(owner.getMaxHp() * healRatio);
                    owner.removeBuff("在火的远处");
                    Buff buff = new Buff("在火的远处", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 织造命运之线: 每有#2点防御力, 造成的伤害提高#3, 最多#4. */
        static final class FateThread implements Trace {
            private final double perDef;
            private final double perValue;
            private final double cap;

            FateThread(double perDef, double perValue, double cap) {
                this.perDef = perDef;
                this.perValue = perValue;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "织造命运之线";
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
                double def = owner.getAttribute(AttributeType.DEFENCE) != null
                        ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                double bonus = Math.min(cap, def / perDef * perValue);
                owner.removeBuff("织造命运之线");
                Buff buff = new Buff("织造命运之线", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 银河沦陷日: 攻击后若不少于2个目标具有对应属性弱点, 暴击伤害提高#2 (#3回合). */
        static final class GalaxyFall implements Trace {
            private final double cdmg;
            private final int turns;

            GalaxyFall(double cdmg, int turns) {
                this.cdmg = cdmg;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "银河沦陷日";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                long weak = targets.stream()
                        .filter(t -> t instanceof Enemy e && e.isWeakTo(owner.getElement())).count();
                if (weak >= 2) {
                    owner.removeBuff("银河沦陷日");
                    Buff buff = new Buff("银河沦陷日", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 好戏开演: 施加负面状态后获得1层【戏法】 (伤害+#1/层, 最多#2层, #3回合); 效果命中≥#4时攻击力+#5. */
        static final class Show implements Trace {
            private final double perStack;
            private final int maxStacks;
            private final int turns;
            private final double ehrThreshold;
            private final double atkBonus;
            private int stacks = 0;

            Show(double perStack, int maxStacks, int turns, double ehrThreshold, double atkBonus) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
                this.turns = turns;
                this.ehrThreshold = ehrThreshold;
                this.atkBonus = atkBonus;
            }

            @Override
            public String getName() {
                return "好戏开演";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                stacks = 0;
                double ehr = owner.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                        ? owner.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
                boolean active = owner.hasBuffNamed("好戏开演·攻");
                boolean shouldBe = ehr >= ehrThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("好戏开演·攻");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("好戏开演·攻", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                boolean appliedDebuff = targets.stream().anyMatch(t -> !t.isDeath() && t.hasDebuff());
                if (appliedDebuff) {
                    stacks = Math.min(maxStacks, stacks + 1);
                    owner.removeBuff("戏法");
                    Buff buff = new Buff("戏法", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 铭记于心的约定: 施放终结技时, 暴击率提高#2 (#3回合). */
        static final class Promise implements Trace {
            private final double crit;
            private final int turns;

            Promise(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "铭记于心的约定";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("铭记于心的约定");
                Buff buff = new Buff("铭记于心的约定", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 两个人的演唱会: 场上每有一名持有护盾的角色, 装备者造成的伤害提高#2. */
        static final class Concert implements Trace {
            private final double perShielded;

            Concert(double perShielded) {
                this.perShielded = perShielded;
            }

            @Override
            public String getName() {
                return "两个人的演唱会";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (battle == null) {
                    return 1.0;
                }
                long shielded = battle.getAlivePlayerUnits().stream()
                        .filter(u -> u.getShield() > 0).count();
                return 1 + shielded * perShielded;
            }
        }

        /** 无边曼舞: 对处于防御降低或减速状态的敌人造成的暴击伤害提高#2. */
        static final class Boundless implements Trace {
            private final double cdmg;

            Boundless(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "无边曼舞";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                boolean defDown = defender.getBuffs().stream()
                        .anyMatch(b -> b.getCategory() == Buff.Category.DEBUFF
                                && b.getModifiers() != null
                                && b.getModifiers().stream().anyMatch(m -> m.attribute() == AttributeType.DEFENCE));
                return defDown || defender.hasBuffNamed("Slow") || defender.hasBuffNamed("Slow+")
                        ? 1 + cdmg : 1.0;
            }
        }

        /** 谐乐静默之后: 施放终结技后, 速度提高#2 (#3回合). */
        static final class AfterHarmony implements Trace {
            private final double speed;
            private final int turns;

            AfterHarmony(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "谐乐静默之后";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("谐乐静默之后");
                Buff buff = new Buff("谐乐静默之后", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 芳华待灼: 进入战斗时, 若有两名及以上同命途角色, 这些角色暴击伤害提高#2. */
        static final class Bloom implements Trace {
            private final double cdmg;

            Bloom(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "芳华待灼";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                java.util.Map<String, Long> paths = new java.util.HashMap<>();
                for (Character c : battle.getAliveCharacters()) {
                    var data = Constant.CHARACTERS.get(c.getCid());
                    String path = data != null ? data.mt() : "all";
                    paths.merge(path, 1L, Long::sum);
                }
                if (paths.values().stream().anyMatch(v -> v >= 2)) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        Buff buff = new Buff("芳华待灼", Buff.Category.BUFF, owner, ally, -1)
                                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 黑夜如影随行: 进入战斗时或造成击破伤害后, 速度提高#2 (#3回合, 每回合1次). */
        static final class NightShadow implements Trace {
            private final double speed;
            private final int turns;
            private boolean usedThisTurn = false;

            NightShadow(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "黑夜如影随行";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
                // 进入战斗的触发不计入当回合次数, 首次击破仍可触发.
                usedThisTurn = false;
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
                refresh(battle, owner);
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                refresh(battle, owner);
            }

            private void refresh(Battle battle, Character owner) {
                if (usedThisTurn) {
                    return;
                }
                usedThisTurn = true;
                owner.removeBuff("黑夜如影随行");
                Buff buff = new Buff("黑夜如影随行", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 梦的蒙太奇: 攻击弱点击破状态的目标后, 恢复#2点能量 (每回合最多#3次). */
        static final class Montage implements Trace {
            private final double energy;
            private final int maxPerTurn;
            private int usedThisTurn = 0;

            Montage(double energy, int maxPerTurn) {
                this.energy = energy;
                this.maxPerTurn = maxPerTurn;
            }

            @Override
            public String getName() {
                return "梦的蒙太奇";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = 0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (usedThisTurn >= maxPerTurn || targets == null) {
                    return;
                }
                boolean hitBroken = targets.stream()
                        .anyMatch(t -> t instanceof Enemy e && e.isBroken());
                if (hitBroken) {
                    usedThisTurn++;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 胜利只在朝夕间: 忆灵施放技能时, 我方全体伤害提高#2 (#3回合). */
        static final class MorningEvening implements Trace {
            private final double bonus;
            private final int turns;

            MorningEvening(double bonus, int turns) {
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "胜利只在朝夕间";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("胜利只在朝夕间", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 天才们的问候: 施放终结技后, 装备者与忆灵普攻伤害提高#2 (#3回合). */
        static final class GeniusGreet implements Trace {
            private final double basicBonus;
            private final int turns;

            GeniusGreet(double basicBonus, int turns) {
                this.basicBonus = basicBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "天才们的问候";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("天才们的问候");
                Buff buff = new Buff("天才们的问候", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.NORMAL_DAMAGE_BOOST, DoubleValue.Modifier.pure(basicBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 多流汗，少流泪: 忆灵在场上时, 装备者与忆灵造成的伤害提高#2. */
        static final class SweatTears implements Trace {
            private final double bonus;

            SweatTears(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "多流汗，少流泪";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getSummons().isEmpty() ? 1.0 : 1 + bonus;
            }
        }

        /** 愿旅途永远坦然: 我方目标持有护盾时, 造成的伤害提高#2. */
        static final class WishJourney implements Trace {
            private final double bonus;

            WishJourney(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "愿旅途永远坦然";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getShield() > 0 ? 1 + bonus : 1.0;
            }
        }

        /** 故事的下一页: 忆灵攻击后, 装备者与忆灵治疗量提高#2 (#3回合). */
        static final class NextPage implements Trace {
            private final double healBoost;
            private final int turns;

            NextPage(double healBoost, int turns) {
                this.healBoost = healBoost;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "故事的下一页";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    return;
                }
                owner.removeBuff("故事的下一页");
                Buff buff = new Buff("故事的下一页", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 直到明天的明天: 我方目标生命≥#2时, 造成的伤害提高#3. */
        static final class Tomorrow implements Trace {
            private final double hpThreshold;
            private final double bonus;

            Tomorrow(double hpThreshold, double bonus) {
                this.hpThreshold = hpThreshold;
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "直到明天的明天";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getHpPercent() >= hpThreshold ? 1 + bonus : 1.0;
            }
        }

        /** 追逐风的时候: 进入战斗后, 我方全体击破伤害提高#1 (近似: 击破特攻). */
        static final class ChasingWind implements Trace {
            private final double bonus;

            ChasingWind(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "追逐风的时候";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("追逐风的时候", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 花儿不会忘记: 忆灵造成的暴击伤害额外提高#2 (近似: 忆灵在场时装备者暴伤+额外). */
        static final class Flowers implements Trace {
            private final double cdmg;

            Flowers(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "花儿不会忘记";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                boolean active = owner.hasBuffNamed("花儿不会忘记");
                boolean shouldBe = !owner.getSummons().isEmpty();
                if (active && !shouldBe) {
                    owner.removeBuff("花儿不会忘记");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("花儿不会忘记", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 一行往日的血: 战技和终结技伤害提高#2. */
        static final class BloodLine implements Trace {
            private final double bonus;

            BloodLine(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "一行往日的血";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.SKILL || type == SkillType.ULTRA ? 1 + bonus : 1.0;
            }
        }

        /** 氤氲麦香的梦: 终结技和追加攻击伤害提高#2 (近似: 全类型). */
        static final class DreamMalt implements Trace {
            private final double bonus;

            DreamMalt(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "氤氲麦香的梦";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA || type == SkillType.TALENT ? 1 + bonus : 1.0;
            }
        }

        /** 假日浴场大冒险: 攻击后, #2基础概率使目标易伤 (#3, #4回合). */
        static final class Resort implements Trace {
            private final double chance;
            private final double vuln;
            private final int turns;

            Resort(double chance, double vuln, int turns) {
                this.chance = chance;
                this.vuln = vuln;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "假日浴场大冒险";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("假日浴场")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("假日浴场", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 于那终点再见: 战技和追加攻击伤害提高#2 (近似: 战技/全类型). */
        static final class Farewell implements Trace {
            private final double bonus;

            Farewell(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "于那终点再见";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.SKILL || type == SkillType.TALENT ? 1 + bonus : 1.0;
            }
        }

        /** 菇菇嘎嘎历险记: 施放欢愉技时, 敌方全体受到的欢愉伤害提高#2 (#3回合). */
        static final class Mushroom implements Trace {
            private final double vuln;
            private final int turns;

            Mushroom(double vuln, int turns) {
                this.vuln = vuln;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "菇菇嘎嘎历险记";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                    Buff debuff = new Buff("菇菇嘎嘎", Buff.Category.DEBUFF, owner, enemy, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }

        /** 今日好手气: 施放欢愉技时, 欢愉度提高#2 (最多叠加#3次). */
        static final class LuckyDay implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            LuckyDay(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "今日好手气";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("今日好手气");
                Buff buff = new Buff("今日好手气", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

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
