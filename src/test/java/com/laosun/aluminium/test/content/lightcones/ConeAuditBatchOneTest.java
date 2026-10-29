package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The first audit batch of light cones (20000-20005): every clause read as a BATTLE, with its numbers taken from
 * {@code weapons.json}'s {@code skill_value} rows.
 *
 * <p>⚠ <b>Why a batch and not one class per cone.</b> These six are one-clause cones, and what can go wrong is the same in
 * all of them (the wrong row, attribute, lifetime or target) - so one case each is enough, and it is the CLAUSE that is
 * being checked rather than the file.
 *
 * <p>⚠⚠ <b>What this batch found by measuring instead of trusting a note:</b>
 * <ol>
 *   <li><b>20003 counted its base DEF twice</b> (fixed): the file carried a rule for the base half on the belief that "a
 *       light cone has no {@code properties} row" - false, {@code weapons.json}'s {@code ability_property} is applied by
 *       {@code Weapon.build} (same evidence as 23055's case). It only showed up once a turn started, because the rule sat
 *       on {@code TURN_START}.</li>
 *   <li><b>"持续 3 回合" and {@code turns: 3} differ by one at a battle start</b>: the modifier is alive at the wearer's
 *       first two turn-starts and gone at the third. That is the engine's clock ({@code BuffManager.tickBuff} removes a
 *       buff whose duration reaches zero at a turn start), so it is REGISTERED (§4.3) and pinned here rather than silently
 *       bent in content - the case is the witness that goes red when the clock is fixed.</li>
 *   <li><b>`turns: 1` on a {@code TURN_START} rule is live only DURING that turn</b> (measured: the DEF reads 1124.219
 *       right after {@code beforeMove} and 1007.795 after {@code afterMove}), so a lifetime read has to happen inside the
 *       turn - that is a fixture trap, not a content bug.</li>
 * </ol>
 */
public class ConeAuditBatchOneTest {
    private static final int LEVEL = 80;

    /**
     * 20000 锋镝: 「战斗开始时，暴击率 +12%，持续 3 回合」.
     */
    @Test
    public void coneTwoThousandCritChanceForThreeTurns() {
        Fixture f = new Fixture(20000, 1003, false);
        double bare = new Fixture(null, 1003, false).critChance();
        System.out.println("[20000] 暴击率增量 +" + (f.critChance() - bare));
        Assertions.assertEquals(0.12, f.critChance() - bare, 1e-9, "叠影 1 的 #1 = 0.12");

        TestTurns.take(f.battle, f.wearer);
        Assertions.assertEquals(0.12, f.critChance() - bare, 1e-9, "第 1 个自己的回合里仍在");
        TestTurns.take(f.battle, f.wearer);
        Assertions.assertEquals(0.12, f.critChance() - bare, 1e-9, "第 2 个自己的回合里仍在");
        TestTurns.take(f.battle, f.wearer);
        System.out.println("[20000] 第 3 个自己的回合开始后的增量 +" + (f.critChance() - bare)
                + "（引擎今天在这里到期；句子要的是再撑一个回合 —— 已登记 §4.3）");
        Assertions.assertEquals(0.0, f.critChance() - bare, 1e-9,
                "今天的行为：`turns: 3` 在第 3 个回合开始时到期（缺口见证）");
    }

    /**
     * 20004 幽邃: 「战斗开始时，效果命中 +20%，持续 3 回合」.
     */
    @Test
    public void coneTwoThousandFourEffectHitRate() {
        double delta = new Fixture(20004, 1005, false).attribute(AttributeType.EFFECT_HIT_RATE)
                - new Fixture(null, 1005, false).attribute(AttributeType.EFFECT_HIT_RATE);
        System.out.println("[20004] 效果命中增量 +" + delta);
        Assertions.assertEquals(0.2, delta, 1e-9, "叠影 1 的 #1 = 0.20");
    }

