package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code lowest_ally_hp_percent}: 「若队伍中有存在当前生命值百分比小于等于 60% 的角色」 (1222 Lingsha (灵砂) 行迹 遗爇).
 *
 * <p><b>Why a minimum and not a count.</b> "somebody is at or below N%" is exactly "the lowest share is at or below N%", so one
 * scalar answers the sentence - and the same operator spells the negation ("everybody is above N%"). The two subjects the
 * vocabulary already had cannot answer it: {@code hp_percent} reads the OWNER and {@code target_hp_percent} the event's target,
 * while this sentence is about <em>anybody on the team</em>.
 *
 * <p>This is the engine half on its own: a naive substitution of {@code hp_percent} would pass any test where the owner happens to
 * be the wounded one, so the discriminating shape is "the owner is at full HP and a TEAMMATE is wounded".
 */
public class LowestAllyHpConditionTest {
    private static final int OWNER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double GRANT = 0.1;
    private static final double EPS = 1e-9;

    @Test
    public void theWorstBarInThePartyAnswersTheQuestion() {
        Battle battle = scene();
        Character owner = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        Enemy enemy = (Enemy) battle.enemies().getFirst();

        double base = crit(owner);
        fire(battle, enemy, owner);
        Assertions.assertEquals(base, crit(owner), EPS,
                "everybody is at full HP, so 「队伍中有 HP% ≤ 60% 的角色」 is false and the rule stays shut");

        while (ally.getCurrentHp() / ally.getMaxHp() > 0.6) {
            ally.takeDamage(ally.getCurrentHp() * 0.3);
        }
        Assertions.assertTrue(owner.getCurrentHp() / owner.getMaxHp() > 0.99, "precondition: the OWNER is untouched");
        fire(battle, enemy, owner);
        Assertions.assertEquals(base + GRANT, crit(owner), EPS,
                "the wounded TEAMMATE opens the gate - the owner's own bar answers the other question and would be false here");
    }

    /**
     * 「队伍中…的**角色**」: a summon is not a character. A low-HP summon must not satisfy the question, or every summoner would
     * silently enjoy clauses meant for the party.
     */
    @Test
    public void aSummonIsNotACharacter() {
        Battle battle = scene();
        Character owner = battle.characters().get(0);
        battle.placePanelLessSummon(owner, "测试召唤物", 90.0);
        battle.processRequests();
        battle.summonsOf(owner).getFirst().takeDamage(battle.summonsOf(owner).getFirst().getCurrentHp() * 0.9);

        double base = crit(owner);
        fire(battle, (Enemy) battle.enemies().getFirst(), owner);
        Assertions.assertEquals(base, crit(owner), EPS,
                "a wounded summon is not 「队伍中有…角色」, so the rule must stay shut");
    }

    private static double crit(Character unit) {
        return unit.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static void fire(Battle battle, Enemy enemy, Character owner) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.HP_LOST, enemy, owner, 1, 0));
        battle.processRequests();
    }

    private static Battle scene() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule())));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /** One rule that grants CRIT DMG when the party has somebody at or below 60% HP, so "did it fire" is a readable number. */
    private static TriggerSpec rule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "CRIT_ATTACK");
        TriggerSpecs.set(effect, "percent", GRANT);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("HP_LOST", List.of("lowest_ally_hp_percent <= 0.6"), effect);
    }
}
