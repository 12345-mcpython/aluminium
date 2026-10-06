package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1209 Yanqing, from his own file: the state that rides on himself, and the two fixtures that pin a 60% chance.
 */
public class YanqingTest {
    private static final int YANQING = 1209;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The Skill applies the state AND its two modifiers, all on himself, all for the document's one turn. */
    @Test
    public void theSkillSyncsAndRaisesBothCritStats() {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(0.0));
        battle.startBattle();
        double critBefore = yanqing.getAttribute(AttributeType.CRIT_CHANCE).get();
        double dmgBefore = yanqing.getAttribute(AttributeType.CRIT_ATTACK).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, yanqing, enemy, 0, 0);

        Assertions.assertTrue(yanqing.getBuffManager().hasState("智剑连心"),
                "「并为彦卿附加【智剑连心】」 (and applies Soulsteel Sync (【智剑连心】) to Yanqing)");
        Assertions.assertEquals(0.2, yanqing.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore, 1e-9,
                "「为自身提高20.00%暴击率」 (raises his own CRIT Rate by 20.00%)");
        Assertions.assertEquals(0.3, yanqing.getAttribute(AttributeType.CRIT_ATTACK).get() - dmgBefore, 1e-9,
                "「和30%暴击伤害」 (and 30% CRIT DMG)");
    }

    /** Note: The Ultimate's conditional half: +50% CRIT DMG ONLY while the state is up. */
    @Test
    public void theUltimateAddsCritDamageOnlyWhileSynced() {
        double synced = ultCritDamageGain(true);
        double unsynced = ultCritDamageGain(false);

        Assertions.assertEquals(0.5, synced, 1e-9,
                "「若彦卿处于【智剑连心】效果，则使其暴击伤害额外提高50%」 (if Yanqing has the Soulsteel Sync (【智剑连心】) effect, his CRIT DMG is raised by an extra 50%)");
        Assertions.assertEquals(0.0, unsynced, 1e-9,
                "without the state the extra 50% must not be granted");
    }

    /** Note: A 60% chance, pinned from both sides by the fixture: 0.0 always fires, 1.0 never does. */
    @Test
    public void theFollowUpChanceIsPinnedByTwoFixtures() {
        double always = followUpLoss(0.0);
        double never = followUpLoss(1.0);

        Assertions.assertTrue(always > 0,
                "「有60%的固定概率发动追加攻击」 (a 60% fixed chance to launch a follow-up attack): a 0.0 roll is below 0.6, so it MUST fire");
        Assertions.assertEquals(0.0, never, 1e-9,
                "a 1.0 roll is not below 0.6, so it must not fire -- which is what makes this a test of the CHANCE and not of the damage");
    }

    /** The Ultimate's CRIT DMG gain, with the state applied first when asked. */
    private static double ultCritDamageGain(boolean synced) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(0.0));
        battle.startBattle();
        if (synced) {
            // The state is applied DIRECTLY, so the Skill's own CRIT modifiers cannot interfere: this clause is about the Ultimate alone.
            yanqing.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("智剑连心", 1, false));
        }
        double before = yanqing.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, yanqing, enemy, 0, 0);
        return yanqing.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    /** The damage the follow-up deals on one attack, under a chosen roll. */
    private static double followUpLoss(double roll) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(roll));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, yanqing, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
