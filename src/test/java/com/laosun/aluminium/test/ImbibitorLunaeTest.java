package com.laosun.aluminium.test;

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
 * 1213 Dan Heng \u2022 Imbibitor Lunae, from his own file (2026-09-29, rounds 171-172).
 *
 * <p>His talent is the same stackable-modifier shape Argenti's file already ships, and the ultimate's 【\u9006\u9CDE】 is an ADD_STACK resource.
 * Round 171's "ADD_STACK lands nothing" was a bad escape in the TEST (\u9C7E is \u9C7E, not \u9CDE); rounds 172's hand-built table and file-loaded
 * comparison settled it.
 */
public class ImbibitorLunaeTest {
    private static final int DHIL = 1213;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String OUTROAR = "\u4ea2\u5fc3";
    private static final String SQUAMA = "\u9006\u9CDE";

    /** \u26a0 「施放每段攻击后获得1层【亢心】…可叠加6层」: the count reaches the cap and stops there. */
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
                "\u300c\u65bd\u653e\u6bcf\u6bb5\u653b\u51fb\u540e\u83b7\u5f971\u5c42\u3010\u4ea2\u5fc3\u3011\u2026\u8be5\u6548\u679c\u53ef\u4ee5\u53e0\u52a06\u5c42\u300d");
        Assertions.assertTrue(dhil.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > boostBefore,
                "\u300c\u4f7f\u81ea\u8eab\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad810.00%\u300d per stack");

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
