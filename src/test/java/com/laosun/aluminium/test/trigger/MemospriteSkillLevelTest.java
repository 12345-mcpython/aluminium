package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;

/**
 * A <b>memosprite's</b> skill level: 「忆灵技等级+1」, and reading one of its rows from somebody else's event.
 *
 * <h2>Why a memosprite needs its own spellings</h2>
 * A character's skills are stored by {@code SkillType}; a memosprite's run to the eighteen-plus <b>data slots</b> the
 * game's servant table uses ({@code Summon.skillsByDataSlot}). So neither {@code RAISE_SKILL_LEVEL}'s {@code skill}
 * (a {@code SkillType}) nor {@code percent_from_skill_param}'s {@code "<SKILLTYPE>:<index>"} can name one, and both
 * needed a spelling of their own: {@code memosprite: true} + {@code skill_id} for the raise, and
 * {@code percent_from_summon_param} / {@code amount_from_summon_param} ({@code "<DATA SLOT>:<index>"}) for the reads.
 *
 * <h2>Where the raise lives, and why</h2>
 * The raise is filed on the <b>summoner</b>, not on the memosprite: {@code RAISE_SKILL_LEVEL} is validated onto
 * {@code BATTLE_START}, and at that moment no memosprite exists yet - it is summoned later, possibly more than once.
 * {@code CanHit.skillLevel} therefore asks the master for a memosprite skill's bonus, which also means a re-summon
 * inherits the raise without a copy step.
 *
 * <p><b>Why a custom spec in one case.</b> Every shipped memosprite is declared at level 10 and every one of its tables
 * has ten rows - so a container-level raise clamps straight back to 10 ({@link com.laosun.aluminium.entity.CanHit#skillLevel}
 * clamps to the table's row count, which IS the documented cap). To watch the number move, that case builds the same
 * spec at a lower declared level, i.e. the state a future 「忆灵技等级+1」 would move.
 */
public class MemospriteSkillLevelTest {
    private static final double EPS = 1e-6;

    /**
     * Cyrene (昔涟) - her memosprite is Demiurge (德谬歌), and its odes live in the high data slots.
     */
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * The ode of romance's data slot (its own row, ten of them).
     */
    private static final int ODE = 14;

    // ==================================================================
    // 1. The raise is filed on the summoner
    // ==================================================================

    /**
     * A {@code BATTLE_START} raise with {@code memosprite: true} files a level on the <b>summoner</b>, under the
     * memosprite's data slot - no memosprite has to exist yet.
     */
    @Test
    public void theRaiseIsFiledOnTheSummonerUnderTheMemospriteDataSlot() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        cyrene.setTriggerTable(new TriggerTable(CYRENE, List.of(
                TriggerSpecs.rule("BATTLE_START", null, raiseMemospriteLevel(ODE, 1)))));

