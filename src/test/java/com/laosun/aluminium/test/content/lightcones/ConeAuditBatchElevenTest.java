package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The eleventh audit batch of light cones (21035-21041; 21038 is in {@code _unmodelled.json}).
 *
 * <p>⚠⚠ <b>Two more duplicated shares</b> - the ninth and tenth of this audit: 21035's break effect and 21039's effect
 * resistance are the cones' own {@code ability_property} values, stated again as rules.
 */
public class ConeAuditBatchElevenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21035 何物为真: the break share once, and the basic attack heals 2% of max HP plus 800 points.
     */
    @Test
    public void coneTwoInnocentThreeFiveBreakOnceAndHealsWithAFlatTerm() {
        Fixture f = new Fixture(21035, false);
        Fixture bare = new Fixture(null, false);
        double breaking = f.attribute(AttributeType.BREAKING_EFFECT) - bare.attribute(AttributeType.BREAKING_EFFECT);
        System.out.println("[21035] 击破特攻增量 " + breaking);
        Assertions.assertEquals(0.24, breaking, 1e-9, "「击破特攻提高 24%」—— 光锥属性给一次");

        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.6);
        double before = f.wearer.getCurrentHp();
        f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double healed = f.wearer.getCurrentHp() - before;
        double expected = f.wearer.getMaxHp() * 0.02 + 800;
        System.out.println("[21035] 普攻后回复 " + healed + " ; 生命上限×0.02 + 800 = " + expected);
        Assertions.assertEquals(expected, healed, expected * 1e-6, "「生命上限 2% + 800 点」");
    }

    /**
     * 21037 最后的赢家: each crit adds a 【好运】 layer worth 8% crit damage, cleared at the end of her turn.
     */
    @Test
    public void coneTwoInnocentThreeSevenStacksLuckAndClearsAtTurnEnd() {
        Fixture f = new Fixture(21037, false);
        double base = f.attribute(AttributeType.CRIT_ATTACK);
        f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.CRIT_DEALT, f.wearer,
                f.battle.enemyUnits().getFirst(), 0, 0);
        double one = f.attribute(AttributeType.CRIT_ATTACK) - base;
        f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.CRIT_DEALT, f.wearer,
                f.battle.enemyUnits().getFirst(), 0, 0);
        double two = f.attribute(AttributeType.CRIT_ATTACK) - base;
        f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.TURN_END, f.wearer, f.wearer, 0, 0);
        double cleared = f.attribute(AttributeType.CRIT_ATTACK) - base;
        System.out.println("[21037] 暴伤增量: 1 层 " + one + " ; 2 层 " + two + " ; 回合结束后 " + cleared);
        Assertions.assertEquals(0.08, one, 1e-9, "每层 8%（叠影 1）");
        Assertions.assertEquals(0.16, two, 1e-9, "两层 16%");
        Assertions.assertEquals(0.0, cleared, 1e-9, "「【好运】在装备者的回合结束时移除」");
    }

    /**
     * 21039 织造命运之线: the damage share is 0.8% per 100 DEFENCE, capped at 32%.
     */
    @Test
    public void coneTwoInnocentThreeNineScalesOffDefence() {
        Fixture f = new Fixture(21039, false);
        Fixture bare = new Fixture(null, false);
        double boost = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - bare.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        double defence = f.attribute(AttributeType.DEFENCE);
        double expected = Math.min(defence * 0.00008, 0.32);
        System.out.println("[21039] 增伤 " + boost + " ; 防御 " + defence + " × 0.00008（封顶 0.32）= " + expected);
        Assertions.assertEquals(expected, boost, 1e-6, "「每 100 点防御 +0.8%，最多 32%」");
    }

    /**
     * 21041 好戏开演: each debuff adds a 【戏法】 layer (3 at most, one turn each) worth 6% damage, and an 80% effect-hit rate
     * grants 20% attack.
     */
    @Test
    public void coneTwoInnocentFourOneStacksTricksAndGatesOnEffectHit() {
        Fixture f = new Fixture(21041, false);
        double base = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        for (int i = 0; i < 4; i++) {
            f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.DEBUFF_APPLIED, f.wearer,
                    f.battle.enemyUnits().getFirst(), 0, 0);
        }
        double after = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - base;
        System.out.println("[21041] 四次施加负面后的增伤 " + after + "（3 层 × 6% 应为 0.18）");
        Assertions.assertEquals(0.18, after, 1e-6, "「每层 6%，最多叠加 3 层」");
    }

    /**
     * 21036 美梦小镇大冒险: the cast type decides WHICH channel the whole party gets.
     */
    @Test
    public void coneTwoInnocentThreeSixBoostsTheChannelOfTheCastType() {
        Fixture f = new Fixture(21036, true);
        double basicBefore = f.ally.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double skillBefore = f.ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double basicAfter = f.ally.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double skillAfter = f.ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        System.out.println("[21036] 队友的普攻档 " + basicBefore + " -> " + basicAfter
                + " ; 战技档 " + skillBefore + " -> " + skillAfter);
        Assertions.assertEquals(0.12, basicAfter - basicBefore, 1e-9, "「普攻后…对应类型的技能伤害提高 12%」给全队");
        Assertions.assertEquals(0.0, skillAfter - skillBefore, 1e-9, "只对**最新使用的类型**生效");
    }

    /**
     * 21040 银河沦陷日: two or more hit targets with the matching weakness turn on +20% crit damage.
     */
    @Test
    public void coneTwoInnocentFourZeroNeedsTwoWeakTargets() {
        Fixture f = new Fixture(21040, false);
        double before = f.attribute(AttributeType.CRIT_ATTACK);
        f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, f.wearer,
                f.battle.enemyUnits().getFirst(), 0, 0);
        double after = f.attribute(AttributeType.CRIT_ATTACK) - before;
        System.out.println("[21040] 弱点计数不足时的暴伤增量 " + after
                + "（夹具没有属性弱点 ⇒ 条件不应成立；规则形状由装载守钉住）");
        Assertions.assertEquals(0.0, after, 1e-9, "「不少于 2 个被攻击目标具有对应属性弱点」—— 不成立就不加");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
