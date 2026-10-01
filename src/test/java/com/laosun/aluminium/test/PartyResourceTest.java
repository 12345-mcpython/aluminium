package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A PARTY-scoped resource (2026-09-30): the shared \u7b11\u70b9 counter, reader 1505 \u7eef\u82f1\u2019s skill \u300c\u2026\u5e76\u989d\u5916\u83b7\u5f97 10 \u70b9\u7b11\u70b9\u300d.
 *
 * <p>\u2b50 `ResourceManager` refuses an unwired scope with the reason this capability answers: \u300c\u4e00\u4e2a party-level resource needs a per-battle
 * owner\u300d. The battle is that owner now, and the judge reads the counter from the BATTLE (not from the caster), which is what makes
 * it shared rather than a copy per character.
 */
public class PartyResourceTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "\u7b11\u70b9";

    private Battle battle;
    private Character elation;

    private void build() {
        elation = CharacterFactory.create(WEARER, LEVEL);
        battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
    }

    @Test
    public void theSkillAddsToThePartysCounter() {
        build();
        int before = battle.partyResourceValue(LAUGH);
        battle.castImmediate(elation.getSkills().get(SkillType.SKILL), elation, List.of());
        int after = battle.partyResourceValue(LAUGH);
        System.out.println("[party] \u7b11\u70b9 on the battle: " + before + " -> " + after
                + " ; the caster holds it herself? " + elation.getResources().has(LAUGH));
        Assertions.assertEquals(0, before, "the battle starts with none");
        Assertions.assertEquals(10, after, "her skill adds ten to the SHARED counter");
        Assertions.assertFalse(elation.getResources().has(LAUGH),
                "and the counter does not live on the caster (that is what makes it party-level)");
    }

    /** \u2605 The shipped declaration and rule, read off the compiled character (discipline 232). */
    @Test
    public void theShippedDeclarationSaysParty() {
        build();
        var spec = elation.getTriggerTable().resources().stream()
                .filter(resource -> resource.id().equals(LAUGH)).findFirst().orElseThrow();
        System.out.println("[party] declared " + spec.id() + " scope=" + spec.scope() + " max=" + spec.max());
        Assertions.assertEquals("PARTY", spec.scope(), "the declaration says whose counter it is");
        var rules = elation.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().endsWith("skill_laughs")).toList();
        Assertions.assertEquals(1, rules.size(), "one rule adds to it");
        Assertions.assertEquals(10.0, rules.getFirst().effects().getFirst().getAmount(), 1e-9, "ten points");
    }
}
