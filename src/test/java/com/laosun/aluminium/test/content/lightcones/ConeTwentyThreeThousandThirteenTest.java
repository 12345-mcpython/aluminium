package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 时节不居 (23013): 「当装备者对我方目标提供治疗时，<b>记录治疗量</b>」.
 *
 * <p>What landed: the recording half, as one declared resource fed from `HEALED` with `amountFromEvent` - the same spelling
 * 1409's tally uses. What did NOT: the payment half, because 「该伤害不受加成影响」 has no expression (every DAMAGE instance goes
 * through the boost zone), and writing it anyway would report a number that is too high.
 */
public class ConeTwentyThreeThousandThirteenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    @Test
    public void healingIsRecordedAtItsActualAmount() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23013, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        Assertions.assertTrue(wearer.getResources().has("记录的治疗量"), "the cone declares the counter");
        Assertions.assertEquals(0, wearer.getResources().value("记录的治疗量"), "and it starts empty");

        ally.takeDamage(ally.getMaxHp() * 0.5);
        battle.heal(wearer, ally, ally.getMaxHp() * 0.3);
        battle.processRequests();
        int recorded = wearer.getResources().value("记录的治疗量");
        System.out.println("[23013] recorded after one 30%-MaxHP heal: " + recorded);
        Assertions.assertTrue(recorded > 0, "「记录治疗量」 - the heal landed in the counter");

        // The payment half: an ally's attack pays 36% of the record as an UNBOOSTED instance. A huge all-type boost on the
        // wearer must not move it - that is what 「该伤害不受加成影响」 says, and it is the whole point of the `unboosted` flag.
        wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(5.0));
        List<com.laosun.aluminium.damage.Damage> paid = new ArrayList<>();
        battle.addDamageObserver((com.laosun.aluminium.damage.Damage damage, double applied, double hpLost,
                                  double shield, boolean died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.ADDITIONAL) {
                paid.add(damage);
            }
        });
        // A REAL attack: the rider names 「随机 1 个**受到攻击的**敌方目标」, so it reads the attack's hit set.
        battle.castImmediate(ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), ally, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(1, paid.size(), "one rider instance for the attack");
        var rider = paid.getFirst();
        System.out.println("[23013] rider base " + rider.getSkillBaseValue() + " boostZone x"
                + rider.boostArea().getRate() + " on a record of " + recorded);
        Assertions.assertEquals(recorded * 0.36, rider.getSkillBaseValue(), 1e-6,
                "「根据记录治疗量的 36%」 - the base is that share of the record");
        Assertions.assertEquals(1.0, rider.boostArea().getRate(), 1e-9,
                "「该伤害不受加成影响」 - the boost zone is skipped, even with +500% all-type boost on the wearer");
    }
}
