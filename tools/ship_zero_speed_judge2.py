"""The judge, written the way the shipped memosprite judges are written (round 1674).

Last round's judge tried to drive a battle and cast the act; `DragonPanelTest` shows the shipped way is
`SummonFactory.memosprite(master, spec, resourceValue)`, which builds the summon directly with no battle at all. Copied
verbatim in shape, with the two readings this capability needs:

  * 小伊卡 -- 50% of 风堇's Max HP (document AND the game table's HPInherit "#1" of 140904 agree), and the speed the data
    states, which is 0 and is moved by an ability;
  * 德谬歌 -- 100% of 昔涟's Max HP (same agreement, HPInherit "#1" of 141503), same zero speed.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/ZeroSpeedByAbilityTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Two panels whose speed the game's own table gives as ZERO (2026-10-02).
 *
 * <p>`ExcelOutput/AvatarServantConfig.json` gives 11409 (小伊卡) and 11415 (德谬歌) `SpeedBase "0"` and `SpeedInherit "0"`,
 * and their servant configs keep Speed out of the summoner sync -- an ability moves it. The validator used to refuse a zero
 * HEALTH/SPEED outright, so neither file could exist. `"by_ability": true` is how a panel says "the data states this zero,
 * an ability supplies the real value", which is different from silently accepting a zero nobody stated.
 */
public class ZeroSpeedByAbilityTest {
    /** 小伊卡: half of 风堇's Max HP, and the stated zero speed. */
    @Test
    public void theIcaCarriesTheShareTheDataStates() {
        MemospriteSpec spec = Memosprites.of(1409);
        Assertions.assertNotNull(spec, "the spec loads from resources/memosprites/1409.json");
        Assertions.assertEquals(Boolean.TRUE, spec.panel().get(1).byAbility(),
                "the file states that an ability supplies the speed");

        Character master = CharacterFactory.create(1409, 80, false, null, null, 0);
        Summon ica = SummonFactory.memosprite(master, spec, name -> 0);
        double masterHp = master.getAttribute(AttributeType.HEALTH).get();
        System.out.println("[zero-speed] " + spec.name() + " hp=" + ica.getAttribute(AttributeType.HEALTH).get()
                + " master=" + masterHp + " speed=" + ica.getAttribute(AttributeType.SPEED).get());
        Assertions.assertEquals(0.5 * masterHp, ica.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "HPInherit #1 of skill 140904 is 0.5, exactly as the document says");
        Assertions.assertEquals(0.0, ica.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the table states 0 -- written down, not hidden");
    }

    /** 德谬歌: all of 昔涟's Max HP, same zero speed. */
    @Test
    public void theDemiurgeCarriesHers() {
        MemospriteSpec spec = Memosprites.of(1415);
        Assertions.assertNotNull(spec, "the spec loads from resources/memosprites/1415.json");
        Assertions.assertEquals(1.0, spec.panel().getFirst().percent(), 1e-9,
                "HPInherit #1 of skill 141503 is 1");

        Character master = CharacterFactory.create(1415, 80, false, null, null, 0);
        Summon demiurge = SummonFactory.memosprite(master, spec, name -> 0);
        Assertions.assertEquals(master.getAttribute(AttributeType.HEALTH).get(),
                demiurge.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "its Max HP is the summoner's");
        Assertions.assertEquals(0.0, demiurge.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "and the speed the table states is 0 here too");
    }
}
''')
print("ok   judge written in the shipped pattern")
