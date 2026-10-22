package com.laosun.aluminium.test.content.characters.himeko;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Himeko's 秘技 「不完全燃烧」: the gate, the magnitude, and that it is <b>Fire-only</b>.
 *
 * <p>「使用秘技后，制造1片持续15秒的特殊领域。与处于特殊领域内的敌人进入战斗后，有100%的基础概率使敌方目标受到的<b>火属性</b>伤害
 * 提高10%，持续2回合。」
 *
 * <p>⚠ <b>Why the fire/ice pair is the point.</b> Before this rule her clause had no spelling: {@code MODIFY_DAMAGE_TAKEN}'s only
 * scope was a damage TYPE ({@code DOT}, {@code BREAK}, {@code ADDITIONAL}, {@code ULTRA}), and the attribute vocabulary offered
 * {@code ALL_TYPE_RESISTANCE} (every element). The cheap version of this rule -- a plain all-type vulnerability -- would have said
 * 10% more of EVERY element: a wrong number with no symptom, and nothing else in the suite would have noticed, because the rule
 * would still fire at battle start and still produce a zone.
 */
public class HimekoTechniqueTest {

    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * What the vulnerable area reads when nothing has raised it.
     *
     * <p>⚠ Measured, and the reason an earlier version of this test failed against a working rule: the area is a MULTIPLIER, so an
     * untouched hit reads {@code 1.0} (bare 1.0, one +0.1 -> 1.1, two -> 1.3). "No zone" is 1.0, not 0.
     */
    private static final double NO_ZONE = 1.0;

    /**
     * ⭐ The gate: a declared technique raises the Fire damage the enemies take by 10%.
     *
     * <p>The pair is asserted together, because a rule gated on {@code self has_state 秘技} can only be trusted against its own
     * control: "the zone appeared" alone would pass for the wrong reason if something else also applied it.
     */
    @Test
    public void aDeclaredTechniqueRaisesTheFireDamageTheEnemiesTake() {
        double off = elementVulnerability(false, DamageElement.FIRE);
        double on = elementVulnerability(true, DamageElement.FIRE);

        Assertions.assertEquals(NO_ZONE, off, EPS,
                "「使用秘技后」 -- without the technique the zone must not exist at all");
        Assertions.assertEquals(0.1, on - NO_ZONE, EPS,
                "「使敌方目标受到的火属性伤害提高10%」 -- 10%, and of the damage-taken zone rather than a boost");
    }

    /**
     * ⭐ It is FIRE-only: the same battle leaves Ice and Physical damage untouched.
     *
     * <p>⚠ This is the case the element scope exists for. A 10% zone applying to every element would be wrong against every
     * non-Fire attacker in the party, and the only way to tell is to compare the elements against each other.
     */
    @Test
    public void itLeavesOtherElementsUntouched() {
        Assertions.assertEquals(0.1, elementVulnerability(true, DamageElement.FIRE) - NO_ZONE, EPS,
                "Fire is raised");
        Assertions.assertEquals(NO_ZONE, elementVulnerability(true, DamageElement.ICE), EPS,
                "and Ice is not -- 「火属性」 names one element, not all of them");
        Assertions.assertEquals(NO_ZONE, elementVulnerability(true, DamageElement.PHYSICAL), EPS,
                "nor Physical");
    }

    /**
     * The zone carries the duration the clause states.
     *
     * <p>⚠ Asserted as "the countdown it was given", not as "it expires after N of my calls": measured, a carrier's buffs are ticked
     * by the turn loop on ITS OWN turn, and {@code Battle.afterMove()} acts on the battle's current actor rather than on a unit
     * handed to it -- an earlier version of this case called it repeatedly and watched a 2-turn zone sit unchanged through six
     * calls, failing against a working rule.
     */
    @Test
    public void theZoneCarriesTheStatedDuration() {
        Battle battle = battle(true);
        Enemy enemy = battle.enemyUnits().get(0);

        int duration = enemy.getBuffManager().activeBuffs().stream()
                .mapToInt(buff -> buff.duration())
                .max()
                .orElse(-1);
        Assertions.assertEquals(2, duration, "「持续2回合」 -- the zone the technique applies lasts two turns");
    }

    /**
     * The vulnerability rate one element's hit collects from the enemies' own buffs.
     *
     * @param technique whether Himeko's technique was declared
     * @param element   the element of the hit
     * @return the vulnerable area's value (1.0 = nothing raised it)
     */
    private static double elementVulnerability(boolean technique, DamageElement element) {
        Battle battle = battle(technique);
        Enemy enemy = battle.enemyUnits().get(0);
        Damage damage = new Damage(null, enemy, element, DamageType.NORMAL, 1000);
        // ⚠ The unit's own entry point, which is the path settlement takes: `CanHit.onDamage` walks its own buffs, so this
        // cannot drift from what a real hit would collect. Casting instead would fold in resistance, defence and crit as well, and
        // this test's claim is about the zone alone.
        enemy.onDamage(battle, damage);
        return damage.vulnerableArea().raw().get();
    }

    /**
     * One started battle, optionally with her technique declared.
     *
     * @param technique whether she used it
     * @return the battle
     */
    private static Battle battle(boolean technique) {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(himeko), List.of(enemy), BattleFixture.alwaysRoll(1.0));
        if (technique) {
            battle.markTechniqueUsed(himeko);
        }
        battle.startBattle();
        return battle;
    }

    /**
     * ⭐ The SETTLED number rises by exactly 10% for Fire, and not at all for Ice.
     *
     * <p>⚠ The other cases read the zone's own multiplier, which proves only that it was INJECTED. This one reads what the engine
     * actually settles, because `Battle.applyDamage` is where a zone turns into damage: measured, Fire `550.799986` becomes
     * `605.879985` -- a ratio of exactly 1.1 -- while Ice stays `359.999991` to the last digit. A zone attached to an instance whose
     * settlement ignored it would pass every other case here and fail this one.
     */
    @Test
    public void theSettledFireDamageRisesByTenPercent() {
        double fireOff = settled(false, DamageElement.FIRE);
        double fireOn = settled(true, DamageElement.FIRE);
        double iceOff = settled(false, DamageElement.ICE);
        double iceOn = settled(true, DamageElement.ICE);

        Assertions.assertEquals(1.1, fireOn / fireOff, 1e-9,
                "「使敌方目标受到的火属性伤害提高10%」 -- the settled Fire damage must scale by exactly 1.1: "
                        + fireOff + " -> " + fireOn);
        Assertions.assertEquals(iceOff, iceOn, 0.0,
                "and Ice must be BIT-IDENTICAL, not merely close: an element scope is a filter, not a multiplier to trim. "
                        + iceOff + " vs " + iceOn);
    }

    /**
     * The damage the engine actually settles for one element's hit, against a nominal 1000 base.
     *
     * <p>⚠ Through {@link Battle#applyDamage}, the settlement entry point, so this goes through the whole zone pipeline -- defence,
     * resistance, boosts and the vulnerable area together -- rather than summing the zone by hand.
     *
     * @param technique whether the technique was declared
     * @param element   the element of the hit
     * @return the settled amount
     */
    private static double settled(boolean technique, DamageElement element) {
        Battle battle = battle(technique);
        Enemy enemy = battle.enemyUnits().get(0);
        return battle.applyDamage(enemy,
                new Damage(battle.characters().get(0), enemy, element, DamageType.NORMAL, 1000));
    }
}
