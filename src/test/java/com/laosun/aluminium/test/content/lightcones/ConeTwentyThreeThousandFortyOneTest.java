package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
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
 * 只看今朝 (23041): 「若敌方目标拥有<b>装备者添加的</b>弱点，装备者对其造成的伤害提高 <b>60%</b>」.
 *
 * <p><b>Why this was listed as impossible.</b> The old note said the engine can ask 「目标有几个弱点」 but not 「这个弱点是不是她加的」.
 * It never needed the applier: the engine already keeps TWO tables - {@code stanceWeak} is DATA from the monster table, and
 * {@code timedWeak} holds exactly the weaknesses an EFFECT inserted. So 「添加的」 is the second table, and the new condition
 * {@code target has_added_weakness} asks precisely that.
 */
public class ConeTwentyThreeThousandFortyOneTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23041, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * The boost one settled instance carried.
         */
        double boostOfOneHit() {
            Damage hit = new Damage(wearer, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000);
            battle.applyDamage(enemy, hit);
            battle.processRequests();
            return hit.boostArea().getRate() - 1.0;
        }
    }

    @Test
    public void theBoostOnlyAppliesToAnAddedWeakness() {
        Run plain = new Run();
        Assertions.assertEquals(0.0, plain.boostOfOneHit(), 1e-9,
                "the enemy's OWN weaknesses are not 「装备者添加的」");

        Run added = new Run();
        added.enemy.addWeakness(DamageElement.QUANTUM, 2);
        Assertions.assertEquals(0.6, added.boostOfOneHit(), 1e-6,
                "「拥有装备者添加的弱点 ⇒ 对其造成的伤害提高 60%」");
    }
}
