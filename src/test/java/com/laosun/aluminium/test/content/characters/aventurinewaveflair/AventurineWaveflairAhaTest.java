package com.laosun.aluminium.test.content.characters.aventurinewaveflair;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1513's 【阿哈】 is a <b>panel-less</b> summon: `SUMMON { memosprite_name: 阿哈, panel_less: true }` places it with HER OWN attributes.
 *
 * <p>This is the third summon model in the engine (monster id / memosprite spec / inherited panel). The document writes 「使阿哈…」
 * exactly like 「召唤忆灵」, so the content states which one it means.
 */
public class AventurineWaveflairAhaTest {
    private static final int WAVEFLAIR = 1513;
    private static final int LEVEL = 80;

    @Test
    public void ahaTakesTheFieldWithHerOwnPanel() {
        Character waveflair = CharacterFactory.create(WAVEFLAIR, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(waveflair), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        Summon aha = battle.summonsOf(waveflair).stream()
                .filter(summon -> "阿哈".equals(summon.getName()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(aha, "「阿哈」 reached the field through SUMMON { panel_less: true }");
        Assertions.assertEquals(waveflair.getAttribute(AttributeType.SPEED).get(),
                aha.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "…and it inherits her panel instead of needing a spec it never had");
    }
}
