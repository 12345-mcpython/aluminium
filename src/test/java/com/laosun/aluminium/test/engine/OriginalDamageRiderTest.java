package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * "equal to <b>the original damage</b> X%" - the rider settles to the share the text states.
 *
 * <p>A {@code DAMAGE} effect's value is a <b>base</b>: settlement multiplies it by the instance's zones again, so a
 * scale that reads an already-settled amount has to divide by the triggering instance's own factor
 * ({@code toValue() / skillBaseValue}). Without that division a 40% share lands as 0.4 x the shared zone factor, which
 * is the kind of wrong number that looks entirely plausible.
 *
 * <p>Note: The long detour this cost was <b>this judge's own fault</b>: an earlier version replaced the character's table
 * with a hand-built one to control variables, and that also dropped her {@code level_convention}, so the cast ran at the
 * Lv1 row while the rider used the full ATK - a clean factor 2 that read like a zone discrepancy. Both sides now run at
 * whatever level the character is actually at, and the assertion below is the one that would have caught it at once.
 */
public class OriginalDamageRiderTest {
    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;

    /**
     * A share lands as that share of what the triggering instance settled.
     */
    @Test
    public void theRiderSettlesTheShareTheTextStates() {
        double baseline = loss(0.0);
        double full = loss(1.0);
        double forty = loss(0.4);

        Assertions.assertTrue(baseline > 0, "precondition: her attack deals damage (" + baseline + ")");
        Assertions.assertEquals(baseline, full - baseline, baseline * 1e-6,
                "a 100% rider settles what the original settled: " + baseline + " -> " + full);
        Assertions.assertEquals(baseline * 0.4, forty - baseline, baseline * 1e-6,
                "and Himeko (姬子)'s Eidolon 6 share (40%) lands as 40%, not as 40% x the zone factor");
    }

    /**
     * Himeko (姬子)'s shipped Eidolon 6, through her own file.
     *
     * <p>Note: <b>The baseline is NOT "her total damage at E0".</b> In the same battle another of her file's rules lands a
     * 1064.438828 instance, so the E0 total (11482.69) is mostly damage this clause must <b>not</b> scale: "the original damage" is
     * the damage of <b>that cast</b> -- the ultimate's own 18.25531 -- and the two guards ({@code damage_is_attack}
     * and {@code from_skill ULTRA}) are what keep the clause off everything else. Reading the comparison the other way
     * round cost two rounds: the expectation was 0.8 x the total instead of 0.8 x what the ultimate itself settled.
     */
    @Test
    public void hisEidolonSixShips() {
        double e6 = ultimateLoss(6);
        double e0 = ultimateLoss(0);
        // measured again on 2026-10-19 after her three traces landed (道标/星火/灼热), with crit switched off below:
        double ultimateOwnInstance = 933.7324506605253;
        // ⚠ The toughness damage reduction reaches this clause in a shape worth writing down, measured (ratio 1.0000000004):
        // the rider is 2 x 40% of the ultimate's own instance and the two instances take the reduction ONCE between them.
        // That is not an accident of arithmetic - "the original damage" is read when DEALING_DAMAGE fires, which is step 1
        // of `Battle.assemble`, BEFORE the reduction zone joins at step 3.5 - and the rider's own instances then settle
        // through the zones like any other. So the base is the pre-reduction value and the 0.9 lands once.
        double reduction = 1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION;

        double expected = 2 * 0.4 * ultimateOwnInstance * reduction;
        System.out.println("[e6_rider] e0=" + e0 + " e6=" + e6 + " delta=" + (e6 - e0)
                + " ; 2 x 40% x the original, times the toughness reduction = " + expected
                + " ; ratio = " + ((e6 - e0) / expected));
        Assertions.assertEquals(expected, e6 - e0, Math.max(1e-6, expected * 1e-9),
                "\"additionally deal 2 instances of damage ... equal to 40% of the original damage\" = 2 x 40% x the ULTIMATE's own instance, "
                        + "and NOT of the damage another rule contributes in the same battle: " + e0 + " -> " + e6);
    }

    /**
     * Her ultimate alone, at the given eidolon rank (her own table, so the level convention is in force).
     */
    private static double ultimateLoss(int eidolon) {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, eidolon);
        // 2026-10-19: 行迹「道标」现在会在血线之上加暴击率，那会让这一发的暴击抽签变掉、原始伤害跟着变；
        // 这条判据量的是「2 × 40% × 原伤害」，所以把暴击关掉（与 HimekoChargeTest 的同一手法）。
        her.setAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE,
                new com.laosun.aluminium.models.DoubleValue(0));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        her.getSkills().get(SkillType.ULTRA).execute(battle, her, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static double loss(double share) {
        Character owner = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, 0);
        owner.setTriggerTable(new TriggerTable(HIMEKO, share > 0 ? List.of(rider(share)) : List.of()));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        owner.getSkills().get(SkillType.COMMON).execute(battle, owner, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    private static com.laosun.aluminium.beans.TriggerSpec rider(double share) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "original_damage");
        TriggerSpecs.set(effect, "percent", share);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpecs.set(effect, "critRate", 0.0);
        TriggerSpecs.set(effect, "critDamage", 0.0);
        return TriggerSpecs.rule("DAMAGE_SETTLED", List.of("actor == self", "damage_is_attack"), effect);
    }
}
