package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's ultimate heals: 「为我方全体目标回复等同于风堇 10% 生命上限 + 200 的生命值」 (141303).
 *
 * <p>Why it is a rule and not data: her Skill is {@code Restore}, so its own data heals (measured: 8% + 160), while her
 * Ultimate is {@code Support} - nothing in the data heals, measured as a 0 heal before this rule existed. The memosprite
 * half needs {@code self_summon_count >= 1}, which is why the ultimate also summons in an earlier rule.
 */
public class HyacineUltHealTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;

    @Test
    public void theUltimateHealsTenPercentOfHerMaxHpPlusTwoHundred() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.6);
        double before = ally.getCurrentHp();
        // Her Max HP BEFORE the ultimate: the same cast also raises it by 30% + 600 for 【雨过天晴】, and the heal is
        // computed from the panel the cast started with (measured: 319.52864 = 1195.2864 x 0.1 + 200).
        double herMaxHpBefore = hyacine.getAttribute(AttributeType.HEALTH).get();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double healed = ally.getCurrentHp() - before;
        double expected = herMaxHpBefore * 0.1 + 200.0;
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「等同于风堇 10% 生命上限 + 200」 (healed=" + healed + ", expected=" + expected + ")");
    }
}
