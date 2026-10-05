package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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
 * 1213 Dan Heng - Imbibitor Lunae, from his own file (2026-09-29, rounds 11-12).
 *
 * <p>His talent is the same stackable-modifier shape Argenti's file already ships, and the ultimate's [逆鳞] is an ADD_STACK resource.
 * Round 11's "ADD_STACK lands nothing" was a bad escape in the TEST (鱾 is 鱾, not 鳞, "that character is not this one"); rounds 12's hand-built table and file-loaded
 * comparison settled it.
 */
public class ImbibitorLunaeTest {
    private static final int DHIL = 1213;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String OUTROAR = "亢心";
    private static final String SQUAMA = "逆鳞";

    /** Note: "施放每段攻击后获得1层[亢心]…可叠加6层": the count reaches the cap and stops there. */
    @Test
    public void hisTalentStacksPerHitUpToTheCap() {
        Character dhil = CharacterFactory.create(DHIL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(dhil), List.of(enemy), fixed());
        battle.startBattle();
        double boostBefore = dhil.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        for (int i = 0; i < 6; i++) {
            battle.castImmediate(dhil.getSkills().get(SkillType.COMMON), dhil, List.of(enemy));
        }
        Assertions.assertEquals(6, dhil.getBuffManager().stacksOf(OUTROAR),
                "「施放每段攻击后获得1层【亢心】…该效果可以叠加6层」");
        Assertions.assertTrue(dhil.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > boostBefore,
                "「使自身造成的伤害提高10.00%」 per stack");

        battle.castImmediate(dhil.getSkills().get(SkillType.COMMON), dhil, List.of(enemy));
        Assertions.assertEquals(6, dhil.getBuffManager().stacksOf(OUTROAR),
                "the seventh hit must not push past the cap");
    }

    /** Census: the talent, the ultimate's resource and the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(DHIL);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "the per-hit stack");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.ULT_CAST),
                "the Squama grant is registered: `amount` on ADD_STACK is not a layer count today");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
