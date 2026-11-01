package com.laosun.aluminium.test.content.characters.tribbie;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1403 缇宝's talent, last sentence: 「若追加攻击施放前目标被消灭则对<b>新入场</b>的敌方目标发动追加攻击」.
 *
 * <p>The shape is {@code defer_until_wave}: an other-ally Ultimate that finds no living enemy is HELD, and the next wave's
 * {@code WAVE_START} releases it against the enemy that just entered. The yardstick is the SAME follow-up fired directly in
 * a fresh battle - NOT a plain hit measured later in the same battle, because her own follow-up grants a damage boost that
 * makes a later hit look stronger (measured 1.72x, which is what made an earlier reading look like a defect).
 */
public class TribbieDeferredFollowUpTest {

    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;

    @Test
    public void aFollowUpWithNoTargetWaitsForTheNextWave() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(tribbie, ally)), new ArrayList<>(List.of(first)),
                BattleFixture.alwaysRoll(0.99));
        battle.startBattle();

        // The only enemy dies; now the other ally casts their Ultimate - there is nobody to hit.
        battle.applyTrueDamage(ally, first, DamageElement.PHYSICAL, first.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(first.isDeath());
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ULT_CAST, ally, first, 0, 0,
                SkillCategory.ULTRA, 3, 0, List.of()));

        Enemy fresh = BattleFixture.monster();
        battle.addEnemy(fresh);
        double beforeWave = fresh.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.WAVE_START, tribbie, tribbie, 0, 0));
        double deferred = beforeWave - fresh.getCurrentHp();

        double direct = directFollowUp();
        System.out.println("[1403] deferred=" + deferred + " direct=" + direct);
        Assertions.assertEquals(direct, deferred, 1e-6,
                "「对新入场的敌方目标发动追加攻击」 - the same follow-up, only later");
    }

    /**
     * The same follow-up with a living enemy, in its own battle: same 18% of Max HP, same zones, and no boost from the
     * follow-up itself (it has not landed yet when it is measured).
     */
    private static double directFollowUp() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(tribbie, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ULT_CAST, ally, enemy, 0, 0,
                SkillCategory.ULTRA, 3, 0, List.of()));
        return before - enemy.getCurrentHp();
    }
}
