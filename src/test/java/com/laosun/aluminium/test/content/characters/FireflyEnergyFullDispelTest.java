package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Firefly (流萤) (1310) TALENT: "when energy is restored to the maximum, remove all of its own negative effects".
 *
 * <p>The first shipped reader of the {@code self_energy_percent} condition variable. matching() evaluates conditions,
 * so both directions are pinned: at full energy the rule matches, below full it must not. The engine mutation (flip
 * the ratio) is caught by the SECOND assertion -- with the ratio inverted a zero-energy unit reads Infinity.
 */
public class FireflyEnergyFullDispelTest {
    private static final int CID = 1310;
    private static final int LEVEL = 80;

    private static Character wearer(Battle[] out) {
        Character c = CharacterFactory.create(CID, LEVEL);
        Battle b = new Battle(List.of(c), List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        b.startBattle();
        out[0] = b;
        return c;
    }

    private static int matched(Character owner, Battle battle) {
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, owner, 0, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        return owner.getTriggerTable().matching(TriggerEvent.ENERGY_GAINED, ctx).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList().size();
    }

    @Test
    public void theRuleStatesItsGuardAndItsEffect() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        // rulesFor does NOT evaluate conditions -- that is what makes it the shape test. matching() below is the
        // one that evaluates them, and it is why this test would have failed if it had used matching() at zero energy.
        var mine = c.getTriggerTable().rulesFor(TriggerEvent.ENERGY_GAINED).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList();
        Assertions.assertEquals(1, mine.size(), "the rule is installed");
        Assertions.assertEquals(List.of("actor == self", "self_energy_percent >= 1.0"),
                mine.getFirst().conditions().stream().map(x -> x.source()).toList(), "its guard");
        Assertions.assertEquals("DISPEL", mine.getFirst().effects().getFirst().getOp(), "its effect");
        System.out.println("[1310] shape ok");
    }

    @Test
    public void onlyAFullEnergyBarMatches() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        Battle b = rules[0];
        Assertions.assertTrue(c.getCurrentEnergy() < c.getMaxEnergy(),
                "a fresh unit starts below its maximum (got " + c.getCurrentEnergy() + "/" + c.getMaxEnergy() + ")");
        Assertions.assertEquals(0, matched(c, b),
                "below full energy the talent must NOT fire");
        c.gainEnergy(c.getMaxEnergy() * 2.0);
        Assertions.assertEquals(c.getMaxEnergy(), c.getCurrentEnergy(), 1e-9, "the grant clamps to the cap");
        Assertions.assertEquals(1, matched(c, b),
                "at full energy the talent must fire -- this is the assertion the engine mutation trips");
        System.out.println("[1310] energy guard ok: " + c.getCurrentEnergy() + "/" + c.getMaxEnergy());
    }
}
