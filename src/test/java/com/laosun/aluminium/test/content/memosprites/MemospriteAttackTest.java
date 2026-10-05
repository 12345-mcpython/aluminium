package com.laosun.aluminium.test.content.memosprites;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A memosprite's <b>attack</b>: its damage is a share of an attribute of its own, not of its summoner.
 *
 * <p><b>Why this is not a rule on the summoning character.</b> A memosprite's damage is written against the
 * memosprite - Evernight (长夜月)'s memosprite skill 1 is "deals to a single enemy ice damage equal to <b>50%</b> of '长夜''s Max HP" - and the panel
 * halves that number on the way in ("长夜" has 50% of her Max HP). Reading the share off the summoner would
 * therefore produce a hit <b>twice</b> as large: a wrong number that looks entirely plausible, which is the
 * kind a test has to catch. So the spec states the attack, {@code SummonFactory} installs it on the memosprite,
 * and the base is read from the memosprite every time it swings.
 *
 * <p><b>What these cases are guarding.</b>
 * <ol>
 *   <li>the shipped attack is Evey (长夜)'s own memosprite skill 1 (element / share / base / shape), not an ATK-based fallback
 *       invented for a unit the text gives no ATK;</li>
 *   <li>the damage really is proportional to the memosprite's Max HP - doubling the panel's share doubles the
 *       hit, with everything else held fixed;</li>
 *   <li>it lands on the <b>opposing camp</b>: ours, so the monsters, never our own side. Sweeping
 *       {@code battle.allies} would only be right while enemies were the only casters;</li>
 *   <li>{@code hits} are separate instances, and a spec that states no attack installs none (the engine does
 *       not fall back to something no document states);</li>
 *   <li>a wrong attack is refused where the spec is read, including "scales off an attribute the panel never
 *       gives it", which would otherwise be a silent zero.</li>
 * </ol>
 */
public class MemospriteAttackTest {
    private static final double EPS = 1e-6;

    /** Evernight (长夜月) - the one shipped memosprite whose document states an attack (memosprite skill 1). */
    private static final int CASTORICE_LIKE = 1413;
    /** Aglaea (阿格莱雅) - used as the summoner for fixture specs; her rule summons only on her ultimate. */
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int OTHER_MONSTER = 8002040;

    // ==================================================================
    // 1. The shipped attack
    // ==================================================================

    /** Evey (长夜) acts with its own memosprite skill 1, and the numbers are that skill's. */
    @Test
    public void theShippedAttackIsTheMemospriteSkillsOwnNumbers() {
        Character summoner = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        Summon evey = SummonFactory.memosprite(summoner);

        Skill attack = evey.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "an attack stated in the spec must reach the summon's COMMON slot");
        Assertions.assertTrue(attack instanceof EnemySkill,
                "a memosprite's attack is the same shape an enemy's is (one attribute × one multiplier): "
                        + attack.getClass().getName());
        EnemySkill direct = (EnemySkill) attack;

        Assertions.assertEquals(DamageElement.ICE, direct.getElement(), "memosprite skill 1 is ice");
        Assertions.assertEquals(0.5, direct.getMultiplier(), EPS, "「50% of Max HP」 - the figure the text quotes");
        Assertions.assertEquals(AttributeType.HEALTH, direct.getBaseAttribute(),
                "its OWN Max HP: the panel halves it on the way in, so a base read off the summoner would be "
                        + "twice this and would still look plausible");
        Assertions.assertEquals(1, direct.getHits(), "memosprite skill 1 is a single segment");
        Assertions.assertNull(attack.getData(),
                "not a character skill: there is no multiplier table for it in the generated data at all");

