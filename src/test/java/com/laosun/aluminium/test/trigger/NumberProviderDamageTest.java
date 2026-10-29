package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.NumberProviders;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The provider types that read the <b>damage in flight</b>: {@code original_damage},
 * {@code settled_damage_this_event}, {@code elation_base} and {@code cast_applied}.
 *
 * <p><b>Why these four are the ones worth a judge.</b> They are the numbers a rider sentence needs ("30% of the damage
 * that triggered me", "20% of everything settled this firing", "for each target frozen"), and each is read from a place
 * no other provider can reach - the instance, the battle's running total, the Elation level row, and the cast's own
 * outcome. A provider that read the wrong one of those would still produce a number, so nothing would report it.
 *
 * <p>⚠ The first two cases call {@link NumberProviders#resolve} directly, with a hand-built
 * {@link com.laosun.aluminium.trigger.TriggerTable.TriggerContext}. That is deliberate: the numbers are functions of the
 * <b>context</b> (which instance, which row), and a full battle would put damage settlement between the reader and the
 * assertion. The reader is the thing under test.
 */
public class NumberProviderDamageTest {

    private static final int OWNER = 1001;
    private static final int LEVEL = 80;

    /**
     * {@code original_damage} is the instance that triggered the event, before the zones: with no zone multiplier the
     * instance's value and its base coincide, and the provider returns what the event carried.
     */
    @Test
    public void originalDamageIsTheInstancesOwnValue() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        Damage instance = new Damage(owner, enemy, DamageElement.FIRE, DamageType.NORMAL, 400, SkillCategory.ULTRA);

        double value = NumberProviders.resolve(provider("original_damage", p -> { }),
                effect(), context(battle, owner, enemy, 400, instance));

        Assertions.assertEquals(400, value, 1e-9, "the triggering instance was 400, so the provider reads 400");
    }

    /**
     * {@code elation_base} is the character-level row, not a share of anything - the reader is the same one the
     * {@code elation_base} scale uses.
     */
    @Test
    public void elationBaseIsTheCharactersLevelRow() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());

        double value = NumberProviders.resolve(provider("elation_base", p -> { }),
                effect(), context(battle, owner, enemy, 0, null));

        Assertions.assertEquals(Constant.ELATION_BASIC_LEVEL_DAMAGE.get(LEVEL), value, 1e-9,
                "the row for the owner's character level is what the Elation formula multiplies");
    }

    /**
     * {@code cast_applied} counts what the cast actually did, and a zero count is a legal answer (nothing landed).
     */
    @Test
    public void castAppliedCountsTheCastsOutcome() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setCurrentEnergy(0);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.recordCastApplied("冻结");
        battle.recordCastApplied("冻结");

        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_ENERGY");
        TriggerSpecs.set(gain, "amountFrom", provider("cast_applied", p -> p.setState("冻结")));
        TriggerSpecs.set(gain, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", null, gain))));
        battle.startBattle();

        Assertions.assertEquals(2, owner.getCurrentEnergy(), 1e-9, "two frozen targets must grant two energy");
    }

    /**
     * The running total needs the battle that is summing it; without one the provider is loud rather than 0.
     */
    @Test
    public void settledDamageNeedsTheBattleThatIsSummingIt() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = BattleFixture.monster();

        Assertions.assertThrows(IllegalStateException.class,
                () -> NumberProviders.resolve(provider("settled_damage_this_event", p -> { }),
                        effect(), context(null, owner, enemy, 0, null)),
                "a running total with no battle to hold it is not a number");
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    private static TriggerTable.TriggerContext context(Battle battle, Character owner, Enemy enemy,
                                                       double amount, Damage instance) {
        return new TriggerTable.TriggerContext(owner, owner, enemy, 0, amount, instance, battle,
                SkillCategory.UNSPECIFIED);
    }

    private static EffectSpec effect() {
        return new EffectSpec();
    }

    private static NumberProvider provider(String type, java.util.function.Consumer<NumberProvider> fill) {
        NumberProvider provider = new NumberProvider();
        provider.setType(type);
        fill.accept(provider);
        return provider;
    }
}
