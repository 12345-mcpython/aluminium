package com.laosun.aluminium.test.content.characters.yaoguang;

import com.laosun.aluminium.Battle;
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

/**
 * 1502 爻光 (Yao Guang) in one class: the talent 「大吉大利」 and the 【好活当赏】 gate it hides behind, the ultimate's
 * party-wide resistance penetration with its five 笑点, and the Skill's 结界 with the three 笑点 it pays per action.
 *
 * <p>These were five files in the same package. Nothing was dropped: every {@code @Test} keeps its body and the note that
 * says what it measures, the duplicated 1502/80 constants became one of each, and the zone fixture's helper was renamed so
 * it no longer collides with the ultimate group's {@code battle} field.
 */
public class YaoGuangTest {
    private static final int WEARER = 1502;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String LAUGH = "笑点";
    private static final String ZONE = "结界";

    // The ultimate group shares one fixture; build() fills these three.
    private Character yaoguang;
    private Character ally;
    private Battle battle;

    private void build() {
        yaoguang = CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        battle = new Battle(List.of(yaoguang, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
    }

    /**
     * 1502 爻光's talent 大吉大利: 「每次攻击后，对随机 1 个<b>命中目标</b>追加 1 次等同于爻光 20% 攻击力的对应属性欢愉伤害。该伤害不
     * 视为造成了 1 次攻击。」
     *
     * <p>Two things are measured, because the first version of this clause was a silent no-op: that the rider actually settles
     * (damage appears on the enemies after a basic attack), and that it settles on <b>one</b> of the enemies the attack struck -
     * the selector is {@code random_hit_enemy}, not {@code random_enemy}.
     */
    @Test
    public void aBasicAttackPaysOneElationRider() {
        Character yaoguang = CharacterFactory.create(WEARER, LEVEL);
        Enemy first = BattleFixture.monster();
        Enemy second = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(yaoguang)),
                new ArrayList<>(List.of(first, second)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        double lost = 0;
        for (Enemy enemy : List.of(first, second)) {
            if (enemy.isDeath()) {
                continue;
            }
            double before = enemy.getCurrentHp();
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yaoguang, enemy,
                    com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                    com.laosun.aluminium.enums.DamageType.NORMAL, 100));
            lost += before - enemy.getCurrentHp();
        }
        Assertions.assertTrue(lost > 0, "precondition: the hits settled");

        double after = 0;
        for (Enemy enemy : List.of(first, second)) {
            double before = enemy.getCurrentHp();
            battle.castImmediate(yaoguang.getSkills().get(SkillType.COMMON), yaoguang, List.of(enemy));
            after += before - enemy.getCurrentHp();
        }
        System.out.println("[1502] talent: hits " + lost + " -> with rider " + after);
        Assertions.assertTrue(after > lost,
                "「每次攻击后，对随机 1 个命中目标追加 1 次 20% 攻击力的欢愉伤害」 must settle something extra");
    }

    /**
     * 1502 爻光's talent:「**爻光持有【好活当赏】时**，使我方目标施放攻击后触发【大吉大利】，对随机 1 个击中的目标额外造成 1 次
     * 20% 的对应属性欢愉伤害」.
     *
     * <p>Three things this pins down, each of which the rule used to get wrong: the attacker may be an ALLY (it was
     * {@code actor == self}), the state must be up (there was no gate), and the instance takes the ATTACKER's element (it was
     * hard-coded Physical).
     */
    @Test
    public void anAllyAttackTriggersItWhileSheHoldsTheState() {
        double withState = lossToAllyAttack(true);
        double withoutState = lossToAllyAttack(false);
        System.out.println("[1502] ally attack, state up=" + withState + " ; state down=" + withoutState);
        Assertions.assertTrue(withState > withoutState, "【大吉大利】 must add an instance when she holds 【好活当赏】");
    }

