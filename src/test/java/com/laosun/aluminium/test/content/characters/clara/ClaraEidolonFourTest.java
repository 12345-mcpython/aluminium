package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
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
 * 1107 克拉拉's Eidolon 4: 「受到攻击后，克拉拉受到的伤害降低 30%，效果持续到自己的下个回合开始」.
 *
 * <p>Two things are load-bearing and both are measured here: the reduction must NOT cover the attack that granted it
 * (the sentence says 受到攻击<b>后</b>), and it must survive until her own next turn begins ({@code NEXT_TURN_START}, not
 * "one turn" - {@code turns: 1} would drop it at the end of a turn, one beat too early).
 */
public class ClaraEidolonFourTest {

    private static final int LEVEL = 80;
    private static final int CLARA = 1107;

    /**
     * The hit that triggers it is not reduced; the next one is, by 30%.
     */
    @Test
    public void theReductionStartsAfterTheHitAndEndsAtHerNextTurn() {
        double beforeE0 = hitOne(0);
        double beforeE4 = hitOne(4);
        Assertions.assertEquals(1.0, beforeE4 / beforeE0, 1e-6,
                "the attack that triggers it is NOT reduced (「受到攻击<b>后</b>」)");

        double afterE0 = hitAfterBeingAttacked(0);
        double afterE4 = hitAfterBeingAttacked(4);
        System.out.println("[1107] after E0=" + afterE0 + " E4=" + afterE4 + " ratio=" + (afterE4 / afterE0));
        Assertions.assertEquals(0.7, afterE4 / afterE0, 1e-6,
                "「受到的伤害降低 30%」 - and it is still up until her next turn starts");
    }

    /**
     * Eidolon 3 and 5: the two level clauses the data spells as {@code skill_add_level}.
     */
    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        int baseSkill = level(0, SkillType.SKILL);
        int baseBasic = level(0, SkillType.COMMON);
        int baseUlt = level(0, SkillType.ULTRA);
        int baseTalent = level(0, SkillType.TALENT);

        Assertions.assertEquals(baseSkill + 2, level(3, SkillType.SKILL), "E3: 战技等级 +2");
        Assertions.assertEquals(baseBasic + 1, level(3, SkillType.COMMON), "E3: 普攻等级 +1");
        Assertions.assertEquals(baseUlt + 2, level(5, SkillType.ULTRA), "E5: 终结技等级 +2");
        Assertions.assertEquals(baseTalent + 2, level(5, SkillType.TALENT), "E5: 天赋等级 +2");
    }

    private static int level(int eidolon, SkillType slot) {
        // Levels are raised by BATTLE_START rules, so the battle must have started before the slot reads its level.
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(clara)), new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded());
        battle.startBattle();
        return 1 + clara.skillLevelBonus(slot);
    }
    // ==================================================================

    private static double hitOne(int eidolon) {
        Fixture f = new Fixture(eidolon);
        return f.applyProbeDamage();
    }

    private static double hitAfterBeingAttacked(int eidolon) {
        Fixture f = new Fixture(eidolon);
        f.applyProbeDamage();                       // the attack that lands first
        f.fireAttackFinishedOnClara();              // its "after" moment
        return f.applyProbeDamage();                // the next one
    }

    private static final class Fixture {
        private final Character clara;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int eidolon) {
            clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }

        private double applyProbeDamage() {
            double before = clara.getCurrentHp();
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - clara.getCurrentHp();
        }

        private void fireAttackFinishedOnClara() {
            battle.fire(TriggerPayload.Cast.of(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 200.0,
                    com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED, 0, 0, List.of(clara)));
        }
    }
}





