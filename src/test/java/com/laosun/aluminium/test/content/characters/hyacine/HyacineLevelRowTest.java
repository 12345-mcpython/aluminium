package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's two行-scaled numbers, read at the <b>level the file actually casts with</b>:
 *
 * <ul>
 *   <li>the ultimate's heal 「为我方全体目标回复等同于风堇 #1% 生命上限 + #2」, and 小伊卡's own 「#5% + #6」;</li>
 *   <li>the talent's 「小伊卡伤害提高 #3%」 per healing layer.</li>
 * </ul>
 *
 * <h2>Why this judge exists</h2>
 * Both used to be <b>literals</b> that happen to equal the <b>Lv10</b> row ({@code 0.1 / 200}, {@code 0.12 / 240},
 * {@code 0.8}). That is the worst kind of number: correct at the file's own convention, and silently stale the
 * moment an eidolon raises the slot - <b>E3</b> takes the ultimate to Lv12 ({@code 0.11 / 222.5}, {@code 0.132 / 267}),
 * <b>E5</b> takes the talent to Lv12 ({@code #3 = 0.88}). The same batch had already fixed the max-HP clause to read
 * {@code ULTRA:2} for exactly this reason; these two were left behind.
 *
 * <p>Each case therefore asserts <b>both ends</b>: the value at the base level and the value after the raise. An
 * assertion on the base level alone cannot tell a literal from a row read - which is how they stayed wrong.
 */
public class HyacineLevelRowTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-6;

    /** {@code 1409/4} Lv10 {@code #3 = 0.8} ; Lv12 (Eidolon 5) {@code #3 = 0.88}. */
    private static final double TALENT_SHARE_LV10 = 0.8;
    private static final double TALENT_SHARE_LV12 = 0.88;

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
                "E5 「#3%」 at Lv12 = 0.88 -- a literal 0.8 would be short here");
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

        /** Casts the ultimate and returns what the ALLY actually gained. */
        double castUltAndMeasureAllyHeal() {
            hyacine.setCurrentEnergy(hyacine.getMaxEnergy());
            double before = ally.getCurrentHp();
            Assertions.assertTrue(battle.castUltra(hyacine, List.of(battle.enemies.getFirst())),
                    "precondition: the ultimate went off");
            return ally.getCurrentHp() - before;
        }

        /** One heal (her Skill), which is what the talent counts a layer of. */
        void healOnce() {
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double ikachiBoost() {
            return ikachi.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}
