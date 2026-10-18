package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's Rank 1 and Rank 2 clauses:
 * <ul>
 *   <li>Rank 1: while 【雨过天晴】 is up, 「施放攻击后立即回复等同于风堇 8% 生命上限的生命值」;</li>
 *   <li>Rank 2: 「我方目标生命值降低时，速度提高 30%，持续 2 回合」.</li>
 * </ul>
 */
public class HyacineEidolonOneTwoTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;

    @Test
    public void rankOneHealsTheAttackerForEightPercentOfHerMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double hyacineMax = hyacine.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get();

        ally.consumeHp(ally.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get() * 0.5);
        double wounded = ally.getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();

        double healed = ally.getCurrentHp() - wounded;
        Assertions.assertTrue(healed > 0, "an attack heals the attacker while 雨过天晴 is up");
        Assertions.assertEquals(Math.min(hyacineMax * 0.08, ally.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get() - wounded),
                healed, 1e-6, "「回复等同于风堇 8% 生命上限」: " + healed);
    }

    @Test
    public void rankTwoRaisesSpeedWhenAnAllyLosesHp() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double base = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).baseValue();
        double speedBefore = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        // Two corrections against the version that was @Disabled until 2026-10-18:
        //   ① the rule's target is `target` = the unit that LOST HP, not 风堇 -- the old assertion read the wrong unit;
        //   ② `ally.takeDamage(...)` bypasses Battle, so HP_LOST was never emitted. Only Battle.applyDamage settles a
        //      hit and pays out the event (CanHit.takeDamage's own javadoc says so).
        battle.applyDamage(ally, new com.laosun.aluminium.models.Damage(hypotheticalAttacker(battle), ally,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, ally.getMaxHp() * 0.1));
        battle.processRequests();

        double speedAfter = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        Assertions.assertEquals(base * 0.3, speedAfter - speedBefore, 1e-6,
                "Rank 2: 「我方目标生命值降低时，速度提高 30%」 (before=" + speedBefore + ", after=" + speedAfter
                        + ", base=" + base + ")");
    }

    private static com.laosun.aluminium.models.CanHit hypotheticalAttacker(Battle battle) {
        return battle.enemies.getFirst();
    }
}