    /**
     * 20002 天倾: 「普攻和战技造成的伤害提高 20%」 - two attack-type channels, and the all-type channel stays empty.
     */
    @Test
    public void coneTwoThousandTwoBoostsBasicsAndSkillsOnly() {
        Fixture f = new Fixture(20002, 1002, false);
        Fixture bare = new Fixture(null, 1002, false);
        double basic = f.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST)
                - bare.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST);
        double skill = f.attribute(AttributeType.SKILL_DAMAGE_BOOST)
                - bare.attribute(AttributeType.SKILL_DAMAGE_BOOST);
        double all = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST)
                - bare.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        System.out.println("[20002] 普攻 +" + basic + " 战技 +" + skill + " 全属性 +" + all);
        Assertions.assertEquals(0.2, basic, 1e-9, "「普攻…提高 20%」");
        Assertions.assertEquals(0.2, skill, 1e-9, "「战技…提高 20%」");
        Assertions.assertEquals(0.0, all, 1e-9, "这一句只点名两个作用域，全属性那一档必须为空");
    }

    /**
     * 20005 齐颂: 「进入战斗后，我方全体的攻击力提高 8%」 - read on the TEAMMATE, who carries no light cone of her own.
     *
     * <p>⚠ Not a ratio: the 8% joins the unit's OWN attack percent (measured 1.0625 on a unit with +28% from traces), so
     * the reference has to be the panel's BASE attack, which no light cone can touch.
     */
    @Test
    public void coneTwoThousandFiveRaisesTheWholeParty() {
        Fixture f = new Fixture(20005, 1002, true);
        Fixture bare = new Fixture(null, 1002, true);
        Character teammate = f.battle.characters().get(1);
        Character bareTeammate = bare.battle.characters().get(1);
        double raised = attack(teammate) - attack(bareTeammate);
        double base = bareTeammate.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[20005] 队友 " + teammate.getName() + " 的攻击增量 " + raised + " ; 基础攻击 " + base);
        Assertions.assertEquals(0.08 * base, raised, base * 1e-9,
                "「我方全体」⇒ 队友的基础攻击 ×8%（她身上没有锥，增量只能是这一句）");
    }

    /**
     * 20001 物穰: 「装备者施放战技和终结技时，治疗量提高 12%」 - the boost lives for THAT cast only, and it is 12%.
     *
     * <p>⚠ The window is measured inside ONE battle: a heal of exactly the amount the cast just healed, given right after
     * the cast, must come out unchanged. (A "with cone vs without" pair reads 1.487, not 1.120: the cone's own HP/ATK also
     * scale the heal.) The 12% itself is read from the file, because no shipped kit lets a test inspect the wearer's
     * outgoing-healing bonus mid-cast.
     */
    @Test
    public void coneTwoThousandOneBoostsTheHealOfThatCastOnly() {
        Fixture f = new Fixture(20001, 1409, true);
        Character ally = f.battle.characters().get(1);
        ally.consumeHp(ally.getMaxHp() * 0.5);
        double before = ally.getCurrentHp();
        SkillExecutor.execute(f.battle, f.wearer.getSkills().get(SkillType.SKILL), f.wearer, List.of(ally));
        f.battle.processRequests();
        double inCast = ally.getCurrentHp() - before;

        ally.consumeHp(ally.getMaxHp() * 0.5);
        double beforeSecond = ally.getCurrentHp();
        f.battle.heal(f.wearer, ally, inCast);
        f.battle.processRequests();
        double afterCast = ally.getCurrentHp() - beforeSecond;
        System.out.println("[20001] 这次施放的治疗 " + inCast + " ; 施放之后同样金额的治疗 " + afterCast);
        Assertions.assertEquals(inCast, afterCast, 1e-6,
                "寿命是 `cast_end` ⇒ 施放结束后同样金额的治疗不再被放大");

        JsonObject rule = theRule(20001, "1", "cone20001_heal_on_skill");
        Assertions.assertEquals(0.12, rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "叠影 1 的 #1 = 0.12");
        Assertions.assertEquals("cast_end", rule.getAsJsonArray("do").get(0).getAsJsonObject().get("until").getAsString(),
                "窗口就是那一次施放");
    }

    /**
     * 20003 琥珀: the base share is the cone's own {@code ability_property} - applied ONCE - and below 50% HP the extra
     * share lands on top of it, for that turn.
     *
     * <p>⚠ The reads happen right after {@code beforeMove}: a {@code turns: 1} modifier applied by a {@code TURN_START}
     * rule is gone by {@code afterMove} (measured), so reading after the turn would show nothing.
     */
    @Test
    public void coneTwoThousandThreeDefenceIsNotCountedTwice() {
        Fixture f = new Fixture(20003, 1013, false);
        double atBattleStart = f.attribute(AttributeType.DEFENCE);

        // Full HP, inside the first turn: the old file stacked its base rule here (this is the regression the fix closes).
        double fullHpInTurn = f.defenceInsideTurn();
        System.out.println("[20003] 战斗开始时 " + atBattleStart + " ; 满血在自己的回合里 " + fullHpInTurn);
        Assertions.assertEquals(atBattleStart, fullHpInTurn, atBattleStart * 1e-9,
                "「防御力提高 16%」只该由光锥属性给一次；旧文件在这里叠了第二条规则");

        // One DEF share, read off the panel: rank 1 and rank 5 differ only by one share (0.16 -> 0.32).
        double rank1 = CharacterFactory.create(1013, LEVEL, false, Weapon.build(20003, LEVEL, false, 1))
                .getAttribute(AttributeType.DEFENCE).get();
        double rank5 = CharacterFactory.create(1013, LEVEL, false, Weapon.build(20003, LEVEL, false, 5))
                .getAttribute(AttributeType.DEFENCE).get();
        double perShare = (rank5 - rank1) / 0.16;
        System.out.println("[20003] 一档份额的基数 " + perShare + " ; 叠影1 " + rank1 + " ; 叠影5 " + rank5);

        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.6);
        double belowInTurn = f.defenceInsideTurn();
        System.out.println("[20003] 低于 50% 时（自己的回合里） " + belowInTurn + " ; 期望 " + (atBattleStart + perShare * 0.16));
        Assertions.assertEquals(atBattleStart + perShare * 0.16, belowInTurn, belowInTurn * 1e-9,
                "「当前生命值百分比小于 50% 时，其防御力额外提高 16%」—— 在光锥属性之上再加一档");
    }

    // ==================================================================

    private static double attack(Character unit) {
        return unit.getAttribute(AttributeType.ATTACK).get();
    }

    private static JsonObject theRule(int cone, String level, String id) {
        JsonArray rules = read("/game/light_cones/" + cone + ".json").getAsJsonArray(level);
        for (int i = 0; i < rules.size(); i++) {
            JsonObject each = rules.get(i).getAsJsonObject();
            if (id.equals(each.get("id").getAsString())) {
                return each;
            }
        }
        throw new AssertionError("no rule " + id + " in light cone " + cone + " level " + level);
    }

    private static JsonObject read(String path) {
        try (InputStream stream = ConeAuditBatchOneTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;

        private Fixture(Integer cone, int wearerId, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(wearerId, LEVEL, false, weapon);
            List<Character> party = withAlly
                    ? List.of(this.wearer, CharacterFactory.create(1204, LEVEL))
                    : List.of(this.wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double critChance() {
            return attribute(AttributeType.CRIT_CHANCE);
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }

        /**
         * Walks to the wearer's next turn and reads the DEF <b>inside</b> it - the window a {@code TURN_START} rule with
         * {@code turns: 1} is alive in.
         */
        private double defenceInsideTurn() {
            for (int step = 0; step < 40; step++) {
                battle.stepForward();
                boolean mine = battle.queue().getCurrentActor().getCanHit() == wearer;
                if (mine) {
                    battle.beforeMove();
                    double inside = attribute(AttributeType.DEFENCE);
                    battle.afterMove();
                    return inside;
                }
                battle.afterMove();
            }
            throw new AssertionError("the wearer never got a turn");
        }
    }
}
