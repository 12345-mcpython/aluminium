package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): her TECHNIQUE's half that nobody had written - 美学底本, and 深度学习 on its holder.
 *
 * <p>「使用秘技后获得【美学底本】，切换行动角色时【美学底本】转移至当前行动角色。下一次战斗开始时，真珠获得 20 点【好活当赏】，
 * 并【深度学习】持有【美学底本】的角色，该【深度学习】具有 2 点充能。」
 *
 * <p>⚠ Written because only the 20 points shipped. The technique's headline effect - her 深度学习 landing on an ally
 * before the first turn - never happened, and the 美学底本 the technique grants did not exist either. Nothing failed,
 * which is what made it invisible: the rule that was there did exactly what it said.
 *
 * <p>「切换行动角色」 happens on the map, so the caller states who holds the mark (the same shape the technique itself
 * already used). These cases are the two halves and the control: the mark exists, 深度学习 lands on its HOLDER rather
 * than on Pearl or on everybody, and a battle with no mark at all is not an error.
 */
public class PearlTechniqueArchetypeTest {
    private static final int PEARL = 1503;
    private static final int ARCHETYPE = 1502;
    private static final int OTHER = 1204;
    private static final String MARK = "美学底本";
    private static final String DEEP = "深度学习";
    private static final String COUNTER = "好活当赏";

    /**
     * The technique marks the archetype and deep-studies THAT unit, and pays Pearl her 20.
     */
    @Test
    public void theTechniqueMarksTheArchetypeAndDeepStudiesIt() {
        Battle battle = newBattle(true, true);
        Character pearl = battle.characters.get(0);
        Character archetype = battle.characters.get(1);
        Character other = battle.characters.get(2);
        System.out.println("[pearl_technique] the archetype holds " + MARK + "="
                + archetype.getBuffManager().hasState(MARK) + " " + DEEP + "="
                + archetype.getBuffManager().hasState(DEEP)
                + " ; another ally holds " + DEEP + "=" + other.getBuffManager().hasState(DEEP)
                + " ; Pearl holds " + DEEP + "=" + pearl.getBuffManager().hasState(DEEP)
                + " ; her counter " + pearl.getResources().value(COUNTER));

        Assertions.assertTrue(archetype.getBuffManager().hasState(MARK),
                "「使用秘技后获得【美学底本】」: the mark is on the unit the caller named");
        Assertions.assertTrue(archetype.getBuffManager().hasState(DEEP),
                "「并【深度学习】持有【美学底本】的角色」: 深度学习 lands on the HOLDER");
        Assertions.assertFalse(other.getBuffManager().hasState(DEEP),
                "and not on the rest of the party - the clause names one unit");
        Assertions.assertFalse(pearl.getBuffManager().hasState(DEEP),
                "「真珠只能【深度学习】除自身以外的角色」");
        Assertions.assertEquals(20, pearl.getResources().value(COUNTER), "and she gets her 20 points");
    }

    /**
     * Without the technique, the technique's own rule does nothing: no 深度学习 and no counter.
     *
     * <p>Note: The mark itself IS still there, because the caller named a holder and the map's answer is what the engine
     * applies - see {@link #theCallersAnswerIsTheArchetype()}. This case is about the RULE's gate (the 秘技 marker), not
     * about the mark, which is why it asserts the two things the rule produces and not the state the caller set.
     */
    @Test
    public void withoutTheTechniqueTheRuleProducesNothing() {
        Battle battle = newBattle(false, true);
        Character pearl = battle.characters.get(0);
        Character archetype = battle.characters.get(1);
        System.out.println("[pearl_technique] with no technique: deep="
                + archetype.getBuffManager().hasState(DEEP) + " counter="
                + pearl.getResources().value(COUNTER));
        Assertions.assertFalse(archetype.getBuffManager().hasState(DEEP),
                "「并【深度学习】持有【美学底本】的角色」 is gated on the 秘技 marker, which is not there");
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), "and no counter either");
    }

    /**
     * The MAP decides who holds the mark, so naming a holder is enough - and the engine applies it.
     *
     * <p>Note: This case replaces one that asserted the opposite, and the change is the point of it. The first version
     * required that a technique had been used as well, which was my own invention: 「切换行动角色」 is a map action and
     * the map is pre-battle configuration, so by the time combat starts exactly one ally holds the mark and the caller
     * has already accounted for the technique. An extra condition here could only ever DISAGREE with the caller.
     */
    @Test
    public void theCallersAnswerIsTheArchetype() {
        Battle battle = newBattle(false, true);
        Character archetype = battle.characters.get(1);
        System.out.println("[pearl_technique] a named holder with no technique state: mark="
                + archetype.getBuffManager().hasState(MARK) + " deep="
                + archetype.getBuffManager().hasState(DEEP));
        Assertions.assertTrue(archetype.getBuffManager().hasState(MARK),
                "the unit the caller named holds 【美学底本】 - that IS the map's answer");
        Assertions.assertFalse(archetype.getBuffManager().hasState(DEEP),
                "while 深度学习 still needs the technique rule, whose gate is the 秘技 marker");
    }

    /**
     * Nobody named means no archetype, and that is not an error.
     */
    @Test
    public void nobodyNamedMeansNoArchetype() {
        Battle battle = newBattle(true, false);
        Character pearl = battle.characters.get(0);
        System.out.println("[pearl_technique] no archetype named: counter=" + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(20, pearl.getResources().value(COUNTER),
                "the counter half is about Pearl and happens anyway");
        for (Character ally : battle.characters) {
            Assertions.assertFalse(ally.getBuffManager().hasState(MARK),
                    "and nobody was marked, because the caller named nobody");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Battle newBattle(boolean techniqueUsed, boolean archetypeNamed) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(ARCHETYPE, 80);
        Battle battle = new Battle(List.of(pearl, archetype, CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (techniqueUsed) {
            battle.markTechniqueUsed(pearl);
        }
        if (archetypeNamed) {
            battle.markArchetypeHolder(archetype);
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
