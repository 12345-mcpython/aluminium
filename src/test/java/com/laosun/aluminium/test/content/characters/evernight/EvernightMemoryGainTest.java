package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1413 长夜月's 忆灵技1 clause 「…施放后获得 <b>#4</b> 点【忆质】」 (data row {@code [0.25, 0.05, 4, 1]}, {@code #4} = 1).
 *
 * <p>Three facts, each of which a plausible wrong implementation would get wrong:
 * <ol>
 *   <li><b>the count comes from the cast's own row</b> ({@code amount_from_cast_param: 3}), so it is 1 at every level
 *       and would follow the data if the row changed;</li>
 *   <li><b>it is HER memosprite's cast</b> - another character's memosprite casting its own slot 1 must not hand her
 *       anything (the {@code actor == summon} reading, the shape {@code AGENT.md} §3.7zb had to fix once already);</li>
 *   <li><b>it accumulates</b> - twice cast, two points.</li>
 * </ol>
 *
 * <p>⚠ The other half of that sentence (「每有 #3 点【忆质】额外造成 #2%」) is still registered: the game folds it into
 * the same six instances' {@code DamagePercentage}, and the engine has no "scale this instance by a counter's tiers"
 * spelling yet.
 */
public class EvernightMemoryGainTest {

    private static final int EVERNIGHT = 1413;
    private static final int HYACINE = 1409;
    private static final String MEMORY = "亿质";
    private static final int RAINCLOUDS = 1;

    /**
     * Casting 长夜's slot 1 gives one 【亿质】, and casting it again gives another.
     */
    @Test
    public void herMemospriteSkillGrantsOneMemoryPerCast() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.memory(), "precondition: nothing has been cast yet");

        f.castRainclouds();
        Assertions.assertEquals(1, f.memory(), "「施放后获得 #4 点【亿质】」 -- #4 is 1 in the row");

        f.castRainclouds();
        Assertions.assertEquals(2, f.memory(), "and it accumulates, one per cast");
    }

    /**
     * Another character's memosprite casting its own slot 1 is not 长夜's cast.
     */
    @Test
    public void anotherCharactersMemospriteGrantsNothing() {
        Fixture f = new Fixture();
        Summon ika = (Summon) f.battle.memospriteOf(f.hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        SkillExecutor.execute(f.battle, ika.skillAt(RAINCLOUDS), ika, List.of(f.battle.enemies().getFirst()));
        f.battle.processRequests();

        Assertions.assertEquals(0, f.memory(),
                "小伊卡 casting its own slot 1 must not feed 长夜月's 【亿质】 -- 「长夜施放后」 is HER memosprite");
    }

    /** 长夜月 with her memosprite out, plus another character's memosprite as the control. */
    private static final class Fixture {
        final Battle battle;
        final Character evernight;
        final Character hyacine;

        Fixture() {
            this.evernight = CharacterFactory.create(EVERNIGHT, 80);
            this.hyacine = CharacterFactory.create(HYACINE, 80);
            this.battle = new Battle(List.of(evernight, hyacine), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(evernight);
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            Assertions.assertNotNull(battle.memospriteOf(evernight), "precondition: 长夜 is out");
        }

        int memory() {
            return evernight.getResources().value(MEMORY);
        }

        /** 长夜 casts its own data slot 1. */
        void castRainclouds() {
            Summon longNight = (Summon) battle.memospriteOf(evernight);
            Skill skill = longNight.skillAt(RAINCLOUDS);
            Assertions.assertNotNull(skill, "precondition: 长夜 carries data slot 1");
            SkillExecutor.execute(battle, skill, longNight, List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }
    }
}
