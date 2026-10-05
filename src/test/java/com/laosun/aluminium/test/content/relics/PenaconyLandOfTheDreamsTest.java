package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Relic set 312 (2-piece): other allies with the SAME Type as the wearer deal 10% more damage.
 *
 * <p>The elements are read from the units rather than assumed, and the allies are chosen by searching a handful of characters
 * for one that shares the wearer's element and one that does not -- so the test states the RULE ("same element, not me") and not a
 * fact about which character happens to be which element.
 */
public class PenaconyLandOfTheDreamsTest {
    private static final int SET = 312;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;
    private static final int MONSTER = 1002011;
    private static final int[] CANDIDATES = {1002, 1004, 1006, 1008, 1009, 1013, 1101, 1102, 1103, 1104, 1105, 1106,
            1107, 1108, 1109, 1110, 1111, 1112, 1201, 1202, 1203, 1204, 1206, 1207, 1208, 1209, 1210, 1211};

    private Character wearer;
    private Battle battle;

    @Test
    public void onlyOtherAlliesOfMyElementAreBoosted() {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(SET, 5, RELIC_LEVEL));
        com.laosun.aluminium.enums.DamageElement mine = wearer.getElement();
        List<Character> same = new ArrayList<>();
        List<Character> different = new ArrayList<>();
        List<Character> party = new ArrayList<>();
        party.add(wearer);
        for (int id : CANDIDATES) {
            if (party.size() >= 4 || (same.size() >= 2 && !different.isEmpty())) {
                break;
            }
            Character candidate = CharacterFactory.create(id, LEVEL);
            if (candidate.getElement() == null) {
                continue;
            }
            if (candidate.getElement() == mine) {
                same.add(candidate);
            } else if (different.isEmpty()) {
                different.add(candidate);
            }
            party.add(candidate);
        }
        Assertions.assertFalse(same.isEmpty(), "the search found an ally of the wearer’s own element");
        Assertions.assertFalse(different.isEmpty(), "and one of another element");
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(party, List.of(enemy), new Random(0));
        battle.startBattle();
        double mineBoost = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[312] the wearer’s element=" + mine + " ; same-element allies=" + same.size()
                + " different=" + different.size());
        for (Character ally : same) {
            System.out.println("[312] same-element ally " + ally.getName() + " (" + ally.getElement()
                    + ") boost=" + ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
            Assertions.assertEquals(0.1, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                    "an ally of my element deals 10% more");
        }
        for (Character ally : different) {
            System.out.println("[312] another-element ally " + ally.getName() + " (" + ally.getElement()
                    + ") boost=" + ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
            Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                    "and another element gets nothing (false case)");
        }
        System.out.println("[312] the wearer itself: boost=" + mineBoost);
        Assertions.assertEquals(0.0, mineBoost, 1e-9, "其他我方角色 excludes the wearer (false case)");
    }

    @Test
    public void withoutTheSetNothingIsBoosted() {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        com.laosun.aluminium.enums.DamageElement mine = wearer.getElement();
        Character ally = null;
        for (int id : CANDIDATES) {
            Character candidate = CharacterFactory.create(id, LEVEL);
            if (candidate.getElement() == mine) {
                ally = candidate;
                break;
            }
        }
        Assertions.assertNotNull(ally, "the search found an ally of the wearer’s element");
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        System.out.println("[312] without the set: " + ally.getName() + " boost="
                + ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "no set, no boost (false case)");
    }
}
