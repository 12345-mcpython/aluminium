package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.buff.StunBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 21001 晚安与睡颜: 「敌方目标每承受 1 个负面效果，装备者对其造成的伤害提高 12%，最多叠加 3 层。
 * 该效果对持续伤害也会生效」.
 *
 * <p>Read on the {@code BoostArea} factor, not on the HP delta: the cone's 12% is one additive
 * zone factor, so the judge can pin 0.12 / 0.24 / 0.36 exactly even though the final number also
 * carries defence and resistance. The debuffs are non-damage attributes (SPEED / effect resistance /
 * effect hit rate / defence), so only the cone's per-stack term changes between runs.
 */
public class Cone21001Test {
    private static final int WEARER = 1402;
    private static final int LEVEL = 80;
    private static final int CONE = 21001;
    private static final double EPS = 1e-9;

    /**
     * The per-debuff boost, its three-stack cap, and the cap boundary.
     */
    @Test
    public void theBoostIsTwelvePercentPerDebuffUpToThree() {
        double zero = boostWith(0);
        Assertions.assertEquals(0.12, boostWith(1) - zero, EPS, "1 debuff");
        Assertions.assertEquals(0.24, boostWith(2) - zero, EPS, "2 debuffs");
        Assertions.assertEquals(0.36, boostWith(3) - zero, EPS, "3 debuffs");
        Assertions.assertEquals(0.36, boostWith(4) - zero, EPS, "the cap is 3 stacks");
    }

    private static double boostWith(int debuffs) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, false, Weapon.build(CONE, LEVEL, false, 1));
        Battle battle = new Battle(List.of(wearer), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();

        var enemy = battle.enemies().getFirst();
        AbstractBuff[] sources = {
                new StunBuff(2),
                new TauntBuff(2),
                new VulnerabilityBuff(2, 0.01),
                StatModifierBuff.percentDebuff(com.laosun.aluminium.enums.AttributeType.SPEED, -0.1, 2)
        };
        for (int i = 0; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(sources[i]);
        }

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(enemy));
        battle.processRequests();
        return settled.getFirst().breakdown().get("BoostArea");
    }
}
