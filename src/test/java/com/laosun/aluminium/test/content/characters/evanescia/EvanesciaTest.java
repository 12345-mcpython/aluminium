package com.laosun.aluminium.test.content.characters.evanescia;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1505 Evanescia (绯英), every clause her own text states: the talent's half-panel Elation value and the technique's 20
 * [好活当赏], the energy sync, the Skill rider (8%) and the Ultimate rider (12%), plus the three Eidolon halves that are
 * plain attributes.
 *
 * <p><b>The two clauses her own text states completely.</b> 「绯英获得等同于暴击伤容 50% 的欢愉度」 (Evanescia gains
 * Elation equal to 50% of her CRIT DMG) and the technique's 20 [好活当赏].
 *
 * <p><b>The three Eidolon halves.</b> The character's {@code _gaps} was a pointer to the ledger, and the ledger said
 * 「未逐句核」 — so nothing pinned these numbers. Each assertion below is the Eidolon's own sentence read off the panel.
 *
 * <p><b>The energy sync.</b> "when Evanescia gains energy, she gains an equal amount of [好活当赏] at the same time".
 * The energy comes from the ENGINE's own credit (casting a basic attack), not from a hand-written rule, and the old table
 * is never replaced -- an earlier version of this test did that and silently removed the very rule under test (measured:
 * it read 20, the technique's gift alone). The expectation is the MEASURED energy delta, so the judge states the rule
 * rather than a number borrowed from somewhere else.
 *
 * <p><b>The Skill rider.</b> "while 绯英 holds [好活当赏], casting the Skill deals 8% Physical Elation damage to the
 * enemy targets that were attacked". Two-sided on the SAME cast: with the resource her skill costs the enemy extra health,
 * and after spending it the same cast deals strictly less. That is what "while holding [好活当赏]" means, read on real
 * numbers.
 *
 * <p><b>The Ultimate rider.</b> "while Evanescia (绯英) holds [好活当赏], casting the Ultimate deals 12% Physical Elation
 * damage to all enemies". The rider is wired to CAST_SETUP, which fires ONCE per cast: on DEALING_DAMAGE an all-target
 * ultimate would produce one instance per victim and the rider would follow N times. The judge reads the two enemies'
 * health and pins the shipped rule too, because "more damage than without" alone cannot see a change to the percentage or
 * the type.
 */
public class EvanesciaTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "elation:certified_banger";

    // ==================================================================
    // The talent and the technique
    // ==================================================================

    @Test
    public void theTalentDerivesTheElationValueAndTheTechniqueGivesTwenty() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        double critDamage = elation.getAttribute(AttributeType.CRIT_ATTACK).get();
        // ⚠ 1505's 「进入战斗后…获得 20 点【好活当赏】」 is the TECHNIQUE's clause: its rule carries self has_state sys:technique, so
        // the release must be declared BEFORE the battle starts (it was ungated until 2026-11-09).
        battle.markTechniqueUsed(elation);
        battle.startBattle();
        int elationValue = elation.getResources().value("elation:elation_degree");
        int gifts = elation.getResources().value("elation:certified_banger");
        System.out.println("[1505] crit damage=" + critDamage + " -> Elation (elation:elation_degree)=" + elationValue
                + " (half is " + Math.round(critDamage * 0.5) + ") ; Certified Banger gifts (elation:certified_banger)=" + gifts);
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), elationValue,
                "the talent sets the Elation value to half the crit-damage panel");
        Assertions.assertEquals(20, gifts, "and the technique grants 20 gifts at battle start");
    }

    @Test
    public void raisedCritDamagePanel() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        elation.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), raiseCritDamage()),
                TriggerSpecs.rule("BATTLE_START", List.of(), derive()))));
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        double critDamage = elation.getAttribute(AttributeType.CRIT_ATTACK).get();
        int elationValue = elation.getResources().value("elation:elation_degree");
        System.out.println("[1505] with a raised panel: crit damage=" + critDamage + " -> Elation (elation:elation_degree)="
                + elationValue + " (half is " + Math.round(critDamage * 0.5) + ")");
        Assertions.assertTrue(critDamage >= 1.4, "the panel really moved (it reads " + critDamage + ")");
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), elationValue,
                "the talent still tracks the panel, and now the half is not zero");
    }

    /**
     * The shipped numbers themselves. The behavioural tests above build their own effect in Java, so a
     * json-only change to `amountPercent` slipped past them -- measured: `0.5 -> 0.25` came back with 0 red. This reads the
     * character's OWN compiled rules, which is exactly what the content file says.
     */
    @Test
    public void theShippedRulesCarryTheStatedNumbers() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        int seen = 0;
        for (var rule : elation.getTriggerTable().rulesFor(
                com.laosun.aluminium.enums.TriggerEvent.BATTLE_START)) {
            if (!rule.id().startsWith("p1505_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                seen++;
                System.out.println("[1505] spec " + rule.id() + " resource=" + effect.getResource()
                        + " amount=" + effect.getAmount() + " fromAttr=" + effect.getAmountFromAttr()
                        + " percent=" + effect.getAmountPercent());
                if (effect.getAmountFromAttr() != null) {
                    Assertions.assertEquals("CRIT_ATTACK", effect.getAmountFromAttr(),
                            "the talent derives the value from the crit-damage panel");
                    Assertions.assertEquals(0.5, effect.getAmountPercent(), 1e-9, "and takes half of it");
                } else if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
                    // the third source: "获得能量时同步获得等值[elation:certified_banger]" (gaining energy also gains an equal amount of [好活当赏]) -- the amount is the event's own
                    Assertions.assertEquals("elation:certified_banger", effect.getResource(),
                            "the mirrored resource is 【elation:certified_banger】 (gifts)");
                } else if ("DAMAGE".equals(effect.getOp())) {
                    // the technique's own damage: a rule-driven ORDINARY instance, so it has no `amount` at all
                    Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "100% of her attack");
                    Assertions.assertEquals(Boolean.TRUE, effect.getOrdinary(), "as an ordinary hit");
                } else {
                    Assertions.assertEquals(20.0, effect.getAmount(), 1e-9, "the technique grants twenty");
                }
            }
        }
        Assertions.assertEquals(3, seen, "BATTLE_START carries the derive, the technique’s twenty and its damage");

        // The third clause lives on a DIFFERENT event: "获得能量时同步获得等值的[elation:certified_banger]" (gaining energy also gains an equal amount of [好活当赏])
        // is triggered by ENERGY_GAINED, so it is pinned by reading that event's rules -- the first version of this test counted
        // BATTLE_START only and read 2, which is exactly what made it obvious.
        var theSyncRule = elation.getTriggerTable().rulesFor(
                        com.laosun.aluminium.enums.TriggerEvent.ENERGY_GAINED).stream()
                .filter(rule -> rule.id().endsWith("energy_sync")).toList();
        Assertions.assertEquals(1, theSyncRule.size(), "the energy sync is wired to ENERGY_GAINED");
        var syncEffect = theSyncRule.getFirst().effects().getFirst();
        System.out.println("[1505] spec " + theSyncRule.getFirst().id() + " resource=" + syncEffect.getResource()
                + " amountFromEvent=" + syncEffect.getAmountFromEvent());
        Assertions.assertEquals(Boolean.TRUE, syncEffect.getAmountFromEvent(),
                "and its amount is the event’s own magnitude");
    }

    // ==================================================================
    // The Eidolon halves that are plain attributes
    // ==================================================================

    /**
     * 星魂 1: 「全属性抗性穿透提高 20%」 — the penetration attribute, on herself.
     */
    @Test
    public void eidolon1RaisesAllTypeResistancePenetration() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.DAMAGE_PENETRATION), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.2, attributeAt(1, AttributeType.DAMAGE_PENETRATION), 1e-9, "「全属性抗性穿透提高 20%」");
    }

    /**
     * 星魂 2: 「暴击伤害提高 36%」.
     */
    @Test
    public void eidolon2RaisesCritDamage() {
        Assertions.assertEquals(0.5, attributeAt(0, AttributeType.CRIT_ATTACK), 1e-9,
                "precondition: the base crit damage is 0.5");
        Assertions.assertEquals(0.86, attributeAt(2, AttributeType.CRIT_ATTACK), 1e-9, "「暴击伤害提高 36%」");
    }

    /**
     * 星魂 4: 「绯英造成的伤害无视敌方目标 15% 的防御力」 — HER defence ignore, not a debuff on the enemy.
     */
    @Test
    public void eidolon4IgnoresFifteenPercentDefence() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.DEFENCE_IGNORE), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.15, attributeAt(4, AttributeType.DEFENCE_IGNORE), 1e-9,
                "「造成的伤害无视敌方目标 15% 的防御力」");
    }

    // ==================================================================
    // The energy sync
    // ==================================================================

    @Test
    public void gainingEnergyMirrorsIntoGifts() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy),
                BattleFixture.seeded());
        // ⚠ 1505's 「进入战斗后…获得 20 点【好活当赏】」 is the TECHNIQUE's clause: its rule carries self has_state sys:technique, so
        // the release must be declared BEFORE the battle starts (it was ungated until 2026-11-09).
        battle.markTechniqueUsed(elation);
        battle.startBattle();
        int giftsBefore = elation.getResources().value(GIFTS);
        double energyBefore = elation.getCurrentEnergy();
        battle.castImmediate(elation.getSkills().get(SkillType.COMMON), elation, List.of(enemy));
        double energyAfter = elation.getCurrentEnergy();
        int giftsAfter = elation.getResources().value(GIFTS);
        int mirrored = (int) Math.round(energyAfter - energyBefore);
        System.out.println("[1505-sync] the cast credited " + (energyAfter - energyBefore) + " energy ; gifts "
                + giftsBefore + " -> " + giftsAfter + " (expected " + (giftsBefore + mirrored) + ")");
        Assertions.assertTrue(mirrored > 0, "the cast really credited energy (it credited " + mirrored + ")");
        Assertions.assertEquals(giftsBefore + mirrored, giftsAfter,
                "every point of energy is mirrored one-for-one into [elation:certified_banger] (Certified Banger)");
    }

    @Test
    public void withNoEnergyNothingIsMirrored() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        // ⚠ The technique's 20 gifts need the release declared before the battle starts (`min_eidolon`-style gate is
        // `self has_state sys:technique`; the rule was ungated until 2026-11-09).
        battle.markTechniqueUsed(elation);
        battle.startBattle();
        int gifts = elation.getResources().value(GIFTS);
        System.out.println("[1505-sync] before any energy is credited the gifts read " + gifts);
        Assertions.assertEquals(20, gifts, "only the technique’s 20 so far (the false side of the sync)");
    }

    // ==================================================================
    // The Skill rider
    // ==================================================================

    @Test
    public void theRiderNeedsTheGifts() {
        double withGifts = skillDamage(true);
        double without = skillDamage(false);
        System.out.println("[1505-rider] the skill took " + withGifts + " while holding [elation:certified_banger] (Certified Banger) and "
                + without + " after spending it (difference " + (withGifts - without) + ")");
        Assertions.assertTrue(without > 0, "the skill itself deals damage");
        Assertions.assertTrue(withGifts > without,
                "and holding [elation:certified_banger] (Certified Banger) adds the 8% Elation rider on top");
    }

    /**
     * The shipped numbers and the type, read off the compiled rule. The behavioural test above only asks
     * whether holding [好活当赏] adds damage, so a change to `percent` (8 -> 4) or to `damageType` would still satisfy it --
     * measured: both mutations came back with 0 red without this test (discipline 232).
     */
    @Test
    public void theShippedRiderStatesItsNumberAndType() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE)
                .stream().filter(rule -> rule.id().startsWith("p1505_")).toList();
        Assertions.assertEquals(1, rules.size(), "the skill rider is one rule on DEALING_DAMAGE");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[1505-rider] spec " + rules.getFirst().id() + " scale=" + effect.getScale()
                + " percent=" + effect.getPercent() + " element=" + effect.getElement()
                + " damageType=" + effect.getDamageType());
        Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "8% of the derived scale");
        Assertions.assertEquals("self_attr:ATTACK", effect.getScale(), "which is her own attack");
        Assertions.assertEquals("physical", effect.getElement(), "the element her text names");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "and the type it calls Elation damage");
    }

    // ==================================================================
    // The Ultimate rider
    // ==================================================================

    @Test
    public void theUltimateRiderNeedsTheGifts() {
        double withGifts = ultimateDamage(true);
        double without = ultimateDamage(false);
        System.out.println("[1505-ult] the ultimate took " + withGifts + " in total while holding [elation:certified_banger] (Certified Banger) and "
                + without + " after spending it (difference " + (withGifts - without) + ")");
        Assertions.assertTrue(without > 0, "the ultimate itself deals damage");
        Assertions.assertTrue(withGifts > without, "holding [elation:certified_banger] (Certified Banger) adds the all-enemy Elation rider");
    }

    /**
     * The shipped rule, read off the compiled table (discipline 232): the number, the scope and the type.
     */
    @Test
    public void theShippedUltRiderStatesItsNumberScopeAndType() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.CAST_SETUP)
                .stream().filter(rule -> rule.id().contains("ult_")).toList();
        Assertions.assertEquals(2, rules.size(),
                "the two ultimate riders (12% all-enemy, 14% random) -- picked by id, because CAST_SETUP also carries the skill’s laugh rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[1505-ult] spec " + rules.getFirst().id() + " percent=" + effect.getPercent()
                + " target=" + effect.getTarget() + " damage_type=" + effect.getDamageType());
        Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "12% of her attack");
        Assertions.assertEquals("all_enemies", effect.getTarget(), "over the whole enemy side");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "as Elation damage");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static EffectSpec derive() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(spec, "resource", "elation:elation_degree");
        TriggerSpecs.set(spec, "amountFromAttr", "CRIT_ATTACK");
        TriggerSpecs.set(spec, "amountPercent", 0.5);
        return spec;
    }

    private static EffectSpec raiseCritDamage() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "MODIFY_ATTR");
        TriggerSpecs.set(spec, "attribute", "CRIT_ATTACK");
        TriggerSpecs.set(spec, "percent", 1.0);
        TriggerSpecs.set(spec, "permanent", true);
        TriggerSpecs.set(spec, "target", "self");
        return spec;
    }

    private static double attributeAt(int eidolon, AttributeType type) {
        Character owner = CharacterFactory.create(WEARER, 80, false, null, null, eidolon);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(type).get();
    }

    private double skillDamage(boolean keepTheGifts) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy),
                BattleFixture.seeded());
        // ⚠ 1505's 「进入战斗后…获得 20 点【好活当赏】」 is the TECHNIQUE's clause: its rule carries self has_state sys:technique, so
        // the release must be declared BEFORE the battle starts (it was ungated until 2026-11-09).
        battle.markTechniqueUsed(elation);
        battle.startBattle();
        if (!keepTheGifts) {
            elation.getResources().spend(GIFTS, elation.getResources().value(GIFTS));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(elation.getSkills().get(SkillType.SKILL), elation, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    private double ultimateDamage(boolean keepTheGifts) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(MONSTER, 90, 2);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(first, second),
                BattleFixture.seeded());
        // ⚠ 1505's 「进入战斗后…获得 20 点【好活当赏】」 is the TECHNIQUE's clause: its rule carries self has_state sys:technique, so
        // the release must be declared BEFORE the battle starts (it was ungated until 2026-11-09).
        battle.markTechniqueUsed(elation);
        battle.startBattle();
        if (!keepTheGifts) {
            elation.getResources().spend(GIFTS, elation.getResources().value(GIFTS));
        }
        double before = first.getCurrentHp() + second.getCurrentHp();
        battle.castImmediate(elation.getSkills().get(SkillType.ULTRA), elation, List.of(first, second));
        return before - (first.getCurrentHp() + second.getCurrentHp());
    }
}
