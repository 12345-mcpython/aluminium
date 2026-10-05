package com.laosun.aluminium.test.trigger;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 114151complete (2026-10-02): the whole sentence, both halves.
 *
 * "when summoning the dead dragon, all overflow [新蕊] is consumed; for every 1% of overflow consumed, when the dead dragon summoned this time triggers the skill effect of the talent [灼掠幽墟的晦翼], the damage multiplier it deals is increased by #2%(0.0012);
 *   and if no more than #6(2) enemy targets are on the field at the time of summoning, the damage multiplier is additionally increased by #5%(0.0024)."
 *
 * Both halves are ratios the sentence states itself: with the ode and one enemy the six hits are `#1 + spent x #2 + #5` of her max HP; against three enemies the `#5` half is off,
 * so the reading drops by exactly `#5 / (#1 + spent x #2)`.
 */
public class LifeOdeRaisesTheDragonTalentTest {
    private static final int LEVEL = 80;
    private static final int CASTORICE = 1407;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 17;
    private static final String BUD = "新蕊";
    private static final int CAP = 34000;
    private static final int OVERFLOW = 680;
    private static final double PER_POINT = 0.0012;
    private static final double SMALL_PACK = 0.0024;

    @Test
    public void theSpentOverflowRaisesTheDragonDamage() {
        double with = hits(true, 1);
        double without = hits(false, 1);
        // BOTH readings carry the small-pack extra (one enemy on each side), and only the ode's amendment differs so the denominator is `#1 + #5`, not `#1`.
        double expected = (0.56 + OVERFLOW * PER_POINT + SMALL_PACK) / (0.56 + SMALL_PACK);
        System.out.println("[life_ratio] with the ode " + with + " ; without it " + without
                + " ; ratio " + (with / without) + " (the sentence says " + expected + ")");
        Assertions.assertEquals(expected, with / without, 1e-6, "the amendment is the overflow the summon spent");
    }

    @Test
    public void aSmallPackRaisesItOnceMore() {
        double small = hits(true, 1);
        double big = hits(true, 3);
        double expected = (0.56 + OVERFLOW * PER_POINT + SMALL_PACK) / (0.56 + OVERFLOW * PER_POINT);
        System.out.println("[life_ratio] one enemy " + small + " ; three enemies " + big
                + " ; ratio " + (small / big) + " (the sentence says " + expected + ")");
        Assertions.assertEquals(expected, small / big, 1e-6, "the extra only applies against two or fewer");
    }

    /** What the dragon's talent deals when it arrives, with or without the ode cast at her first. */
    private static double hits(boolean castTheOde, int enemies) {
        Character her = CharacterFactory.create(CASTORICE, LEVEL);
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        List<com.laosun.aluminium.models.enemy.Enemy> pack = new java.util.ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            pack.add(EnemyFactory.create(MONSTER, 90, 1));
        }
        Battle battle = new Battle(List.of(her, cyrene), pack, new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        if (castTheOde) {
            var sprite = battle.summonServant(battle.characters.get(1));
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 17");
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(her));
            battle.processRequests();
            her = battle.characters.getFirst();
        }
        battle.partyResource(BUD).gain(CAP + OVERFLOW + 1000);
        battle.processRequests();
        double before = 0;
        for (var e : battle.enemies) {
            before += e.getCurrentHp();
        }
        battle.summonMemosprite(her);
        battle.processRequests();
        double after = 0;
        for (var e : battle.enemies) {
            after += e.getCurrentHp();
        }
        return before - after;
    }
}
