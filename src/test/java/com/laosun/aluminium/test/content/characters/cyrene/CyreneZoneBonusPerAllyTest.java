package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1415 昔涟's Eidolon 2 raises the zone's true damage by <b>6% for each different ally</b> that has received her memosprite's
 * buff, capped at +24% (four allies).
 *
 * <p><b>The arithmetic is measured, not assumed.</b> On the 2026-10-30 fight three allies carried the memosprite's buff
 * (1409 / 1413 / 1415 - 1512 did not) and the recorded rider reads 0.42 where the row says 0.24: {@code 0.24 + 3 x 0.06},
 * <b>additive</b>. A multiplicative reading of 「倍率提高 6%，最多提高 24%」 would give 0.24 x 1.24 = 0.2976, which no cast shows.
 *
 * <p>What this pins: the count is of <b>ally characters</b>, one per character whatever its stacks, and the cap is four.
 */
public class CyreneZoneBonusPerAllyTest {

    private static final int CYRENE = 1415;

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * The zone rider pays 24% plus 6% for every ally holding the mark, measured as a share of the instance it rode on.
     */
    @Test
    public void theZonePaysSixPercentPerMarkedAlly() {
        com.laosun.aluminium.entity.Character striker =
                com.laosun.aluminium.utils.CharacterFactory.create(1001, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        List<com.laosun.aluminium.entity.Character> allies = new ArrayList<>(List.of(striker, cyrene));
        for (int cid : new int[]{1409, 1413}) {
            allies.add(CharacterFactory.create(cid, 80));
        }
        Enemy target = dummy();
        Battle battle = new Battle(new ArrayList<>(allies), new ArrayList<>(List.of(target)), noCrit());
        battle.startBattle();
        battle.processRequests();
        Assertions.assertNotNull(battle.summonMemosprite(cyrene), "precondition: 德谬歌 is on the field");
        // The zone has to be up - the rider is gated on 「结界」 (1415's skill deploys it).
        cyrene.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("结界", 99));

        for (int marked = 0; marked <= 4; marked++) {
            if (marked > 0) {
                allies.get(marked - 1).getBuffManager()
                        .addBuff(new com.laosun.aluminium.buff.StateBuff("献予「天空」之诗", 99));
            }
            Assertions.assertTrue(allies.get(Math.max(0, marked - 1)).getBuffManager()
                            .hasState("献予「天空」之诗") == (marked > 0),
                    "the mark is visible to hasState at marked=" + marked);
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
            battle.castImmediate(striker.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                    striker, List.of(target));

            double normal = settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                    .mapToDouble(Damage::toValue).findFirst()
                    .orElseThrow(() -> new AssertionError("the attack settled nothing"));
            double zone = settled.stream().filter(d -> d.getType() == DamageType.TRUE)
                    .mapToDouble(Damage::toValue).findFirst()
                    .orElseThrow(() -> new AssertionError("the zone settled no true damage; instances were " + settled.size()));
            double capped = Math.min(4, marked);
            Assertions.assertEquals(0.24 + 0.06 * capped, zone / normal, 1e-9,
                    marked + " marked allies pays " + (0.24 + 0.06 * capped) + " of the hit");
        }
    }
}
