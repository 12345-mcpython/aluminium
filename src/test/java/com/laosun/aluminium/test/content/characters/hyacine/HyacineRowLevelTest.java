package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's two row-scaled numbers, each asserted at <b>both ends</b> of the level range:
 *
 * <ul>
 *   <li>the ultimate's heal 「为我方全体目标回复等同于风堇 <b>#1% 生命上限</b> + <b>#2</b>」;</li>
 *   <li>the talent's 「小伊卡伤害提高 <b>#3%</b>」 per healing layer.</li>
 * </ul>
 *
 * <h2>Why "both ends" is the whole point</h2>
 * Both used to be <b>literals</b> equal to the <b>Lv10</b> row ({@code 0.1 / 200} and {@code 0.8}). A literal is
 * right at the file's own convention and silently stale the moment an eidolon raises the slot: <b>E3</b> takes the
 * ultimate to Lv12 ({@code 0.11 / 222.5}), <b>E5</b> the talent to Lv12 ({@code #3 = 0.88}). An assertion on the
 * base level alone cannot tell a literal from a row read - which is how they stayed wrong.
 *
 * <h2>The heal's base is HER max HP, measured</h2>
 * The heal's {@code scale} is {@code owner_max_hp} = <b>风堇's own</b> Max HP, taken <b>before</b> the same cast's
 * 「雨过天晴」 raises it. A probe measured her max HP at cast time as {@code 1195.2864} (it becomes
 * {@code 2121.2736} right afterwards) and the ally's gain as {@code 319.52864 = 0.1 x 1195.2864 + 200} at E0 -
 * the <b>same</b> {@code 319.52864} at E3, which is the drift this judge pins.
 */
public class HyacineRowLevelTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-6;

    /** {@code 1409/3} Lv10 = {@code [0.1, 200, 0.3, 600, 3, 0.12, 240]}; Lv12 = {@code [0.11, 222.5, …]}. */
    private static final double ULT_SHARE_LV10 = 0.1;
    private static final double ULT_FLAT_LV10 = 200;
    private static final double ULT_SHARE_LV12 = 0.11;
    private static final double ULT_FLAT_LV12 = 222.5;
    /** {@code 1409/4} Lv10 {@code #3 = 0.8}; Lv12 (Eidolon 5) {@code #3 = 0.88}. */
    private static final double TALENT_SHARE_LV10 = 0.8;
    private static final double TALENT_SHARE_LV12 = 0.88;

    @Test
    public void theUltimateHealsTheLv10RowAtE0() {
        Fixture f = new Fixture(0);
        double[] result = f.castUltAndMeasureAllyHeal();
        double expected = ULT_SHARE_LV10 * result[1] + ULT_FLAT_LV10;
        Assertions.assertEquals(expected, result[0], Math.max(EPS, expected * 1e-9),
                "E0 「#1% 生命上限 + #2」 at Lv10, base = HER max HP at cast time (" + result[1] + ")");
    }

    @Test
    public void theUltimateHealsTheLv12RowAtE3() {
        Fixture f = new Fixture(3);
        Assertions.assertEquals(12, f.hyacine.skillLevel(f.hyacine.getSkills().get(SkillType.ULTRA)),
                "precondition: Eidolon 3 raised the ultimate to Lv12");
        double[] result = f.castUltAndMeasureAllyHeal();
        double expected = ULT_SHARE_LV12 * result[1] + ULT_FLAT_LV12;
        double literal = ULT_SHARE_LV10 * result[1] + ULT_FLAT_LV10;
        Assertions.assertEquals(expected, result[0], Math.max(EPS, expected * 1e-9),
                "E3 must read the Lv12 row (" + expected + "); the old literal would heal " + literal);
    }

    @Test
    public void theTalentsPerLayerBoostIsTheLv10RowAtE0() {
        Fixture f = new Fixture(0);
        f.healOnce();
        Assertions.assertEquals(TALENT_SHARE_LV10, f.ikachiBoost(), EPS,
                "「小伊卡伤害提高 #3%」 at Lv10 = 0.8 for one healing layer");
    }

    @Test
    public void theTalentsPerLayerBoostIsTheLv12RowAtE5() {
        Fixture f = new Fixture(5);
        Assertions.assertEquals(12, f.hyacine.skillLevel(f.hyacine.getSkills().get(SkillType.TALENT)),
                "precondition: Eidolon 5 raised the talent to Lv12");
        f.healOnce();
        Assertions.assertEquals(TALENT_SHARE_LV12, f.ikachiBoost(), EPS,
                "E5 「#3%」 at Lv12 = 0.88 -- the old literal 0.8 would be short here");
    }

    /** 风堇 + a wounded ally + 小伊卡 on the field. */
    private static final class Fixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;
        final Summon ikachi;

        Fixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            this.ikachi = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ikachi, "precondition: 小伊卡 is out");
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        }

        /** @return {what the ALLY gained, 风堇's max HP at cast time} */
        double[] castUltAndMeasureAllyHeal() {
            hyacine.setCurrentEnergy(hyacine.getMaxEnergy());
            double herMaxHpAtCast = hyacine.getMaxHp();
            double before = ally.getCurrentHp();
            Assertions.assertTrue(battle.castUltra(hyacine, List.of(battle.enemies().getFirst())),
                    "precondition: the ultimate went off");
            return new double[]{ally.getCurrentHp() - before, herMaxHpAtCast};
        }

        void healOnce() {
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double ikachiBoost() {
            return ikachi.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}
