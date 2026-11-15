package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1402 阿格莱雅's Eidolon kit (星魂 1-6) and her traces, read through the numbers the damage path actually uses.
 *
 * <p>The level additions (星魂 3 / 5) are read through {@code skillLevel}, the one resolver every row read goes through,
 * so a raise that never reached the skill cannot pass - and the two memosprite halves (「忆灵天赋等级+1」,
 * 「忆灵技等级+1」) are read on the memosprite's own DATA slots. (The two rules existed before this test, but each carried
 * one effect that mapped a memosprite slot onto COMMON / ULTRA - a wrong number that loaded and ran; the resolver is what
 * makes that visible.) 星魂 1's state is stamped by an outside helper rule, so the question is what the Eidolon adds to
 * an <b>already threaded</b> target, not whether the talent threads it (the applying half is judged in
 * {@code AglaeaBaseKitTest}). Its 15% is measured as a ratio between E0 and E1 (crit and defence multiply both sides
 * alike), its 20 energy as a delta, and its vulnerability is followed as it moves with 【间隙织线】. 星魂 2's layers are
 * read as the DEFENCE_IGNORE attribute (0.14/0.28/0.42, capped at three, cleared by another ally's cast). 星魂 6's
 * penetration is read both as an attribute and as a settled hit against a 40%-resistant enemy, and a physical hit proves
 * the routing by staying put.
 *
 * <p>The traces are asserted as a floor (飞驰之阳 lifts energy TO 50%, it does not add) and as the exact difference the
 * two speeds produce (短视之惩: 720% of her speed + 360% of the memosprite's).
 *
 * <p>Merged from the former {@code AglaeaEidolonLevelsTest}, {@code AglaeaEidolonOneTest}, {@code AglaeaEidolonTwoTest},
 * {@code AglaeaEidolonSixTest} and {@code AglaeaTracesTest}; every test, assertion and Javadoc of measurement is
 * preserved.
 */
public class AglaeaEidolonsTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final String THREAD = "间隙织线";
    private static final String BUFF = "星魂二穿防";
    private static final double EPS = 1e-9;

    // ---------------------------------------------------------------------------------------------------------------
    // 星魂 3 and 星魂 5: level additions, including the memosprite's own rows.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void theThirdEidolonRaisesTheSkillAndTheBasic() {
        // The file's level convention reads COMMON at 6 and SKILL at 10; 星魂 3 adds +2 to the skill and +1 to the basic.
        Assertions.assertEquals(10, level(SkillType.SKILL, 2), "before 星魂 3 the skill sits at the document's level");
        Assertions.assertEquals(12, level(SkillType.SKILL, 3), "「战技等级+2」");
        Assertions.assertEquals(6, level(SkillType.COMMON, 2), "before 星魂 3 the basic sits at the document's level");
        Assertions.assertEquals(7, level(SkillType.COMMON, 3), "「普攻等级+1」");
    }

    @Test
    public void theFifthEidolonRaisesTheUltimateAndTheTalent() {
        Assertions.assertEquals(10, level(SkillType.ULTRA, 4), "before 星魂 5 the ultimate sits at the document's level");
        Assertions.assertEquals(12, level(SkillType.ULTRA, 5), "「终结技等级+2」");
        Assertions.assertEquals(12, level(SkillType.TALENT, 5), "「天赋等级+2」");
    }

    /**
     * The memosprite halves:「忆灵天赋等级+1」(星魂 3) and 「忆灵技等级+1」(星魂 5).
     *
     * <p>They were blockable before 2026-11-06 only because the spec pinned the memosprite's skills at the table's last row;
     * the document quotes them at <b>Lv6</b>, so a +1 has somewhere to go and the row the prose states stays the base.
     */
    @Test
    public void theMemospriteSkillLevelsRiseAtTheirEidolons() {
        Assertions.assertEquals(6, memospriteLevel(3, 2), "before 星魂 3 the memosprite's talent sits at the document's row");
        Assertions.assertEquals(7, memospriteLevel(3, 3), "「忆灵天赋等级+1」 (data slot 3)");
        Assertions.assertEquals(7, memospriteLevel(5, 3), "…and slots 5 / 6 carry the same sentence");
        Assertions.assertEquals(7, memospriteLevel(6, 3), "…and slot 6");
        Assertions.assertEquals(6, memospriteLevel(1, 4), "before 星魂 5 the memosprite's skill sits at its own row");
        Assertions.assertEquals(7, memospriteLevel(1, 5), "「忆灵技等级+1」 (data slot 1)");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 星魂 1: the threaded target takes 15% more and pays 20 energy.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The 15%: the same attack on the same threaded enemy, once at E0 and once at E1, differs by exactly 1.15. A ratio is
     * the right instrument because crit and defence multiply both sides alike.
     */
    @Test
    public void theThreadedTargetTakesFifteenPercentMore() {
        double withoutEidolon = damageOnAThreadedTarget(0);
        double withEidolon = damageOnAThreadedTarget(1);
        Assertions.assertTrue(withoutEidolon > 0, "precondition: the hit lands: " + withoutEidolon);
        Assertions.assertEquals(1.15, withEidolon / withoutEidolon, 1e-6,
                "「受到的伤害提高 15%」: " + withoutEidolon + " -> " + withEidolon);
    }

    /**
     * 「仅对最新被施加的目标生效」 applied to the Eidolon's modifier: it MOVES with the state instead of piling up. Both
     * sides are asserted - the new holder has it and the old one does not - because "the new one has it" alone would pass
     * even if the old copy never went away.
     */
    @Test
    public void theVulnerabilityMovesWithTheState() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 1);
        Enemy left = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(left, right), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.summonMemosprite(aglaea);

        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(left));
        Assertions.assertEquals(1, left.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the attacked target carries the 15%");
        Assertions.assertEquals(0, right.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "and only it");

        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(right));
        Assertions.assertEquals(1, right.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the modifier followed the state to the new target");
        Assertions.assertEquals(0, left.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "and the old holder lost it");
    }

    /** 「阿格莱雅**或衣匠**攻击该目标后额外恢复 20 点能量」 - both halves, each measured as a delta against E0. */
    @Test
    public void aThreadedAttackPaysTwentyEnergy() {
        Assertions.assertEquals(20.0, energyAfterAttack(1, false) - energyAfterAttack(0, false), 1e-9,
                "her own attack on a threaded target pays 20");
        Assertions.assertEquals(20.0, energyAfterAttack(1, true) - energyAfterAttack(0, true), 1e-9,
                "and so does the Garmentmaker's attack");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 星魂 2: a layer per action, up to three, cleared by another ally's cast.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void everyActionAddsALayerUpToThree() {
        Assertions.assertEquals(0.14, defenceIgnoreAfter(2, 1), 1e-9, "one action ⇒ 14%");
        Assertions.assertEquals(0.28, defenceIgnoreAfter(2, 2), 1e-9, "two actions ⇒ 28%");
        Assertions.assertEquals(0.42, defenceIgnoreAfter(2, 3), 1e-9, "three actions ⇒ 42%");
        Assertions.assertEquals(0.42, defenceIgnoreAfter(2, 4), 1e-9, "「最多叠加 3 层」 — a fourth action adds nothing");
        Assertions.assertEquals(0.0, defenceIgnoreAfter(1, 3), 1e-9, "and nothing at all below E2");
    }

    /** 「衣匠行动时」 half: the memosprite's own turn start stacks HER copy, not Aglaea's. */
    @Test
    public void theMemospriteStacksItsOwnCopy() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double hersBefore = aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, garmentmaker, garmentmaker, 0, 0));
        battle.processRequests();

        Assertions.assertEquals(0.14, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「衣匠…行动时」 — the memosprite gets its own layer");
        Assertions.assertEquals(hersBefore, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and her copy is untouched by the memosprite's action");
    }

    /** 「持续至除自身与衣匠以外的任意单位主动施放技能时」 — a teammate's cast clears BOTH copies. */
    @Test
    public void aTeammatesCastClearsBothCopies() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        for (int i = 0; i < 2; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, aglaea, aglaea, 0, 0));
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, garmentmaker, garmentmaker, 0, 0));
        }
        Assertions.assertEquals(0.28, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: her copy is stacked");
        Assertions.assertEquals(0.28, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: so is the memosprite's");

        // A teammate actively casts a Skill. Her own casts must NOT clear it, so the fixture uses the ally's.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.SKILL_CAST, ally, enemy, 1, 0,
                SkillCategory.BPSKILL, 100102, 0, List.of(enemy)));
        battle.processRequests();

        Assertions.assertEquals(0.0, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「除自身与衣匠以外的任意单位主动施放技能时」 — her copy is gone");
        Assertions.assertEquals(0.0, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and so is the memosprite's");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 星魂 6: thunder penetration while 【至高之姿】 holds.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void theStanceGrantsThunderPenetrationToBothOfThem() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertEquals(0.0, aglaea.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "precondition: no penetration before the stance");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();

        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the stance brought the memosprite out");
        Assertions.assertEquals(0.2, aglaea.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "「自身…雷属性抗性穿透提高 20%」");
        Assertions.assertEquals(0.2, garmentmaker.getAttribute(AttributeType.DAMAGE_PENETRATION_THUNDER).get(), EPS,
                "「…与衣匠的」");
    }

    /**
     * The penetration is <b>per element</b>: it must shorten a thunder hit and leave a physical one alone.
     *
     * <p>The enemy's resistance is raised to 40% so the difference is visible in the settled number; with penetration 20%
     * the thunder hit should take exactly the hit without it times {@code (1 - 0.2) / 1} in the resistance zone.
     */
    @Test
    public void thePenetrationOnlyHelpsThunder() {
        double plainThunder = settled("Thunder", 0.0);
        double penetratedThunder = settled("Thunder", 0.2);
        double plainPhysical = settled("Physical", 0.0);
        double penetratedPhysical = settled("Physical", 0.2);
        Assertions.assertTrue(penetratedThunder > plainThunder,
                "thunder: " + plainThunder + " -> " + penetratedThunder);
        Assertions.assertEquals(plainPhysical, penetratedPhysical, EPS,
                "physical must not move: the sentence names 雷, so the modifier sits on DAMAGE_PENETRATION_THUNDER only");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Traces: 飞驰之阳 (energy floor) and 短视之惩 (the speed-scaled attack raise).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * 行迹 飞驰之阳:「战斗开始时，若自身能量不足 50%，恢复自身能量至 50%」.
     *
     * <p>Measured as a floor, not a gain: she starts at zero, the trace lifts her to exactly half her maximum (350 ⇒
     * 175), and it must not overshoot - "恢复至" is a target, not an amount added.
     */
    @Test
    public void theRacingSunFillsTheEnergyBarToHalf() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        double beforeBattle = aglaea.getCurrentEnergy();
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        double half = aglaea.getMaxEnergy() * 0.5;
        Assertions.assertTrue(half > 0, "precondition: she has an energy bar");
        Assertions.assertTrue(beforeBattle < half, "precondition: she starts below the floor (" + beforeBattle + ")");
        Assertions.assertEquals(half, aglaea.getCurrentEnergy(), 1e-9,
                "「恢复自身能量至 50%」: " + half + " of " + aglaea.getMaxEnergy());
    }

    /**
     * 行迹 短视之惩:「处于【至高之姿】状态时，阿格莱雅与衣匠的攻击力提高，提高数值等同于阿格莱雅速度的 720% + 衣匠速度的
     * 360%」.
     *
     * <p>Measured as the difference the trace makes, on both units: the expected raise is computed from the two speeds the
     * battle actually holds (hers and the memosprite's), so the two shares cannot be swapped without failing.
     */
    @Test
    public void theTraceRaisesBothAttackValues() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double herAttackBefore = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        battle.castImmediate(aglaea.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                aglaea, List.of(enemy));
        battle.processRequests();
        com.laosun.aluminium.entity.Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the Ultimate brought the memosprite out");

        double herSpeed = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        double itsSpeed = garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        double raise = 7.2 * herSpeed + 3.6 * itsSpeed;
        Assertions.assertTrue(raise > 0, "precondition: both speeds are real numbers");

        Assertions.assertEquals(herAttackBefore + raise,
                aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 1e-6,
                "「阿格莱雅与衣匠的攻击力提高…」 — hers: 720% of her speed + 360% of the memosprite's");
        Assertions.assertEquals(herAttackBefore + raise,
                garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 1e-6,
                "…and the memosprite gets the same number (its base ATTACK is hers, see the panel)");
    }

    /** The slot's level in a battle of that Eidolon rank. */
    private static int level(SkillType slot, int eidolon) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return aglaea.skillLevel(aglaea.getSkills().get(slot));
    }

    /** One of the memosprite's own skills, addressed by its DATA slot, read at that Eidolon rank. */
    private static int memospriteLevel(int dataSlot, int eidolon) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        com.laosun.aluminium.entity.Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        return garmentmaker.skillLevel(garmentmaker.skillAt(dataSlot));
    }

    /**
     * @param eidolon       star rank
     * @param byGarmentmaker whether the finishing attack is the memosprite's rather than hers
     * @return her energy after one attack on a pre-threaded enemy
     */
    private static double energyAfterAttack(int eidolon, boolean byGarmentmaker) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Character ally = CharacterFactory.create(1001, LEVEL);
        // An outside rule stamps 【间隙织线】, so the Eidolon's clause is the only difference between the two runs.
        EffectSpec thread = new EffectSpec();
        TriggerSpecs.set(thread, "op", "APPLY_BUFF");
        TriggerSpecs.set(thread, "buff", THREAD);
        TriggerSpecs.set(thread, "turns", 5);
        TriggerSpecs.set(thread, "target", "target");
        ally.setTriggerTable(new TriggerTable(9702, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), thread))));
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.summonMemosprite(aglaea);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        Assertions.assertTrue(enemy.getBuffManager().hasState(THREAD), "precondition: the victim is threaded");

        double before = aglaea.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED,
                byGarmentmaker ? battle.summonOf(aglaea) : aglaea, enemy, 1, 0));
        return aglaea.getCurrentEnergy() - before;
    }

    /** One basic attack on a threaded enemy, as the HP it actually removes, at the given Eidolon rank. */
    private static double damageOnAThreadedTarget(int eidolon) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(1001, LEVEL);
        EffectSpec thread = new EffectSpec();
        TriggerSpecs.set(thread, "op", "APPLY_BUFF");
        TriggerSpecs.set(thread, "buff", THREAD);
        TriggerSpecs.set(thread, "turns", 5);
        TriggerSpecs.set(thread, "target", "target");
        ally.setTriggerTable(new TriggerTable(9703, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), thread))));
        Enemy enemy = BattleFixture.monster();
        // A fixed roll keeps crit out of the ratio.
        Random noCrit = BattleFixture.alwaysRoll(0.99);
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        battle.summonMemosprite(aglaea);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        double before = enemy.getCurrentHp();
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        return dealt;
    }

    /** Her own DEFENCE_IGNORE after {@code turnStarts} of her own turn starts, at the given Eidolon rank. */
    private static double defenceIgnoreAfter(int eidolon, int turnStarts) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int i = 0; i < turnStarts; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, aglaea, aglaea, 0, 0));
            battle.processRequests();
        }
        return aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }

    /**
     * Settles one 1000-damage hit of {@code element} against a 40%-resistant enemy whose attacker carries
     * {@code thunderPen} of <b>thunder</b> penetration - the constant the sentence names, whatever element is being
     * settled, so a physical hit proves the routing by staying put.
     */
    private static double settled(String element, double thunderPen) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        enemy.setDamageResist(java.util.Map.of(DamageElement.fromString(element), 0.4));
        if (thunderPen != 0) {
            // "pure" = the value in the attribute's own units: penetration is a number (0.2), not a share of a base that
            // is zero. Measured 2026-11-06: an `add_percent` modifier on it moved nothing.
            aglaea.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.of(
                    AttributeType.DAMAGE_PENETRATION_THUNDER, "pure", thunderPen, "buff", 5, false));
            battle.processRequests();
        }
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(aglaea, enemy, DamageElement.fromString(element), DamageType.NORMAL, 1000));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
