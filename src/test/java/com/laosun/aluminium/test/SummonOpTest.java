package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The {@code SUMMON} op: a rule bringing its owner's <b>memosprite</b> (忆灵) onto the field (P9-4).
 *
 * <p><b>Where this sits in the chain.</b> {@code resources/memosprites/<cid>.json} says how a memosprite's
 * panel derives from its summoner, {@code Battle.summonMemosprite} puts one on the field, and this op is what
 * lets <b>content</b> ask for it: 「进入战斗时召唤忆灵「长夜」」 (1413) and 「召唤忆灵衣匠」 (1402) are now rule files
 * rather than prose.
 *
 * <p><b>Three design choices, each pinned below.</b>
 * <ol>
 *   <li>No arguments and no {@code target}: a memosprite belongs to its summoner, so there is nothing to point
 *       at — and a stray {@code target} is <b>refused</b> rather than quietly ignored (a stray argument is a
 *       rule that does something other than what the file says);</li>
 *   <li>idempotent per summoner: firing again keeps the memosprite already out, because two copies of it
 *       would be a wrong state the player cannot see. The documents' 「若已在场，则使其生命值回复至上限」 is the
 *       refresh, and that is <b>not</b> modelled — a no-op is the honest stand-in;</li>
 *   <li>a character whose rules use this op but has no memosprite spec is refused <b>when the character is
 *       built</b>, not mid-battle: only the assembly point knows both the cid and the merged rules, since a
 *       relic rule is shared by every wearer.</li>
 * </ol>
 */
public class SummonOpTest {
    private static final double EPS = 1e-6;

    /** 长夜月 — 「进入战斗时召唤忆灵「长夜」」, authored in characters/1413.json. */
    private static final int CASTORICE_LIKE = 1413;
    /** 阿格莱雅 — 「召唤忆灵衣匠」 on her Ultimate, authored in characters/1402.json. */
    private static final int AGLAEA = 1402;
    /** 姬子 — a character with no memosprite spec, for the refusal cases. */
    private static final int NO_MEMOSPRITE = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. The shipped content summons through the real event path
    // ==================================================================

