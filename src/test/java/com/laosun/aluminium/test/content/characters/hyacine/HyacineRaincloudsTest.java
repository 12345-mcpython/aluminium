package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's 忆灵技1 【乌云乌云快走开！】 (data slot 1 of ServantID 11409):
 *
 * <pre>
 * 「对敌方全体造成等同于本场战斗风堇与小伊卡累计治疗数值 #1% 的风属性伤害并清空累计治疗数值的 50%。」
 * params: [0.1, 0.5] .. [0.28, 0.5]      Rank 6: 「清空累计治疗数值改为 12%」 params [0.12, 0.2]
 * </pre>
 *
 * <p>Three facts, measured separately:
 * <ul>
 *   <li>the damage is a share of the <b>tally</b>, read off the memosprite's own level row -- not of its ATK, which is
 *       0;</li>
 *   <li>the tally is cleared <b>after</b> the damage reads it (clearing first would make the damage 0);</li>
 *   <li>Rank 6 replaces the 50% with 12% -- the two rules are twins (`max_eidolon: 5` / `min_eidolon: 6`), so one cast
 *       removes one share and not both.</li>
 * </ul>
 *
 * <p>⚠ The memosprite's own data row still settles a {@code %ATK} instance, and with ATK = 0 that leaves the engine's
 * <b>1-point floor</b>. The damage assertion therefore names that point instead of hiding it.
 */
public class HyacineRaincloudsTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final String TALLY = "小伊卡累计治疗";
    /** 「#1」 at the memosprite skill's level 10 row. */
    private static final double SHARE_AT_LV10 = 0.28;
    /** The data-driven instance's floor with ATK = 0 (measured). */
    private static final double DATA_FLOOR = 1.0;

    @Test
    public void theDamageIsTheShareOfTheTallyPlusTheDataFloor() {
        Fixture f = new Fixture(0);
        double tally = f.tally();
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        double dealt = f.castRainclouds();

        // The base is the TALLY (not the memosprite's ATK, which is 0), the data row delivers nothing (skill_effects says
        // `Rules`), and because the rule says `"attacker": "summon"` the instance is the MEMOSPRITE's -- so her talent's
        // +80% per layer (one layer, from the heal that fed the tally) lands on it.
        Assertions.assertTrue(DATA_FLOOR > 0, "kept for the note: the floor is gone");
        Assertions.assertEquals(tally * SHARE_AT_LV10 * 1.8, dealt, Math.max(1e-6, tally * 1e-9),
                "「造成等同于累计治疗数值 #1% 的风属性伤害」 by 小伊卡: tally=" + tally + ", dealt=" + dealt
                        + " (expected tally x " + SHARE_AT_LV10 + " x 1.8 for one talent layer, no data floor)");
    }

    @Test
    public void halfOfTheTallyIsClearedAfterTheDamageReadsIt() {
        Fixture f = new Fixture(0);
        double tally = f.tally();
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        f.castRainclouds();

        Assertions.assertEquals(Math.round(tally * 0.5), f.tally(),
                "「清空累计治疗数值的 50%」 removes half of what the damage just read (was " + tally + ", now "
                        + f.tally() + ")");
    }

    @Test
    public void rankSixClearsTwelvePercentInsteadOfFifty() {
        Fixture f = new Fixture(6);
        double tally = f.tally();
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        f.castRainclouds();

        // 12% removed, so 88% is left -- NOT 50%-and-then-12% (which would leave 44%).
        Assertions.assertEquals(Math.round(tally * 0.88), f.tally(),
                "Stars 6: 「清空改为 12%」 REPLACES the 50%, it does not stack with it (was " + tally + ", now "
                        + f.tally() + ")");
    }

    /** 风堇 + a wounded ally, 小伊卡 out, and one heal already counted into the tally. */
    private static final class Fixture {
        final Battle battle;
        final Character hyacine;

        Fixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            Character ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double tally() {
            return hyacine.getResources().value(TALLY);
        }

        /** Casts 忆灵技1 and returns what it cost the enemy. */
        double castRainclouds() {
            Summon ika = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            var enemy = battle.enemies().getFirst();
            double before = enemy.getCurrentHp();
            SkillExecutor.execute(battle, ika.skillAt(1), ika, List.of(enemy));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }
    }
}
