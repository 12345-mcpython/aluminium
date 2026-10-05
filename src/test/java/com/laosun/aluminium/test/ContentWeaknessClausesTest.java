package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Content-layer judges for four pieces of content - that is, "is that clause really wired to an engine capability".
 *
 * <p>The underlying mechanics were each verified on their own (piece 12 {@code ADD_ELEMENTAL_WEAKNESS}, piece 18 {@code random_absent}/{@code party_first},
 * piece 19 {@code turns}), so the only question here is the content's wiring: what is added is exactly the attribute that clause should add.
 *
 * <p>Note: each of the four uses its own trigger - which is part of "the content is really wired":
 * {@code 1315} uses the real ultimate (full energy); {@code 1405}/{@code 1006} use two events; {@code 1310} uses {@code markTechniqueUsed} and then starts the battle
 * (Note: order: the technique state must be registered before {@code startBattle()}, {@code Battle:42} adds it before any {@code BATTLE_START} rule).
 */
public class ContentWeaknessClausesTest {
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    private static Enemy enemyOf(Battle battle) {
        return (Enemy) battle.getOpponents(battle.characters.getFirst()).getFirst();
    }

    /** Whether light cone 23050 is equipped or nothing is, neither affects these four - they are the character's own rules. */
    private static Battle battleWith(Character wearer) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static List<DamageElement> weaknesses(Enemy enemy) {
        List<DamageElement> had = new ArrayList<>();
        for (DamageElement e : DamageElement.values()) {
            if (enemy.isWeakTo(e)) {
                had.add(e);
            }
        }
        return had;
    }

