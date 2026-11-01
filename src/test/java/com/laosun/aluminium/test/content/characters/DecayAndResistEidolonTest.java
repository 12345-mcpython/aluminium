package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Two Eidolons that change something the engine already models: 1009's slower charge decay, and 1006's effect-resistance
 * drop when a wave enters.
 */
public class DecayAndResistEidolonTest {

    private static final int LEVEL = 80;

    @Test
    public void eidolonSixSlowsAstaChargeDecay() {
        int without = chargeAfterTwoTurns(0);
        int with = chargeAfterTwoTurns(6);
        System.out.println("[1009] charge after two turns: " + without + " vs " + with);
        Assertions.assertEquals(3, without, "control: the second turn takes three layers");
        Assertions.assertEquals(4, with, "「每回合减少的蓄能层数降低 1」 => two layers");

    }

    /**
     * 1006's Eidolon 2 in its <b>enhanced</b> form: the documents give a 强化前 (「效果抵抗降低 20%」) and a 强化后
     * (「使其受到的伤害提高 20%」), and the live game applies the enhanced one - so that is what her file states.
     */
    @Test
    public void eidolonTwoRaisesTheDamageTheEnemyTakesOnEntry() {
        Assertions.assertEquals(0.0, resDrop(0), 1e-9, "control: nothing without the Eidolon");
        Assertions.assertEquals(0.0, resDrop(2), 1e-9, "强化前's resistance drop is superseded, not stacked");
        double with = damageTaken(2);
        double without = damageTaken(0);
        System.out.println("[1006] Eidolon 2: " + without + " -> " + with);
        Assertions.assertEquals(1.2, with / without, 1e-9, "「强化后：使其受到的伤害提高 20%」");
    }

    /** One fixed hit against a monster, in a battle built with the given Eidolon rank. */
    private static double damageTaken(int eidolon) {
        Character silverWolf = CharacterFactory.create(1006, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(silverWolf, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(
                silverWolf, enemy, com.laosun.aluminium.enums.DamageElement.QUANTUM,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    /**
     * @return the charge layers left after two of her turn starts, starting from six
     * <p>Note: {@code stacksOf} counts BUFF INSTANCES, so the layers are added as six one-layer buffs (exactly what the
     * shipped {@code AstaChargeDecayTest} does) - one buff with twelve stacks would read as "1 layer".
     */
    private static int chargeAfterTwoTurns(int eidolon) {
        Character asta = CharacterFactory.create(1009, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int i = 0; i < 6; i++) {
            asta.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("蓄能", 1, true, 99));
        }
        for (int i = 0; i < 2; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, asta, asta, 0, 0));
        }
        return asta.getBuffManager().stacksOf("蓄能");
    }

    /** @return how the enemy's effect resistance moved across a wave start */
    private static double resDrop(int eidolon) {
        Character wolf = CharacterFactory.create(1006, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wolf, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.WAVE_START, wolf, enemy, 1, 0));
        return enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - before;
    }
}