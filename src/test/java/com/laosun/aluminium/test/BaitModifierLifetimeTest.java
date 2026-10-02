package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1504 不死途's 「【饲饵】存在时，敌方全体目标防御力降低 40%」, judged on a HAND-BUILT table.
 *
 * <p>⚠ The table carries only the one rule under test. 1504's own file also has a talent that fires on
 * {@code ALLY_ATTACK} and pays out a follow-up -- and <b>any</b> probe attack is an {@code ALLY_ATTACK}, so a probe run
 * against her real file measures the talent plus the aura (the first draft of this judge read 4.49x and blamed the
 * aura). With the talent out of the picture, the cut is the only thing between the two numbers.
 *
 * <p>⚠ The cut targets {@code all_enemies} and carries the state's name. So when the bait moves, the old enemy keeps
 * the cut -- because the NEW bait is out, and the sentence is 「【饲饵】存在时，敌方全体…」, i.e. it holds while a bait
 * exists at all, not only on the bait itself. What moves is the STATE; the cut is re-laid over the whole camp each cast.
 * That is why the second assertion below expects the cut to SURVIVE the move, not to disappear.
 */
public class BaitModifierLifetimeTest {
    private static final int BAIT = 1504;
    private static final int PROBE = 1002;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    /** The one rule: strip the state, lay the cut over the whole enemy camp, then mark the new bait. */
    private static void install(Character bait) {
        EffectSpec strip = new EffectSpec();
        TriggerSpecs.set(strip, "op", "REMOVE_STATE");
        TriggerSpecs.set(strip, "buff", "\u9972\u997c_state");      // ⚠ read below, not typed twice
        EffectSpec cut = new EffectSpec();
        TriggerSpecs.set(cut, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(cut, "percent", 0.4);
        TriggerSpecs.set(cut, "permanent", true);
        TriggerSpecs.set(cut, "target", "all_enemies");
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", "\u9972\u997c_state");
        TriggerSpecs.set(mark, "permanent", true);
        TriggerSpecs.set(mark, "target", "target");
        bait.setTriggerTable(new TriggerTable(BAIT, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), strip, cut, mark))));
    }

    /** @param baits 0 = no bait anywhere, 1 = the first enemy, 2 = then the second as well */
    private static double damageToFirstAfter(int baits) {
        Character bait = CharacterFactory.create(BAIT, LEVEL);
        install(bait);
        Character ally = CharacterFactory.create(PROBE, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(bait, ally), List.of(first, second), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;                       // ⚠ never crits: the subject is the cut, not the roll
            }
        });
        battle.startBattle();
        if (baits >= 1) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, bait, first, 0, 0);
        }
        if (baits >= 2) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, bait, second, 0, 0);
        }
        double before = first.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), ally, List.of(first));
        return before - first.getCurrentHp();
    }

    @Test
    public void theCutIsUpWhileABaitExistsAndFollowsTheMove() {
        double none = damageToFirstAfter(0);
        double onBait = damageToFirstAfter(1);
        double moved = damageToFirstAfter(2);
        System.out.println("[bait] probe damage to the first enemy -- no bait=" + none
                + "  cut up=" + onBait + " (" + (onBait / none) + "x)"
                + "  bait moved to the other one=" + moved + " (" + (moved / none) + "x)");

        Assertions.assertTrue(none > 0, "the probe must land at all (" + none + ")");
        Assertions.assertEquals(1.4, onBait / none, 0.06,
                "while a bait exists, the cut raises the damage the first enemy takes");
        Assertions.assertEquals(1.4, moved / none, 0.06,
                "⚠ and it STAYS up after the bait moves: the sentence is 「【饲饵】存在时，敌方全体…」, and the cut is "
                        + "re-laid over the whole camp on every cast -- what the state's own name buys is that the OLD "
                        + "cut goes off first, so they never stack");
    }
}