    /** 长夜月 has the memosprite out as soon as the battle starts. */
    @Test
    public void theBattleStartClauseBringsItOut() {
        Character castorice = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        Battle battle = new Battle(List.of(castorice), List.of(monster()), new Random(0));

        Assertions.assertNull(battle.memospriteOf(castorice), "nothing is out before the battle starts");

        battle.startBattle();

        Summon nightfall = battle.memospriteOf(castorice);
        Assertions.assertNotNull(nightfall, "the authored BATTLE_START rule summoned it");
        Assertions.assertEquals("长夜", nightfall.getName());
        Assertions.assertEquals(160, nightfall.getAttribute(
                com.laosun.aluminium.enums.AttributeType.SPEED).get(), EPS,
                "with the panel from its own spec, not a monster row");
        Assertions.assertTrue(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == nightfall),
                "and it is on the action bar: startBattle's processRequests() admits it");
    }

    /** The op is the only thing her file says so far, and the loader reads exactly it. */
    @Test
    public void herFileContainsJustTheSummoningClause() {
        Assertions.assertEquals(1, TriggerTables.of(CASTORICE_LIKE).ruleCount(TriggerEvent.BATTLE_START));
        Assertions.assertFalse(TriggerTables.of(CASTORICE_LIKE).isEmpty());
    }

    /** 阿格莱雅's Ultimate summons 衣匠 — and it is HER Ultimate, not a teammate's. */
    @Test
    public void herUltimateSummonsItAndSomeoneElsesDoesNot() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character ally = CharacterFactory.create(NO_MEMOSPRITE, LEVEL);
        Battle battle = new Battle(List.of(aglaea, ally), List.of(monster()), new Random(0));
        battle.startBattle();

        Assertions.assertNull(battle.memospriteOf(aglaea), "her file has no battle-start rule");

        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, null, 0, 0);
        Assertions.assertNull(battle.memospriteOf(aglaea), "「阿格莱雅」 -- an ally's Ultimate is not hers");

        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);
        Assertions.assertNotNull(battle.memospriteOf(aglaea), "and hers is");
    }

    /**
     * A second firing keeps the <b>same</b> memosprite.
     *
     * <p>This is the op-level half of the idempotence: the refresh the texts describe is not modelled, so the
     * stand-in must at least never produce a second copy of the same unit.
     */
    @Test
    public void summoningAgainKeepsTheSameOne() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(monster()), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);
        Summon first = battle.memospriteOf(aglaea);
        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);

        Assertions.assertSame(first, battle.memospriteOf(aglaea), "still the one");
        Assertions.assertEquals(2, battle.allies.size(), "the hero and the one memosprite");
    }

    // ==================================================================
    // 2. Load-time and build-time refusals
    // ==================================================================

    /** A {@code target} on this op is refused, because it would be ignored rather than used. */
    @Test
    public void aTargetOnSummonIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> summonTable(CASTORICE_LIKE, effectWith("target", "self")));

        Assertions.assertTrue(rejected.getMessage().contains("target"), rejected.getMessage());
    }

    /** A duration is refused: the op acts in one moment. */
    @Test
    public void aDurationOnSummonIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> summonTable(CASTORICE_LIKE, effectWith("turns", 2)));

        Assertions.assertTrue(rejected.getMessage().contains("no duration"), rejected.getMessage());
    }

    /**
     * A character whose rules summon but who has no memosprite spec is refused <b>when it is built</b>.
     *
     * <p>The check is at assembly because that is the only place that knows both things: a rule file knows no
     * cid, and a relic rule is shared by every wearer. Without it, the failure would surface inside
     * {@code startBattle} with a message about a file, long after the rule was written.
     */
    @Test
    public void aSummonRuleWithoutAMemospriteSpecIsRefusedAtAssembly() {
        Character himeko = CharacterFactory.create(NO_MEMOSPRITE, LEVEL);
        TriggerTable summoning = new TriggerTable(NO_MEMOSPRITE, List.of(summonRule()));

        CharacterException refused = Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.requireSummonable(NO_MEMOSPRITE, summoning));

        Assertions.assertTrue(refused.getMessage().contains("SUMMON"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("memosprites"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains(String.valueOf(NO_MEMOSPRITE)),
                "the message names the file to write: " + refused.getMessage());

        // …and the same table passes for a character that does have one.
        Assertions.assertSame(summoning, CharacterFactory.requireSummonable(CASTORICE_LIKE, summoning),
                "only the character without a spec is a problem; the check is about the pair, not the table");
        Assertions.assertNotNull(himeko, "and building her normally is untouched");
    }

    /**
     * The assembly point really calls that check — a rule file alone cannot be trusted to be consistent.
     *
     * <p>Driven through {@code CharacterFactory.create} with a <b>relic</b> whose rules summon (a test-resource
     * fixture, {@code src/test/resources/relic_sets/103.json}) on a character with no memosprite spec. The
     * relic half is the harder one and the reason the check lives at assembly at all: a relic rule is shared by
     * every wearer, so no rule file can know the cid it will be checked against.
     *
     * <p>Without a case like this, "the check is correct" and "the check is never called" look identical.
     */
    @Test
    public void buildingACharacterWhoseEquippedRulesSummonWithoutASpecIsRefused() {
        RelicSuit suit = RelicFactory.suit(103, 5, 15);

        CharacterException refused = Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.create(NO_MEMOSPRITE, LEVEL, true, null, suit));

        Assertions.assertTrue(refused.getMessage().contains("SUMMON"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("relic-set rule may be the source"),
                "the message must point at the equipment, because that is where this rule came from: "
                        + refused.getMessage());
    }

    /**
     * The op still fails loudly at fire time when a table is installed by hand on a spec-less character.
     *
     * <p>Assembly catches the shipped path; this pins the other one, because tests and tools do attach tables
     * directly, and a silent no-op there is exactly what the op must not do.
     */
    @Test
    public void firingItForACharacterWithNoSpecFailsLoudly() {
        Character himeko = CharacterFactory.create(NO_MEMOSPRITE, LEVEL);
        Battle battle = new Battle(List.of(himeko), List.of(monster()), new Random(0));
        battle.startBattle();
        himeko.setTriggerTable(new TriggerTable(NO_MEMOSPRITE, List.of(summonRule())));

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle.fireTriggers(TriggerEvent.BATTLE_START, himeko, null, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("memosprites"), refused.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** A table whose one rule fires {@code SUMMON} at battle start, with the given effect built in. */
    private static TriggerTable summonTable(int cid, EffectSpec effect) {
        return new TriggerTable(cid, List.of(
                TriggerSpecs.rule("BATTLE_START", null, effect)));
    }

    private static TriggerSpec summonRule() {
        return TriggerSpecs.rule("BATTLE_START", null, summonEffect());
    }

    private static EffectSpec summonEffect() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SUMMON");
        return effect;
    }

    /** The same effect plus one extra field, to check that the field is refused. */
    private static EffectSpec effectWith(String field, Object value) {
        EffectSpec effect = summonEffect();
        TriggerSpecs.set(effect, field, value);
        return effect;
    }

    private static Enemy monster() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
