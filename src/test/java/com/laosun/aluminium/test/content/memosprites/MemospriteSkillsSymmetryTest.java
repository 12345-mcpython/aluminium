package com.laosun.aluminium.test.content.memosprites;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Both summon paths install the spec's skills. */
public class MemospriteSkillsSymmetryTest {
    @Test
    public void theMemoSpritePathInstallsTheSpecsSkillsToo() {
        String viaMemosprite = slots(false);
        String viaServant = slots(true);
        System.out.println("[skill_symmetry] via summonMemosprite: " + viaMemosprite + " ; via summonServant: " + viaServant);
        Assertions.assertEquals(viaServant, viaMemosprite, "the two paths must install the same skills");
        Assertions.assertFalse(viaMemosprite.isEmpty(), "and there must be some");
    }

    private static String slots(boolean viaServant) {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var dragon = viaServant
                ? battle.summonServant(battle.characters.get(0))
                : battle.summonMemosprite(battle.characters.get(0));
        battle.processRequests();
        return new java.util.TreeSet<>(dragon.skillsByDataSlot().keySet()).toString();
    }
}
