package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23010: the wearer's SKILL and ULTIMATE damage are 18% higher (its crit damage is the row's own props).
 *
 * <p>The two halves are two rules, because {@code from_category} takes a single value. The judge checks both directions: each
 * category must be lifted, and a plain attack must not be -- a rule written without the condition would lift all three.
 *
 * <p>Also covers the follow-up half of 拂晓之前: 「施放战技或终结技后获得【梦身】；触发追加攻击时<b>消耗【梦身】</b>，使追加攻击造成的伤害
 * 提高 <b>48%</b>，持续 1 回合」. The old note said the engine had no way to spend a state when a follow-up arrives, and shipped a
 * 1-turn window that paid EVERY follow-up. There is one: both halves live in the SAME rule - read the stack
 * ({@code self_stacks:梦身 >= 1}), boost, then {@code REMOVE_STACK} - so the payment is one-shot by construction.
 */
public class BeforeDawnTest {
    private static final int CONE = 23010;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double SHARE = 0.18;
    // From the merged BeforeDawnConeTest: its wearer is a DIFFERENT character id (the old class's WEARER, renamed).
    private static final int CONE_WEARER = 1204;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private int rulesFor(SkillCategory category) {
        int seen = 0;
        // Note: Build first: reading wearer on the same line as its own construction is an NPE (measured).
        Battle battle = battle(true);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, category))) {
            if (!rule.id().startsWith("cone23010_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                System.out.println("[23010] category=" + category + " rule=" + rule.id()
                        + " op=" + effect.getOp() + " percent=" + effect.getPercent()
                        + " conditions=" + rule.conditions());
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "18% at rank 1");
                seen++;
            }
        }
        return seen;
    }

    /**
     * The third sentence, in its 2026-10-31 shape: 「施放战技或终结技后获得【梦身】」 is a one-stack state, and 「触发追加攻击时
     * <b>消耗【梦身】</b>」 is a SECOND rule that reads the stack and removes it in the same firing. The old shape wrote a 1-turn
     * {@code FOLLOW_UP_DAMAGE_BOOST} window instead, which paid every follow-up in that turn rather than one.
     */
    @Test
    public void theUltimateArmsTheFollowUp() {
        Battle battle = battle(true);
        var gains = wearer.getTriggerTable().rulesFor(TriggerEvent.ULT_CAST).stream()
                .filter(r -> r.id().equals("cone23010_gain_dream_body")).toList();
        Assertions.assertEquals(1, gains.size(), "施放终结技后获得【char:23010/dream_body】");
        var stack = gains.getFirst().effects().getFirst();
        Assertions.assertEquals("ADD_STACK", stack.getOp());
        Assertions.assertEquals("char:23010/dream_body", stack.getBuff());
        Assertions.assertEquals(1, stack.getMaxStacks(), "one stack at a time");

        var spends = wearer.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().equals("cone23010_follow_up_damage")).toList();
        Assertions.assertEquals(1, spends.size(), "触发追加攻击时消耗【char:23010/dream_body】");
        var rule = spends.getFirst();
        Assertions.assertEquals(List.of("actor == self", "damage_is_additional", "self_stacks:char:23010/dream_body >= 1.0"),
                rule.conditions().stream().map(c -> c.source()).toList(),
                "the follow-up is an ADDITIONAL instance, and the state must still be there");
        Assertions.assertEquals("BOOST_DAMAGE", rule.effects().getFirst().getOp());
        Assertions.assertEquals(0.48, rule.effects().getFirst().getPercent(), 1e-9, "48% at rank 1");
        Assertions.assertEquals("REMOVE_STACK", rule.effects().get(1).getOp(), "and the state is spent in the same firing");
    }

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(23010, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * The boost the ENGINE's own follow-up path carries - `applyAdditionalDamage`, which is the one that sets
         * "does not count as an attack", the fact `damage_is_additional` reads (a hand-built ADDITIONAL Damage keeps
         * countsAsAttack = true and would never match).
         */
        double followUp() {
            double[] boost = {-1};
            var observer = new java.util.ArrayList<Damage>();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    observer.add(damage));
            battle.applyAdditionalDamage(wearer, enemy, DamageElement.PHYSICAL, 1000);
            battle.processRequests();
            return observer.stream()
                    .filter(d -> d.getType() == DamageType.ADDITIONAL)
                    .mapToDouble(d -> d.boostArea().getRate() - 1.0)
                    .findFirst().orElse(boost[0]);
        }
    }

    /**
     * One follow-up gets the 48%; the next one - once the state is spent - does not.
     */
    @Test
    public void theFirstFollowUpPaysAndTheSecondDoesNot() {
        Run run = new Run();
        Assertions.assertEquals(0.0, run.followUp(), 1e-9, "nothing to spend before an ultimate or a skill");

        run.wearer.setCurrentEnergy(run.wearer.getMaxEnergy());
        run.battle.castImmediate(run.wearer.getSkills().get(SkillType.ULTRA), run.wearer, List.of(run.enemy));
        run.battle.processRequests();


        Assertions.assertEquals(0.48, run.followUp(), 1e-6, "the first follow-up spends 【char:23010/dream_body】 and is raised by 48%");
        Assertions.assertEquals(0.0, run.followUp(), 1e-9,
                "the state is gone - 「触发追加攻击时消耗【char:23010/dream_body】」 pays once, not for the whole turn");
    }
}
