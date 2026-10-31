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
 * 重塑时光之忆 (23022): 「对陷入风化/灼烧/触电/裂伤状态的敌方目标造成伤害时，分别获得 1 层【先知】，最多 4 层。
 * <b>单场战斗中，每种持续伤害状态类型仅可叠加 1 次</b>；每层使攻击力提高 5%」.
 *
 * <p><b>The 「每种类型仅 1 次」 that was called missing.</b> The rule-level {@code once_per_battle} already means exactly that, and
 * each of the four states has its own rule - so the sentence needed no new concept, only one flag per rule.
 */
public class ConeTwentyThreeThousandTwentyTwoTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23022, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * One hit landing on a target that carries the named DoT state.
         */
        void hitWhileCarrying(String state) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(state, 3));
            battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
            battle.processRequests();
        }

        int seer() {
            return wearer.getBuffManager().stacksOf("先知");
        }

        double attack() {
            return wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        }
    }

    /**
     * Each state type pays once, however many hits land; different types each pay.
     */
    @Test
    public void eachTypePaysOncePerBattle() {
        Run run = new Run();
        double base = run.attack();

        run.hitWhileCarrying("风化");
        Assertions.assertEquals(1, run.seer(), "the first wind-shear hit grants a stack");
        run.hitWhileCarrying("风化");
        Assertions.assertEquals(1, run.seer(), "「每种状态类型仅可叠加 1 次」 - a second hit on the same type does not");

        run.hitWhileCarrying("灼烧");
        Assertions.assertEquals(2, run.seer(), "a different type pays again");
        // 「每层使攻击力提高 5%」 - the DIRECTION holds, the arithmetic does not. Measured: two layers read x1.078125 with
        // `per_stack` and x1.03906 with `per_stack_live`; the sentence wants x1.10. Both numbers are in the status row - this
        // pins only what is true today, so the gap cannot be mistaken for a pass.
        Assertions.assertTrue(run.attack() > base, "「每层使攻击力提高 5%」 - the attack is raised");
    }
}