    @Test
    public void charactersOwnClausesLand() {
        // ---- 1315 Boothill: the ultimate "adds a Physical weakness to one designated enemy, lasting 2 turns" (turns supplied by 13ccc3)
        Character bto = CharacterFactory.create(1315, LEVEL);
        Battle b1 = battleWith(bto);
        Enemy e1 = enemyOf(b1);
        Assertions.assertFalse(e1.isWeakTo(DamageElement.PHYSICAL), "1002011 must not already have Physical");
        bto.setCurrentEnergy(bto.getMaxEnergy());
        Assertions.assertTrue(b1.castUltra(bto, List.of(e1)), "full energy, so the ultimate must cast");
        System.out.println("[content] 1315 ultimate -> physical=" + e1.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertTrue(e1.isWeakTo(DamageElement.PHYSICAL), "the ultimate's own clause inserts Physical");

        // ---- 1405 Anaxa: the talent "after a hit, adds 1 random elemental weakness for 3 turns, preferring one the target does not have"
        Character ana = CharacterFactory.create(1405, LEVEL);
        Battle b2 = battleWith(ana);
        Enemy e2 = enemyOf(b2);
        List<DamageElement> before = weaknesses(e2);
        b2.fireTriggers(TriggerEvent.ALLY_ATTACK, ana, e2, 0, 0);
        List<DamageElement> added = new ArrayList<>(weaknesses(e2));
        added.removeAll(before);
        System.out.println("[content] 1405 ally-attack -> had=" + before.size() + " added=" + added);
        Assertions.assertEquals(1, added.size(), "exactly one random weakness is inserted");
        Assertions.assertFalse(before.contains(added.getFirst()), "and it is one the target did not have");

        // ---- 1006 Silver Wolf: the skill "adds 1 weakness of an element held by a party target on the field" (skill description: the first slot)
        Character sw = CharacterFactory.create(1006, LEVEL);
        Battle b3 = battleWith(sw);
        Enemy e3 = enemyOf(b3);
        Assertions.assertFalse(e3.isWeakTo(sw.getElement()), "the enemy must not already have the wearer's element");
        b3.fireTriggers(TriggerEvent.SKILL_CAST, sw, e3, 0, 0);
        System.out.println("[content] 1006 skill -> " + sw.getElement() + "=" + e3.isWeakTo(sw.getElement()));
        Assertions.assertTrue(e3.isWeakTo(sw.getElement()), "the first party member's element is what its text names");
    }

    @Test
    public void theTechniqueClauseAppliesToEveryEnemy() {
        // ---- 1310 Firefly: the technique "at the start of every wave, adds a Fire weakness to all enemies for 2 turns"
        Character firefly = CharacterFactory.create(1310, LEVEL);
        // Note: only one enemy with no Fire weakness is needed (it is the control: absent before the technique, and
        // it must be present after it); the second has no condition - the control needed is "it did not have it
        // originally", not "neither of them had it" (measured: only 1 in that id range had no Fire).
        // Note: both must originally have no Fire weakness - Note: the previous version let the second be any enemy,
        // and it picked 1002011, which already had Fire, so that true never proved anything.
        List<Enemy> picked = new ArrayList<>();
        for (int cid = 1002011; cid <= 1002100 && picked.size() < 2; cid++) {
            try {
                Enemy candidate = EnemyFactory.create(cid, 90, 1);
                if (!candidate.isWeakTo(DamageElement.FIRE)) {
                    picked.add(candidate);
                }
            } catch (RuntimeException ignored) {
                // skip ids that do not exist
            }
        }
        Assertions.assertEquals(2, picked.size(),
                "need TWO enemies that do NOT already have Fire, or the assertion proves nothing");
        Enemy a = picked.get(0);
        Enemy b = picked.get(1);
        // Note: markTechniqueUsed must come before startBattle() - startBattle adds that state before any BATTLE_START rule
        Battle battle = new Battle(List.of(firefly), List.of(a, b), new Random(0));
        battle.markTechniqueUsed(firefly);
        battle.startBattle();
        // Note: WAVE_START is fired by WaveManager.nextWave() (it is not inside startBattle()).
        // Note: it carries no actor and no subject, and like BATTLE_START it is "a fact about the battle".
        // Note: it is fired directly here, because what this case must prove is "1310's clause is wired to the engine"; "WAVE_START does get fired" already has 10 precedents.
        battle.fireTriggers(TriggerEvent.WAVE_START);
        System.out.println("[content] 1310 technique -> a=" + a.isWeakTo(DamageElement.FIRE)
                + " b=" + b.isWeakTo(DamageElement.FIRE));
        Assertions.assertTrue(a.isWeakTo(DamageElement.FIRE) && b.isWeakTo(DamageElement.FIRE),
                "the technique clause reaches EVERY enemy, not just the first");
    }

    /**
     * Note: the other half of the content layer: does that clause really hand the turns to the engine.
     *
     * <p>The underlying layer is already proven (piece 19), so the only question here is the content: the 1315 rule carries
     * "turns": 2, so its Physical weakness ought to expire.
     *
     * <p>Note: mutation point: delete "turns": 2 from 1315.json and this must go red. Otherwise the weakness stays forever - and
     * "forever" and "the 2 turns the document states" look exactly alike to an assertion, unless some turns really pass.
     */
    @Test
    public void theInsertedWeaknessFromContentActuallyExpires() {
        Character bto = CharacterFactory.create(1315, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(bto), List.of(enemy), new Random(0));
        battle.startBattle();
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL), "1002011 must not already have Physical");
        bto.setCurrentEnergy(bto.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(bto, List.of(enemy)), "the ultimate must cast at full energy");
        Assertions.assertTrue(enemy.isWeakTo(DamageElement.PHYSICAL), "the clause inserts it");

        int steps = 0;
        while (enemy.isWeakTo(DamageElement.PHYSICAL) && !battle.isOver() && steps < 60) {
            battle.stepForward();      // Note: it only swaps currentMove (measured: it does not execute a turn)
            battle.afterMove();        // ends that action - the timed weakness is decremented right here
            steps++;
        }
        System.out.println("[content] 1315 timed: physical gone after " + steps + " steps (over=" + battle.isOver() + ")");
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL),
                "the content carries turns: 2, so it must expire -- " + steps + " steps and it is still there");
        Assertions.assertTrue(steps > 1, "but not on the first step: the count would be wrong, not just the ticking");
    }
}
