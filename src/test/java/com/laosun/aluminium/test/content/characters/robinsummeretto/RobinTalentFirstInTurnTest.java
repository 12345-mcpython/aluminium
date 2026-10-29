package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512's talent (151204)'s <b>「第一次」</b> clause: 「我方目标施放攻击时，或<b>在任意目标回合第一次提供治疗/护盾时</b>，获得 1 点
 * 气氛值，上限 50 点。」.
 *
 * <p><b>Why this needed a new spelling.</b> "The first heal this turn" is not a property of any unit - it is a fact about
 * what has happened in the turn so far. The effect now states {@code "once_per_turn": "<key>"}: the first use of that key
 * in a turn goes through, every later one is skipped, and the window resets at every turn boundary
 * ({@code Battle.firstThisTurn}). The two halves share one key, so a heal and then a shield in the same turn pays once.
 */
public class RobinTalentFirstInTurnTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    private static final String VIBES = "气氛值";

    private record Scene(Battle battle, Character robin, Character ally) {
    }

    private static Scene scene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.partyResource(VIBES).setValue(0);
        return new Scene(battle, robin, ally);
    }

    private static void heal(Battle battle, Character healer, Character target) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.HEALED, healer, target, 500, 0));
        battle.processRequests();
    }

    private static void shield(Battle battle, Character giver, Character target) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SHIELD_GRANTED, giver, target, 500, 0));
        battle.processRequests();
    }

    /**
     * The first heal of a turn pays 1; a second heal in the SAME turn pays nothing.
     */
    @Test
    public void onlyTheFirstHealOfATurnPays() {
        Scene scene = scene();
        heal(scene.battle, scene.robin, scene.ally);
        double afterFirst = scene.battle.partyResourceValue(VIBES);
        Assertions.assertEquals(1.0, afterFirst, 1e-9,
                "「在任意目标回合第一次提供治疗…时，获得 1 点气氛值」");

        heal(scene.battle, scene.robin, scene.ally);
        Assertions.assertEquals(afterFirst, scene.battle.partyResourceValue(VIBES), 1e-9,
                "the second heal in the same turn is not a 第一次 and pays nothing");
    }

    /**
     * The heal and the shield share one key: a heal then a shield in the same turn pays once, not twice.
     */
    @Test
    public void aHealAndAShieldInOneTurnPayOnce() {
        Scene scene = scene();
        heal(scene.battle, scene.robin, scene.ally);
        shield(scene.battle, scene.robin, scene.ally);

        Assertions.assertEquals(1.0, scene.battle.partyResourceValue(VIBES), 1e-9,
                "「第一次提供治疗/护盾」 is one event class, so the pair pays once");
    }

    /**
     * A new turn opens the window again.
     */
    @Test
    public void aNewTurnOpensTheWindowAgain() {
        Scene scene = scene();
        heal(scene.battle, scene.robin, scene.ally);
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, scene.robin, scene.robin, 0, 0));
        heal(scene.battle, scene.robin, scene.ally);

        Assertions.assertEquals(2.0, scene.battle.partyResourceValue(VIBES), 1e-9,
                "「任意目标回合」: a turn boundary starts the window over");
    }

    /**
     * The talent's other half: every ally-side attack pays 1, and that half is NOT once per turn.
     */
    @Test
    public void everyAllyAttackPaysOne() {
        Scene scene = scene();
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, scene.ally,
                scene.battle.enemies().getFirst(), 1, 0));
        double afterFirst = scene.battle.partyResourceValue(VIBES);
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, scene.ally,
                scene.battle.enemies().getFirst(), 1, 0));

        Assertions.assertEquals(1.0, afterFirst, 1e-9, "「我方目标施放攻击时…获得 1 点气氛值」");
        Assertions.assertEquals(2.0, scene.battle.partyResourceValue(VIBES), 1e-9,
                "every attack pays - this half is not a once-per-turn clause");
    }
}
