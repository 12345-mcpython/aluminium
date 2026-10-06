package com.laosun.aluminium.test.content.lightcones;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** F-7: light cone 23046 gates on the TEAM's skill point cap being 6 or higher. */
public class Cone23046CapGateTest {
    private static final int CONE = 23046;
    private static final int WEARER = 1204;
    private static final int RAISER = 1306;   // Sparkle: her talent raises the cap by 2

    @Test
    public void theAtkBonusAppearsOnlyWhenSomethingRaisedTheCap() {
        double without = wearerAttack(false);
        double with = wearerAttack(true);
        System.out.println("[cone23046_cap] wearer ATK with the base cap = " + without
                + " ; with the cap raised = " + with);
        Assertions.assertEquals(1639.24992, without, 1e-3, "the base cap is 5, below the gate's 6");
        Assertions.assertTrue(with > without,
                "with Sparkle the cap is 7, so the gate fires and the wearer's ATK is higher (measured " + with + ")");
    }

    /**
     * Equips the cone, optionally alongside the cap raiser, and reads the WEARER's ATK.
     *
     * <p>Note: The raiser stands SECOND in the team on purpose. On BATTLE_START that order silently defeated the
     * gate, because those rules run in team order and hers had not run yet; BATTLE_READY exists so that the order
     * no longer matters, and this is the case that proves it.
     */
    private static double wearerAttack(boolean withRaiser) {
        Character wearer = CharacterFactory.create(WEARER, 80, true, Weapon.build(CONE, 80, false, 1));
        List<Character> team = withRaiser
                ? List.of(wearer, CharacterFactory.create(RAISER, 80))
                : List.of(wearer);
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.stream()
                .filter(c -> c.getCid() == WEARER)
                .findFirst()
                .orElseThrow();
        return mine.getAttribute(AttributeType.ATTACK).get();
    }
}