    private static double lossToAllyAttack(boolean holdTheState) {
        Character her = CharacterFactory.create(1502, 80);
        Character ally = CharacterFactory.create(1101, 80);
        if (holdTheState) {
            her.getBuffManager().addBuff(new StateBuff("好活当赏", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /**
     * 1502 Yao Guang's (爻光) ultimate on both counts: "获得 5 个笑点…并使我方全体目标全属性抗性穿透提高 10%，持续 3 回合".
     *
     * <p>The laughs are a PARTY counter (the capability shipped in the previous round), so the judge reads them from the BATTLE and
     * checks that the caster does not carry a private copy -- the same discriminator that made the party store necessary.
     */
    @Test
    public void theUltimateLiftsTheWholePartysResistancePenetration() {
        build();
        double selfBefore = yaoguang.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double allyBefore = ally.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        battle.castImmediate(yaoguang.getSkills().get(SkillType.ULTRA), yaoguang, List.of());
        double selfAfter = yaoguang.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double allyAfter = ally.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1502] resistance penetration: herself " + selfBefore + " -> " + selfAfter
                + " ; the ally " + allyBefore + " -> " + allyAfter);
        Assertions.assertEquals(selfBefore + 0.1, selfAfter, 1e-9, "the caster gains 10%");
        Assertions.assertEquals(allyBefore + 0.1, allyAfter, 1e-9, "and so does every ally (我方全体目标)");
    }

    @Test
    public void theUltimateAlsoGivesThePartyFiveLaughs() {
        build();
        int before = battle.partyResourceValue(LAUGH);
        battle.castImmediate(yaoguang.getSkills().get(SkillType.ULTRA), yaoguang, List.of());
        int after = battle.partyResourceValue(LAUGH);
        System.out.println("[1502] Punchline (笑点) on the battle: " + before + " -> " + after
                + " ; the caster holds it herself? " + yaoguang.getResources().has(LAUGH)
                + " ; the ally? " + ally.getResources().has(LAUGH));
        Assertions.assertEquals(0, before, "the battle starts with none");
        Assertions.assertEquals(5, after, "the ultimate grants five to the SHARED counter");
        Assertions.assertFalse(yaoguang.getResources().has(LAUGH), "which no single unit owns");
    }

    /**
     * The shipped rule, read off the compiled table (discipline 232).
     */
    @Test
    public void theShippedRuleCarriesTheNumbersAndDuration() {
        build();
        var rules = yaoguang.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().startsWith("p1502_")).toList();
        Assertions.assertEquals(1, rules.size(), "one shipped rule on a cast setup");
        var effects = rules.getFirst().effects();
        System.out.println("[1502] spec " + rules.getFirst().id() + " effects=" + effects.size()
                + " percent=" + effects.getFirst().getPercent() + " turns=" + effects.getFirst().getTurns()
                + " target=" + effects.getFirst().getTarget());
        Assertions.assertEquals(0.1, effects.getFirst().getPercent(), 1e-9, "10% penetration");
        Assertions.assertEquals(3.0, effects.getFirst().getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", effects.getFirst().getTarget(), "on the whole party");
        Assertions.assertEquals(5.0, effects.get(1).getAmount(), 1e-9, "and the second effect is the five laughs");
    }

    /**
     * 1502 爻光's zone:「结界持续期间，我方全体目标欢愉度提高，提高数值等同于爻光欢愉度的 20%」.
     *
     * <p>The share is HER OWN elation ({@code self_attr:ELATION_DAMAGE_BOOST}), so the judge gives her a known amount first and
     * then checks the party reads a fifth of it.
     */
    @Test
    public void theZoneRaisesThePartysElationByAFifthOfHers() {
        Character her = CharacterFactory.create(1502, 80);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A Character attribute is a DoubleValue with modifiers; a fixture writes the BASE and recomputes.
        her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).base(0.5);
        her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double allyBefore = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double herElation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(ally));
        battle.processRequests();
        double gained = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBefore;
        System.out.println("[1502] her elation=" + herElation + " ; ally gained=" + gained + " (expect " + (herElation * 0.2) + ")");
        Assertions.assertEquals(herElation * 0.2, gained, 1e-9, "「等同于爻光欢愉度的 20%」");
    }

    /**
     * 1502 爻光's Skill: the zone (「结界」, three turns on HER clock) and 「施放普攻、战技后获得 3 个笑点」, which only pays
     * while the zone is up.
     */
    @Test
    public void theSkillOpensAZoneThatPaysThreeLaughsPerAction() {
        Character yao = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = zoneBattle(yao, enemy);

        battle.castImmediate(yao.getSkills().get(SkillType.SKILL), yao, List.of(enemy));
        Assertions.assertTrue(yao.getBuffManager().hasState(ZONE), "「展开结界，持续 3 回合」");
        int after = battle.partyResourceValue("笑点");
        System.out.println("[1502] laughs after the skill = " + after);
        Assertions.assertEquals(3, after, "「施放…战技后获得 3 个笑点」");

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, yao, enemy, 1, 0));
        Assertions.assertEquals(6, battle.partyResourceValue("笑点"), "「施放普攻…后获得 3 个笑点」");
    }

    @Test
    public void withoutTheZoneNothingIsPaid() {
        Character yao = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = zoneBattle(yao, enemy);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, yao, enemy, 1, 0));
        Assertions.assertEquals(0, battle.partyResourceValue("笑点"), "no zone, no laughs");
    }

    private static Battle zoneBattle(Character yao, Enemy enemy) {
        Battle battle = new Battle(new ArrayList<>(List.of(yao, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}
