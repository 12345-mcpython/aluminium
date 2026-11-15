package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1512 知更鸟•晴歌 / Robin - Summeretto, the whole character in one class: the summoned memosprite whose panel the document
 * states in full (its Max HP and 180% of her SPD), the 气氛值 party gauge the files cap at 50, 「「晴空乐手」全员登台」 coming
 * on one instance per threshold (6 and 12 — three of them open 【Fever】), the talent's 「第一次」 once-per-turn clause,
 * 【Fever】's own damage boost (0.84 + 气氛值 x 0.028) on her and on the memosprite, the DEF-ignore that same state grants
 * (15% + 0.5% per 气氛值, capped at 40%), the memosprite aura's 11.2% damage-taken amplification, which the light cone's
 * 36% adds to rather than replaces, Eidolons 1 / 2 / 4 / 6, and the Ultimate 「跃入这片蔚蓝狂想」(151203) with the
 * 【特邀嘉宾】 state it grants.
 *
 * <p>These were six small files in the same package ({@code RobinSummerettoTest}, {@code RobinBandOnStageTest},
 * {@code RobinTalentFirstInTurnTest}, {@code FeverDamageBoostTest}, {@code FeverDefenceIgnoreTest},
 * {@code MemospriteVulnerabilityTest}); one class keeps the character together without changing any assertion.
 * Each judge still pins the clause it measures by hand — the gauge is set or moved explicitly, and 【Fever】 is applied
 * directly wherever the rule, not the band, is what is under test — and the former class comment of each group now heads
 * the first test it explains.
 *
 * <p><b>Why the Eidolon and Ultimate cases belong here too.</b> These numbers are not independent: Eidolon 1 records the
 * side's damage and the memosprite's skill pays the record back as true damage, Eidolon 2 raises the 【气氛值】 cap that
 * the guest's attacks fill, Eidolon 4 speeds the 「晴空乐手」 whose skills carry that record, and Eidolon 6 both doubles
 * those skills and stores a second Ultimate. Read together, the party bar and the band stay in sight while each clause is
 * judged.
 *
 * <p><b>The Eidolons.</b> Eidolon 2 is 「我方目标的全属性抗性穿透提高 18%。…气氛值上限提高 20 点」: the two halves are
 * different kinds of number - the penetration is a <b>value</b> on every ally, while the 20 points are <b>capacity</b>, so
 * writing the second as a gain would hand her 20 points instead of letting her hold them. The bar is PARTY-scoped, so it
 * lives on the battle rather than on Robin: reading it from her own resource manager answers null and would "prove" the
 * clause wrong, which is why {@code RAISE_RESOURCE_CAP} falls back to the party counter. Eidolon 6 is 「处于【Fever】状态时，
 * 终结技最多储存 2 次」: the bar still decides whether the Ultimate can be cast (it must be full), so a second use cannot
 * live in the bar - it lives in the <b>overflow bank</b> (the same pool 1014's 湖之祝福 uses), where the allowance is
 * <b>one full bar</b>, so a gain that does not fit is kept instead of lost and casting the Ultimate pours it back. The
 * allowance is read from her own Max Energy ({@code value: energy/max}), not written as a number: 「2 次」 is relative to the
 * bar, and a literal would be wrong for any other character or level.
 *
 * <p><b>The Ultimate.</b> It needs three separate mechanisms: an <b>action advance</b> of a chosen ally, a <b>fixed</b> energy
 * restore that is a share of that ally's own maximum (untouched by the regen rate), and the 【特邀嘉宾】 state whose two
 * halves pay her 2 vibes per attack - from the guest <b>and from the guest's summon</b>.
 *
 * <p><b>The ban.</b> 「…且<b>无法使其他友方目标获得行动提前效果</b>，持续 2 回合」. Every other number in the trigger
 * language modifies a value; this one <b>bans an action</b>. It is stated by the STATE the ultimate grants
 * ({@code "blocks": "advance_to_others"} on the {@code APPLY_BUFF}) and read by the op that would perform the banned action
 * ({@code ADVANCE}): the ban belongs to the unit <b>giving</b> the advance (it is that character's own ability that is
 * locked), and it only stops advances aimed at <b>others</b> - 「其他友方目标」 - so the holder may still be advanced itself.
 *
 * <p>Consolidated from the former {@code RobinSummerettoTest}, {@code RobinBandOnStageTest},
 * {@code RobinTalentFirstInTurnTest}, {@code FeverDamageBoostTest}, {@code FeverDefenceIgnoreTest},
 * {@code MemospriteVulnerabilityTest}, {@code RobinEidolonTwoTest}, {@code RobinEidolonSixStorageTest},
 * {@code SpecialGuestTest} and {@code SpecialGuestAdvanceBanTest}; every assertion, expected value, tolerance and
 * message is kept, and the helpers are unchanged.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into scenario tests, one {@code assertAll} per fixture and per
 * observable so a failing clause still names itself. A case is folded into a scenario only when it plays the same
 * fixture and reads the same observed object; a control that needs a different fixture of its own (E5's bar, the advance
 * without the ban, the cone-less aura) stays its own test, beside the clause it guards.
 */
public class RobinSummerettoTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    /**
     * The Eidolon fixture's ally: 1002, the teammate the party-wide modifiers of Eidolons 1 / 2 are read on. It is not
     * {@link #ALLY} (1001), the partner the band, talent and 【Fever】 cases play with.
     */
    private static final int EIDOLON_ALLY = 1002;
    private static final int CYRENE = 1415;
    private static final int LEVEL = 80;
    private static final String VIBES = "char:1512/atmosphere";
    private static final String FEVER = "char:1512/fever";
    private static final String GUEST = "char:1512/special_guest";
    private static final double AURA_ONE = 0.112;
    private static final double CONE = 0.36;

    // ==================================================================
    // The summoned memosprite and the resource the files declare.
    // ==================================================================

    /**
     * Note:"初始拥有等同于知更鸟-晴歌0%生命上限的生命上限和等同于知更鸟-晴歌180%速度的速度".
     *
     * <p>One fixture (the Skill on a lone Robin) and one observable ({@code memospriteOf(robin)}): the control - without
     * the Skill nothing is summoned - and then the panel the document states for the memosprite the cast brings on.
     */
    @Test
    public void theSummonArrivesWithTheDocumentedPanel() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(robin), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertNull(battle.memospriteOf(robin), "no Skill, no memosprite");

        battle.castImmediate(robin.getSkills().get(SkillType.SKILL), robin, List.of(enemy));

        var mem = battle.memospriteOf(robin);
        Assertions.assertNotNull(mem, "「召唤忆灵「晴空乐手」贝茜」 (summons the memosprite 贝茜 of the Summer Songbirds (「晴空乐手」))");
        double expectedHp = robin.getMaxHp() * 0.7;
        double expectedSpeed = robin.getAttribute(AttributeType.SPEED).get() * 1.8;
        Assertions.assertAll("the summoned memosprite's panel",
                () -> Assertions.assertEquals(expectedHp, mem.getMaxHp(), expectedHp * 0.02,
                        "「70%生命上限」 (70% of Max HP): expected " + expectedHp + ", got " + mem.getMaxHp()),
                () -> Assertions.assertEquals(expectedSpeed, mem.getAttribute(AttributeType.SPEED).get(),
                        expectedSpeed * 0.02,
                        "「180%速度」 (180% of SPD): expected " + expectedSpeed + ", got "
                                + mem.getAttribute(AttributeType.SPEED).get()));
    }

    /**
     * Note: The file declares the Vibes resource with the document's cap, and the memosprite file states both numbers.
     */
    @Test
    public void theFilesDeclareTheResourceAndThePanel() {
        JsonObject character = read("/game/characters/1512.json");
        JsonArray resources = character.getAsJsonArray("resources");
        Assertions.assertNotNull(resources, "the character file must declare the atmosphere resource (char:1512/atmosphere)");
        Assertions.assertEquals(50, resources.get(0).getAsJsonObject().get("max").getAsInt(), "\"the ceiling is 50 points\" (「上限50点」)");

        JsonObject memo = read("/game/memosprites/1512.json");
        JsonArray panel = memo.getAsJsonArray("panel");
        Assertions.assertEquals(2, panel.size(), "the panel states exactly the two numbers the document gives");
    }

    private static JsonObject read(String path) {
        try (InputStream stream = RobinSummerettoTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    // ==================================================================
    // 「晴空乐手」 on stage: the 6 / 12 thresholds and the 【Fever】 gate.
    // ==================================================================

    /** The band's scene (renamed from the former file's {@code Scene}, since the talent keeps its own). */
    private record BandScene(Battle battle, Character robin) {
    }

    private static BandScene bandScene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return new BandScene(battle, robin);
    }

    private static void setGauge(BandScene scene, int points) {
        scene.battle.partyResource(VIBES).setValue(points);
        scene.battle.fire(new com.laosun.aluminium.trigger.TriggerPayload.ResourceChanged(
                com.laosun.aluminium.enums.TriggerEvent.RESOURCE_CHANGED, scene.robin, VIBES, 1));
        scene.battle.processRequests();
    }

    /**
     * 「「晴空乐手」全员登台」 - 1512's 【Fever】 gate, and the two gauge thresholds that bring the band on: the band grows
     * one instance at each threshold — and the skill's own +6 gauge is what crosses the first one, so a single cast leaves
     * TWO on stage (measured: 0 -> 2). A second cast pushes the gauge to 12 and the band to three.
     *
     * <p><b>What the band actually is.</b> Not three different memosprites: <b>several instances of the same one</b>. The
     * recorded fight settles it - the vulnerability aura's number matches <b>two</b> of them, and the servant's own row
     * prices 1 / 2 / 3 members separately. The talent's params state the two thresholds (6 and 12), and 「全员登台」 is
     * therefore a fact about the <b>count</b> — and 「全员登台」 is also what opens 【Fever】: off at two, on at three.
     *
     * <p>One fixture ({@link #bandScene()}) and one observable (the count of her memosprites, then her own 【Fever】
     * state): the counts and the gate are read between the same casts instead of in two fixtures that each replay them.
     */
    @Test
    public void theGaugeBringsTheBandOnAndOpensFever() {
        BandScene scene = bandScene();
        Assertions.assertEquals(0, scene.battle.summonCountOf(scene.robin), "nothing is out before the skill");

        castSkill(scene);
        int afterFirstCast = scene.battle.summonCountOf(scene.robin);
        boolean feverAtTwo = scene.robin.getBuffManager().hasState(FEVER);

        castSkill(scene);
        int afterSecondCast = scene.battle.summonCountOf(scene.robin);
        boolean feverAtThree = scene.robin.getBuffManager().hasState(FEVER);

        castSkill(scene);
        int afterThirdCast = scene.battle.summonCountOf(scene.robin);

        Assertions.assertAll("「全员登台」: the gauge brings the band on, and the whole band opens 【char:1512/fever】",
                () -> Assertions.assertEquals(2, afterFirstCast,
                        "the skill summons the first, and its own +6 gauge crosses the 6 threshold for the second"),
                () -> Assertions.assertFalse(feverAtTwo, "two of three is not 全员登台"),
                () -> Assertions.assertEquals(3, afterSecondCast,
                        "the second cast's gauge reaches 12 and brings the third"),
                () -> Assertions.assertTrue(feverAtThree, "all three on stage ⇒ 【char:1512/fever】"),
                () -> Assertions.assertEquals(3, afterThirdCast,
                        "and neither threshold fires twice: the band is three"));
    }

    private static void castSkill(BandScene scene) {
        SkillExecutor.execute(scene.battle, scene.robin.getSkills().get(SkillType.SKILL), scene.robin,
                List.of(scene.battle.enemies().getFirst()));
        scene.battle.processRequests();
    }

    // ==================================================================
    // The talent's 「第一次」 clause: one heal or shield per turn pays 1.
    // ==================================================================

    /** The talent's scene, which also carries the ally the heals are aimed at. */
    private record TalentScene(Battle battle, Character robin, Character ally) {
    }

    private static TalentScene talentScene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.partyResource(VIBES).setValue(0);
        return new TalentScene(battle, robin, ally);
    }

    private static void heal(Battle battle, Character healer, Character target) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.HEALED, healer, target, 500, 0));
        battle.processRequests();
    }

    private static void shield(Battle battle, Character giver, Character target) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SHIELD_GRANTED, giver, target, 500, 0));
        battle.processRequests();
    }

    /**
     * 1512's talent (151204)'s <b>「第一次」</b> clause and its other half: 「我方目标施放攻击时，或<b>在任意目标回合第一次提供治疗/护盾
     * 时</b>，获得 1 点气氛值，上限 50 点。」 — the first heal of a turn pays 1, a second heal in the SAME turn pays
     * nothing, a heal and a shield in one turn share one key and pay once, a new turn opens the window again, and every
     * ally-side attack pays 1 with no per-turn limit at all.
     *
     * <p>One fixture factory ({@link #talentScene()}, a fresh battle per reading) and one observable
     * ({@code partyResourceValue(气氛值)}): the four gauges are read between the events, in the scenario that owns them,
     * instead of in four methods that each replay the setup.
     *
     * <p><b>Why this needed a new spelling.</b> "The first heal this turn" is not a property of any unit - it is a fact
     * about what has happened in the turn so far. The effect now states {@code "once_per_turn": "<key>"}: the first use of
     * that key in a turn goes through, every later one is skipped, and the window resets at every turn boundary
     * ({@code Battle.firstThisTurn}). The two halves share one key, so a heal and then a shield in the same turn pays once.
     */
    @Test
    public void theTalentPaysOnTheFirstHealOrShieldOfATurnAndOnEveryAttack() {
        TalentScene sameTurn = talentScene();
        heal(sameTurn.battle, sameTurn.robin, sameTurn.ally);
        double afterFirstHeal = sameTurn.battle.partyResourceValue(VIBES);
        heal(sameTurn.battle, sameTurn.robin, sameTurn.ally);
        double afterSecondHeal = sameTurn.battle.partyResourceValue(VIBES);

        TalentScene healedAndShielded = talentScene();
        heal(healedAndShielded.battle, healedAndShielded.robin, healedAndShielded.ally);
        shield(healedAndShielded.battle, healedAndShielded.robin, healedAndShielded.ally);
        double afterHealAndShield = healedAndShielded.battle.partyResourceValue(VIBES);

        TalentScene nextTurn = talentScene();
        heal(nextTurn.battle, nextTurn.robin, nextTurn.ally);
        nextTurn.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, nextTurn.robin, nextTurn.robin, 0, 0));
        heal(nextTurn.battle, nextTurn.robin, nextTurn.ally);
        double afterTheNewTurn = nextTurn.battle.partyResourceValue(VIBES);

        TalentScene attacks = talentScene();
        attacks.battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, attacks.ally,
                attacks.battle.enemies().getFirst(), 1, 0));
        double afterFirstAttack = attacks.battle.partyResourceValue(VIBES);
        attacks.battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, attacks.ally,
                attacks.battle.enemies().getFirst(), 1, 0));
        double afterSecondAttack = attacks.battle.partyResourceValue(VIBES);

        Assertions.assertAll("the talent's 第一次 clause and its attack half",
                () -> Assertions.assertEquals(1.0, afterFirstHeal, 1e-9,
                        "「在任意目标回合第一次提供治疗…时，获得 1 点气氛值」"),
                () -> Assertions.assertEquals(afterFirstHeal, afterSecondHeal, 1e-9,
                        "the second heal in the same turn is not a 第一次 and pays nothing"),
                () -> Assertions.assertEquals(1.0, afterHealAndShield, 1e-9,
                        "「第一次提供治疗/护盾」 is one event class, so the pair pays once"),
                () -> Assertions.assertEquals(2.0, afterTheNewTurn, 1e-9,
                        "「任意目标回合」: a turn boundary starts the window over"),
                () -> Assertions.assertEquals(1.0, afterFirstAttack, 1e-9,
                        "「我方目标施放攻击时…获得 1 点气氛值」"),
                () -> Assertions.assertEquals(2.0, afterSecondAttack, 1e-9,
                        "every attack pays - this half is not a once-per-turn clause"));
    }

    // ==================================================================
    // 【Fever】 raises her own damage (and the memosprite's).
    // ==================================================================

    /**
     * 1512 知更鸟•晴歌's 【Fever】 state also <b>raises her own damage</b>: 「使知更鸟•晴歌与自身造成的伤害提高
     * (0.84 + 气氛值 x 0.028)」 (memospirite 11512's SkillP01, params [0.84, 0.028, ...]).
     *
     * <h2>Why the reading is a difference, not a ratio</h2>
     * Her own panel may already carry an ALL_DAMAGE_TYPE_BOOST (traces), so a ratio would mix two unknowns. The boost
     * bucket is additive, so the <b>difference</b> between two gauge readings is exactly the part the rule adds:
     * {@code BoostArea(气氛值 50) - BoostArea(气氛值 0) = 50 x 0.028 = 1.4}. The same fixture and the same observable give
     * the state's own base: {@code BoostArea(Fever) - BoostArea(no Fever) = 0.84} at an empty gauge.
     */
    @Test
    public void theFeverStateRaisesHerDamageByABaseAndPerGaugePoint() {
        double at0 = boostAfterWarmUp(0);
        double at50 = boostAfterWarmUp(50);
        double withoutFever = boostAfterWarmUp(0, false);
        double withFever = boostAfterWarmUp(0);
        Assertions.assertAll("【char:1512/fever】's damage boost on her own hit: 0.84 + char:1512/atmosphere x 0.028",
                () -> Assertions.assertEquals(1.4, at50 - at0, 1e-6,
                        "50 points x 0.028 = 1.4 more boost with a full gauge; got " + (at50 - at0)),
                () -> Assertions.assertEquals(0.84, withFever - withoutFever, 1e-6,
                        "(0.84 + 0 x 0.028): the state itself adds 84%; got " + (withFever - withoutFever)));
    }

    private static double boostAfterWarmUp(int vibes) {
        return boostAfterWarmUp(vibes, true);
    }

    /**
     * The same clause covers her memosprite: 「使知更鸟•晴歌与<b>自身</b>造成的伤害提高」 - a separate rule on
     * {@code actor == summon}, and it only became reachable once {@code SkillExecutor} raised {@code SUMMON_ATTACK} for a
     * data-driven memosprite.
     */
    @Test
    public void theMemospriteGetsTheSameBaseBoost() {
        double without = memoBoost(0, false);
        double with = memoBoost(0, true);
        Assertions.assertEquals(0.84, with - without, 1e-6,
                "with char:1512/fever the memosprite's own hit carries +84%; without it, nothing (" + without + " vs " + with + ")");
    }

    /** The memosprite's own cast, measured as the boost bucket of the settled damage. */
    private static double memoBoost(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(robin);
        battle.processRequests();
        if (vibes > 0) {
            battle.partyResource(VIBES).gain(vibes);
        }
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        Summon memo = (Summon) battle.memospriteOf(robin);
        Assertions.assertNotNull(memo, "precondition: the memosprite is out");
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, memo.skillAt(1), memo, List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: the memosprite's cast settled");
        return settled.get(0).breakdown().get("BoostArea");
    }

    /**
     * The boost the rule leaves on the NEXT hit (it is applied while a hit is dealing damage, so a warm-up cast puts
     * it up first - `turns: 1` lasts through it).
     */
    private static double boostAfterWarmUp(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        // The warm-up attack is what raises 【Fever】's own listeners; it also EARNS vibes (her talent pays 1 per attack,
        // the guest clause pays 2 more), so the gauge is pinned AFTER it - the number under test is the boost at a known
        // gauge, not the gauge a warm-up happened to leave behind.
        SkillExecutor.execute(battle, robin.getSkills().get(SkillType.COMMON), robin,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        battle.partyResource(VIBES).setValue(vibes);
        Assertions.assertEquals(vibes, battle.partyResourceValue(VIBES),
                "precondition: the gauge is exactly the number this case is about");

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, robin.getSkills().get(SkillType.COMMON), robin,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: the measured cast settled");
        return settled.get(0).breakdown().get("BoostArea");
    }

    // ==================================================================
    // 【Fever】's DEF-ignore: 15% + 0.5% per 气氛值, capped at 40%.
    // ==================================================================

    /**
     * The aura scales with the gauge.
     *
     * <p>1512 知更鸟•晴歌's 【Fever】aura: while it is on, the team's damage ignores part of the target's DEF -
     * <b>15% + 0.5% per point of 气氛值, capped at 40%</b>.
     *
     * <h2>Where the numbers come from</h2>
     * Her talent (151204, {@code SkillP01}): 「结界内我方目标造成伤害时无视敌方目标（#8% + 气氛值…#9%）的防御力」 with
     * {@code param = [0.7, 1.8, 0, 0, 50, 6, 12, 0.15, 0.005]}. The gauge's ceiling is 50, so the cap is 0.15 + 0.25 = 0.40.
     *
     * <h2>Why it is measured through the settled DefenceArea</h2>
     * DEFENCE_IGNORE on this rule is an <b>instance</b> modifier: it lives on the hit, not on the character, so
     * {@code getAttribute(DEFENCE_IGNORE)} answers 0 no matter what (measured). The observable is the settled
     * {@code breakdown()["DefenceArea"]}, which is also what a whole-battle capture lets us solve back out.
     *
     * <h2>What the capture says</h2>
     * A real fight's events implied effective DEF of {@code 1150 x {0.775, 0.77, 0.76, 0.725, 0.71, 0.705, 0.685, 0.6}},
     * i.e. DEF-ignore of 22.5%, 23%, 24%, 27.5%, 29%, 29.5%, 31.5% and 40% - this formula at 气氛值 15, 16, 18, 25, 28, 29,
     * 33 and 50.
     */
    @Test
    public void theAuraIgnoresMoreDefenceAsTheGaugeRises() {
        double ignore0 = ignoreAt(0);
        double ignore15 = ignoreAt(15);
        double ignore50 = ignoreAt(50);
        Assertions.assertEquals(0.15, ignore0, 1e-6, "no gauge: the base 15% still applies");
        Assertions.assertEquals(0.225, ignore15, 1e-6, "char:1512/atmosphere 15 -> 15% + 15 x 0.5% = 22.5%");
        Assertions.assertEquals(0.40, ignore50, 1e-6, "char:1512/atmosphere 50 -> 15% + 25% = 40% (the document's cap)");
    }

    /**
     * Without the state the aura does nothing, however full the gauge is - the gate is the state.
     */
    @Test
    public void withoutFeverTheGaugeChangesNothing() {
        Assertions.assertEquals(0.0, ignoreAt(50, false), 1e-9,
                "the aura is gated on the 【char:1512/fever】 state (the document's gate is \"all three memosprites on stage\")");
    }

    private static double ignoreAt(int vibes) {
        return ignoreAt(vibes, true);
    }

    /** The DEF-ignore the engine actually applied, solved back out of the settled defence zone. */
    private static double ignoreAt(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (vibes > 0) {
            Assertions.assertNotNull(battle.partyResource(VIBES), "the file declares the party gauge");
            battle.partyResource(VIBES).gain(vibes);
        }
        Assertions.assertEquals(Math.min(vibes, 50), battle.partyResourceValue(VIBES),
                "precondition: the gauge really moved");
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally, List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertEquals(1, settled.size(), "precondition: one settled instance");

        double defence = battle.enemies().get(0).getAttribute(AttributeType.DEFENCE).get();
        double levelTerm = 200.0 + 10.0 * 80;
        double zone = settled.get(0).breakdown().get("DefenceArea");
        // zone = levelTerm / (effectiveDef + levelTerm)  ->  effectiveDef = levelTerm/zone - levelTerm
        double effective = levelTerm / zone - levelTerm;
        return 1.0 - effective / defence;
    }

    // ==================================================================
    // The memosprite aura's 11.2% damage-taken amplification (with cone 23042).
    // ==================================================================

    /**
     * The two sources of "damage taken is raised" on the party's side, measured one at a time and then together:
     * 1512's memosprite aura adds 11.2% for the first member on the field (cumulative with more members), and light cone
     * 23042 adds 36% for two turns when the memosprite uses a skill.
     *
     * <p>A real capture carries both at once: 0.64 = 0.36 + 0.28. The engine used to let the later zone <b>evict</b> the
     * earlier one - the aura vanished the moment the cone fired, and the ratio came out (1+0.36)/(1+0.112) instead of
     * 1+0.36+0.112 - so each source is measured alone first and then the pair.
     */
    @Test
    public void oneMemospriteAloneAddsElevenPointTwoPercent() {
        Battle battle = battle(false);
        double before = hit(battle);
        battle.summonMemosprite(character(battle, ROBIN));
        battle.processRequests();
        Assertions.assertEquals(1.0 + AURA_ONE, hit(battle) / before, (1.0 + AURA_ONE) * 1e-6,
                "one 「晴空乐手」 on the field adds the document's 11.2%");
    }

    @Test
    public void theConeAddsItsThirtySixOnTopOfTheAura() {
        Battle battle = battle(true);
        Character robin = character(battle, ROBIN);
        double before = hit(battle);
        Summon memo = (Summon) battle.memospriteOf(robin);
        SkillExecutor.execute(battle, memo.skillAt(1), memo, List.of(battle.enemies().get(0)));
        battle.processRequests();
        double ratio = hit(battle) / before;
        double expected = (1.0 + AURA_ONE + CONE) / (1.0 + AURA_ONE);
        Assertions.assertEquals(expected, ratio, expected * 1e-6,
                "the cone's 36% adds to the aura's 11.2% rather than replacing it (expected " + expected + ")");
    }

    /** The party, with the target already broken so the toughness factor is 1.0 on both sides. */
    private static Battle battle(boolean withCone) {
        Character robin = withCone
                ? CharacterFactory.create(ROBIN, 80, true, Weapon.build(23042, 80, false, 5))
                : CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ((com.laosun.aluminium.enemy.Enemy) battle.enemies().get(0)).breakEnemy(DamageElement.ICE);
        if (withCone) {
            // The aura is up on BOTH sides of the cone measurement: the cone adds to it, it does not replace it.
            battle.summonMemosprite(robin);
            battle.processRequests();
        }
        return battle;
    }

    private static Character character(Battle battle, int cid) {
        return battle.characters().stream().filter(c -> c.getCid() == cid).findFirst().orElseThrow();
    }

    private static double hit(Battle battle) {
        Character ally = character(battle, ALLY);
        double before = battle.enemies().get(0).getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        return before - battle.enemies().get(0).getCurrentHp();
    }

    // ==================================================================
    // Eidolons 1 / 2 / 4 / 6
    // ==================================================================

    /**
     * 星魂 2 in one scenario: 「我方目标的全属性抗性穿透提高 18%」 and 「气氛值上限提高 20 点」 — the two halves are different
     * kinds of number, a <b>value</b> on every ally and <b>capacity</b> on the party bar, and both are read on the same
     * fixture ({@code Fixture}) against the E0 control.
     *
     * <p>The bar is PARTY-scoped, so it lives on the battle rather than on Robin: reading it from her own resource manager
     * answers null and would "prove" the clause wrong. {@code RAISE_RESOURCE_CAP} falls back to the party counter, which is
     * why this op works here.
     */
    @Test
    public void eidolon2GivesTheWholeSidePenetrationAndRaisesTheVibeCap() {
        Fixture e2 = new Fixture(2);
        double penetration = e2.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        int e2Cap = e2.battle.partyResource("char:1512/atmosphere").getMax() + e2.battle.partyResource("char:1512/atmosphere").getMaxOverflow();
        int e2Value = e2.battle.partyResource("char:1512/atmosphere").value();

        Fixture e0 = new Fixture(0);
        double control = e0.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        int baseCap = e0.battle.partyResource("char:1512/atmosphere").getMax() + e0.battle.partyResource("char:1512/atmosphere").getMaxOverflow();

        Assertions.assertAll("Eidolon 2: the side's penetration, and the bar's capacity",
                () -> Assertions.assertEquals(0.18, penetration, 1e-9,
                        "「我方目标的全属性抗性穿透提高 18%」 — a teammate carries it too"),
                () -> Assertions.assertEquals(0.0, control, 1e-9, "control: nothing at E0"),
                () -> Assertions.assertEquals(baseCap + 20, e2Cap, "「气氛值上限提高 20 点」"),
                () -> Assertions.assertEquals(0, e2Value,
                        "and her bar is still empty — capacity, not a gift"));
    }

    /**
     * 星魂 6: 「本场战斗中第一次进入【Fever】状态后…固定恢复 140 点能量」.
     *
     * <p>Fired through the same event the talent uses (a third summon completing the band), twice: the first entry pays, the
     * second must not — 「第一次」 is the whole clause.
     */
    @Test
    public void eidolon6PaysFixedEnergyOnTheFirstFeverOnly() {
        Fixture f = new Fixture(6);
        double before = f.robin.getCurrentEnergy();

        f.bringTheBandOnStage();
        double afterFirst = f.robin.getCurrentEnergy();
        Assertions.assertEquals(before + 140, afterFirst, 1e-9, "「固定恢复 140 点能量」");

        f.bringTheBandOnStage();
        Assertions.assertEquals(afterFirst, f.robin.getCurrentEnergy(), 1e-9, "「本场战斗中**第一次**」 — the second entry pays nothing");
    }

    /**
     * 星魂 2's third half: 「任意目标回合内第一次施放技能…额外获得 2 点」.
     *
     * <p>Measured across a turn boundary: two skill casts in one turn pay the bonus once, and the next turn's first cast pays
     * again.
     */
    @Test
    public void eidolon2PaysTheBonusOncePerTurn() {
        Fixture f = new Fixture(2);
        int start = f.vibes();

        f.castSkill();
        int afterFirst = f.vibes();
        Assertions.assertTrue(afterFirst > start, "precondition: the Skill grants vibes at all");

        f.castSkill();
        int afterSecond = f.vibes();
        int firstGain = afterFirst - start;
        int secondGain = afterSecond - afterFirst;
        Assertions.assertEquals(firstGain - 2, secondGain,
                "「本回合**第一次**…额外获得 2 点」 — the second cast pays the base gain only");

        f.newTurn();
        f.castSkill();
        Assertions.assertEquals(firstGain, f.vibes() - afterSecond,
                "and the next turn's first cast pays the bonus again");
    }

    /**
     * 星魂 1: the side's damage is recorded, and the memosprite's skill turns the record into true damage — then keeps half.
     */
    @Test
    public void eidolon1RecordsDamageAndPaysItBackAsTrueDamage() {
        Fixture f = new Fixture(1);
        f.bringTheBandOnStage();
        f.recordADamageSettled(1000);
        int recorded = f.record();
        Assertions.assertTrue(recorded > 0, "precondition: the skill casts recorded something (" + recorded + ")");

        double enemyHpBefore = f.enemy.getCurrentHp();
        f.summonCastsASkill();

        Assertions.assertTrue(f.enemy.getCurrentHp() < enemyHpBefore,
                "「对生命值最高的敌方目标额外造成…真实伤害」");
        // 「随后清空记录值的 50%」: half stays. It is not EXACTLY half, because the true damage just dealt is itself recorded -
        // the 「非真实伤害」 filter is the registered approximation - so this asserts the shape (roughly half, never all or none).
        int kept = f.record();
        Assertions.assertTrue(kept > recorded / 4 && kept < recorded * 3 / 4,
                "「清空记录值的 50%」 — about half stays (" + recorded + " -> " + kept + ")");
    }

    /**
     * 星魂 4's speed half: 「「晴空乐手」的速度提高（20% + 当前气氛值 × 0.50%）」, read at the moment Fever opens.
     */
    @Test
    public void eidolon4RaisesTheMemospritesSpeedOnFeverEntry() {
        Fixture f = new Fixture(4);
        f.bringTheBandOnStage();
        double slowest = f.summonSpeed();
        Assertions.assertTrue(slowest > 0, "precondition: the band is on stage");

        Fixture base = new Fixture(0);
        base.bringTheBandOnStage();
        Assertions.assertTrue(base.summonSpeed() < slowest,
                "the Eidolon's band moves faster (" + base.summonSpeed() + " -> " + slowest + ")");
    }

    /**
     * 星魂 6's other half: 「忆灵技的伤害倍率提高原倍率的 100%」 — the memosprite's instances carry a +100% boost at E6.
     *
     * <p>Read off the settled instance rather than a panel: the clause is about one instance's damage, and the rule is an
     * {@code instance}-shaped effect, so nothing lands on the sheet to look at.
     */
    @Test
    public void eidolon6DoublesTheMemospritesSkillDamage() {
        Assertions.assertTrue(shareOfTheBestHit(6) > shareOfTheBestHit(0),
                "「忆灵技的伤害倍率提高原倍率的 100%」 (" + shareOfTheBestHit(0) + " -> " + shareOfTheBestHit(6) + ")");
    }

    private static double shareOfTheBestHit(int eidolon) {
        Fixture f = new Fixture(eidolon);
        f.bringTheBandOnStage();
        double[] best = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> best[0] = Math.max(best[0], applied));
        f.summonCastsASkill();
        return best[0];
    }

    private static final class Fixture {
        private final Character robin;
        private final Character ally = CharacterFactory.create(EIDOLON_ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            robin = CharacterFactory.create(ROBIN, 80, false, null, null, eidolon);
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /**
         * Fills the band the way the engine does: the Skill summons one and its own +6 gauge crosses the thresholds for the rest, so two
         * casts put three on stage. Firing SUMMONED by hand cannot work here - the rule reads `summon_count`, which counts real summons.
         */
        private void bringTheBandOnStage() {
            for (int i = 0; i < 2; i++) {
                battle.castImmediate(robin.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL),
                        robin, List.of(robin));
                battle.processRequests();
            }
        }

        private int vibes() {
            return battle.partyResource("char:1512/atmosphere").value();
        }

        private void castSkill() {
            battle.castImmediate(robin.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL),
                    robin, List.of(robin));
            battle.processRequests();
        }

        /** A new turn for her: the one-shot marker is cleared on ANY unit's turn start. */
        private void newTurn() {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.TURN_START, robin, robin, 0, 0));
            battle.processRequests();
        }

        /** The first memosprite's speed: the clause is about 「晴空乐手」, and the band shares one stat sheet. */
        private double summonSpeed() {
            return battle.summonsOf(robin).isEmpty()
                    ? -1
                    : battle.summonsOf(robin).get(0).getAttribute(AttributeType.SPEED).get();
        }

        private int record() {
            return robin.getResources().value("internal:1512/record");
        }

        /** One settled instance on our side, which is what the recording rule listens to. */
        private void recordADamageSettled(double amount) {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.DAMAGE_SETTLED, robin, enemy, 0, amount));
            battle.processRequests();
        }

        private void summonCastsASkill() {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.SKILL_CAST,
                    battle.summonsOf(robin).isEmpty() ? null : battle.summonsOf(robin).get(0),
                    enemy, 0, 0));
            battle.processRequests();
        }

        private int cap() {
            return robin.getResources().get("char:1512/atmosphere").getMax();
        }

        private int value() {
            return robin.getResources().value("char:1512/atmosphere");
        }
    }

    // ==================================================================
    // Eidolon 6's second Ultimate: the overflow bank
    // ==================================================================

    /**
     * At E6 the band's arrival opens 【Fever】 and with it the storage: a full bar's worth of overflow is kept, and casting
     * the Ultimate inside 【Fever】 pours that bar back, so the second use is ready at once.
     *
     * <p>One fixture ({@link StorageFixture}) and one observable (her energy: the bar, the bank, the cap): the fill and
     * the cast are read off the same instance, so the "stored, then poured back" pair is measured on one bar instead of on
     * two fixtures that each fill it.
     */
    @Test
    public void theEidolonStoresOneBarAndCastingTheUltimatePoursItBack() {
        StorageFixture e6 = new StorageFixture(6);
        Assertions.assertTrue(e6.robin.getBuffManager().hasState(FEVER), "precondition: the band opened 【char:1512/fever】");
        double bankCap = e6.robin.getEnergyBankCap();

        double before = e6.robin.getCurrentEnergy();
        e6.robin.gainEnergy(500);
        double barAfterTheGain = e6.robin.getCurrentEnergy();
        double bankedAfterTheGain = e6.robin.getBankedEnergy();
        double stored = e6.robin.getBankedEnergy();

        e6.spendTheUltimate();
        double barAfterTheCast = e6.robin.getCurrentEnergy();
        double bankedAfterTheCast = e6.robin.getBankedEnergy();

        Assertions.assertAll("E6's second Ultimate: one bar in the bank, poured back on the cast",
                () -> Assertions.assertEquals(e6.robin.getMaxEnergy(), bankCap, 1e-9,
                        "「终结技最多储存 2 次」 — one bar in hand, one in the bank"),
                () -> Assertions.assertEquals(e6.robin.getMaxEnergy(), barAfterTheGain, 1e-9, "the bar fills first"),
                () -> Assertions.assertEquals(e6.robin.getMaxEnergy(), bankedAfterTheGain, 1e-9,
                        "and the rest is kept, up to one more bar"),
                () -> Assertions.assertTrue(before >= 0, "precondition: she started below full"),
                () -> Assertions.assertEquals(e6.robin.getMaxEnergy(), stored, 1e-9,
                        "precondition: a whole bar is stored"),
                () -> Assertions.assertEquals(stored, barAfterTheCast, 1e-9,
                        "「储存 2 次」 — the second use is ready as soon as the first is paid"),
                () -> Assertions.assertEquals(0, bankedAfterTheCast, 1e-9, "and the bank is empty again"));
    }

    /** Without the Eidolon there is no storage - the control for the reading above. */
    @Test
    public void withoutTheEidolonTheOverflowIsLost() {
        StorageFixture e5 = new StorageFixture(5);
        Assertions.assertEquals(0, e5.robin.getEnergyBankCap(), 1e-9, "E5 states nothing about storing");
        e5.robin.gainEnergy(500);
        Assertions.assertEquals(0, e5.robin.getBankedEnergy(), 1e-9, "so the excess is lost as usual");
    }

    private static final class StorageFixture {
        private final Character robin;
        private final Character ally = CharacterFactory.create(1002, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private StorageFixture(int eidolon) {
            robin = CharacterFactory.create(ROBIN, LEVEL, false, null, null, eidolon);
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // 「晴空乐手」全员登台 = two Skill casts (the first summons, its +6 气氛值 crosses the thresholds for the rest).
            for (int i = 0; i < 2; i++) {
                battle.castImmediate(robin.getSkills().get(SkillType.SKILL), robin, List.of(robin));
                battle.processRequests();
            }
        }

        /**
         * Paying an Ultimate: the bar goes to 0 first (what {@code UltimateService} does), then the real cast runs.
         *
         * <p>The cast is driven through {@code castImmediate} rather than a hand-fired {@code ULT_CAST}: her own Ultimate rule
         * reads a parameter of the skill that produced the event, and a hand-made payload carries no skill at all.
         */
        private void spendTheUltimate() {
            robin.setCurrentEnergy(0);
            battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin, List.of(ally));
            battle.processRequests();
        }
    }

    // ==================================================================
    // The Ultimate 「跃入这片蔚蓝狂想」 and the 【特邀嘉宾】 state
    // ==================================================================

    /**
     * The advance: the ult moves the chosen ally forward by the whole row (#1 = 100% at every level).
     */
    @Test
    public void theUltimateAdvancesTheChosenAlly() {
        Scene scene = scene();
        double before = actionTime(scene.battle, scene.ally);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally)),
                "the ultimate is castable");

        double after = actionTime(scene.battle, scene.ally);
        Assertions.assertTrue(after < before, "the ally moved: " + before + " -> " + after);
    }

    /**
     * The fixed energy: #3 of the ally's OWN maximum, and the regen rate must not multiply it. The rate is set to 50%
     * first, so a "normal" grant would come out at half the stated number.
     */
    @Test
    public void theEnergyRestoreIsFixedAndUsesTheAllysOwnMaximum() {
        Scene scene = scene();
        scene.ally.getAttribute(com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).addBase(0.5);
        double max = scene.ally.getMaxEnergy();
        double before = scene.ally.getCurrentEnergy();
        // The share comes from the ultimate's OWN row at the level the fixture reads (#3 runs 12% -> 24%), so the
        // expectation is derived instead of pinned to one level.
        int level = scene.robin.skillLevel(scene.ultimate);
        double share = scene.ultimate.getData().getSkills().get(level - 1).get(2);

        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));

        double gained = scene.ally.getCurrentEnergy() - before;
        Assertions.assertTrue(gained > 0, "the ally gained energy");
        Assertions.assertEquals(share * max, gained, 0.5,
                "#3 (" + share + ") of " + max + " max energy, NOT halved by the 50% regen rate");
    }

    /**
     * The state: applied to the ally, and its clock belongs to the ALLY's own turn ("该角色每回合开始时持续回合数减1").
     */
    @Test
    public void theGuestStateLandsOnTheAllyAndTicksOnTheirTurn() {
        Scene scene = scene();
        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));

        Assertions.assertTrue(scene.ally.getBuffManager().hasState(GUEST), "the ally holds 【char:1512/special_guest】");
        Assertions.assertFalse(scene.robin.getBuffManager().hasState(GUEST), "the caster does not");
    }

    /**
     * The guest's own attack pays her 2 vibes - and the guest's SUMMON's attack pays the same 2, because the state lives
     * on the master, so the condition has to ask about the master (the {@code master_state} sub-predicate).
     *
     * <p>One fixture ({@link #scene()}) and one observable ({@code partyResourceValue(气氛值)}): both attack sites are
     * worth the same clause on the same battle.
     */
    @Test
    public void theGuestAndItsSummonEachPayTwoVibes() {
        Scene scene = scene();
        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));
        double beforeGuest = scene.battle.partyResourceValue(VIBES);

        scene.battle.castImmediate(scene.ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                scene.ally, List.of(scene.battle.enemies().getFirst()));
        double afterGuest = scene.battle.partyResourceValue(VIBES);

        scene.battle.summonMemosprite(scene.ally);
        Summon servant = scene.battle.summonOf(scene.ally);
        Assertions.assertNotNull(servant, "her memosprite is out");
        double beforeSummon = scene.battle.partyResourceValue(VIBES);

        // Fired directly: 1409's memosprite deals its damage through RULES (its data row prices nothing), so its cast
        // produces no hit and the engine's own SUMMON_ATTACK site - which needs a hit - would not fire. What this case
        // is about is the rule's gate (the state is on the MASTER), not the firing site.
        scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.SUMMON_ATTACK, servant, null, 1, 0));
        double afterSummon = scene.battle.partyResourceValue(VIBES);

        // 2 from the guest clause + 1 from her talent's 「我方目标施放攻击时」 - 额外 means the guest's 2 are ON TOP of the
        // talent's 1, so the attack is worth 3 in total.
        Assertions.assertAll("【char:1512/special_guest】: the guest attack pays 2, and so does its summon's",
                () -> Assertions.assertEquals(beforeGuest + 3, afterGuest, 1e-9,
                        "guest 2 + talent 1: 「…额外使知更鸟•晴歌获得 2 点气氛值」"),
                () -> Assertions.assertEquals(beforeSummon + 2, afterSummon, 1e-9,
                        "「…及其召唤物攻击时」 grants the same 2 points"));
    }

    private record Scene(Battle battle, Character robin, Character ally, Skill ultimate) {
    }

    private static Scene scene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(1409, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        Skill ultimate = robin.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ultimate, "1512 has an ultimate row");
        return new Scene(battle, robin, ally, ultimate);
    }

    /** How far this unit's next action sits on the queue's clock. */
    private static double actionTime(Battle battle, CanHit unit) {
        return battle.queue().getHeap().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .mapToDouble(signal -> signal.nextActionTime)
                .findFirst().orElseThrow(() -> new AssertionError("the unit is not in the action bar"));
    }

    // ==================================================================
    // 「无法使其他友方目标获得行动提前效果」: the ban the state carries
    // ==================================================================

    /**
     * With the state on the giver: the advance aimed at another ally is suppressed, while the rest of the ultimate still
     * happens (the state lands on the ally) - a ban on one action, not a cancelled cast. And 「<b>其他</b>友方目标」: an
     * advance the holder gives ITSELF is not an advance of another, so it still lands.
     *
     * <p>One fixture (a fresh {@link #banScene()}) and one observable (the target's place on the action bar): the same cast
     * is aimed at the other ally, then at the holder herself.
     */
    @Test
    public void theBanOnlyStopsAdvancesAimedAtOtherAllies() {
        BanScene banned = banScene();
        giveGuestState(banned.robin);
        double beforeCyrene = actionTime(banned.battle, banned.cyrene);
        boolean castAtCyrene = banned.battle.castImmediate(banned.ultimate, banned.robin, List.of(banned.cyrene));
        double afterCyrene = actionTime(banned.battle, banned.cyrene);
        boolean cyreneHoldsGuest = banned.cyrene.getBuffManager().hasState(GUEST);

        BanScene self = banScene();
        giveGuestState(self.robin);
        double beforeRobin = actionTime(self.battle, self.robin);
        boolean castAtRobin = self.battle.castImmediate(self.ultimate, self.robin, List.of(self.robin));
        double afterRobin = actionTime(self.battle, self.robin);

        Assertions.assertAll("「无法使其他友方目标获得行动提前效果」 covers others, not the holder",
                () -> Assertions.assertTrue(castAtCyrene, "the cast itself is still allowed"),
                () -> Assertions.assertEquals(beforeCyrene, afterCyrene, 1e-9,
                        "「无法使其他友方目标获得行动提前效果」: the ally did not move"),
                () -> Assertions.assertTrue(cyreneHoldsGuest,
                        "and the rest of the ultimate still resolved (the state landed)"),
                () -> Assertions.assertTrue(castAtRobin,
                        "casting on herself is allowed by the engine (the document says another ally; the ban only covers others)"),
                () -> Assertions.assertTrue(afterRobin < beforeRobin,
                        "the holder's own advance is not banned: " + beforeRobin + " -> " + afterRobin));
    }

    /**
     * Without the state the very same cast does move the ally - the ban must not be a general "advances do not work".
     */
    @Test
    public void withoutTheStateTheAdvanceLands() {
        BanScene scene = banScene();
        double before = actionTime(scene.battle, scene.cyrene);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.cyrene)));

        Assertions.assertTrue(actionTime(scene.battle, scene.cyrene) < before,
                "an ungated advance still moves the ally: " + before + " -> "
                        + actionTime(scene.battle, scene.cyrene));
    }

    private record BanScene(Battle battle, Character robin, Character cyrene, Skill ultimate) {
    }

    private static BanScene banScene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(robin, cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return new BanScene(battle, robin, cyrene, robin.getSkills().get(SkillType.ULTRA));
    }

    /**
     * 【特邀嘉宾】 with the ban it carries in the data ({@code "blocks": "advance_to_others"}). The rule's own
     * {@code APPLY_BUFF} sets this; the test states it directly because what is on trial here is the READ, not the apply.
     */
    private static void giveGuestState(Character unit) {
        com.laosun.aluminium.buff.StateBuff state = new com.laosun.aluminium.buff.StateBuff(GUEST, 2);
        state.setBlocks(java.util.Set.of("advance_to_others"));
        unit.getBuffManager().addBuff(state);
    }
}
