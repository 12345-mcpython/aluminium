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
 * {@code CAST_SKILL}: "使<那个单位>立即施放 1 次…" - the op that lets a rule command somebody else to cast.
 *
 * <p><b>Why this file exists now.</b> The op was added on 2026-09-30 by loosening {@code commandSummon}, and on
 * 2026-10-02 the <b>first attempt to make it fire</b> found that it could never have worked: it read the multiplier
 * through {@code multiplierOf}, which requires {@code damage_param}, and <b>all nine</b> rules using it (none of which
 * states one) died with a {@code NullPointerException}; it also hard-coded {@code DamageType.NORMAL} and gave a blast's
 * neighbours the centre's number. Nothing caught any of it, because no test ever let the op run - a green suite is not
 * a verification.
 *
 * <p>Now it hands the cast to {@link com.laosun.aluminium.models.skill.SkillExecutor}, the engine's own path, so there
 * is <b>one</b> reading of a skill's row. What each case below pins:
 * <ul>
 *   <li>the commanded cast IS that unit's own cast - same units, same numbers (and <b>not zero</b>, or "the same"
 *       would be satisfied by two silent no-ops);</li>
 *   <li>a BLAST keeps the row's two columns apart - the defect that survived here after §24.10 fixed it elsewhere;</li>
 *   <li>the damage TYPE comes from the skill's data, not from the op (observed through a {@code damage_type: ELATION}
 *       taken-modifier, which is a fact about the instance rather than about the op);</li>
 *   <li>two shipped rules actually deal damage - 1404's turn-start auto-cast and 1504's commanded talent;</li>
 *   <li>and the fields that would be a <b>second</b> reading of the row are refused at load time rather than ignored.</li>
 * </ul>
 */
