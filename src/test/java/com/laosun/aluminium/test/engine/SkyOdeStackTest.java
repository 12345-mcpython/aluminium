package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, first sentence: "德谬歌施放忆灵技时，使风堇获得2层[献予'天空'之诗]".
 *
 * <p>TWO-SIDED in one battle: the character the game NAMES BY CID (1409, measured in the ability data) gets the 2 layers the data states, and a different ally present gets
 * none. Note: A cap had to be stated -- the data puts no `MaxLayer` beside this modifier, and our `StackBuff` clamps to 1 without one; 99999 is how this kit spells "no limit".
 */
public class SkyOdeStackTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYACINE = 1409;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "献予「天空」之诗";

    @Test
    public void theNamedCharacterGetsTwoLayersAndNobodyElseDoes() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);
        other = battle.characters.get(2);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");
        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 19 -- the skill the sentence belongs to");

        SkillExecutor.execute(battle, ode, demiurge, List.of(hyacine));
        battle.processRequests();

        int named = hyacine.getBuffManager().stacksOf(MARK);
        int bystander = other.getBuffManager().stacksOf(MARK);
        System.out.println("[sky_stacks] the named character has " + named + " ; the other ally has " + bystander);

        Assertions.assertEquals(2, named,
                "「使风堇获得 2 层」 (grants Hyacine 2 stacks)-- the data states LayerAddWhenStack: 2, and the sentence agrees");
        Assertions.assertEquals(0, bystander, "and nobody else -- the game names the cid, and so does `ally_cid:`");
    }

    /**
     * The OTHER half of `ally_cid:`: a battle that does not contain the named character. The clause must do nothing -- not throw. Note: This is not hypothetical: content that
     * reached this branch with nobody to find turned 22 unrelated judges red before the fix.
     */
    @Test
    public void theClauseDoesNothingWhenSheIsNotThere() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        other = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: slot 19");

        // no assertion about layers -- the reading IS that this does not throw
        Assertions.assertDoesNotThrow(
                () -> {
                    SkillExecutor.execute(battle, ode, demiurge, List.of(battle.enemies.getFirst()));
                    battle.processRequests();
                },
                "when the character the sentence names is not on the field, this clause should do nothing");
        Assertions.assertEquals(0, other.getBuffManager().stacksOf(MARK),
                "and it lands on nobody -- not on a bystander");
        System.out.println("[sky_stacks] with the named character absent: no throw, and the bystander has "
                + other.getBuffManager().stacksOf(MARK));
    }
}
