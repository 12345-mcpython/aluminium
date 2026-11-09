package com.laosun.aluminium.test.content.characters.herta;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1401 大黑塔's 【灵感】/【解读】: 「持有【灵感】时，战技强化为【我有一个大胆的想法】」 and 「战技…并施加 1 层【解读】」.
 *
 * <p>Two shapes that only look similar: the SKILL is <b>replaced</b> while the stacks last (data slot 9, the enhanced form, which
 * spends one on cast and gives way when they run out), while 【解读】 is an ordinary counter on the enemy that the Skill adds one
 * of. Both are pinned here because neither has a symptom when it silently stops: the Skill simply stays the plain one, and the
 * enemy simply never gets a stack.
 */
public class HertaSkillFormTest {
    private static final int HERTA = 1401;
    private static final int LEVEL = 80;
    private static final int PLAIN_SKILL_SLOT = 2;
    private static final int ENHANCED_SKILL_SLOT = 9;
    private static final String INSPIRATION = "灵感";
    private static final String INSIGHT = "解读";

    /** 「战技…并施加 1 层【解读】」 - the plain Skill adds one stack to the enemy it hit. */
    @Test
    public void thePlainSkillAddsOneInsight() {
        Scene scene = new Scene();
        Assertions.assertEquals(0, scene.enemy.getBuffManager().stacksOf(INSIGHT), "the enemy enters with none");
        scene.castSkill();
        Assertions.assertEquals(1, scene.enemy.getBuffManager().stacksOf(INSIGHT),
                "「施加 1 层【解读】」");
    }

    /**
     * 「持有【灵感】时，战技强化为【我有一个大胆的想法】」: the Ultimate swaps the slot, the enhanced cast spends a stack, and when the
     * last one is gone the plain Skill is back.
     */
    @Test
    public void theUltimateEnhancesTheSkillUntilTheInspirationRunsOut() {
        Scene scene = new Scene();
        Assertions.assertEquals(PLAIN_SKILL_SLOT, scene.skillSlot(), "the plain Skill before the Ultimate");

        scene.castUltimate();
        Assertions.assertEquals(1, scene.herta.getBuffManager().stacksOf(INSPIRATION), "「获得 1 层【灵感】」");
        Assertions.assertEquals(ENHANCED_SKILL_SLOT, scene.skillSlot(),
                "「持有【灵感】时，战技强化为【我有一个大胆的想法】」");

        scene.castSkill();
        Assertions.assertEquals(0, scene.herta.getBuffManager().stacksOf(INSPIRATION),
                "「消耗 1 层【灵感】」");
        Assertions.assertEquals(PLAIN_SKILL_SLOT, scene.skillSlot(),
                "「持有」的相反面 - no stack left, so the plain Skill comes back");
    }

    // ==================================================================

    private static final class Scene {
        private final Character herta;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Scene() {
            herta = CharacterFactory.create(HERTA, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(herta)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private int skillSlot() {
            return herta.getSkills().get(SkillType.SKILL).getSkillSlot();
        }

        private void castSkill() {
            battle.castImmediate(herta.getSkills().get(SkillType.SKILL), herta, List.of(enemy));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(herta.getSkills().get(SkillType.ULTRA), herta, List.of(enemy));
            battle.processRequests();
        }
    }
}
