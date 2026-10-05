package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, last sentence (2026-10-02): "提高数值等同于本次治疗数值的 #1%", counted into 小伊卡's healing total.
 *
 * Two-sided: the ode is cast at her (capturing #1 in basis points), then a 1000-heal runs while she wears the ode -- the total must gain exactly 1000 * #1. In the control the
 * ode is never cast, so nothing was captured and the same heal adds nothing.
 */
public class SkyOdeHealingTotalTest {
    private static final int LEVEL = 80;
    private static final int HYACINE = 1409;
    private static final int CYRENE = 1415;
    private static final int HEALER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "献予「天空」之诗";
    private static final String TOTAL = "小伊卡累计治疗";
    private static final double HEAL = 1000;
    /** Her HP at this level, and the HP she is left at before the heal -- the heal is capped by the missing amount. */
    private static final double HEAL_UNIT_MAX = 1195.2864000000002;
    private static final double HEAL_UNIT_HP = 597.6432000000001;

    @Test
    public void theHealAddsItsOwnShareToTheTotal() {
        double[] with = total(true);
        double[] without = total(false);
        System.out.println("[sky_heal] the healing total gained " + with[0] + " on a heal of " + with[1]
                + " ; without the ode " + without[0] + " on a heal of " + without[1]);
        Assertions.assertEquals(0.0, without[0], 1e-9, "without the ode nothing was captured, so the same heal adds nothing");
        // Both numbers are the engine's own: the HP the heal restored, and the share captured at cast time (#1 = 1.008 at this level).
        // A resource holds an INTEGER, so the gain is the healed amount times #1 rounded -- measured, the difference between 602 and 602.42 was exactly that.
        Assertions.assertEquals(Math.round(with[1] * 1.008), with[0], 1e-9,
                "the total gained the healed amount times #1, rounded into the resource");
    }

    /** The healing total after one exact 1000 heal, with or without the ode cast at her. */
    private static double[] total(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character healer = CharacterFactory.create(HEALER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine, healer),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);
        healer = battle.characters.get(2);

        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(19);
            Assertions.assertNotNull(ode, "precondition: slot 19");
            SkillExecutor.execute(battle, ode, sprite, List.of(hyacine));
            battle.processRequests();
        }

        // \\u2b50 the healer is the RULE OWNER of a HEAL (measured), and the ode's mark must be on the healer for the gate `actor has_state`
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "ADD_STACK");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "amount", 1.0);
        TriggerSpecs.set(mark, "maxStacks", 99999);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        EffectSpec heal = new EffectSpec();
        TriggerSpecs.set(heal, "op", "HEAL");
        TriggerSpecs.set(heal, "amount", HEAL);
        TriggerSpecs.set(heal, "target", "ally_cid:" + HYACINE);
        healer.setTriggerTable(new TriggerTable(HEALER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), mark),
                TriggerSpecs.rule("TURN_START", List.of(), heal))));

        // fire BATTLE_START by hand: a hand-built table is not registered the way loaded content is, so the engine's own battle-start event does not reach it
        battle.fireTriggers(TriggerEvent.BATTLE_START, healer, null, 0, 0);
        battle.processRequests();
        hyacine.takeDamage(hyacine.getMaxHp() * 0.5);
        battle.processRequests();
        System.out.println("[sky_heal]   diag: her captured share = "
                + battle.characters.get(1).getResources().value("天空之诗的治疗份额")
                + " ; the healer's mark = " + battle.characters.get(2).getBuffManager().stacksOf(MARK)
                + " ; her HP " + battle.characters.get(1).getCurrentHp() + "/" + battle.characters.get(1).getMaxHp());
        double before = battle.characters.get(1).getResources().value(TOTAL);
        double hpBefore = battle.characters.get(1).getCurrentHp();
        battle.fireTriggers(TriggerEvent.TURN_START, healer, null, 0, 0);
        battle.processRequests();
        // pair [the total it gained, the HP the heal actually restored]: the heal is capped by her missing HP, so the second number is the engine's own answer
        return new double[]{battle.characters.get(1).getResources().value(TOTAL) - before,
                battle.characters.get(1).getCurrentHp() - hpBefore};
    }
}
