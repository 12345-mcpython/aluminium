package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The light-cone rule channel, through its first content: 「装备者施放普攻、战技或终结技攻击敌方目标后，分别获取一层【淘气值】」.
 *
 * <p>The clause carries no rank-varying magnitude, which is why it is the first one authored: the rank axis
 * (`EquipmentSkillConfig`'s `SkillID` + `Level`) is the channel's next step, and content that needs it is registered rather
 * than guessed.
 */
public class LightConeChannelTest {
    private static final int WEAPON_ID = 21005;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "淘气值";

    @Test
    public void theWearerGainsACounterOnEachOfTheThreeAttacks() {
        Fixture f = fixture(true);
        Assertions.assertEquals(0, f.character.getBuffManager().stacksOf(COUNTER),
                "precondition: nothing before the attacks");
        f.battle.fireTriggers(TriggerEvent.BASIC_ATTACK, f.character, f.enemy, 1, 0);
        Assertions.assertEquals(1, f.character.getBuffManager().stacksOf(COUNTER), "a basic attack grants one layer");
        f.battle.fireTriggers(TriggerEvent.SKILL_CAST, f.character, f.enemy, 1, 0);
        f.battle.fireTriggers(TriggerEvent.ULT_CAST, f.character, f.enemy, 1, 0);
        Assertions.assertEquals(3, f.character.getBuffManager().stacksOf(COUNTER),
                "and the skill and the ultimate each grant one more");
    }

    @Test
    public void withoutTheLightConeNothingIsGranted() {
        Fixture f = fixture(false);
        f.battle.fireTriggers(TriggerEvent.BASIC_ATTACK, f.character, f.enemy, 1, 0);
        Assertions.assertEquals(0, f.character.getBuffManager().stacksOf(COUNTER),
                "the rules come from the light cone, so an unequipped character has none");
    }

    private static Fixture fixture(boolean equipped) {
        Weapon weapon = equipped ? Weapon.build(WEAPON_ID, LEVEL) : null;
        Character character = CharacterFactory.create(WEARER, LEVEL, true, weapon);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(character), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Fixture(character, enemy, battle);
    }

    private record Fixture(Character character, Enemy enemy, Battle battle) {
    }
}
