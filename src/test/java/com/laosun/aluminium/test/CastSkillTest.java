package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
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
 * {@code CAST_SKILL}: 「使<那个单位>立即施放 1 次…」 — the op that lets a rule command somebody else to cast.
 *
 * <p><b>Why this file exists now.</b> The op was added on 2026-09-30 by loosening {@code commandSummon}, and on
 * 2026-10-02 the <b>first attempt to make it fire</b> found that it could never have worked: it read the multiplier
 * through {@code multiplierOf}, which requires {@code damage_param}, and <b>all nine</b> rules using it (none of which
 * states one) died with a {@code NullPointerException}; it also hard-coded {@code DamageType.NORMAL} and gave a blast's
 * neighbours the centre's number. Nothing caught any of it, because no test ever let the op run — a green suite is not
 * a verification.
 *
 * <p>Now it hands the cast to {@link com.laosun.aluminium.models.skill.SkillExecutor}, the engine's own path, so there
 * is <b>one</b> reading of a skill's row. What each case below pins:
 * <ul>
 *   <li>the commanded cast IS that unit's own cast — same units, same numbers (and <b>not zero</b>, or "the same"
 *       would be satisfied by two silent no-ops);</li>
 *   <li>a BLAST keeps the row's two columns apart — the defect that survived here after §24.10 fixed it elsewhere;</li>
 *   <li>the damage TYPE comes from the skill's data, not from the op (observed through a {@code damage_type: ELATION}
 *       taken-modifier, which is a fact about the instance rather than about the op);</li>
 *   <li>two shipped rules actually deal damage — 1404's turn-start auto-cast and 1504's commanded talent;</li>
 *   <li>and the fields that would be a <b>second</b> reading of the row are refused at load time rather than ignored.</li>
 * </ul>
 */
