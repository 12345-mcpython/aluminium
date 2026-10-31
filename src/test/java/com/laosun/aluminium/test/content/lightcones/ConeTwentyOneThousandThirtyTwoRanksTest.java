package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 镂月裁云之意 (21032), the part its behaviour test does not read: <b>the shares are per rank</b>.
 *
 * <p>The file used to repeat rank 1's numbers (10% / 12% / 6%) on every rank, so a rank-5 wearer got rank-1 values -
 * a wrong number with no symptom. {@code SecretLightConeTest} already judges "exactly one effect, never the same twice,
 * the whole party"; this test only pins the five rows.
 */
public class ConeTwentyOneThousandThirtyTwoRanksTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 21032;
    private static final List<String> SECRETS = List.of("秘密-攻击力", "秘密-暴击伤害", "秘密-能量恢复");

    /**
     * The active share matches the rank's row: attack 10/12.5/15/17.5/20%, crit 12/15/18/21/24%, regeneration 6/7.5/9/10.5/12%.
     */
    @Test
    public void theActiveShareMatchesItsRank() {
        double[] attack = {0.1, 0.125, 0.15, 0.175, 0.2};
        double[] crit = {0.12, 0.15, 0.18, 0.21, 0.24};
        double[] err = {0.06, 0.075, 0.09, 0.105, 0.12};
        for (int rank = 1; rank <= 5; rank++) {
            Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded()).startBattle();
            String active = activeSecret(wearer);
            Assertions.assertNotNull(active, "rank " + rank + ": one effect must be up");
            double expected = switch (active) {
                case "秘密-攻击力" -> attack[rank - 1];
                case "秘密-暴击伤害" -> crit[rank - 1];
                default -> err[rank - 1];
            };
            Assertions.assertEquals(expected, share(wearer, active), 1e-6, "rank " + rank + " active " + active);
        }
    }

    private static String activeSecret(Character wearer) {
        for (AbstractBuff buff : wearer.getBuffManager().activeBuffs()) {
            String name = buff.getBuffName();
            if (name != null && SECRETS.contains(name.trim())) {
                return name.trim();
            }
        }
        return null;
    }

    /**
     * The named modifier's own magnitude: measured by REMOVING it and reading the drop, because the wearer's ATTACK
     * carries the cone's flat stats and the other two attributes have non-zero baselines.
     */
    private static double share(Character wearer, String effect) {
        AttributeType type = switch (effect) {
            case "秘密-攻击力" -> AttributeType.ATTACK;
            case "秘密-暴击伤害" -> AttributeType.CRIT_ATTACK;
            default -> AttributeType.ENERGY_REGENERATION_RATE;
        };
        double before = wearer.getAttribute(type).get();
        wearer.getBuffManager().removeState(effect);
        double after = wearer.getAttribute(type).get();
        double base = type == AttributeType.ATTACK ? wearer.getAttribute(type).baseValue() : 1.0;
        return (before - after) / base;
    }
}