        Assertions.assertEquals(0.5 * evey.getMaxHp(), 0.5 * 0.5 * summoner.getMaxHp(), EPS,
                "…and the two numbers really are different, so the assertion above is not vacuous");
    }

    /**
     * A spec that states no attack installs none.
     *
     * <p>Not "the engine assumes a basic attack": for a unit the text gives no ATK, an ATK-based fallback would
     * deal a number no document states. Absent means absent, and the demo says so out loud
     * ({@code Main.summonTurn}'s "no attack of its own").
     */
    @Test
    public void aMemospriteWithNoAttackStatedGetsNone() {
        Summon tailor = SummonFactory.memosprite(CharacterFactory.create(AGLAEA, LEVEL));

        Assertions.assertNull(tailor.getSkills().get(SkillType.COMMON),
                "memosprites/" + AGLAEA + ".json states no attack, so nothing is installed");
        Assertions.assertEquals(Camp.PLAYER, tailor.getCamp(), "…and it is still one of ours");
    }

    // ==================================================================
    // 2. What it deals, measured
    // ==================================================================

    /** The attack lands on the monsters - and takes nothing off our own side. */
    @Test
    public void theAttackLandsOnTheOpposingCampAndNotOnOurs() {
        Character summoner = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        Battle battle = new Battle(List.of(summoner), List.of(monster()), new Random(11));
        battle.startBattle();                        // her own rule summons Evey (长夜): the real path, not a fixture
        Summon evey = battle.memospriteOf(summoner);
        Assertions.assertNotNull(evey, "precondition: Evernight (长夜月)'s BATTLE_START rule put it on the field");

        Enemy target = battle.enemyUnits().getFirst();
        double enemyBefore = target.getCurrentHp();

        Cast cast = takeItsTurn(battle, evey, List.of(target));

        Assertions.assertTrue(cast.dealt() > 0, "the memosprite dealt nothing");
        Assertions.assertEquals(enemyBefore - cast.dealt(), target.getCurrentHp(), EPS,
                "every point it dealt came off the monster");
        Assertions.assertEquals(0, cast.ourSideLost(), EPS,
                "…and none of it off our side: the shape dispatch sweeps the caster's opposing camp, not "
                        + "`allies` (the bug this case exists for)");
    }

    /**
     * The damage is proportional to the memosprite's <b>own</b> Max HP.
     *
     * <p>Two specs that differ only in the panel's HEALTH share, the same summoner, the same seed. Doubling the
     * share doubles the derived Max HP and must double the hit - the ratio is exact because a direct hit's only
     * randomness is the crit roll, and both runs draw it from the same seed. Comparing two runs, rather than
     * comparing against an absolute number, is what keeps this case from re-implementing the damage zones.
     */
    @Test
    public void theDamageIsProportionalToItsOwnMaxHp() {
        double half = damageFrom(CharacterFactory.create(AGLAEA, LEVEL), healthShareSpec(0.5));
        double whole = damageFrom(CharacterFactory.create(AGLAEA, LEVEL), healthShareSpec(1.0));

        Assertions.assertTrue(half > 0, "precondition: the smaller share still dealt damage");
        Assertions.assertEquals(2.0, whole / half, 1e-6,
                "twice the Max HP, twice the hit (" + half + " → " + whole + ")");
    }

    /**
     * {@code hits} are separate instances: 3 x 20% is the same total as 1 x 60%.
     *
     * <p>Crit is pinned to 0 so the total is exact - otherwise the two specs would draw different crit rolls and
     * the case would prove nothing. If {@code hits} were ignored (always one segment), the first total would be
     * a third of the second and this fails.
     */
    @Test
    public void hitsAreSeparateInstances() {
        Character summoner = CharacterFactory.create(AGLAEA, LEVEL);
        summoner.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));   // no crits: an exact total

        double threeSegments = damageFrom(summoner, attackSpec("Ice", "HEALTH", 0.2, 3, "SingleAttack"));
        double oneSegment = damageFrom(summoner, attackSpec("Ice", "HEALTH", 0.6, 1, "SingleAttack"));

        Assertions.assertTrue(oneSegment > 0, "precondition: the single segment dealt damage");
        Assertions.assertEquals(oneSegment, threeSegments, EPS,
                "3 × 20% and 1 × 60% of the same Max HP are the same total");
    }

    /** An AOE attack reaches <b>every</b> monster - the multi-target half of "opposing camp". */
    @Test
    public void anAoeAttackReachesEveryMonster() {
        Character summoner = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy first = monster();
        Enemy second = otherMonster();
        Battle battle = battleWith(summoner, List.of(first, second), 7);
        Summon evey = place(battle, summoner, attackSpec("Ice", "HEALTH", 0.3, 1, "AoEAttack"));

        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();

        // Both monsters are handed in, as a caller that knows the shape would: the first is the main target and
        // the shape expands from there. `dealt` is then the loss across the list it was given.
        Cast cast = takeItsTurn(battle, evey, List.of(first, second));

        Assertions.assertTrue(first.getCurrentHp() < firstBefore, "the main target was hit");
        Assertions.assertTrue(second.getCurrentHp() < secondBefore, "and so was the one it was not aimed at");
        Assertions.assertEquals(firstBefore - first.getCurrentHp() + secondBefore - second.getCurrentHp(),
                cast.dealt(), EPS, "the damage is both monsters' loss");
        Assertions.assertEquals(0, cast.ourSideLost(), EPS, "still nothing off our own side");
    }

    // ==================================================================
    // 2b. The attack is announced - once, and to the right owner
    // ==================================================================

    /**
     * An {@code "until": "next_attack"} buff <b>on the memosprite</b> ends when the memosprite attacks.
     *
     * <p>Written as a rule, because that is how such a buff ever gets onto a summon: "the wearer and their memosprite" is two
     * rules, the second one {@code target: "summon"}. Before the engine announced a summon's attack, this buff
     * was never consumed - it stayed for the rest of the battle, silently.
     */
    @Test
    public void theMemospriteOwnUntilBuffEndsWhenItAttacks() {
        Battle battle = battleWithUntilBuffsOn(true, false);
        Summon evey = battle.memospriteOf(battle.characters.getFirst());

        Assertions.assertEquals(0.3, critDamageOf(evey), EPS, "precondition: the rule put the buff on it");

        takeItsTurn(battle, evey, List.of(battle.enemyUnits().getFirst()));

        Assertions.assertEquals(0, critDamageOf(evey), EPS,
                "「lasts until after the next attack ends」 on the summon means the summon's own attack, and that has happened");
    }

    /**
     *  ...and the <b>summoner's</b> own {@code until} buff is <b>not</b> consumed by the memosprite's attack.
     *
     * <p>This is the owner test doing its job on a new path. A memosprite's attack is a real attack by a unit of
     * ours, so the whole side hears about it; but "until the wearer's next attack" is about the <em>wearer</em> attacking,
     * and a summon swinging is not its master swinging. Without the owner test, the announcement added for the
     * memosprite would silently eat its summoner's buff.
     */
    @Test
    public void theSummonersOwnUntilBuffSurvivesTheMemospriteAttack() {
        Battle battle = battleWithUntilBuffsOn(false, true);
        Character summoner = battle.characters.getFirst();
        Summon evey = battle.memospriteOf(summoner);
        double summonerBefore = critDamageOf(summoner);

        takeItsTurn(battle, evey, List.of(battle.enemyUnits().getFirst()));

        Assertions.assertEquals(0.3, summonerBefore - bareCritDamage(), EPS,
                "precondition: the rule put the buff on the summoner");
        Assertions.assertEquals(summonerBefore, critDamageOf(summoner), EPS,
                "a summon's attack is not its summoner's attack");
    }

    /**
     * An enemy's attack is announced to our side too, and <b>nothing of ours reacts</b>: every listener is
     * keyed to its own owner.
     *
     * <p>Pinned because the announcement is broadcast to the whole side whatever the attacker's camp - which is
     * what lets a future "when I am attacked" buff exist - and this is the case that says that widening did not
     * quietly start consuming our buffs. Driven through the enemy's own skill, which is the path in question.
     */
    @Test
    public void anEnemyAttackConsumesNothingOnOurSide() {
        Battle battle = battleWithUntilBuffsOn(true, true);
        Character summoner = battle.characters.getFirst();
        Summon evey = battle.memospriteOf(summoner);
        Enemy monster = battle.enemyUnits().getFirst();

        monster.getSkills().get(SkillType.COMMON).execute(battle, monster, List.of(summoner));
        battle.processRequests();

        Assertions.assertTrue(summoner.getCurrentHp() < summoner.getMaxHp(),
                "precondition: the monster's attack really landed");
        Assertions.assertEquals(0.3, critDamageOf(evey), EPS, "the summon's buff is untouched");
        Assertions.assertEquals(bareCritDamage() + 0.3, critDamageOf(summoner), EPS,
                "and so is the summoner's");
    }

    /**
     * The announcement says <b>which targets</b> the attack connected with and <b>how much</b> it settled, once.
     *
     * <p>Every shipped listener answers a question about itself ("is this my attack?" / "does this end my
     * buff?"), so the payload is only visible from outside - via a listener that records instead of reacting.
     * Without this case, the targets a summon's attack reports would be asserted nowhere, and "one attack, one
     * announcement" would too: firing it per segment would still consume the buff exactly once.
     */
    @Test
    public void theAnnouncementCarriesTheTargetsAndTheTotal() {
        Character summoner = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy first = monster();
        Enemy second = otherMonster();
        Battle battle = battleWith(summoner, List.of(first, second), 7);
        Summon evey = place(battle, summoner, attackSpec("Ice", "HEALTH", 0.3, 1, "AoEAttack"));
        AttackRecorder recorder = new AttackRecorder();
        summoner.getBuffManager().addBuff(recorder);

        Cast cast = takeItsTurn(battle, evey, List.of(first, second));

        Assertions.assertEquals(1, recorder.calls, "one attack, one announcement — not one per target or segment");
        Assertions.assertEquals(List.of(first, second), recorder.targets,
                "the targets it actually connected with, in hit order");
        Assertions.assertEquals(cast.dealt(), recorder.total, EPS,
                "and the sum the instances settled");
    }

    // ==================================================================
    // 2c. The attack pushes the toughness bar (the toughness-reduction half)
    // ==================================================================

    /**
     * A memosprite's attack removes the toughness its document states - "toughness reduction, single target, 30".
     *
     * <p>Two affordances, both stated rather than hidden: the target's weakness set is rewritten to include the
     * attack's element (toughness only comes off a weakness, which is the engine's own rule and the demo's scene 1
     * does the same thing), and the monster's bar is 60, so 30 cannot break it and the delta is exact.
     */
    @Test
    public void theAttackRemovesTheToughnessItsDocumentStates() {
        Character summoner = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        Enemy target = monster();
        target.setStanceWeak(Set.of(DamageElement.ICE));
        Battle battle = new Battle(List.of(summoner), List.of(target), new Random(11));
        battle.startBattle();
        Summon evey = battle.memospriteOf(summoner);
        double before = target.getStance();

        takeItsTurn(battle, evey, List.of(target));

        Assertions.assertEquals(before - 30, target.getStance(), EPS,
                "「toughness reduction, single target, 30」 is a number beside the damage numbers, not something to infer");
    }

    /**
     * An attack that states no toughness leaves the bar alone.
     *
     * <p>The fixture's spec has no {@code stance}, which is what every enemy attack means too (they do not attack a
     * toughness bar) - so 0 is the default and the enemy path is unchanged.
     */
    @Test
    public void anAttackWithoutAStatedToughnessLeavesTheBarAlone() {
        Character summoner = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy target = monster();
        target.setStanceWeak(Set.of(DamageElement.ICE));
        Battle battle = battleWith(summoner, List.of(target), 7);
        Summon evey = place(battle, summoner, attackSpec("Ice", "HEALTH", 0.3, 1, "SingleAttack"));
        double before = target.getStance();

        takeItsTurn(battle, evey, List.of(target));

        Assertions.assertEquals(before, target.getStance(), EPS, "no stance stated, no toughness removed");
    }

    // ==================================================================
    // 3. Refusals
    // ==================================================================

    /**
     * A wrong attack is rejected where the spec is read, each with the consequence in the message.
     *
     * <p>The one that matters most is {@code base} not being in the panel: a panel entry <b>replaces</b> the
     * attribute, so the memosprite's value is 0, the attack fires, the log line prints, and every hit deals
     * exactly nothing.
     */
    @Test
    public void anUnusableAttackIsRejectedAtLoadTime() {
        assertAttackRejected(attackSpec("Firey", "HEALTH", 0.5, 1, null), "element");
        assertAttackRejected(attackSpec("Ice", null, 0.5, 1, null), "base");
        assertAttackRejected(attackSpec("Ice", "ATTACK", 0.5, 1, null), "never states");
        assertAttackRejected(attackSpec("Ice", "HEALTH_PERCENT", 0.5, 1, null), "builder-only");
        assertAttackRejected(attackSpec("Ice", "HEALTH", 0.0, 1, null), "positive");
        assertAttackRejected(attackSpec("Ice", "HEALTH", 0.5, 0, null), "hits");
        assertAttackRejected(attackSpec("Ice", "HEALTH", 0.5, 1, "Restore"), "does not deal damage");
        assertAttackRejected(attackSpec("Ice", "HEALTH", 0.5, 1, "Nonsense"), "does not deal damage");
    }

    /** The spec-taking seam validates too, so a bad spec cannot slip in through the one caller a test uses. */
    @Test
    public void theSpecTakingSeamValidatesTheAttackAsWell() {
        Character summoner = CharacterFactory.create(AGLAEA, LEVEL);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SummonFactory.memosprite(summoner, attackSpec("Ice", "ATTACK", 0.5, 1, null)));

        Assertions.assertTrue(refused.getMessage().contains("ATTACK"), refused.getMessage());
    }

    /** The optional block is optional: a panel-only spec is still valid, through the loader and the seam. */
    @Test
    public void aPanelOnlySpecIsStillValid() {
        MemospriteSpec spec = panelOnlySpec();

        Assertions.assertDoesNotThrow(() -> Memosprites.validate(spec, "test"), "no attack block, no rejection");
        Assertions.assertNull(SummonFactory.memosprite(CharacterFactory.create(AGLAEA, LEVEL), spec)
                .getSkills().get(SkillType.COMMON));
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** What one cast cost each side: the damage dealt, and how much of it came off our own camp. */
    private record Cast(double dealt, double ourSideLost) {
    }

    /**
     * A listener that records what an attack announcement carried.
     *
     * <p>A buff, because that is the only way the engine delivers
     * {@link com.laosun.aluminium.models.event.AttackEvent}; everything here is a no-op except the recording, so
     * attaching it cannot change the battle.
     */
    private static final class AttackRecorder extends AbstractBuff {
        private List<CanHit> targets = List.of();
        private double total;
        private int calls;

        private AttackRecorder() {
            super(99, false);                        // long enough not to tick away during the case
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            calls++;
            targets = List.copyOf(hitTargets);
            total = totalDamage;
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
        }
    }

    /**
     * Advances to the summon's turn, casts its attack, and reports both sides' loss across <b>the cast alone</b>
     * (measured around the cast, not around the loop, so who acted before it cannot affect the result).
     */
    private static Cast takeItsTurn(Battle battle, Summon summon, List<CanHit> targets) {
        Skill attack = summon.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "the summon has no attack to take a turn with");

        for (int action = 0; action < 20; action++) {
            battle.stepForward();
            if (battle.isOver()) {
                break;
            }
            Signal current = battle.queue.getCurrentActor();
            battle.beforeMove();
            if (current != null && current.getCanHit() == summon) {
                double targetBefore = targets.stream().mapToDouble(CanHit::getCurrentHp).sum();
                double ourSideBefore = ourSideHp(battle);
                Assertions.assertTrue(battle.performAction(attack, targets),
                        "the summon's action was refused by the engine");
                battle.processRequests();
                battle.afterMove();
                return new Cast(targetBefore - targets.stream().mapToDouble(CanHit::getCurrentHp).sum(),
                        ourSideBefore - ourSideHp(battle));
            }
            battle.afterMove();                      // somebody else's turn: let it pass unplayed
        }
        Assertions.fail("the summon never reached its turn within 20 actions");
        return null;                                 // unreachable: fail throws
    }

    /** One memosprite, one monster, one cast - the damage dealt, for comparing two specs. */
    private static double damageFrom(Character summoner, MemospriteSpec spec) {
        Enemy target = monster();
        Battle battle = battleWith(summoner, List.of(target), 7);
        Summon evey = place(battle, summoner, spec);
        return takeItsTurn(battle, evey, List.of(target)).dealt();
    }

    private static Battle battleWith(Character summoner, List<Enemy> enemies, long seed) {
        Battle battle = new Battle(List.of(summoner), List.copyOf(enemies), new Random(seed));
        battle.startBattle();
        return battle;
    }

    /**
     * Evernight (长夜月) with a hand-built table: summon "长夜" at battle start, then hang an
     * {@code "until": "next_attack"} CRIT DMG buff on the memosprite and/or on herself.
     *
     * <p>A hand-built table rather than a file: a character fixture under {@code src/test/resources} would be
     * picked up by the suites that sweep every character, and the point here is one rule, not a character.
     * {@code MODIFY_ATTR} on a ratio attribute is in percentage points, so the buff is {@code +0.3} = +30%.
     */
    private static Battle battleWithUntilBuffsOn(boolean onMemosprite, boolean onSummoner) {
        List<TriggerSpec> rules = new ArrayList<>();
        rules.add(TriggerSpecs.rule("BATTLE_START", null, summon()));
        if (onMemosprite) {
            rules.add(TriggerSpecs.rule("BATTLE_START", null, untilCritDamage("summon")));
        }
        if (onSummoner) {
            rules.add(TriggerSpecs.rule("BATTLE_START", null, untilCritDamage("self")));
        }
        Character summoner = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        summoner.setTriggerTable(new TriggerTable(CASTORICE_LIKE, rules));
        return battleWith(summoner, List.of(monster()), 7);
    }

    private static EffectSpec summon() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SUMMON");
        return effect;
    }

    private static EffectSpec untilCritDamage(String target) {
        EffectSpec effect = TriggerSpecs.modifyAttr("CRIT_ATTACK", 0.3, null, null, null, null, target);
        TriggerSpecs.set(effect, "until", List.of("next_attack"));
        return effect;
    }

    /** The memosprite's/character's own CRIT DMG (spelled {@code CRIT_ATTACK} here) - 0 on a memosprite. */
    private static double critDamageOf(CanHit unit) {
        return unit.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double bareCritDamage() {
        return CharacterFactory.create(CASTORICE_LIKE, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    /** Puts a fixture memosprite on the field exactly the way {@code Battle.summonMemosprite} does. */
    private static Summon place(Battle battle, Character master, MemospriteSpec spec) {
        Summon summon = SummonFactory.memosprite(master, spec);
        summon.setMaster(master);
        battle.allies.add(summon);
        battle.addRequestItems.add(summon);
        battle.processRequests();
        return summon;
    }

    private static double ourSideHp(Battle battle) {
        return battle.allies.stream().mapToDouble(CanHit::getCurrentHp).sum();
    }

    private static List<MemospriteSpec.Panel> panel(double healthShare) {
        return List.of(new MemospriteSpec.Panel("HEALTH", healthShare, null),
                new MemospriteSpec.Panel("SPEED", null, 999.0));      // acts first, so the loop is short
    }

    private static MemospriteSpec healthShareSpec(double healthShare) {
        return new MemospriteSpec("fixture", "MemospriteAttackTest", null, panel(healthShare),
                new MemospriteSpec.Attack("Ice", "HEALTH", 0.5, 1, "SingleAttack"));
    }

    private static MemospriteSpec attackSpec(String element, String base, Double percent, Integer hits,
                                             String shape) {
        return new MemospriteSpec("fixture", "MemospriteAttackTest", null, panel(0.5),
                new MemospriteSpec.Attack(element, base, percent, hits, shape));
    }

    private static MemospriteSpec panelOnlySpec() {
        return new MemospriteSpec("fixture", "MemospriteAttackTest", null, panel(0.5));
    }

    private static void assertAttackRejected(MemospriteSpec spec, String expectedInMessage) {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memosprites.validate(spec, "test"));
        Assertions.assertTrue(rejected.getMessage().contains(expectedInMessage),
                "expected the message to mention '" + expectedInMessage + "': " + rejected.getMessage());
    }

    private static Enemy monster() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    private static Enemy otherMonster() {
        return EnemyFactory.create(OTHER_MONSTER, 90, 1);
    }
}
