package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1141517's second sentence (2026-10-02): 「召唤死龙时会消耗所有溢出【新蕊】，每消耗 1% 溢出值，使本次召唤的死龙触发天赋【灼掠幽墟的晦翼】的技能效果时，造成的伤害倍率提高 #2%」.
 *
 * \u2b50 The chain: spend the overflow (round 104) -> capture how much moved (round 107) -> size the amendment with it (round 109). \u2b50 Two-sided, and the difference is a ratio the
 * sentence itself states: the dragon's six hits are `#1 x (1 + spent x #2)` of her max HP, so the expected ratio between the two readings is `(0.56 + 680 x 0.0012) / 0.56`.
 */
public class LifeOdeRaisesTheDragonTalentTest {
    private static final int LEVEL = 80;
    private static final int CASTORICE = 1407;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 17;
    private static final String BUD = "\u65b0\u854a";
    private static final int CAP = 34000;
    private static final int OVERFLOW = 680;
    private static final double RATIO = 0.0012;

    @Test
    public void theSpentOverflowRaisesTheDragonDamage() {
        double with = hits(true);
        double without = hits(false);
        double expected = (0.56 + OVERFLOW * RATIO) / 0.56;
        System.out.println("[life_ratio] the dragon's hits: with the ode " + with + " ; without it " + without
                + " ; ratio " + (with / without) + " (the sentence says " + expected + ")");
        Assertions.assertEquals(expected, with / without, 1e-6, "the amendment is the overflow the summon spent");
    }

    /** What the dragon's talent deals when it arrives, with or without the ode cast at her first. */
    private static double hits(boolean castTheOde) {
        Character her = CharacterFactory.create(CASTORICE, LEVEL);
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(her, cyrene),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
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
        double before = battle.enemies.getFirst().getCurrentHp();
        battle.summonMemosprite(her);
        battle.processRequests();
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
