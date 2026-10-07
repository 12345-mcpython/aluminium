package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): the enhanced basic's extra Elation damage, computed from the ARCHETYPE's Attack.
 *
 * <p>The corpus sentence: 「若【美学底本】为「欢愉」命途，则强化为【行笔，幻造星月】，且攻击后，额外造成 #2[i]% 的冰属性
 * 欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算」. #2 is 0.6 at Lv10 (0.3 at Lv1, 0.75 at max), and the file's level
 * convention already pins her slots at Lv10.
 *
 * <p>What this judge pins, and why each part is needed:
 * <ul>
 *   <li>the extra instance EXISTS on the slot-8 form (the whole sentence);</li>
 *   <li>its size follows the ARCHETYPE's Attack, not Pearl's - the point of `holder_attr:` and the one thing the
 *       text states that no other spelling can read. Measured as a ratio between two runs whose only difference is
 *       that Attack;</li>
 *   <li>the slot-9 form (the non-Elation archetype) pays NOTHING - the sentence's own condition;</li>
 *   <li>and the clause does not recurse: it produces a damage instance, which re-enters DEALING_DAMAGE, so a gate
 *       that fails to exclude its own product would trip the engine's recursion guard rather than pass quietly.</li>
 * </ul>
 */
public class PearlFormAExtraDamageTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;   // Elation path -> the archetype qualifies -> slot 8
    private static final int HUNTER = 1204;         // The Hunt -> slot 9, which carries no extra damage
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    /** The extra damage is real, and it is the ARCHETYPE's Attack that sizes it. */
    @Test
    public void theExtraInstanceFollowsTheArchetypesAttack() {
        double withStrongArchetype = damageOnSlot8(ELATION_ALLY, 3000);
        double withWeakArchetype = damageOnSlot8(ELATION_ALLY, 1000);
        System.out.println("[pearl_formA] slot-8 damage with the archetype's Attack at 3000 = "
                + withStrongArchetype + " ; at 1000 = " + withWeakArchetype
                + " ; ratio = " + (withStrongArchetype / withWeakArchetype));

        Assertions.assertTrue(withStrongArchetype > 0, "the slot-8 enhanced basic deals damage at all");
        Assertions.assertTrue(withStrongArchetype > withWeakArchetype,
                "the damage grows with the ARCHETYPE's Attack (" + withStrongArchetype + " vs "
                        + withWeakArchetype + ") - which is the sentence's 使用【美学底本】的属性值计算");
        // Note: The ratio is NOT 3.0, and that is the zones rather than a partial read: the total is the skill's own
        // instance plus the extra one, and the two sit in different factors (the skill's row times HER Defence, the
        // extra times the ARCHETYPE's Attack). A ratio assertion would therefore be asserting the enemy's defence and
        // her Defence at once; the direction and the control below are what this case can honestly pin.
        Assertions.assertTrue(withStrongArchetype / withWeakArchetype > 1.5,
                "and it grows by much more than a rounding step (" + (withStrongArchetype / withWeakArchetype) + ")");
    }

    /**
     * The slot-9 form - the archetype is NOT on the Elation path - pays no extra damage, and its Attack cannot move
     * the number at all.
     *
     * <p>This is the control that makes the case above mean something: if the clause were firing on both forms, or
     * reading something other than the archetype's Attack, this reading would move too.
     */
    @Test
    public void theNonElationArchetypePaysNoExtraDamage() {
        double strong = damageOnSlot8(HUNTER, 3000);
        double weak = damageOnSlot8(HUNTER, 1000);
        System.out.println("[pearl_formA] slot-9 (non-Elation archetype) with Attack 3000 = " + strong
                + " ; with 1000 = " + weak);
        Assertions.assertEquals(weak, strong, 1e-6,
                "「若【美学底本】为「欢愉」命途」: the other form's sentence has no extra damage, so its Attack"
                        + " cannot move the number");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The damage the enhanced basic deals, for an archetype aim with the given Attack.
     *
     * <p>Note: This does NOT compare against a run with the clause removed - a table cannot drop one of its own rules
     * through any public accessor, and the comparison is not needed: the slot-9 case below is the negative control,
     * and the case that matters here is the RELATIONSHIP between two runs of the SAME form. Holding the form (slot 8),
     * the attacker, the enemy and the levels fixed and moving only the archetype's Attack isolates exactly what the
     * text says the magnitude is computed from.
     */
    private static double damageOnSlot8(int archetypeCid, double archetypeAttack) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(archetypeCid, 80);
        archetype.setAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK,
                new com.laosun.aluminium.models.DoubleValue(archetypeAttack));
        List<Character> team = new ArrayList<>(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        // The Ultimate marks the aim and puts her into deep study, which is what swaps COMMON to slot 8.
        battle.fireTriggers(TriggerEvent.ULT_CAST, her, archetype, 0, 0);
        battle.processRequests();

        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }
}
