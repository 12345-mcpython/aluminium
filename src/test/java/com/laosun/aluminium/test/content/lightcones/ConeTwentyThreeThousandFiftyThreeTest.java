package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 花花世界迷人眼 (23053): 「装备者在场时，队伍中每有 1 名「欢愉」命途角色，战技点上限提高 1 点，最多提高 3 点」.
 *
 * <p><b>What it needed.</b> Nothing new: {@code allies_with_path:<PATH>} is an existing numeric variable over our living
 * units, and {@code RAISE_SKILL_POINT_CAP} is the op that raises the BATTLE's cap (readers 1015 / 1306). The sentence's
 * ceiling is expressed by having exactly three rules (≥ 1, ≥ 2, ≥ 3), so four or more Elation allies still add three.
 */
public class ConeTwentyThreeThousandFiftyThreeTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;   // a non-Elation path, so the wearer is not counted himself
    private static final int CONE = 23053;
    /** Elation-path characters, used as the counted allies. */
    private static final int[] ELATION = {1501, 1502, 1503, 1505};

    /**
     * The cap follows the number of Elation allies, and stops at +3.
     */
    @Test
    public void theCapFollowsTheElationAlliesAndStopsAtThree() {
        Assertions.assertEquals(5, capWith(0), "no Elation ally: the ordinary cap");
        Assertions.assertEquals(6, capWith(1), "one Elation ally: +1");
        Assertions.assertEquals(7, capWith(2), "+2");
        Assertions.assertEquals(8, capWith(3), "+3");
        Assertions.assertEquals(8, capWith(4), "「最多提高 3 点」 - a fourth ally adds nothing");
    }

    /**
     * The wearer's own path counts too: 「队伍中每有 1 名」 is the party, not "the other allies".
     */
    @Test
    public void theWearerCountsWhenSheIsAnElationCharacter() {
        Character wearer = CharacterFactory.create(ELATION[0], LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(WEARER, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(6, battle.getSkillPointMax(),
                "the wearer is an Elation character herself, so the party has one");
    }

    // ==================================================================

    private static int capWith(int elationAllies) {
        List<Character> party = new ArrayList<>();
        party.add(CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1)));
        for (int i = 0; i < elationAllies; i++) {
            party.add(CharacterFactory.create(ELATION[i], LEVEL));
        }
        Battle battle = new Battle(party, new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle.getSkillPointMax();
    }
}
