package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The two named element sources of `ADD_ELEMENTAL_WEAKNESS` (`cacb8b8`):
 * `party_first` (character 1006: "场上我方目标持有属性的弱点", whose skill text names the first slot of the party)
 * and `random_absent` (character 1405: "添加 1 个随机属性弱点，优先添加目标尚未拥有的弱点").
 *
 * <p>Note: these two values are part of a closed set at load time: a name that is neither a legal element nor in `SPECIAL_ELEMENTS` throws at load time,
 * so the "a typo is still loud" line of defence was not opened by this change.
 *
 * <p>Note: the case wears no light cone and swaps no table -- that rule simply hangs on the table of the character under test (`setTriggerTable` only replaces other tables,
 * and there is no other table here).
 */
public class WeaknessElementSourceTest {
    private static final int WEARER = 1006;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    /** Hangs one "on a Skill hit, add a weakness of some source to the target" rule on a character, and returns {battle, wearer, enemy}. */
    private static Object[] scene(String element) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        EffectSpec add = new EffectSpec();
        TriggerSpecs.set(add, "op", "ADD_ELEMENTAL_WEAKNESS");
        TriggerSpecs.set(add, "element", element);
        TriggerSpecs.set(add, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), add);
        TriggerSpecs.set(rule, "id", "probe_element_source");
        TriggerSpecs.set(rule, "when", List.of("actor == self"));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));
        // Note: the second teammate's element differs from the wearer's: otherwise `characters.getFirst()` and `getLast()` are the same unit,
        // and the `party_first` mutation would be invisible (the shape of the case decides whether a mutation can be necessarily red).
        Character other = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, other), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Object[]{battle, wearer, enemy};
    }

    /** The weakness this enemy already had. */
    private static List<DamageElement> before(Enemy enemy) {
        List<DamageElement> had = new ArrayList<>();
        for (DamageElement e : DamageElement.values()) {
            if (enemy.isWeakTo(e)) {
                had.add(e);
            }
        }
        return had;
    }

    private static void fire(Battle battle, Character wearer, Enemy enemy) {
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
    }

    @Test
    public void partyFirstTakesTheElementOfTheFirstPartyMember() {
        Object[] s = scene("party_first");
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        DamageElement expected = wearer.getElement();
        Assertions.assertFalse(enemy.isWeakTo(expected), "the enemy must not already have it, or nothing is inserted");

        fire(battle, wearer, enemy);
        System.out.println("[element] party_first: wearer=" + expected + " inserted=" + enemy.isWeakTo(expected)
                + " (had " + before(enemy).size() + " before)");
        Assertions.assertTrue(enemy.isWeakTo(expected),
                "the first party member's element is what 1006's own skill text names");
    }

    @Test
    public void randomAbsentOnlyPicksAnElementTheTargetLacks() {
        Object[] s = scene("random_absent");
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        List<DamageElement> had = before(enemy);

        fire(battle, wearer, enemy);
        List<DamageElement> now = before(enemy);
        List<DamageElement> added = new ArrayList<>(now);
        added.removeAll(had);
        System.out.println("[element] random_absent: had=" + had.size() + " now=" + now.size()
                + " added=" + added + " (the clause prefers one it did not have)");
        Assertions.assertEquals(1, added.size(), "exactly one element is added");
        Assertions.assertFalse(had.contains(added.getFirst()),
                "and it is one the target did NOT already have -- that is the mutation point");
    }
}
