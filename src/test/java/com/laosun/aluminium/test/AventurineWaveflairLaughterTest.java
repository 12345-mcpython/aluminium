package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1513: "gain 1/4/6 笑点" - the half each of the three sentences gives.
 *
 * <p>Punchline (笑点) is a PARTY-scoped, uncapped counter (declared by 1502 as `max: 21448364`); these
 * readings are about HER grants, each the sentence's own number. Note: The counter is shared, so the sum test is the point --
 * that is what "party-level" means, and `partyResourceValue` is the accessor `YaoGuangTest` already uses.
 */
public class AventurineWaveflairLaughterTest {
    private static final int AVENTURINE = 1513;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";

    /** "Skill ... gain 4 笑点" */
    @Test
    public void herSkillGivesFour() {
        Scene scene = fight();
        Assertions.assertEquals(0, scene.battle.partyResourceValue(LAUGH), "the battle starts with none");
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.SKILL), scene.her, List.of());
        Assertions.assertEquals(4, scene.battle.partyResourceValue(LAUGH), "\"gain 4 笑点\"");
    }

    /** "Ultimate ... gain 6 笑点" */
    @Test
    public void herUltimateGivesSix() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.ULTRA), scene.her, List.of());
        Assertions.assertEquals(6, scene.battle.partyResourceValue(LAUGH), "\"gain 6 笑点\"");
    }

    /** "after a teammate casts an attack ... and 1 笑点" -- a REAL teammate attack, not a hand-fired event. */
    @Test
    public void aTeammateAttackGivesOne() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.mate.getSkills().get(SkillType.COMMON), scene.mate,
                List.of(scene.battle.enemies.getFirst()));
        Assertions.assertEquals(1, scene.battle.partyResourceValue(LAUGH), "\"and 1 笑点\"");
    }

    /** THE SHARED COUNTER: all three in one battle sum, because Punchline (笑点) is party-scoped. */
    @Test
    public void theCounterIsSharedAcrossTheParty() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.SKILL), scene.her, List.of());
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.ULTRA), scene.her, List.of());
        scene.battle.castImmediate(scene.mate.getSkills().get(SkillType.COMMON), scene.mate,
                List.of(scene.battle.enemies.getFirst()));
        Assertions.assertEquals(11, scene.battle.partyResourceValue(LAUGH), "4 + 6 + 1 on the SHARED counter");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character her;
        final Character mate;

        Scene(Battle battle, Character her, Character mate) {
            this.battle = battle;
            this.her = her;
            this.mate = mate;
        }
    }

    private static Scene fight() {
        Character her = CharacterFactory.create(AVENTURINE, 80, false, null, null, 0);
        Character mate = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, mate),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, her, mate);
    }
}
