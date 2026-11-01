package com.laosun.aluminium.test.content.characters.aventurine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.ShieldBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1304 砂金's Eidolon 6: 「每有 1 个队友持有护盾，砂金造成的伤害提高 0.5%，最高不超过 1.5%」.
 */
public class AventurineEidolonSixTest {

    private static final int AVENTURINE = 1304;
    private static final int LEVEL = 80;

    @Test
    public void eachShieldedAllyRaisesHisDamage() {
        // His own kit already shields the party, so the expected share is computed from the count the rule reads, not
        // from the shields this judge adds.
        for (int extra : new int[]{0, 1, 3}) {
            double[] result = boostWith(extra);
            double expected = Math.min(result[1] * 0.5, 1.5);
            Assertions.assertEquals(expected, result[0], 1e-9,
                    extra + " extra shields (" + (int) result[1] + " shielded): min(count x 50%, 150%)");
        }
    }

    /** @return {the boost the Eidolon adds, how many allies carried a shield} */
    private static double[] boostWith(int extraShields) {
        double[] without = zone(0, extraShields);
        double[] with = zone(6, extraShields);
        System.out.println("[1304] " + extraShields + " extra shields, " + (int) with[1] + " shielded => +"
                + (with[0] - without[0]));
        return new double[]{with[0] - without[0], with[1]};
    }

    private static double[] zone(int eidolon, int extraShields) {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL, true, null, null, eidolon);
        List<Character> party = new ArrayList<>();
        party.add(aventurine);
        for (int i = 0; i < 3; i++) {
            party.add(CharacterFactory.create(1001 + i, LEVEL));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(party), new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int i = 0; i < extraShields; i++) {
            party.get(i).getBuffManager().addBuff(new ShieldBuff(aventurine, 500, 3));
        }
        long shielded = party.stream()
                .filter(unit -> unit.getBuffManager().allBuffsOf(ShieldBuff.class).stream()
                        .anyMatch(s -> s.getAmount() > 0))
                .count();
        double[] zone = new double[]{-1};
        battle.addDamageObserver((damage, applied, hp, shield, died) ->
                zone[0] = damage.breakdown().getOrDefault("BoostArea", 0.0));
        battle.applyDamage(enemy, new Damage(aventurine, enemy, DamageElement.IMAGINARY, DamageType.NORMAL, 100));
        return new double[]{zone[0], shielded};
    }
}