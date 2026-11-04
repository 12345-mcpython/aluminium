package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's 【至高之姿】 takes her basic attack over: 「普通攻击强化为【孤锋千吻】」 while the state holds, and the ordinary
 * row comes back when the Garmentmaker (衣匠) leaves - the state's own end, which is why the restore hangs on
 * {@code SUMMON_LEFT} rather than {@code STATE_ENDED} (the anchored removal does not announce the latter).
 */
public class AglaeaStanceSkillSwapTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final int ENHANCED_BASIC_SLOT = 8;

    @Test
    public void theStanceSwapsHerBasicAndItsEndPutsItBack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertEquals(1, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: the ordinary basic attack is in the slot");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(aglaea.getBuffManager().hasState("至高之姿"), "precondition: the stance is on");
        Assertions.assertEquals(ENHANCED_BASIC_SLOT, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "「普通攻击强化为【孤锋千吻】」 — the slot now holds data row 8");

        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the Ultimate brought the memosprite out");
        // The state's end: the memosprite leaves, and the swap must go with it.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.SUMMON_LEFT, garmentmaker, garmentmaker, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(1, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "「衣匠消失时…」 — the ordinary basic attack is back");
    }

    /**
     * 「阿格莱雅与衣匠…**分别**造成…攻击力的雷属性伤害」: the memosprite's half exists as its own instances, attributed to the
     * summon (whose ATTACK is synced from hers), reading the enhanced row's 3rd/4th cells.
     *
     * <p>Three enemies on purpose: the row is a Blast, so the memosprite's half is one instance on the aimed enemy plus one
     * per neighbour - and the neighbour selector only has something to pick with a real row of enemies.
     */
    @Test
    public void theGarmentmakerDealsItsOwnHalfOfTheJointAttack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy aimed = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(left, aimed, right), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(aimed));
        battle.processRequests();
        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the memosprite is out");

        int[] fromSummon = {0};
        double[] dealt = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
            if (damage.getAttacker() == garmentmaker) {
                fromSummon[0]++;
                dealt[0] += applied;
            }
        });
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(aimed));
        battle.processRequests();

        Assertions.assertEquals(3, fromSummon[0],
                "its half is one instance on the aimed enemy plus one on each neighbour (" + fromSummon[0] + ")");
        Assertions.assertTrue(dealt[0] > 0, "and it deals real damage: " + dealt[0]);
    }
}