public class CastSkillTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** 姬子: damage whose number we do not care about, used as the rule owner wherever the owner must not matter. */
    private static final int OWNER = 1003;
    /** 丹恒: the unit being commanded. His ULTRA is a damaging single-target skill. */
    private static final int CASTER = 1002;
    /** 万敌: his SKILL is a BLAST, i.e. the one shape whose two columns differ. */
    private static final int BLAST_CASTER = 1404;
    /** 火花: her ELATION_SKILL is the only kind of skill that settles as a non-NORMAL damage type. */
    private static final int ELATION_CASTER = 1501;

    // ==================================================================
    // 1. One reading of the row
    // ==================================================================

    /**
     * The commanded cast deals exactly what that unit's own cast would: same actor, same skill, same victims.
     *
     * <p>The two battles are built identically (same seed, same units) and differ only in <b>who is asked</b> — a rule
     * commands 停云 to cast her ultimate, versus 停云 casting it herself through the engine's own entry point. The
     * control matters: a comparison of two no-ops also reads as "identical", so the damage must be positive.
     */
    @Test
    public void aCommandedCastIsThatUnitsOwnCast() {
        double commanded = enemyDamage(true);
        double own = enemyDamage(false);

        Assertions.assertTrue(own > 0, "precondition: 停云's ULTRA deals damage at all (" + own + ")");
        Assertions.assertEquals(own, commanded, own * 1e-9,
                "the commanded cast (" + commanded + ") must be the same cast as her own (" + own + ")");
    }

    /**
     * Fires one ULT_CAST and answers the enemy's HP loss.
     *
     * @param commanded {@code true} = 姬子's rule commands 停云 to cast; {@code false} = 停云 casts it herself
     */
    private static double enemyDamage(boolean commanded) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character caster = CharacterFactory.create(CASTER, LEVEL);
        Assertions.assertTrue(caster.getSkills().get(SkillType.ULTRA).getData().getEffect().isDamaging(),
                "precondition: the commanded unit's ULTRA is a damaging skill (the op refuses the others)");
        if (commanded) {
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(command("ULTRA"))));
        } else {
            owner.setTriggerTable(TriggerTable.EMPTY);
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, caster), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        if (commanded) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, owner, caster, 0, 0);
        } else {
            caster.getSkills().get(SkillType.ULTRA).execute(battle, caster, List.of(enemy));
        }
        return before - enemy.getCurrentHp();
    }

    /**
     * ⭐ A BLAST's neighbours take the row's <b>second</b> column, not the centre's.
     *
     * <p>This is the defect that was fixed on the caster-side path in §24.10 (1008's row is {@code [1.92, 0.96]}) and
     * that {@code CAST_SKILL} kept, because it hand-built its own attack with one multiplier for every victim.
     *
     * <p>The expected ratio is read from the skill's own row rather than written here: the claim is "column 0 to the
     * centre, column 1 to the neighbours", and hard-coding 2.0 would also pass on a row that happens to be 2.0.
     */
    @Test
    public void aBlastKeepsTheRowsTwoColumnsApart() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(command("SKILL"))));
        Character caster = CharacterFactory.create(BLAST_CASTER, LEVEL);
        var data = caster.getSkills().get(SkillType.SKILL).getData();
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillEffectType.BLAST, data.getEffect(),
                "precondition: 万敌's SKILL is the blast this case is about");

        Enemy left = EnemyFactory.create(MONSTER, 90, 1);
        Enemy centre = EnemyFactory.create(MONSTER, 90, 1);
        Enemy right = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, caster), List.of(left, centre, right), new Random(0));
        battle.startBattle();
        double[] before = {left.getCurrentHp(), centre.getCurrentHp(), right.getCurrentHp()};

        // ⚠ A commanded cast is aimed at the FIRST living opponent (the op's `target` names who CASTS, not who is
        // hit), so the blast is centred on the left unit and its only neighbour is the middle one.
        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, caster, 0, 0);

        double centreHits = before[0] - left.getCurrentHp();
        double neighbourHits = before[1] - centre.getCurrentHp();
        double thirdHits = before[2] - right.getCurrentHp();
        var row = data.getSkills().get(caster.skillLevel(caster.getSkills().get(SkillType.SKILL)) - 1);
        double expected = row.get(0) / row.get(1);

        Assertions.assertTrue(centreHits > 0, "the blast lands on its main target (" + centreHits + ")");
        Assertions.assertEquals(expected, centreHits / neighbourHits, 1e-6,
                "column 0 goes to the centre and column 1 to the neighbour: " + centreHits + " / " + neighbourHits
                        + " should be " + expected);
        Assertions.assertEquals(0.0, thirdHits, 1e-9,
                "and a blast reaches only the centre's neighbours, so the third unit is untouched");
    }

    // ==================================================================
    // 2. The type is data
    // ==================================================================

    /**
     * The instance's damage <b>type</b> follows the skill, not the op: an Elation skill settles as {@code ELATION}.
     *
     * <p>Observed rather than asserted on the op's source: a rule that raises 「受到的欢愉伤害」 by 100% is applied to
     * the enemies, and the commanded Elation cast must then land twice as hard. If the op still stamped
     * {@code NORMAL} (its old, hard-coded behaviour), the modifier would match nothing and the two numbers would be
     * equal.
     */
    @Test
    public void anElationSkillSettlesAsElationDamage() {
        double plain = elationCastDamage(false);
        double boosted = elationCastDamage(true);

        Assertions.assertTrue(plain > 0, "precondition: the Elation skill deals damage (" + plain + ")");
        Assertions.assertEquals(2.0, boosted / plain, 1e-6,
                "「受到的欢愉伤害提高 100%」 applies only if the instance really is ELATION: "
                        + plain + " -> " + boosted);
    }

    /**
     * Commands 火花's Elation skill and answers the enemies' total HP loss.
     *
     * @param elationVulnerability also apply 「受到的欢愉伤害提高 100%」 to the enemies first
     */
    private static double elationCastDamage(boolean elationVulnerability) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        java.util.List<com.laosun.aluminium.beans.TriggerSpec> rules = new java.util.ArrayList<>();
        if (elationVulnerability) {
            EffectSpec taken = new EffectSpec();
            TriggerSpecs.set(taken, "op", "MODIFY_DAMAGE_TAKEN");
            TriggerSpecs.set(taken, "percent", 1.0);
            TriggerSpecs.set(taken, "damageType", "ELATION");
            TriggerSpecs.set(taken, "target", "all_enemies");
            TriggerSpecs.set(taken, "permanent", true);
            rules.add(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), taken));
        }
        rules.add(command("ELATION_SKILL"));
        owner.setTriggerTable(new TriggerTable(OWNER, rules));

        Character caster = CharacterFactory.create(ELATION_CASTER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, caster), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, caster, 0, 0);
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // 3. Shipped content, no scaffold
    // ==================================================================

    /** ⭐ 1404 万敌 「自身回合开始时自动施放【弑王成王】」 — the rule fires and the skill really lands. */
    @Test
    public void hisTurnStartAutoCastDealsDamage() {
        Character him = CharacterFactory.create(BLAST_CASTER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(him), List.of(enemy), new Random(0));
        battle.startBattle();

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.TURN_START, him, him, 0, 0);

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "the commanded cast moved the enemy's HP (" + before + " -> " + enemy.getCurrentHp() + ")");
    }

    /** ⭐ 1504 不死途 「随后立即对【饲饵】发动 1 次获得强化的天赋追加攻击」 — the commanded TALENT lands. */
    @Test
    public void hisUltimateCommandsHisTalentRightNow() {
        Character him = CharacterFactory.create(1504, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(him), List.of(enemy), new Random(0));
        battle.startBattle();

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, him, him, 0, 0);

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "the commanded talent moved the enemy's HP (" + before + " -> " + enemy.getCurrentHp() + ")");
    }

    // ==================================================================
    // 4. The fields it does NOT read are refused
    // ==================================================================

    /**
     * ⚠ A {@code damage_param} on this op is a load-time refusal, not a silent no-op.
     *
     * <p>It is the exact field whose absence used to make the op explode: the old implementation needed it and the
     * loader never asked. Now the op reads the skill's own row, so naming a column would be a second reading — and a
     * field the engine ignores without a word is the failure mode this project ranks worst.
     */
    @Test
    public void aRowArgumentOnCastSkillIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "CAST_SKILL");
        TriggerSpecs.set(effect, "skill", "ULTRA");
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpecs.set(effect, "damageParam", 0);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), effect))));

        Assertions.assertTrue(refused.getMessage().contains("damage_param"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule that commands the resolved target to cast {@code slot}, right now. */
    private static com.laosun.aluminium.beans.TriggerSpec command(String slot) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "CAST_SKILL");
        TriggerSpecs.set(effect, "skill", slot);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), effect);
    }
}