        new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded()).startBattle();

        Assertions.assertEquals(1, cyrene.memospriteSkillLevelBonus(ODE),
                "「忆灵技等级+1」 is filed on the character, keyed by the memosprite's DATA SLOT");
        Assertions.assertEquals(0, cyrene.memospriteSkillLevelBonus(1),
                "and only the slot the rule names moved");
    }

    // ==================================================================
    // 2. The resolver reads it, and the table is the cap
    // ==================================================================

    /**
     * A memosprite's level is its declared level plus the summoner's raise.
     *
     * <p>Note: The spec is rebuilt at a declared level of 5 so the raise is observable - see the class note on why a
     * shipped memosprite (declared at 10 of 10 rows) can only be clamped.
     */
    @Test
    public void aMemospriteReadsItsDeclaredLevelPlusTheSummonersRaise() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        MemospriteSpec declaredAtFive = withDeclaredLevel(Memosprites.of(CYRENE), ODE, 5);

        Summon plain = com.laosun.aluminium.enemy.SummonFactory.memosprite(cyrene, declaredAtFive);
        // Note: The factory builds, the BATTLE links (`Battle.summonMemosprite` sets the master). A test that skips the
        // battle has to set it itself, or the level resolver has no summoner to ask.
        plain.setMaster(cyrene);
        Assertions.assertEquals(5, plain.skillLevel(plain.skillAt(ODE)),
                "no raise, so the level is exactly what the spec declares");

        cyrene.raiseMemospriteSkillLevel(ODE, 1);
        Summon raised = com.laosun.aluminium.enemy.SummonFactory.memosprite(cyrene, declaredAtFive);
        raised.setMaster(cyrene);
        // Note: The RECEIVER is the memosprite - that is who casts, and `skillLevel` is the resolver `SkillExecutor`
        // calls on the caster. Asking the character instead finds no such skill in its own map and answers the bare
        // declared level (measured: 5, not 6).
        Assertions.assertEquals(6, raised.skillLevel(raised.skillAt(ODE)),
                "the raise reaches the memosprite through its summoner - and a RE-summon inherits it");
        Assertions.assertNotEquals(5, raised.skillLevel(raised.skillAt(ODE)),
                "the resolver every read site goes through is the one that moved");
    }

    /**
     * A raise past the table's row count is clamped to it, exactly as a character's is: the row count IS the documented
     * cap.
     */
    @Test
    public void aRaisePastTheTableIsClampedToTheTablesRows() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        MemospriteSpec shipped = Memosprites.of(CYRENE);
        Summon atTen = com.laosun.aluminium.enemy.SummonFactory.memosprite(cyrene, shipped);
        int rows = atTen.skillAt(ODE).getData().getSkills().size();
        Assertions.assertEquals(rows, atTen.skillLevel(atTen.skillAt(ODE)),
                "precondition: the shipped spec declares the last row");

        cyrene.raiseMemospriteSkillLevel(ODE, 1);
        Summon afterRaise = com.laosun.aluminium.enemy.SummonFactory.memosprite(cyrene, shipped);
        Assertions.assertEquals(rows, afterRaise.skillLevel(afterRaise.skillAt(ODE)),
                "a raise cannot read a row the data does not have");
    }

    // ==================================================================
    // 3. Reading a memosprite row from somebody else's event
    // ==================================================================

    /**
     * {@code percent_from_summon_param: "<DATA SLOT>:<index>"} takes the share out of the owner's memosprite, at that
     * skill's current level - on an event the memosprite did not cause ({@code TURN_START}), where
     * {@code percent_from_cast_param} would have no cast to look at.
     */
    @Test
    public void aShareCanBeReadFromTheOwnersMemoSprite() {
        Scene scene = new Scene();
        var rows = scene.ode().getData().getSkills();
        int level = scene.demiurge().skillLevel(scene.ode());
        double expected = rows.get(level - 1).get(0) * scene.cyrene().getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(expected > 0, "precondition: the ode's #1 is a non-zero share");

        double gained = scene.measure("percent", ODE + ":0", null);

        System.out.println("[summon_param] level = " + level + " ; row = " + rows.get(level - 1) + " ; gained " + gained);
        Assertions.assertEquals(expected, gained, 1e-6,
                "「等同于其 #1% 生命上限」 out of the memosprite's OWN row, times the summoner's Max HP");
    }

    /**
     * {@code amount_from_cast_param: <index>} is the flat sibling: the constant term comes from the skill that produced
     * the event, which here is the memosprite's own cast.
     */
    @Test
    public void aConstantCanBeReadFromTheCastSkill() {
        Scene scene = new Scene();
        var rows = scene.ode().getData().getSkills();
        int level = scene.demiurge().skillLevel(scene.ode());
        double expected = rows.get(level - 1).get(0);

        double gained = scene.measure("cast", null, 0);

        Assertions.assertEquals(expected, gained, 1e-6,
                "a zero share plus the casting skill's #1 is exactly that member (this is the CONSTANT spelling, so the "
                        + "scale's share contributes 0)");
    }

    // ==================================================================
    // 4. Fail fast at load time
    // ==================================================================

    /**
     * Each way of spelling a memosprite raise wrong is refused where the file is read.
     */
    @Test
    public void aMemospriteRaiseIsCheckedAtLoad() {
        // both halves: `skill` names a character slot, `memosprite` says the slot is a memosprite's
        EffectSpec both = raiseMemospriteLevel(ODE, 1);
        TriggerSpecs.set(both, "skill", "TALENT");
        assertRefused(both, "memosprite");

        // no slot at all
        EffectSpec noSlot = raiseMemospriteLevel(ODE, 1);
        TriggerSpecs.set(noSlot, "skillId", null);
        assertRefused(noSlot, "skill_id");

        // `skill_id` without the flag would raise nothing, since a character's slots are SkillTypes
        EffectSpec noFlag = raiseMemospriteLevel(ODE, 1);
        TriggerSpecs.set(noFlag, "memosprite", null);
        assertRefused(noFlag, "memosprite");
    }

    /**
     * The reads are checked too: the slot is a positive data slot and the index a zero-based column, and a constant
     * still has exactly one source.
     */
    @Test
    public void theMemospriteReadsAreCheckedAtLoad() {
        EffectSpec slotZero = derivedModifier();
        TriggerSpecs.set(slotZero, "percentFromSummonParam", "0:0");
        assertRefused(slotZero, "data slot");

        EffectSpec notAnIndex = derivedModifier();
        TriggerSpecs.set(notAnIndex, "percentFromSummonParam", "14:x");
        assertRefused(notAnIndex, "index");

        EffectSpec twoFlats = new EffectSpec();
        TriggerSpecs.set(twoFlats, "op", "HEAL");
        TriggerSpecs.set(twoFlats, "amount", 100.0);
        TriggerSpecs.set(twoFlats, "amountFromSummonParam", "14:0");
        assertRefused(twoFlats, "flat source");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * Cyrene, an ally, and her memosprite on the field; one rule under test, installed on her.
     */
    private static final class Scene {
        private final Character cyrene;
        private final Battle battle;
        private final Summon demiurge;
        private double before;

        private Scene() {
            this.cyrene = CharacterFactory.create(CYRENE, LEVEL);
            Battle standing = new Battle(List.of(cyrene, CharacterFactory.create(ALLY, LEVEL)),
                    List.of(BattleFixture.monster()), BattleFixture.seeded());
            standing.startBattle();
            standing.processRequests();
            this.demiurge = standing.summonServant(cyrene);
            standing.processRequests();
            this.battle = standing;
        }

        private Character cyrene() {
            return cyrene;
        }

        private Summon demiurge() {
            return demiurge;
        }

        private Skill ode() {
            Skill ode = demiurge.skillAt(ODE);
            Assertions.assertNotNull(ode, "precondition: the memosprite carries data slot " + ODE);
            return ode;
        }

        /**
         * One fire (or one cast) with the given spelling on an {@code ATK} modifier, measured as the delta.
         */
        private double measure(String spelling, String summonSpelling, Integer castParam) {
            EffectSpec effect = derivedModifier();
            if ("percent".equals(spelling)) {
                TriggerSpecs.set(effect, "percentFromSummonParam", summonSpelling);
            } else {
                TriggerSpecs.set(effect, "percent", 0.0);
                TriggerSpecs.set(effect, "amountFromCastParam", castParam);
            }
            cyrene.setTriggerTable(new TriggerTable(CYRENE, List.of(
                    TriggerSpecs.rule("percent".equals(spelling) ? "TURN_START" : "CAST_SETUP", null, effect))));

            before = cyrene.getAttribute(AttributeType.ATTACK).get();
            if ("percent".equals(spelling)) {
                battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, null, null, 0, 0));
            } else {
                SkillExecutor.execute(battle, ode(), demiurge, List.of(battle.characters().get(1)));
            }
            battle.processRequests();
            return cyrene.getAttribute(AttributeType.ATTACK).get() - before;
        }
    }

    /**
     * A permanent {@code ATK} modifier derived from HEALTH - the magnitude is what the spelling supplies.
     */
    private static EffectSpec derivedModifier() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "self_attr:HEALTH");
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    /**
     * A {@code RAISE_SKILL_LEVEL} that raises the owner's memosprite data slot.
     */
    private static EffectSpec raiseMemospriteLevel(int dataSlot, int amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RAISE_SKILL_LEVEL");
        TriggerSpecs.set(effect, "memosprite", Boolean.TRUE);
        TriggerSpecs.set(effect, "skillId", dataSlot);
        TriggerSpecs.set(effect, "amount", (double) amount);
        return effect;
    }

    private static void assertRefused(EffectSpec effect, String expectedInMessage) {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(CYRENE, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))));
        Assertions.assertTrue(refused.getMessage().contains(expectedInMessage),
                "expected the message to contain '" + expectedInMessage + "', got: " + refused.getMessage());
    }

    /**
     * The shipped spec with one skill's declared level replaced, everything else untouched.
     */
    private static MemospriteSpec withDeclaredLevel(MemospriteSpec spec, int slot, int level) {
        List<MemospriteSpec.SkillRow> rows = spec.skills().stream()
                .map(row -> row.slot() == slot ? new MemospriteSpec.SkillRow(slot, level) : row)
                .toList();
        return new MemospriteSpec(spec.name(), spec.source(), spec.note(), spec.panel(), spec.attack(),
                spec.aggro(), spec.servantId(), rows, spec.resources());
    }
}
