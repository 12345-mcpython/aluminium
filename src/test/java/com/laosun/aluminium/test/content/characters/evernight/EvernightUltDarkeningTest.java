package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1413 长夜月's ultimate 【至暗之谜】: 「期间敌方全体受到的伤害提高 30%，长夜月和忆灵「长夜」造成的伤害提高 60%」
 * (141303, params [2, 2, 0.6, 0.3]).
 *
 * <p>Measured as the ally's damage before and after the ultimate, with the target already broken so the toughness factor
 * stays at 1.0 on both sides - the same isolation trick the vulnerability tests learned the hard way.
 */
public class EvernightUltDarkeningTest {

    private static final int EVERNIGHT = 1413;
    private static final int ALLY = 1001;
    private static final double VULNERABILITY = 0.3;

    @Test
    public void theUltimateRaisesTheDamageTheEnemyTakesByThirtyPercent() {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(evernight, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ((com.laosun.aluminium.models.enemy.Enemy) battle.enemies().get(0)).breakEnemy(DamageElement.ICE);

        double before = allyHit(battle, ally);
        SkillExecutor.execute(battle, evernight.getSkills().get(SkillType.ULTRA), evernight,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double after = allyHit(battle, ally);

        Assertions.assertTrue(before > 0 && after > 0, "precondition: both ally hits land");
        Assertions.assertEquals(1.0 + VULNERABILITY, after / before, (1.0 + VULNERABILITY) * 1e-6,
                "【至暗之谜】 raises the damage the enemies take by 30% (before=" + before + ", after=" + after + ")");
    }

    private static double allyHit(Battle battle, Character ally) {
        double before = battle.enemies().get(0).getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        return before - battle.enemies().get(0).getCurrentHp();
    }
}
