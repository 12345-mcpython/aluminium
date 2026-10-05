package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Relic set 321 "妖精织梦的乐园" 2-piece: "for each 1 fewer of our targets, raise the damage dealt by the wearer and their memosprite by 12%, up to 3 stacks".
 *
 * <p>Every read happens after a DRIVEN TURN: the memosprite's half hangs on SUMMONED, which is fired from a settle, and rounds 8-9 recorded
 * it as an engine defect only because their probes read before any settle existed. Measured: `no-settle -> 0.0`, `settle -> 0.12`.
 *
 * <p>The fixture is built in TARGETS, not characters: `ally_count` counts a memosprite as an ally target (two characters plus the memosprite is
 * three targets, one missing, +12%), which is the game's own reading of "our target count".
 */
public class ArcadiaDamageTest {
    private static final int WEARER = 1001;
    private static final int MEMOSPRITE_OWNER = 1413;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int VICTIM = 1002011;
    private static final int RELIC_LEVEL = 15;

    @Test
    public void theWearerIsBoostedWithThreeAlliesAndNotWithFour() {
        Assertions.assertEquals(0.12, wearerBoost(3), 1e-9, "one missing ally is one stack");
        Assertions.assertEquals(0.0, wearerBoost(4), 1e-9, "a full party is no stacks");
    }

    @Test
    public void theMemoSpriteIsBoostedToo() {
        // Note: In targets: three characters plus the memosprite would be four (nothing missing), so the boosted case is two characters.
        double fourTargets = memospriteDamage(3);
        double threeTargets = memospriteDamage(2);
        double ratio = threeTargets / fourTargets;
        // Note: Measured 1.08, not 1.12: the memosprite also carries 1413's own enemy-count talent in another zone, which compresses the
        // ratio -- the same effect round 65 measured when a 1.48 zone ratio read 1.1404 on a skill hit. The attribute assertion inside
        // `memospriteDamage` is the exact one; this proves the boost reaches real damage.
        Assertions.assertTrue(fourTargets > 0, "precondition: the memosprite landed both hits");
        Assertions.assertTrue(ratio > 1.05 && ratio <= 1.12,
                "the memosprite's own boost reached its damage: " + fourTargets + " vs " + threeTargets
                        + " (ratio " + ratio + ")");
    }

    private static double wearerBoost(int allies) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(321, 5, RELIC_LEVEL));
        Battle battle = new Battle(party(wearer, allies), List.of(EnemyFactory.create(VICTIM, 90, 1)), new Random(0));
        battle.startBattle();
        driveTurn(battle, wearer);
        return wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /** Note: Takes the number of CHARACTERS: the memosprite adds one more target. */
    private static double memospriteDamage(int allies) {
        Character master = CharacterFactory.create(MEMOSPRITE_OWNER, LEVEL, true, null,
                RelicFactory.suit(321, 5, RELIC_LEVEL));
        Enemy victim = EnemyFactory.create(VICTIM, 90, 1);
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(party(master, allies), List.of(victim), noCrit);
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the memosprite is out");
        driveTurn(battle, master);
        Assertions.assertEquals(allies == 2 ? 0.12 : 0.0,
                memosprite.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the memosprite carries its own half (needs the settle): "
                        + memosprite.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        double before = victim.getCurrentHp();
        battle.castImmediate(memosprite.getSkills().get(SkillType.COMMON), memosprite, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before, "a real measurement: " + dealt + " of " + before);
        return dealt;
    }

    /** Note: A whole turn: SUMMONED and TURN_START both come out of a settle, so nothing may be read without one. */
    private static void driveTurn(Battle battle, Character unit) {
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.afterMove();
    }

    private static List<Character> party(Character wearer, int allies) {
        List<Character> team = new ArrayList<>();
        team.add(wearer);
        while (team.size() < allies) {
            team.add(CharacterFactory.create(ALLY, LEVEL));
        }
        return team;
    }
}
