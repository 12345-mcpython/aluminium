package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999): her ZONE (结界) - both halves, which the registration had called impossible.
 *
 * <p>The Zone is a NAMED STATE marker in this project, not a container: 1203 opens its own with {@code APPLY_BUFF buff: 结界}
 * and reads it with {@code target has_state 结界}, and 1410/1513 do the same. And {@code SKILL_POINT_SPENT} is a real event.
 * So 「【无敌玩家】状态下展开结界」 and 「结界内我方目标每消耗 1 个战技点…触发 1 次【头号补给盲盒】：造成 90% 的虚数属性欢愉伤害」
 * are both writable, and this case pins them.
 */
public class SilverWolfLV999ZoneTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String ZONE = "结界";
    private static final String GOD = "无敌玩家";
    /**
     * 【好活当赏】 - the box's own gate since the audit (「「银狼LV.999」持有【好活当赏】时」): a RESOURCE, because that is what
     * the clause reads ({@code self_resource:好活当赏 >= 1}). Nothing in her file grants it - the Aha Moment does, and this
     * engine has no Aha stage - so the fixture states the premise.
     */
    private static final String GIFT = "好活当赏";

    /**
     * Her Ultimate deploys the Zone, and it lands on her rather than on the party.
     */
    @Test
    public void theUltimateDeploysTheZoneOnHer() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Assertions.assertFalse(wolf.getBuffManager().hasState(ZONE), "precondition: no Zone before the Ultimate");

        castUltimate(battle, wolf);
        System.out.println("[1506] after the Ultimate: 结界=" + wolf.getBuffManager().hasState(ZONE)
                + " 无敌玩家=" + wolf.getBuffManager().hasState(GOD)
                + " ; the ally holds 结界=" + ally.getBuffManager().hasState(ZONE));
        Assertions.assertTrue(wolf.getBuffManager().hasState(ZONE), "终结技: 「展开结界」");
        Assertions.assertFalse(ally.getBuffManager().hasState(ZONE), "and it is HERS - the marker is on the caster");
    }

    /**
     * A Skill Point spent inside the Zone, with 【无敌玩家】 up, deals Elation damage to the enemies.
     */
    @Test
    public void aSpentSkillPointTriggersTheLootBox() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        castUltimate(battle, wolf);

        double before = totalEnemyHp(battle);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        double after = totalEnemyHp(battle);
        System.out.println("[1506] enemy HP before/after one spent Skill Point: " + before + " -> " + after
                + " (dealt " + (before - after) + ")");
        Assertions.assertTrue(before - after > 0,
                "结界内每消耗 1 个战技点触发一次盲盒，造成 90% 虚数欢愉伤害");
    }

    /**
     * Without the state there is no Zone, so a spent Skill Point is just a spent Skill Point.
     */
    @Test
    public void withoutTheZoneASpentPointDoesNothing() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        double before = totalEnemyHp(battle);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        double after = totalEnemyHp(battle);
        System.out.println("[1506] with no Ultimate: enemy HP " + before + " -> " + after);
        Assertions.assertEquals(before, after, 1e-9,
                "the Zone and 【无敌玩家】 are both required, so nothing fires without them");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double totalEnemyHp(Battle battle) {
        return battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).sum();
    }

    private static void castUltimate(Battle battle, Character wolf) {
        wolf.getResources().gain(GIFT, 1);
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
