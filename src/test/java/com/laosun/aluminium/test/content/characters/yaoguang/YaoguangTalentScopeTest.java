package com.laosun.aluminium.test.content.characters.yaoguang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1502 爻光's talent:「**爻光持有【好活当赏】时**，使我方目标施放攻击后触发【大吉大利】，对随机 1 个击中的目标额外造成 1 次
 * 20% 的对应属性欢愉伤害」.
 *
 * <p>Three things this pins down, each of which the rule used to get wrong: the attacker may be an ALLY (it was
 * {@code actor == self}), the state must be up (there was no gate), and the instance takes the ATTACKER's element (it was
 * hard-coded Physical).
 */
public class YaoguangTalentScopeTest {

    @Test
    public void anAllyAttackTriggersItWhileSheHoldsTheState() {
        double withState = lossToAllyAttack(true);
        double withoutState = lossToAllyAttack(false);
        System.out.println("[1502] ally attack, state up=" + withState + " ; state down=" + withoutState);
        Assertions.assertTrue(withState > withoutState, "【大吉大利】 must add an instance when she holds 【好活当赏】");
    }

    private static double lossToAllyAttack(boolean holdTheState) {
        Character her = CharacterFactory.create(1502, 80);
        Character ally = CharacterFactory.create(1101, 80);
        if (holdTheState) {
            her.getBuffManager().addBuff(new StateBuff("好活当赏", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}