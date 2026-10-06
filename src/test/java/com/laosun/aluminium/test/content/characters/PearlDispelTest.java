package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.enums.TriggerEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): her trait dispels a debuff on the Skill and on an enhanced basic. */
public class PearlDispelTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;

    @Test
    public void theEnhancedBasicDispelsADebuffAndLeavesANamedState() {
        Battle battle = battle();
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        ally.getBuffManager().addBuff(new DotBuff(ally, DamageElement.FIRE, 100, 3));
        ally.getBuffManager().addBuff(new StateBuff("_probe_state", 3));

        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, pearl, battle.enemies.getFirst(), 1, 0,
                (SkillCategory) null, 8);

        boolean dot = ally.getBuffManager().hasBuff(DotBuff.class);
        boolean state = ally.getBuffManager().hasBuff(StateBuff.class);
        System.out.println("[pearl_dispel] after the enhanced basic -- DOT still there? " + dot
                + " ; named state still there? " + state);
        Assertions.assertFalse(dot, "the burn is a negative effect, so dispelling takes it");
        Assertions.assertTrue(state, "and a named state stays: it is not classified as negative");
    }

    @Test
    public void anOrdinaryBasicLeavesTheDebuffInPlace() {
        Battle battle = battle();
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        ally.getBuffManager().addBuff(new DotBuff(ally, DamageElement.FIRE, 100, 3));

        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, pearl, battle.enemies.getFirst(), 1, 0,
                (SkillCategory) null, 1);

        Assertions.assertTrue(ally.getBuffManager().hasBuff(DotBuff.class),
                "slot 1 is not the enhanced basic, so no rule of hers dispels");
    }

    private static Battle battle() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
