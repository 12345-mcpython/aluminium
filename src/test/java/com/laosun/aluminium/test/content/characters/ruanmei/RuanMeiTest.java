package com.laosun.aluminium.test.content.characters.ruanmei;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.BuffManager;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1303 Ruan Mei, from her own file: the SELECTOR clause (everyone but her) and the state she puts on herself.
 *
 * <p>These were six small files in the same package - this base kit, the technique, the zone (with Eidolon 1), Eidolon 2,
 * Eidolons 3/4/5 and Eidolon 6; one class keeps the character together without changing any test or assertion, and each
 * former class note now sits on the case or the section it explains. The three ally ids stay apart, because 1001, 1002 and
 * 1210 are different characters and folding one into another would change what the judges measure.
 */
public class RuanMeiTest {

    private static final int RUAN_MEI = 1303;
    private static final int LEVEL = 80;
    // Three different allies: 1002 is the teammate the Talent speeds and the Skill's cross-unit clause buffs, 1001 the body
    // Eidolon 2 measures, and 1210 the ally the zone / Eidolon 6 judges read. They are NOT the same character, so the ids
    // stay separate.
    private static final int ALLY_1002 = 1002;
    private static final int ALLY_1001 = 1001;
    private static final int ALLY_1210 = 1210;
    private static final double EPS = 1e-9;

    // ==================================================================
    // Base kit: the SELECTOR clause (everyone but her) and her own state
    // ==================================================================

    /**
     * Note: "除自身以外": the TEAMMATE gains 10% speed and she does not - measured against a hand-built 20% reference.
     */
    @Test
    public void theTalentSpeedsTeammatesButNotHerself() {
        Character ruanmei = CharacterFactory.create(RUAN_MEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY_1002, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ruanmei, ally), List.of(enemy), fixed());
        double herBase = ruanmei.getAttribute(AttributeType.SPEED).baseValue();
        double allyBase = ally.getAttribute(AttributeType.SPEED).baseValue();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        double herBefore = ruanmei.getAttribute(AttributeType.SPEED).get();

        battle.startBattle();