public class CastSkillTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** Himeko (姬子): damage whose number we do not care about, used as the rule owner wherever the owner must not matter. */
    private static final int OWNER = 1003;
    /** Dan Heng (丹恒): the unit being commanded. His ULTRA is a damaging single-target skill. */
    private static final int CASTER = 1002;
    /** Mydei (万敌): his SKILL is a BLAST, i.e. the one shape whose two columns differ. */
    private static final int BLAST_CASTER = 1404;
    /** Sparkle (火花): her ELATION_SKILL is the only kind of skill that settles as a non-NORMAL damage type. */
    private static final int ELATION_CASTER = 1501;
    /** Dan Heng - Permansor Terrae (丹恒-腾荒): the commanded cast of a NON-damaging skill (his skill is a {@code Defence} shield). */
    private static final int DHPT = 1414;
    /** Ruan Mei (阮-梅): her skill is a {@code Support} buff with no {@code skill_effects.json} entry -- undeliverable. */
    private static final int UNDELIVERABLE = 1303;

    // ==================================================================
    // 1. One reading of the row
    // ==================================================================

    /**
     * The commanded cast deals exactly what that unit's own cast would: same actor, same skill, same victims.
     *
     * <p>The two battles are built identically (same seed, same units) and differ only in <b>who is asked</b> - a rule
     * commands Tingyun (停云) to cast her ultimate, versus Tingyun casting it herself through the engine's own entry point. The
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
     * @param commanded {@code true} = Himeko's rule commands Tingyun to cast; {@code false} = Tingyun casts it herself
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
     * A BLAST's neighbours take the row's <b>second</b> column, not the centre's.
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

        // Note: A commanded cast is aimed at the FIRST living opponent (the op's `target` names who CASTS, not who is
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
     * <p>Observed rather than asserted on the op's source: a rule that raises "受到的欢愉伤害" by 100% is applied to
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
     * Commands Sparkle's Elation skill and answers the enemies' total HP loss.
     *
     * @param elationVulnerability also apply "受到的欢愉伤害提高 100%" to the enemies first
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

    /** 1404 Mydei "自身回合开始时自动施放[弑王成王]" - the rule fires and the skill really lands. */
    @Test
    public void hisTurnStartAutoCastDealsDamage() {
        Character him = CharacterFactory.create(BLAST_CASTER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(him), List.of(enemy), new Random(0));
        battle.startBattle();
        // Note: "[血仇]状态期间…自身回合开始时自动施放[弑王成王]": the gate is part of the sentence, so the scene
        // enters the state. The rule used to fire unconditionally, which is the defect that gate corrects.
        him.getBuffManager().addBuff(
                new com.laosun.aluminium.models.buff.StateBuff("血仇", 9, true));
        battle.processRequests();

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.TURN_START, him, him, 0, 0);

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "the commanded cast moved the enemy's HP (" + before + " -> " + enemy.getCurrentHp() + ")");
    }

    /** 1504 Cipher "随后立即对[饲饵]发动 1 次获得强化的天赋追加攻击" - the commanded TALENT lands. */
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
     * Note: A {@code damage_param} on this op is a load-time refusal, not a silent no-op.
     *
     * <p>It is the exact field whose absence used to make the op explode: the old implementation needed it and the
     * loader never asked. Now the op reads the skill's own row, so naming a column would be a second reading - and a
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

    /** 1414 Dan Heng - Permansor Terrae technique "下一次战斗开始时自动对持有[同袍]的角色施放1次战技，此次战技不消耗战技点". */
    @Test
    public void aCommandedSupportCastShieldsOurSide() {
        // Note: The Bondmate is 1414 HIMSELF (his technique grants it to him), and he is deliberately NOT first in the
        // party: an aim that fell back to "the first ally" would re-designate the wrong unit, which is what
        // `cast_target: holder_of:同袍` exists to prevent.
        Character ally = CharacterFactory.create(CASTER, LEVEL);
        Character him = CharacterFactory.create(DHPT, LEVEL);
        // Note: Read BEFORE the battle starts: the shield is computed inside the cast, i.e. BEFORE the post-cast
        // SKILL_CAST event that raises his ATK by the Shenxiu (神秀) trace -- so the number to compare against is this one.
        double attackBefore = him.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        Battle battle = new Battle(List.of(ally, him), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.markTechniqueUsed(him);
        double pointsBefore = battle.getSkillPoints();
        battle.startBattle();

        Assertions.assertTrue(him.getBuffManager().hasState("同袍"),
                "precondition: the technique put 【同袍】 on him (the aim's true side)");
        Assertions.assertFalse(ally.getBuffManager().hasState("同袍"),
                "and the commanded cast must NOT have moved it onto the first ally");

        double expected = 0.2 * attackBefore + 400;   // the Lv10 row the document quotes (20% ATK + 400)
        Assertions.assertEquals(expected, him.getShield(), 1e-6,
                "the shield is the skill's OWN Lv10 row -- which also pins that `level_convention` ran BEFORE the "
                        + "commanded cast (at Lv1 the row would be 0.14 x ATK + 100)");
        Assertions.assertEquals(expected, ally.getShield(), 1e-6, "「为我方全体提供…护盾」: the ally too");
        // Note: THE discriminating assertion for the op's side choice: a non-damaging cast reaches OUR camp. The ally's
        // shield above cannot show it (his own SKILL_CAST rule grants one to all_allies anyway), but a shield on the
        // ENEMY can only come from the commanded cast.
        Assertions.assertEquals(0.0, battle.enemies.getFirst().getShield(), 1e-9,
                "「为我方全体提供…护盾」 -- the commanded cast must never shield the other side");
        Assertions.assertEquals(pointsBefore, battle.getSkillPoints(),
                "「此次战技不消耗战技点」 -- a commanded cast never spends one (that is the caller's act)");
        Assertions.assertEquals(attackBefore * 1.15, Attack(him), 1e-6,
                "and the cast's own SKILL_CAST event ran 神秀 in the same breath: +15% of his ATK, on the holder "
                        + "(one commanded cast, both halves of the clause)");
    }

    /** 1414's ATK right now - a named helper so the assertion above reads as the claim it is. */
    private static double Attack(Character who) {
        return who.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
    }

    /**
     * Note: A skill the engine has no definition for is refused <b>loudly</b> rather than cast into nothing.
     *
     * <p>1303 Ruan Mei's skill is a {@code Support} buff and {@code skill_effects.json} has no entry for her, so there is
     * nothing to deliver: the commanded cast would fire, announce itself, and change nothing at all - the exact
     * silence this engine refuses. (1414's own skill IS deliverable: it is a {@code Defence} shield.)
     */
    @Test
    public void anUndeliverableSupportCastIsRefused() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(command("SKILL"))));
        Character caster = CharacterFactory.create(UNDELIVERABLE, LEVEL);
        Battle battle = new Battle(List.of(owner, caster), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ULT_CAST, owner, caster, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("skill_effects.json"), refused.getMessage());
    }

    /** Note: {@code cast_target} is read by this op alone, so another op stating it is refused while the file is read. */
    @Test
    public void castTargetOnAnotherOpIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "castTarget", "self");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), effect))));

        Assertions.assertTrue(refused.getMessage().contains("cast_target"), refused.getMessage());
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
