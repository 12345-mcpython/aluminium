package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 致长夜的星光 (23049): 「装备者的忆灵施放技能时，使装备者获得【夜色】。装备者持有【夜色】时，我方全体忆灵造成的伤害无视目标
 * #2% 的防御力，装备者和装备者忆灵造成的伤害提高 #3%，装备者的忆灵消失时为装备者恢复 #4 点能量」.
 *
 * <p><b>What it needed.</b> {@code CAST_SETUP} fires for every cast including a memosprite's non-damaging skill (those
 * raise none of SKILL_CAST / ULT_CAST / BASIC_ATTACK), so 「忆灵施放技能时」 is that event gated on {@code actor == summon}.
 * The two damage clauses are instance modifiers read at settlement; the departure clause is {@code SUMMON_LEFT} gated on
 * the leaving unit being a summon of ours.
 */
public class ConeTwentyThreeThousandFortyNineTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1409;   // memory path: has a memosprite
    private static final int ALLY = 1001;
    private static final int CONE = 23049;

    /**
     * The memosprite casting a skill gives the WEARER [夜色]; nothing else does.
     */
    @Test
    public void herMemospriteCastingGrantsNightfall() {
        Fixture f = new Fixture();
        f.spriteCasts();
        Assertions.assertTrue(f.wearer.getBuffManager().hasState("夜色"), "「忆灵施放技能时，装备者获得【夜色】」");
    }

    /**
     * [夜色] gives every memosprite of ours 20% defence ignore on the instance it settles.
     */
    @Test
    public void nightfallGivesOurMemospriteDefenceIgnore() {
        Assertions.assertEquals(0.2, defenceIgnoreFromMemosprite(true), 1e-9,
                "S1 #2 = 20%, on the instance");
        Assertions.assertEquals(0.0, defenceIgnoreFromMemosprite(false), 1e-9,
                "without [夜色] the clause does not fire");
    }

    /**
     * [夜色] raises the damage of the wearer AND of her memosprite by 30% (S1 #3).
     */
    @Test
    public void nightfallRaisesBothWearerAndMemospriteDamage() {
        double wearerWith = boost(true, false);
        double wearerWithout = boost(false, false);
        double spriteWith = boost(true, true);
        double spriteWithout = boost(false, true);

        Assertions.assertEquals(0.3, wearerWith - wearerWithout, 1e-9, "the wearer's own damage +30%");
        Assertions.assertEquals(0.3, spriteWith - spriteWithout, 1e-9, "her memosprite's damage +30%");
    }

    /**
     * When her memosprite leaves the field, the wearer gets 8 energy (S1 #4).
     */
    @Test
    public void herMemospriteLeavingRestoresEnergy() {
        Fixture f = new Fixture();
        f.wearer.setCurrentEnergy(0);
        f.summon.takeDamage(f.summon.getCurrentHp());
        f.battle.processRequests();
        Assertions.assertTrue(f.summon.isDeath(), "the fixture's memosprite left");
        Assertions.assertEquals(8.0, f.wearer.getCurrentEnergy(), 1e-9,
                "「忆灵消失时为装备者恢复 #4 点能量」");
    }

    // ==================================================================

    private static double defenceIgnoreFromMemosprite(boolean nightfall) {
        Fixture f = new Fixture();
        if (nightfall) {
            f.spriteCasts();
        }
        Damage damage = instance(f, f.summon);
        f.battle.applyDamage(f.enemy, damage);
        return damage.getDefenceIgnore();
    }

    private static double boost(boolean nightfall, boolean fromSprite) {
        Fixture f = new Fixture();
        if (nightfall) {
            f.spriteCasts();
        }
        Damage damage = instance(f, fromSprite ? f.summon : f.wearer);
        f.battle.applyDamage(f.enemy, damage);
        return damage.boostArea().getRate() - 1.0;
    }

    private static Damage instance(Fixture f, CanHit attacker) {
        return new Damage(attacker, f.enemy, DamageElement.FIRE, DamageType.NORMAL, 1000);
    }

    private static final class Fixture {
        private final Character wearer;
        private final Summon summon;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            Summon found = battle.memospriteOf(wearer);
            if (found == null) {
                found = battle.summonMemosprite(wearer);
            }
            summon = found;
            Assertions.assertNotNull(summon, "1409's memosprite must exist for this fixture");
        }

        /** The memosprite casts a skill: the event CAST_SETUP with the memosprite as the actor. */
        private void spriteCasts() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.CAST_SETUP, summon, summon, 0, 0));
        }
    }
}

