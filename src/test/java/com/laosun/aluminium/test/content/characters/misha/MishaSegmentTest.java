package com.laosun.aluminium.test.content.characters.misha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
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
 * 1312 米沙's Talent, the segment half: 「我方全体每消耗 1 个战技点，增加 1 段米沙下一次终结技的攻击段数」.
 */
public class MishaSegmentTest {

    private static final int MISHA = 1312;
    private static final int LEVEL = 80;

    @Test
    public void spendingASkillPointAddsASegment() {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(misha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);   // data slot 3 = the Ultimate
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, misha, misha, 1, 0));
        int after = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);
        System.out.println("[1312] ult hits " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「增加 1 段」");
    }
    @Test
    public void eidolonOneAddsASegmentPerEnemy() {
        // The clause is the DELTA: the same cast with and without the Eidolon.
        Assertions.assertEquals(1, hitsDuringUlt(1, 1) - hitsDuringUlt(1, 0), "one enemy: one extra segment");
        Assertions.assertEquals(3, hitsDuringUlt(3, 1) - hitsDuringUlt(3, 0), "three enemies: three extra segments");
    }

    /**
     * @return how many damage instances the Ultimate settled on the enemies
     * <p>Note: the segment count cannot be read AFTER the cast - her own rule puts it back to the base - so the judge
     * counts what the cast actually delivered.
     */
    private static int hitsDuringUlt(int enemies, int eidolon) {
        Character misha = CharacterFactory.create(MISHA, LEVEL, true, null, null, eidolon);
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.monster());
        }
        Battle battle = new Battle(new ArrayList<>(List.of(misha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(foes), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int[] hits = new int[1];
        battle.addDamageObserver((damage, applied, hp, shield, died) -> {
            if (damage.getAttacker() == misha) {
                hits[0]++;
            }
        });
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.copyOf(foes));
        System.out.println("[1312] e" + eidolon + " " + enemies + " enemies => " + hits[0] + " hits");
        return hits[0];
    }
}
