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
     * 「普通攻击强化为【孤锋千吻】**且无法施放战技**」: the stance bans the Skill slot, and the ban leaves with it.
     *
     * <p>Read through {@code SkillCastability.allows}, the gate every cast path consults — a ban only the executor honoured
     * would let the action bar schedule the cast anyway.
     */
    @Test
    public void theStanceBansHerSkillAndGivesItBack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var skill = aglaea.getSkills().get(SkillType.SKILL);
        Assertions.assertTrue(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "precondition: the Skill is castable before the stance");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(aglaea.getBuffManager().hasState("至高之姿"), "precondition: the stance is on");
        Assertions.assertFalse(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "「…且无法施放战技」");

        // The stance is ANCHORED to the memosprite ("衣匠消失时…解除"), so it has to really leave for the ban to go: a
        // hand-fired SUMMON_LEFT removes the swaps (they were placed by rules) but not an anchored buff.
        Summon garmentmaker = battle.summonOf(aglaea);
        Character ally = CharacterFactory.create(1001, LEVEL);
        battle.applyDamage(garmentmaker,
                new com.laosun.aluminium.damage.Damage(ally, garmentmaker,
                        com.laosun.aluminium.enums.DamageElement.THUNDER,
                        com.laosun.aluminium.enums.DamageType.NORMAL, 1_000_000));
        battle.processRequests();
        Assertions.assertFalse(aglaea.getBuffManager().hasState("至高之姿"), "precondition: the stance ended with it");
        Assertions.assertTrue(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "the ban is the stance's, so it leaves with it");
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
