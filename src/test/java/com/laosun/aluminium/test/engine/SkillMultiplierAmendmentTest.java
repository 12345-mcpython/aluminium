package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code RAISE_SKILL_MULTIPLIER}: 「<b>终结技的伤害倍率提高 N%</b>」 - a sentence about the SKILL ROW's share.
 *
 * <p><b>Why it needed an op of its own.</b> The number belongs to the data row the cast reads, so neither a zone
 * ({@code BOOST_DAMAGE} adds into the damage-boost chain - measured 2.96 where the sentence says 3.4) nor a rule
 * ({@code MODIFY_RULE} amends rules, and the Ultimate's damage is not one) can express it. The only place that can apply it
 * is the settlement, which is where this op's value is read.
 *
 * <p>The enemy-count window is judged at that same read, which is what lets one sentence state disjoint tiers
 * (1401 Eidolon 6: 3+/2/1 enemies ⇒ +140%/+250%/+400%).
 */
public class SkillMultiplierAmendmentTest {
    private static final int HERTA = 1401;
    private static final int LEVEL = 80;

    /**
     * A filed amendment multiplies the row share: 2.4 ⇒ ×3.4 on the Ultimate's damage.
     */
    @Test
    public void theRowShareIsMultiplied() {
        Run plain = new Run();
        Run amended = new Run();
        amended.herta.amendSkillMultiplier(SkillType.ULTRA, 2.4, null, null);
        System.out.println("[multiplier] ult damage: plain=" + plain.ultDamage() + " amended=" + amended.ultDamage());
        Assertions.assertEquals(3.4, amended.ultDamage() / plain.ultDamage(), 1e-6,
                "「伤害倍率提高 240%」 is x3.4 on the row share");
    }

    /**
     * The window is read at settlement, so the SAME filed amendment pays differently as the enemy count changes.
     */
    @Test
    public void theEnemyWindowIsReadWhenTheRowIsPriced() {
        Run one = new Run();
        one.herta.amendSkillMultiplier(SkillType.ULTRA, 4.0, null, 1);
        double withOneEnemy = one.ultDamage();

        Run three = new Run();
        three.herta.amendSkillMultiplier(SkillType.ULTRA, 4.0, null, 1);
        three.addEnemies(2);
        double withThreeEnemies = three.ultDamage();

        Run bare = new Run();
        double baseline = bare.ultDamage();
        System.out.println("[multiplier] baseline=" + baseline + " ; 1 enemy=" + withOneEnemy
                + " ; 3 enemies=" + withThreeEnemies);
        Assertions.assertEquals(5.0, withOneEnemy / baseline, 1e-6,
                "one enemy is inside the window (1+4.0)");
        Assertions.assertEquals(1.0, withThreeEnemies / baseline, 1e-6,
                "three enemies are outside it - the same filed amendment now pays nothing");
    }

    /**
     * A slot it was not filed on is untouched.
     */
    @Test
    public void otherSlotsAreNotAffected() {
        Run run = new Run();
        double skillBefore = run.skillDamage();
        run.herta.amendSkillMultiplier(SkillType.ULTRA, 2.4, null, null);
        Assertions.assertEquals(skillBefore, run.skillDamage(), 1e-6,
                "the amendment names ULTRA, so the Skill's row is not scaled");
    }

    /**
     * The op is also exercised through the LOADER (field names and validation), not only through the API.
     */
    @Test
    public void theOpLoadsAndFilesTheAmendment() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RAISE_SKILL_MULTIPLIER");
        TriggerSpecs.set(effect, "skill", "ULTRA");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "minEnemies", 2);
        // The construction is the assertion: a wrong field name or a refused shape throws here.
        new TriggerTable(HERTA, List.of(TriggerSpecs.rule("BATTLE_START", null, effect)));
    }

    // ==================================================================

    private static final class Run {
        private final Character herta;
        private final Battle battle;

        Run() {
            herta = CharacterFactory.create(HERTA, LEVEL);
            // Fixed crit: the ratio assertions must not depend on a roll.
            herta.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(herta), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        double ultDamage() {
            return damageOf(SkillType.ULTRA);
        }

        double skillDamage() {
            return damageOf(SkillType.SKILL);
        }

        private double damageOf(SkillType slot) {
            // The victim must be ON the battlefield: damage settled against a loose Enemy moves nobody HP.
            Enemy victim = (Enemy) battle.enemies().getFirst();
            double before = victim.getCurrentHp();
            battle.castImmediate(herta.getSkills().get(slot), herta, List.of(victim));
            battle.processRequests();
            return before - victim.getCurrentHp();
        }

        /** Puts extra enemies on the field, one at a time (each is a late arrival). */
        void addEnemies(int count) {
            for (int i = 0; i < count; i++) {
                battle.addEnemy(BattleFixture.monster());
            }
            battle.processRequests();
        }
    }
}
