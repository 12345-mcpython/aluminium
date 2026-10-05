package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * True DMG (真实伤害) through the {@code DAMAGE} op: {@code "damage_type": "TRUE"} must <b>skip every zone</b>.
 *
 * <p>The engine has the path ({@code Battle.applyTrueDamage} sets {@code Damage.trueDamage()}, which
 * {@code toValue()} honours by skipping the zones), and this op is what reaches it: without it a rule stating the type gets an instance that
 * is <i>labelled</i> TRUE while defence still multiplies it.
 *
 * <p>Note: <b>The fixture needs a REAL cast.</b> A hand-fired event carries no instance, so `damage_is_attack` - the guard
 * that keeps a rider from re-triggering itself - is false and the rule never fires; without the guard it recurses
 * ("Trigger recursion exceeded 8 levels", both measured). So each side is a real cast plus a rider, and the claim is
 * about the <b>difference</b> the rider makes, with the victim's defence as the variable.
 */
public class TrueDamageJudgeTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;

    /** The rider's damage does not move with the victim's defence, while an ordinary instance's does. */
    @Test
    public void trueDamageIgnoresDefence() {
        double soft = riderShare(0, "TRUE");
        double hard = riderShare(2000, "TRUE");
        Assertions.assertTrue(soft > 0, "precondition: the rider deals damage (" + soft + ")");
        Assertions.assertEquals(soft, hard, 1e-6,
                "真实伤害 (true damage) skips the defence zone: " + soft + " on a 0-defence target vs " + hard + " on a 2000 one");

        double ordinarySoft = riderShare(0, null);
        double ordinaryHard = riderShare(2000, null);
        Assertions.assertTrue(ordinarySoft > 0, "precondition: the control rider deals damage");
        Assertions.assertTrue(ordinaryHard < ordinarySoft,
                "control: an ordinary instance DOES fall with defence (" + ordinarySoft + " -> " + ordinaryHard + ")");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** The extra damage a 100%-of-ATK rider adds, with the victim at the given defence. */
    private static double riderShare(int defence, String damageType) {
        return cast(true, defence, damageType) - cast(false, defence, damageType);
    }

    private static double cast(boolean withRider, int defence, String damageType) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(new TriggerTable(OWNER, withRider ? List.of(rider(damageType)) : List.of()));
        Enemy enemy = Enemy.fromAttributes("d" + defence, 1_000_000, defence, 100, 100);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        owner.getSkills().get(SkillType.COMMON).execute(battle, owner, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    private static com.laosun.aluminium.beans.TriggerSpec rider(String damageType) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpecs.set(effect, "critRate", 0.0);
        TriggerSpecs.set(effect, "critDamage", 0.0);
        if (damageType != null) {
            TriggerSpecs.set(effect, "damageType", damageType);
        }
        return TriggerSpecs.rule("DAMAGE_SETTLED", List.of("actor == self", "damage_is_attack"), effect);
    }
}
