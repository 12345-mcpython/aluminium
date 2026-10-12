package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): the enhanced basic's extra Elation damage, computed from 【美学底本】's values.
 *
 * <p>The corpus sentence: 「若【美学底本】为「欢愉」命途，则强化为【行笔，幻造星月】，且攻击后，额外造成 #2[i]% 的冰属性
 * 欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算」. #2 is 0.6 at Lv10 (0.3 at Lv1, 0.75 at max), and the file's level
 * convention already pins her slots at Lv10.
 *
 * <p>⚠⚠ <b>WHICH values are borrowed was corrected by the author, and this judge moved with it.</b> The Elation formula is
 * {@code 欢愉基础值(等级) × (1+欢愉度) × (1+增笑) × [1 + 5×好活/(好活+240)] × 暴击区 × 易伤区 × 减伤区 × 抗性区 × 防御区 × 倍率区}, and the
 * UNDERLINED terms 「各自吃各自的」 while the un-underlined ones - the base (the level table's row), 欢愉度 and 增笑 - come from
 * the archetype. There is <b>no attack power</b> in that formula at all: the first version of this content read the
 * sentence as "【美学底本】's ATTACK" and wrote {@code holder_attr:美学底本:ATTACK}, which this case then "pinned" - so the case
 * itself had to be flipped, not just the content.
 *
 * <p>What this judge pins, and why each part is needed:
 * <ul>
 *   <li>the extra instance EXISTS on the slot-8 form (the whole sentence);</li>
 *   <li>its size follows the ARCHETYPE's <b>欢愉度</b>, not Pearl's - the un-underlined term the sentence is about;</li>
 *   <li>and the ARCHETYPE's ATTACK does NOT move it (the control the correction needs: the formula has no such term, and
 *       the wrong reading used to move this number);</li>
 *   <li>the slot-9 form (the non-Elation archetype) pays NOTHING - the sentence's own condition;</li>
 *   <li>and the clause does not recurse: it produces a damage instance, which re-enters DEALING_DAMAGE, so a gate
 *       that fails to exclude its own product would trip the engine's recursion guard rather than pass quietly.</li>
 * </ul>
 */
public class PearlFormAExtraDamageTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;   // Elation path -> the archetype qualifies -> slot 8
    private static final int HUNTER = 1204;         // The Hunt -> slot 9, which carries no extra damage

    /**
     * The extra damage is real, and it is the ARCHETYPE's 欢愉度 that sizes it.
     */
    @Test
    public void theExtraInstanceFollowsTheArchetypesElationDegree() {
        double withBoost = damageOnSlot8(ELATION_ALLY, 1.0, 2000);
        double without = damageOnSlot8(ELATION_ALLY, 0.0, 2000);
        System.out.println("[pearl_formA] slot-8 damage with the archetype's 欢愉度 at +100% = " + withBoost
                + " ; at +0% = " + without);
        Assertions.assertTrue(without > 0, "the slot-8 enhanced basic deals damage at all");
        Assertions.assertTrue(withBoost > without,
                "the extra instance reads the ARCHETYPE's 欢愉度 (「使用【美学底本】的属性值计算」): " + withBoost
                        + " vs " + without);
    }

    /**
     * The control the correction needs: the archetype's ATTACK must NOT move it.
     *
     * <p>⚠ This used to be the POSITIVE case - the content read {@code holder_attr:美学底本:ATTACK}, and a case asserting
     * "it grows with the archetype's Attack" passed while being wrong, because the Elation formula has no attack power in
     * it. Kept as the negative control so the wrong reading cannot come back unnoticed.
     */
    @Test
    public void theArchetypesAttackDoesNotMoveIt() {
        double strong = damageOnSlot8(ELATION_ALLY, 0.0, 3000);
        double weak = damageOnSlot8(ELATION_ALLY, 0.0, 1000);
        System.out.println("[pearl_formA] slot-8 damage with the archetype's Attack at 3000 = " + strong
                + " ; at 1000 = " + weak);
        Assertions.assertEquals(weak, strong, 1e-6,
                "欢愉伤害 has no attack-power term, so the archetype's Attack cannot move this instance");
    }

    /**
     * The slot-9 form - the archetype is NOT on the Elation path - pays no extra damage, and nothing about the archetype
     * moves the number.
     *
     * <p>This is the control that makes the cases above mean something: if the clause were firing on both forms, this
     * reading would move too.
     */
    @Test
    public void theNonElationArchetypePaysNoExtraDamage() {
        double boosted = damageOnSlot8(HUNTER, 1.0, 3000);
        double plain = damageOnSlot8(HUNTER, 0.0, 1000);
        System.out.println("[pearl_formA] slot-9 (non-Elation archetype): 欢愉度+100%/Attack 3000 = " + boosted
                + " ; +0%/Attack 1000 = " + plain);
        Assertions.assertEquals(plain, boosted, 1e-6,
                "「若【美学底本】为「欢愉」命途」: the other form's sentence has no extra damage, so neither the "
                        + "archetype's 欢愉度 nor its Attack can move the number");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The damage the enhanced basic deals, for an archetype aim with the given 欢愉度 boost and Attack.
     *
     * <p>Note: This does NOT compare against a run with the clause removed - a table cannot drop one of its own rules
     * through any public accessor, and the comparison is not needed: the slot-9 case is the negative control, and the
     * cases that matter here are the RELATIONSHIPS between runs of the SAME form. Holding the form (slot 8), the
     * attacker, the enemy and the levels fixed and moving one archetype attribute isolates exactly what the text says
     * the magnitude is computed from.
     */
    private static double damageOnSlot8(int archetypeCid, double archetypeElationBoost, double archetypeAttack) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(archetypeCid, 80);
        archetype.setAttribute(AttributeType.ATTACK, new DoubleValue(archetypeAttack));
        archetype.setAttribute(AttributeType.ELATION_DAMAGE_BOOST, new DoubleValue(archetypeElationBoost));
        List<Character> team = new ArrayList<>(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(team, List.of(enemy), BattleFixture.seeded());
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
