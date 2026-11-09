package com.laosun.aluminium.test.content.characters.feixiao;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1220 飞霄's 【飞黄】: 「我方目标每施放 2 次攻击，飞霄获得 1 点【飞黄】，飞霄的终结技攻击不计入次数。最多累计 12 点」.
 *
 * <p><b>Why a counter rather than one rule.</b> "Every two attacks" has no arithmetic in the DSL, so it is a counter that resets
 * when it fires (the same shape as 1223's "every three charges"). And the exclusion is <b>hers alone</b>: 「飞霄的终结技攻击不计入」
 * says HER Ultimate, so this file gates with {@code from_skill_id != 3} rather than {@code from_category ULTRA} - the latter would
 * also drop a TEAMMATE's Ultimate, which the sentence counts.
 */
public class FeixiaoFlyingAureusTest {
    private static final int FEIXIAO = 1220;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String AUREUS = "飞黄";

    @Test
    public void twoTeammateAttacksPayOnePoint() {
        Scene scene = new Scene();
        Assertions.assertEquals(0, scene.aureus(), "it starts empty");
        scene.teammateAttacks(SkillCategory.NORMAL);
        Assertions.assertEquals(0, scene.aureus(), "one attack is not yet two");
        scene.teammateAttacks(SkillCategory.NORMAL);
        Assertions.assertEquals(1, scene.aureus(), "「每施放 2 次攻击，飞霄获得 1 点【飞黄】」");
        scene.teammateAttacks(SkillCategory.NORMAL, 2);
        Assertions.assertEquals(2, scene.aureus(), "and it keeps counting from zero, not from two");
    }

    /** 「飞霄的终结技攻击不计入次数」 - her own Ultimate does not advance the counter; a teammate's still does. */
    @Test
    public void herOwnUltimateDoesNotCountButATeammatesDoes() {
        Scene scene = new Scene();
        scene.herAttack(SkillCategory.ULTRA, 3, 3);
        Assertions.assertEquals(0, scene.aureus(), "a single Ultimate attack is not two, and must not count either");
        scene.herAttack(SkillCategory.BPSKILL, 2, 2);
        Assertions.assertEquals(1, scene.aureus(), "two of HER non-ultimate attacks do count");

        Scene other = new Scene();
        other.teammateAttacks(SkillCategory.ULTRA, 2);
        Assertions.assertEquals(1, other.aureus(),
                "a TEAMMATE's Ultimate counts - only 飞霄's own is excluded");
    }

    // ==================================================================

    private static final class Scene {
        private final Character feixiao;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Scene() {
            feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(feixiao, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private int aureus() {
            return feixiao.getResources().has(AUREUS) ? feixiao.getResources().value(AUREUS) : 0;
        }

        private void teammateAttacks(SkillCategory category, int times) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0,
                        category, 1, 0, List.of(enemy)));
                battle.processRequests();
            }
        }

        private void teammateAttacks(SkillCategory category) {
            teammateAttacks(category, 1);
        }

        private void herAttack(SkillCategory category, int times, int skillSlot) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, feixiao, enemy, 1, 0,
                        category, skillSlot, 0, List.of(enemy)));
                battle.processRequests();
            }
        }
    }
}
