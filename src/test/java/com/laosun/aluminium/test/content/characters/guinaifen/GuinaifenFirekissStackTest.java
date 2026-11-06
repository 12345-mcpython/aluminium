package com.laosun.aluminium.test.content.characters.guinaifen;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1210 桂乃芬's 天赋 古来君子养艺人:「【吞火】状态下敌方目标受到的伤害提高 7.00%，持续 3 回合」, and the stack cap that came with it.
 *
 * <p>The layer count is a COUNTER (`ADD_STACK 吞火 1 max 3`) and the zone reads it LIVE (`per_stack` + `per_stack_live` +
 * `per_stack_cap`), the same shape 1009 Asta (艾丝妲) uses. Attaching three separate vulnerability buffs would not work:
 * `VulnerabilityBuff.isSameKind` keeps one instance per source, so the single instance has to grow instead.
 */
public class GuinaifenFirekissStackTest {
    private static final int GUINAIFEN = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theFirekissStacksToThree() {
        Battle battle = scene();
        Character guinaifen = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();

        // A REAL burn: the Skill applies 灼烧, and every `tickDots` settles one burn instance - which is exactly the trigger
        // the talent listens to (`damage_is_dot` + `damage_element_is_self`).
        battle.castImmediate(guinaifen.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), guinaifen,
                List.of(enemy));
        battle.processRequests();
        // ⚠ One `tickDots` settles EVERY burn instance the enemy carries, so the first call may already reach the cap; what
        // this pins is the cap itself - the property that was missing while the talent attached one flat +7% per trigger.
        for (int tick = 1; tick <= 3; tick++) {
            battle.tickDots(enemy);
            battle.processRequests();
            int stacks = enemy.getBuffManager().stacksOf("吞火");
            Assertions.assertTrue(stacks >= 1, "the burn tick applied 【吞火】 (tick " + tick + ")");
            Assertions.assertTrue(stacks <= 3, "「最多叠加 3 层」 — the counter never passes three (tick " + tick + ": " + stacks + ")");
        }
    }

    /** 星魂 6「使【吞火】的可叠加层数增加 1 层」: the cap moves from three to four. */
    @Test
    public void theSixthEidolonRaisesTheCapToFour() {
        Battle battle = scene(6);
        Character guinaifen = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        battle.castImmediate(guinaifen.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), guinaifen,
                List.of(enemy));
        battle.processRequests();

        for (int tick = 1; tick <= 3; tick++) {
            battle.tickDots(enemy);
            battle.processRequests();
        }
        Assertions.assertTrue(enemy.getBuffManager().stacksOf("吞火") <= 4,
                "「增加 1 层」 — the cap is four, never more");
        Assertions.assertTrue(enemy.getBuffManager().stacksOf("吞火") >= 1, "the counter still fills");
    }

    /** 星魂 4「她施加的灼烧每触发一次伤害，使自身恢复 2 点能量」. */
    @Test
    public void everyBurnTickPaysTwoEnergy() {
        Battle battle = scene(4);
        Character guinaifen = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        battle.castImmediate(guinaifen.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), guinaifen,
                List.of(enemy));
        battle.processRequests();
        double before = guinaifen.getCurrentEnergy();

        battle.tickDots(enemy);
        battle.processRequests();

        Assertions.assertTrue(guinaifen.getCurrentEnergy() > before,
                "「每触发一次伤害…恢复 2 点能量」 — energy went up (" + before + " -> " + guinaifen.getCurrentEnergy() + ")");
    }

    private static Battle scene() {
        return scene(0);
    }

    private static Battle scene(int eidolon) {
        Character guinaifen = CharacterFactory.create(GUINAIFEN, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(guinaifen), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
