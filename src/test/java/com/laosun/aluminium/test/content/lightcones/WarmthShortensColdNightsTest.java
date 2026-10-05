package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21028: "生命上限提高#1%。施放普攻或战技后，为我方全体恢复等同于各自生命上限#2%的生命值" -- the first `on_any` reader.
 *
 * <p>HEALTH is FLAT and its base value MOVES when the battle starts (the cone's own HEALTH stat is applied), so the share is taken against the post-start base.
 * The party heal is only 2% of max HP, so the wounds are small on purpose: a bigger one would kill the ally and leave the skill path with nobody to heal,
 * which is exactly how the first attempt failed.
 */
public class WarmthShortensColdNightsTest {
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theConeRaisesMaxHpByTheRankShare() {
        Assertions.assertEquals(0.16, share(1), 1e-6, "rank 1 share of the post-start base");
        Assertions.assertEquals(0.32, share(5), 1e-6, "rank 5 share of the post-start base");
    }

    @Test
    public void theConeHealsThePartyOnEitherEvent() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21028, LEVEL, false, 1));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(wearer);
        battle.beforeMove();
        ally.takeDamage(ally.getMaxHp() * 0.3);
        double allyBefore = ally.getCurrentHp();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, wearer, enemy, 0, 0);
        double afterBasic = ally.getCurrentHp();
        Assertions.assertTrue(afterBasic > allyBefore,
                "a basic attack heals the ally: " + allyBefore + " -> " + afterBasic);
        Assertions.assertTrue(ally.getCurrentHp() > 0, "and the ally is still alive");
        // 1% of max HP: the heal is 2%, so this is visible AND cannot kill anyone.
        ally.takeDamage(ally.getMaxHp() * 0.01);
        double before2 = ally.getCurrentHp();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        Assertions.assertTrue(ally.getCurrentHp() > before2,
                "and so does a skill, through on_any: " + before2 + " -> " + ally.getCurrentHp());
    }

    /** The wearer's HEALTH gain at this rank over the base taken AFTER the battle has started. */
    private static double share(int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21028, LEVEL, false, rank));
        Battle battle = new Battle(List.of(wearer), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        double before = wearer.getAttribute(AttributeType.HEALTH).get();
        battle.startBattle();
        double baseAfter = wearer.getAttribute(AttributeType.HEALTH).baseValue();
        return (wearer.getAttribute(AttributeType.HEALTH).get() - before) / baseAfter;
    }
}
