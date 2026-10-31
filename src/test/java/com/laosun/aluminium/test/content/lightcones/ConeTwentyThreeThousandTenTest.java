package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
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
 * 拂晓之前 (23010): 「施放战技或终结技后获得【梦身】；触发追加攻击时<b>消耗【梦身】</b>，使追加攻击造成的伤害提高 <b>48%</b>，持续 1 回合」.
 *
 * <p><b>What was missing.</b> The old note said the engine had no way to spend a state when a follow-up arrives, and shipped a 1-turn
 * window that paid EVERY follow-up. There is one: both halves live in the SAME rule - read the stack ({@code self_stacks:梦身 >= 1}),
 * boost, then {@code REMOVE_STACK} - so the payment is one-shot by construction.
 */
public class ConeTwentyThreeThousandTenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23010, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * The boost the ENGINE's own follow-up path carries - `applyAdditionalDamage`, which is the one that sets
         * "does not count as an attack", the fact `damage_is_additional` reads (a hand-built ADDITIONAL Damage keeps
         * countsAsAttack = true and would never match).
         */
        double followUp() {
            double[] boost = {-1};
            var observer = new java.util.ArrayList<Damage>();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    observer.add(damage));
            battle.applyAdditionalDamage(wearer, enemy, DamageElement.PHYSICAL, 1000);
            battle.processRequests();
            return observer.stream()
                    .filter(d -> d.getType() == DamageType.ADDITIONAL)
                    .mapToDouble(d -> d.boostArea().getRate() - 1.0)
                    .findFirst().orElse(boost[0]);
        }
    }

    /**
     * One follow-up gets the 48%; the next one - once the state is spent - does not.
     */
    @Test
    public void theFirstFollowUpPaysAndTheSecondDoesNot() {
        Run run = new Run();
        Assertions.assertEquals(0.0, run.followUp(), 1e-9, "nothing to spend before an ultimate or a skill");

        run.wearer.setCurrentEnergy(run.wearer.getMaxEnergy());
        run.battle.castImmediate(run.wearer.getSkills().get(SkillType.ULTRA), run.wearer, List.of(run.enemy));
        run.battle.processRequests();


        Assertions.assertEquals(0.48, run.followUp(), 1e-6, "the first follow-up spends 【梦身】 and is raised by 48%");
        Assertions.assertEquals(0.0, run.followUp(), 1e-9,
                "the state is gone - 「触发追加攻击时消耗【梦身】」 pays once, not for the whole turn");
    }
}
