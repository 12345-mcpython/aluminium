package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "等同于<b>原伤害</b> X%" - the rider settles to the share the text states (2026-10-02).
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
    private static final int MONSTER = 1002011;

    /** A share lands as that share of what the triggering instance settled. */
    @Test
    public void theRiderSettlesTheShareTheTextStates() {
        double baseline = loss(0.0);
        double full = loss(1.0);
        double forty = loss(0.4);

        Assertions.assertTrue(baseline > 0, "precondition: her attack deals damage (" + baseline + ")");
        Assertions.assertEquals(baseline, full - baseline, baseline * 1e-6,
                "a 100% rider settles what the original settled: " + baseline + " -> " + full);
        Assertions.assertEquals(baseline * 0.4, forty - baseline, baseline * 1e-6,
                "and 姬子's 星魂 6 share (40%) lands as 40%, not as 40% x the zone factor");
    }

    /**
     * 姬子's shipped 星魂 6, through her own file.
     *
     * <p>Note: <b>The baseline is NOT "her total damage at E0".</b> In the same battle another of her file's rules lands a
     * 1064.438828 instance, so the E0 total (11482.69) is mostly damage this clause must <b>not</b> scale: "原伤害" is
     * the damage of <b>that cast</b> -- the ultimate's own 18.25531 -- and the two guards ({@code damage_is_attack}
     * and {@code from_skill ULTRA}) are what keep the clause off everything else. Reading the comparison the other way
     * round cost two rounds: the expectation was 0.8 x the total instead of 0.8 x what the ultimate itself settled.
     */
    @Test
    public void hisEidolonSixShips() {
        double e6 = ultimateLoss(6);
        double e0 = ultimateLoss(0);
        double ultimateOwnInstance = 718.255731;      // measured: her ultimate's own settled damage in this fixture

        Assertions.assertEquals(2 * 0.4 * ultimateOwnInstance, e6 - e0, 1e-6,
                "「额外造成2次伤害…等同于原伤害40%」 = 2 x 40% x the ULTIMATE's own instance, and NOT of the "
                        + "10764.438828 another rule contributes in the same battle: " + e0 + " -> " + e6);
    }

    /** Her ultimate alone, at the given eidolon rank (her own table, so the level convention is in force). */
    private static double ultimateLoss(int eidolon) {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, eidolon);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(her), List.of(enemy), new Random(0));
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
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
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
