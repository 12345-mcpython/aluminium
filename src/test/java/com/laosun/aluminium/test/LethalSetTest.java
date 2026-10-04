package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「在一次行动中受到致命攻击的<b>全体</b>」 -- one set, not one save per blow (2026-10-02).
 *
 * <p>Reader: 1407 月茇之庇. Its first half was already in the engine (`BuffManager.defersDeath()`: a state may hold the death instead of
 * committing it), and what was missing was this: the SAME action can land a lethal blow on several allies and the effect has to reach all
 * of them.
 *
 * <p>⭐ The action boundary is not new either: `TURN_START` (Battle:1031) and `TURN_END` (Battle:1301) already bracket `performAction`
 * (1245) and its settlement (1276), so the set is cleared at the boundary that exists.
 *
 * <p>⭐ The rule is built in the test -- `TriggerSpecs.set` / `TriggerSpecs.rule`, the factory `CastSkillTest` uses -- because what is being
 * judged is the SELECTOR, not 1407's sentence. Each lethal blow raises the speed of every ally in the set by 100, so the attribute says
 * exactly who was in it.
 */
public class LethalSetTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1217;
    private static final int FIRST = 1002;
    private static final int SECOND = 1003;
    private static final int THIRD = 1004;
    private static final int MONSTER = 1002011;

    /** One blow reaches one ally; a second blow in the SAME action reaches both; a new action starts over. */
    @Test
    public void theSetGrowsWithinAnActionAndResetsAtItsBoundary() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character first = CharacterFactory.create(FIRST, LEVEL);
        Character second = CharacterFactory.create(SECOND, LEVEL);
        Character third = CharacterFactory.create(THIRD, LEVEL);

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "all_allies_lethally_hit_this_action");
        TriggerSpec rule = TriggerSpecs.rule("LETHAL_DAMAGE", List.of("actor is_ally"), effect);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));

        Battle battle = new Battle(List.of(owner, first, second, third),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double firstBase = speed(first);
        double secondBase = speed(second);
        double thirdBase = speed(third);

        blow(battle, first);
        System.out.println("[lethal] first blow: first +" + (speed(first) - firstBase)
                + " second +" + (speed(second) - secondBase) + " third +" + (speed(third) - thirdBase));
        Assertions.assertTrue(speed(first) > firstBase, "the ally the blow landed on is in the set");
        Assertions.assertEquals(secondBase, speed(second), 1e-6, "an untouched ally is not");

        blow(battle, second);
        System.out.println("[lethal] second blow, same action: first +" + (speed(first) - firstBase)
                + " second +" + (speed(second) - secondBase));
        Assertions.assertTrue(speed(second) > secondBase, "the second victim joins the set");
        Assertions.assertTrue(speed(first) > firstBase, "\u300c\u5168\u4f53\u300d-- the first is still reached by the effect");

        battle.afterMove();
        battle.processRequests();
        double secondAfterBoundary = speed(second);
        blow(battle, third);
        System.out.println("[lethal] after a new action began: second +" + (speed(second) - secondAfterBoundary)
                + " third +" + (speed(third) - thirdBase));
        Assertions.assertTrue(speed(third) > thirdBase, "the new action's victim is in the set");
        Assertions.assertEquals(secondAfterBoundary, speed(second), 1e-6,
                "\u300c\u5728**\u4e00\u6b21\u884c\u52a8**\u4e2d\u300d-- a blow from the previous action is not");
    }

    private static void blow(Battle battle, Character victim) {
        battle.applyTrueDamage(battle.enemies.getFirst(), victim, DamageElement.ICE, victim.getCurrentHp() * 2.0);
        battle.processRequests();
    }

    private static double speed(Character who) {
        return who.getAttribute(AttributeType.SPEED).get();
    }
}