        Assertions.assertEquals(allyBase * 0.1, ally.getAttribute(AttributeType.SPEED).get() - before, allyBase * 0.02,
                "「使除自身以外的队友速度提高10.00%」 (raises the SPD of allies other than herself by 10.00%): ally base " + allyBase);
        Assertions.assertEquals(0.0, ruanmei.getAttribute(AttributeType.SPEED).get() - herBefore, 1e-9,
                "「除自身以外」 (other than herself) -- she is excluded, so her own speed must not move (base " + herBase + ")");
        Assertions.assertTrue(battle.getSkillPoints() >= 0, "the battle ran");
    }

    /**
     * Note: The Skill puts [弦外音] on HER, for the document's three turns.
     */
    @Test
    public void theSkillPutsOvertoneOnHerself() {
        Character ruanmei = CharacterFactory.create(RUAN_MEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY_1002, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ruanmei, ally), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(ruanmei.getSkills().get(SkillType.SKILL), ruanmei, List.of(ally));

        Assertions.assertTrue(ruanmei.getBuffManager().hasState("char:1303/overtone"),
                "「施放战技后阮•梅获得【char:1303/overtone】」 (after casting the Skill, Ruan Mei gains Overtone (【char:1303/overtone】))");
        Assertions.assertFalse(ally.getBuffManager().hasState("char:1303/overtone"),
                "the state is on HER, not on the ally she aimed at");
    }

    // ==================================================================
    // The technique: 「下一次战斗开始时自动触发 1 次战技」
    // ==================================================================

    /**
     * Note: 1303 阮•梅's technique — 「下一次战斗开始时自动触发 1 次战技」.
     *
     * <p><b>Why this needed a data entry first.</b> Her Skill is a Support buff: it deals no damage and heals nothing, so
     * {@code SkillExecutor.canDeliver} refused {@code CAST_SKILL} for it — the guard that stops a rule from commanding a cast that
     * would silently do nothing. 1412's Skill was in exactly that position and got a {@code Rules} entry in {@code skill_effects.json};
     * hers now has one too, which is what makes the command legitimate rather than the guard being loosened.
     */
    @Test
    public void theTechniqueAutoCastsTheSkillAtBattleStart() {
        Assertions.assertTrue(techniqueOpensTheZone(), "「使下一次战斗开始时自动触发 1 次战技」");
        Assertions.assertFalse(plainBattleOpensTheZone(), "control: without the technique nothing is cast");
    }

    /**
     * Note: 战技: 「当阮•梅拥有【弦外音】时，我方全体伤害提高 32%」 — the cross-unit gate.
     *
     * <p>This was registered as unwritable for months. {@code while_state} checks the RULE OWNER's state, not the modifier
     * holder's, so "she holds it ⇒ the party gets the modifier" is exactly what it expresses — and the modifier stops when her
     * state does.
     */
    @Test
    public void theOvertoneBoostsTheWholeSideWhileSheHoldsIt() {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, 80);
        Character ally = CharacterFactory.create(1002, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double before = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.castImmediate(ruanMei.getSkills().get(SkillType.SKILL), ruanMei, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ruanMei.getBuffManager().hasState("char:1303/overtone"), "precondition: the Skill grants char:1303/overtone");
        Assertions.assertEquals(before + 0.32, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「当阮•梅拥有【char:1303/overtone】时，我方全体伤害提高 32%」 — the ALLY carries it");
    }

    // ==================================================================
    // The zone: 25.00% All-Type RES PEN, Eidolon 1's defence ignore, and her own clock
    // ==================================================================

    /**
     * Note: 1303 Ruan Mei (阮•梅)'s zone: "我方全体全属性抗性穿透提高25.00%" for two of HER turns, plus Eidolon (星魂) 1's defence ignore on the same clock.
     *
     * <p>Both are stated on her Ultimate with `ticks_on: self`, because the document gives the zone her own clock ("自身每回合开始时
     * 结界持续回合数减1") and nothing extra happens when it ends. Two ways: after the cast every ally reads both values, and before
     * it they read nothing -- plus the eidolon's gate.
     */
    @Test
    public void theZoneGivesEveryAllyResistancePenetration() {
        Assertions.assertEquals(0.25, afterUlt(0, AttributeType.DAMAGE_PENETRATION), EPS,
                "\"while inside the zone, all allies gain 25.00% All-Type RES PEN\" (处于结界中时我方全体全属性抗性穿透提高25.00%)");
        Assertions.assertEquals(0.0, beforeUlt(0, AttributeType.DAMAGE_PENETRATION), EPS,
                "without the Ultimate nothing is stated");
    }

    @Test
    public void theFirstEidolonIgnoresDefenceOnTheZonesClock() {
        Assertions.assertEquals(0.2, afterUlt(1, AttributeType.DEFENCE_IGNORE), EPS,
                "\"while the zone lasts, all allies ignore 20% of the target's DEF when dealing damage\" (结界期间，我方全体造成伤害时无视目标的20%的防御力)");
        Assertions.assertEquals(0.0, afterUlt(0, AttributeType.DEFENCE_IGNORE), EPS, "rank 0 states nothing");
    }

    /**
     * The zone's own clock, which the document gives her: "自身每回合开始时结界持续回合数减1".
     *
     * <p>Note: A WHOLE turn is `beforeMove()` AND `afterMove()`: the countdown is split into an early pass (beforeMove) and a
     * late pass (afterMove) by `if (buff.isEarlyBuff != early) continue;`, and a stat modifier is a LATE buff. Driving only
     * beforeMove leaves the zone standing forever.
     */
    @Test
    public void theZoneExpiresOnHerSecondWholeTurn() {
        Assertions.assertEquals(0.25, penetrationAfterHerTurns(1), EPS, "still up after one turn of hers");
        Assertions.assertEquals(0.0, penetrationAfterHerTurns(2), EPS,
                "\"at the start of each of her own turns, the zone's remaining turns are reduced by 1\" (自身每回合开始时结界持续回合数减1) -- so two turns end it");
    }

    // ==================================================================
    // Eidolon 2: 「我方全体对处于弱点击破状态的敌方目标造成伤害时，攻击力提高 40%」
    // ==================================================================

    /**
     * Note: 1303 阮•梅's Eidolon 2: 「我方全体对处于弱点击破状态的敌方目标造成伤害时，攻击力提高 40%」.
     *
     * <p>The clause is placed on CAST_SETUP, which fires BEFORE the base is computed - on DEALING_DAMAGE it is too late
     * (measured: an ATTACK modifier applied there left the settled damage at ratio 1.0).
     */
    @Test
    public void anAllyHittingABrokenEnemyGetsTheAttackBoost() {
        double plain = hit(false);
        double broken = hit(true);
        System.out.println("[1303] plain=" + plain + " broken=" + broken + " ratio=" + (broken / plain));
        Assertions.assertEquals(1.4, broken / plain, 0.05, "「攻击力提高 40%」");
    }

    // ==================================================================
    // 1303 阮•梅's Eidolons 3, 4 and 5.
    // ==================================================================

    @Test
    public void eidolonFourRaisesHerBreakEffectOnABreak() {
        Character ruanmei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, 4);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ruanmei.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, ruanmei, enemy, 0, 0));
        double after = ruanmei.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[1303] break effect " + before + " -> " + after);
        Assertions.assertEquals(1.0, after - before, 1e-9, "「击破特攻提高 100%」");
    }

    @Test
    public void eidolonsThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E3: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E5: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    // ==================================================================
    // Eidolon 6: the zone lasts one more turn, judged over WHOLE turns
    // ==================================================================

    /**
     * Note: 1303 Ruan Mei (阮-梅)'s eidolon 6: "the duration of the zone unfolded by the ultimate is extended by 1 turn" -- judged over WHOLE turns.
     *
     * <p>The countdown is split into an early pass (`beforeMove`) and a late one (`afterMove`), and a stat modifier is a LATE
     * buff, so a whole turn is both calls. `turns: 2` expires after two of them; with the extension the zone must still stand.
     */
    @Test
    public void theZoneExpiresOnHerSecondWholeTurnWithoutTheEidolon() {
        Assertions.assertEquals(0.25, afterTurns(0, 1), EPS, "still up after one whole turn of hers");
        Assertions.assertEquals(0.0, afterTurns(0, 2), EPS,
                "at the start of each of her own turns the zone's remaining turns are reduced by 1 -- so two end it");
    }

    @Test
    public void theSixthEidolonKeepsItForOneMoreTurn() {
        Assertions.assertEquals(0.25, afterTurns(6, 2), EPS,
                "the zone's duration is extended by 1 turn -- two turns no longer end it");
        Assertions.assertEquals(0.0, afterTurns(6, 3), EPS, "but three do");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static Battle battle(int eidolon) {
        Character ruanmei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    private static double hit(boolean broken) {
        Character ruanmei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY_1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (broken) {
            enemy.getBuffManager().addBuff(new StateBuff(BuffManager.BROKEN_STATE, 3, false));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    private static boolean techniqueOpensTheZone() {
        Battle battle = battleWithTechnique(true);
        return !battle.characters().getFirst().getBuffManager().activeBuffs().isEmpty();
    }

    private static boolean plainBattleOpensTheZone() {
        Battle battle = battleWithTechnique(false);
        return !battle.characters().getFirst().getBuffManager().activeBuffs().isEmpty();
    }

    private static Battle battleWithTechnique(boolean technique) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ruanMei), List.of(enemy), BattleFixture.seeded());
        if (technique) {
            battle.markTechniqueUsed(ruanMei);
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static double penetrationAfterHerTurns(int turns) {
        Fixture f = fixture(0);
        f.battle.castImmediate(f.ruanMei.getSkills().get(SkillType.ULTRA), f.ruanMei, List.of(f.enemy));
        for (int i = 0; i < turns; i++) {
            f.battle.setCurrentMove(new Signal(f.ruanMei));
            f.battle.beforeMove();
            f.battle.afterMove();
        }
        return read(f.ally, AttributeType.DAMAGE_PENETRATION);
    }

    private static double afterUlt(int rank, AttributeType attribute) {
        Fixture f = fixture(rank);
        f.battle.castImmediate(f.ruanMei.getSkills().get(SkillType.ULTRA), f.ruanMei, List.of(f.enemy));
        return read(f.ally, attribute);
    }

    private static double beforeUlt(int rank, AttributeType attribute) {
        Fixture f = fixture(rank);
        return read(f.ally, attribute);
    }

    private static double read(Character ally, AttributeType attribute) {
        return ally.getAttribute(attribute).get() - ally.getAttribute(attribute).baseValue();
    }

    private static Fixture fixture(int rank) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY_1210, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return new Fixture(ruanMei, ally, enemy, battle);
    }

    private static double afterTurns(int rank, int turns) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY_1210, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(ruanMei.getSkills().get(SkillType.ULTRA), ruanMei, List.of(enemy));
        Assertions.assertEquals(0.25, penetrationOf(ally), EPS, "precondition: the zone is up after the cast");
        for (int i = 0; i < turns; i++) {
            battle.setCurrentMove(new Signal(ruanMei));
            battle.beforeMove();
            battle.afterMove();
        }
        return penetrationOf(ally);
    }

    private static double penetrationOf(Character unit) {
        return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get()
                - unit.getAttribute(AttributeType.DAMAGE_PENETRATION).baseValue();
    }

    private record Fixture(Character ruanMei, Character ally, Enemy enemy, Battle battle) {
    }
}
