package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
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
 * <p>Two things are load-bearing and both are measured: the reduction must NOT cover the attack that granted it
 * (「受到攻击<b>后</b>」), and it must survive until her own next turn ({@code NEXT_TURN_START}, not "one turn" -
 * {@code turns: 1} would drop it at the end of a turn, one beat too early).
 *
 * <p>Measured against a bare dummy (her DEF, no rules) rather than against E0: the talent already reduces 10%, and this
 * engine REPLACES same-kind reductions instead of adding them, so an E4/E0 ratio would read 7/9 for a reason unrelated to
 * the 30%. {@code ClaraReductionStackingGapTest} pins that defect.
 */
public class ClaraEidolonFourTest {

    private static final int LEVEL = 80;
    private static final int CLARA = 1107;

    /**
     * The hit that triggers it is reduced only by the talent; the next one by 30%.
     */
    @Test
    public void theReductionStartsAfterTheHitAndEndsAtHerNextTurn() {
        Fixture e0 = new Fixture(0);
        double dummy = e0.probeDummy();
        Assertions.assertEquals(0.9, e0.probeClara() / dummy, 1e-6, "E0: only the talent's 10% protects her");

        Fixture e0After = new Fixture(0);
        e0After.probeClara();
        e0After.fireAttackFinishedOnClara();
        Assertions.assertEquals(0.9, e0After.probeClara() / dummy, 1e-6, "E0: no 30% zone at all");

        Fixture e4 = new Fixture(4);
        Assertions.assertEquals(0.9, e4.probeClara() / dummy, 1e-6,
                "the attack that triggers it is NOT reduced by the Eidolon (「受到攻击<b>后</b>」)");
        e4.fireAttackFinishedOnClara();
        double after = e4.probeClara();
        System.out.println("[1107] e4 after=" + after + " dummy=" + dummy + " ratio=" + (after / dummy));
        // The talent 10% and the Eidolon 30% are two zones, multiplied (0.9 x 0.7 = 0.63).
        Assertions.assertEquals(0.63, after / dummy, 1e-6,
                "「受到的伤害降低 30%」 - and it is still up until her next turn starts");
    }

    /**
     * Eidolon 3 and 5: the two level clauses the data spells as {@code skill_add_level}.
     */
    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(3, SkillType.SKILL), "E3: 战技等级 +2");
        Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(3, SkillType.COMMON), "E3: 普攻等级 +1");
        Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(5, SkillType.ULTRA), "E5: 终结技等级 +2");
        Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(5, SkillType.TALENT), "E5: 天赋等级 +2");
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

    private static final class Fixture {
        private final Character clara;
        private final Character dummy;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private final Battle dummyBattle;

        private Fixture(int eidolon) {
            clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();

            double defence = clara.getAttribute(AttributeType.DEFENCE).get();
            double health = clara.getMaxHp();
            dummy = Character.fromAttributes("Bare Dummy", health, defence, 100, 100);
            dummyBattle = new Battle(new ArrayList<>(List.of(dummy)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.99));
            dummyBattle.startBattle();
        }

        private double probeClara() {
            double before = clara.getCurrentHp();
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - clara.getCurrentHp();
        }

        private double probeDummy() {
            double before = dummy.getCurrentHp();
            dummyBattle.applyDamage(dummy, new Damage(dummyBattle.enemies().getFirst(), dummy,
                    DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - dummy.getCurrentHp();
        }

        private void fireAttackFinishedOnClara() {
            battle.fire(TriggerPayload.Cast.of(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 200.0,
                    com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED, 0, 0, List.of(clara)));
        }
    }
}
