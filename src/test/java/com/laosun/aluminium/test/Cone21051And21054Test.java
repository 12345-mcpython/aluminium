package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 21051 and 21054 -- both grant their bonus to \u300c\u88c5\u5907\u8005\u4e0e\u5fc6\u7075\u300d, i.e. to the wearer AND its memosprite and to nobody else.
 *
 * <p>\u2b50 The third unit in the party is what makes the reading attributable: `all_allies` would raise it too, and the engine's
 * own note says a panel inherits only the attributes a memosprite's spec NAMES, so the wearer's copy is invisible on the
 * memosprite -- hence two effects, `self` and `summon`.
 */
public class Cone21051And21054Test {
    private static final int CONE_BASIC = 21051;
    private static final int CONE_HEAL = 21054;
    private static final int MASTER = 1413;      // the wearer must have a memosprite spec (see resources/memosprites)
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character ally;
    private Summon memosprite;
    private Enemy enemy;

    private Battle battle(int cone) {
        wearer = cone == 0
                ? CharacterFactory.create(MASTER, LEVEL)
                : CharacterFactory.create(MASTER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        memosprite = battle.summonMemosprite(wearer);
        return battle;
    }

    @Test
    public void theUltimateRaisesBasicAttackDamageForTheWearerAndItsMemospriteOnly() {
        Battle battle = battle(CONE_BASIC);
        double wearerBefore = wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double spriteBefore = memosprite.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double allyBefore = ally.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        double wearerDelta = wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - wearerBefore;
        double spriteDelta = memosprite.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - spriteBefore;
        double allyDelta = ally.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - allyBefore;
        System.out.println("[21051] ult: wearer +" + wearerDelta + " memosprite +" + spriteDelta
                + " third ally +" + allyDelta);
        Assertions.assertEquals(0.2, wearerDelta, 1e-9, "rank 1 states 20% for the wearer");
        Assertions.assertEquals(0.2, spriteDelta, 1e-9, "and for the memosprite");
        Assertions.assertEquals(0.0, allyDelta, 1e-9, "but NOT for a third unit -- that is what rules out all_allies");
    }

    @Test
    public void theMemospriteAttackRaisesHealingForTheWearerAndItsMemospriteOnly() {
        Battle battle = battle(CONE_HEAL);
        double wearerBefore = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double spriteBefore = memosprite.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double allyBefore = ally.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, memosprite, enemy, 1, 0);
        double wearerDelta = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - wearerBefore;
        double spriteDelta = memosprite.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - spriteBefore;
        double allyDelta = ally.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - allyBefore;
        System.out.println("[21054] memosprite attack: wearer +" + wearerDelta + " memosprite +" + spriteDelta
                + " third ally +" + allyDelta);
        Assertions.assertEquals(0.12, wearerDelta, 1e-9, "rank 1 states 12% outgoing healing");
        Assertions.assertEquals(0.12, spriteDelta, 1e-9, "for the memosprite too");
        Assertions.assertEquals(0.0, allyDelta, 1e-9, "and for nobody else");
    }

    @Test
    public void aSummonAttackFromSomeoneElsesMemospriteDoesNothing() {
        Battle battle = battle(CONE_HEAL);
        double before = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        // The wearer's OWN cast, not its memosprite's: the gate is `actor == summon`.
        battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, wearer, enemy, 1, 0);
        System.out.println("[21054] SUMMON_ATTACK with the master as actor: delta="
                + (wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - before));
        Assertions.assertEquals(before, wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get(), 1e-9,
                "only the wearer's own memosprite counts (false case)");
    }

    @Test
    public void withoutTheConesNothingMoves() {
        Battle battle = battle(0);
        double basicBefore = wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double healBefore = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, memosprite, enemy, 1, 0);
        System.out.println("[21051/21054] without the cones: basic +"
                + (wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - basicBefore) + " heal +"
                + (wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - healBefore));
        Assertions.assertEquals(basicBefore, wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get(), 1e-9,
                "no cone, no basic-attack boost");
        Assertions.assertEquals(healBefore, wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get(), 1e-9,
                "no cone, no healing boost");
    }
}
