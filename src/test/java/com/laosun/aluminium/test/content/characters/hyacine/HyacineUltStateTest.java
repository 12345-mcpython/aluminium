package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's ultimate state 【雨过天晴】: 「我方全体目标生命上限提高 30% + 600 点」 for 3 turns.
 *
 * <p>Why this case exists: the percentage half was modelled and the flat 600 was registered as missing, because
 * {@code MODIFY_ATTR} was believed to accept a flat {@code amount} only alongside a {@code scale}. It does not - a bare
 * {@code amount} is a flat point value (1503's trace rows raise SPEED by 9 that way) - so the flat half is now a second
 * effect on the same rule, and this measures the whole sentence: {@code base x 1.3 + 600}.
 */
public class HyacineUltStateTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;

    @Test
    public void theUltimateRaisesEveryAlliesMaxHpByThirtyPercentPlusSixHundred() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double allyBase = ally.getAttribute(AttributeType.HEALTH).baseValue();
        double before = ally.getAttribute(AttributeType.HEALTH).get();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.get(0)));
        battle.processRequests();
        double after = ally.getAttribute(AttributeType.HEALTH).get();

        Assertions.assertTrue(allyBase > 0, "precondition: the ally has a base MaxHP");
        Assertions.assertEquals(allyBase * 1.3 + 600.0, after, Math.max(1e-6, allyBase * 1e-9),
                "「生命上限提高 30.00% + 600 点」 (before=" + before + ", after=" + after + ")");
    }
}
