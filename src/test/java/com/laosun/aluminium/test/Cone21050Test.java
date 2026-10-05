package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21050: when the wearer's MEMOSPRITE casts a skill on an ALLY, the whole party's damage rises 8% for 3 turns.
 *
 * <p>Every word already exists: {@code actor == summon} narrows the event to the rule owner's own memosprite (the
 * selector the engine's SUMMON_ATTACK note names) and {@code target is_ally} says the cast was aimed at our side. The judge
 * drives a real cast by a real memosprite, and checks the two ways it must NOT fire: a character's own Skill, and the
 * memosprite aiming at an enemy.
 */
public class Cone21050Test {
    private static final int CONE = 21050;
    private static final int WEARER = 1402;   // has a memosprite spec
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BOOST = 0.08;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double partyBoost() {
        return wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get()
                + ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    @Test
    public void aMemospriteSupportSkillLiftsTheParty() {
        Battle battle = battle(true);
        double before = partyBoost();
        // ON THE FIELD: `actor == summon` reads battle.summonsOf(owner), so a sprite built but never summoned
        // answers nothing (measured: the positive case read 0 while the false cases passed).
        var sprite = battle.summonMemosprite(wearer);
        battle.castImmediate(new DefaultSkill(WEARER, 2, LEVEL), sprite, List.of(ally));
        double after = partyBoost();
        System.out.println("[21050] party damage boost before=" + before + " after a memosprite support skill=" + after);
        Assertions.assertEquals(2 * BOOST, after - before, 1e-9,
                "8% for the wearer and 8% for the ally (我方全体)");
    }

    @Test
    public void aCharactersOwnSkillDoesNotFire() {
        Battle battle = battle(true);
        double before = partyBoost();
        battle.castImmediate(new DefaultSkill(WEARER, 2, LEVEL), wearer, List.of(ally));
        System.out.println("[21050] after the CHARACTER's own Skill: +" + (partyBoost() - before));
        Assertions.assertEquals(0.0, partyBoost() - before, 1e-9, "装备者的忆灵, not the wearer (false case)");
    }

    @Test
    public void aMemospriteSkillOnAnEnemyDoesNotFire() {
        Battle battle = battle(true);
        double before = partyBoost();
        // ON THE FIELD: `actor == summon` reads battle.summonsOf(owner), so a sprite built but never summoned
        // answers nothing (measured: the positive case read 0 while the false cases passed).
        var sprite = battle.summonMemosprite(wearer);
        battle.castImmediate(new DefaultSkill(WEARER, 2, LEVEL), sprite, List.of(enemy));
        System.out.println("[21050] memosprite aiming at an ENEMY: +" + (partyBoost() - before));
        Assertions.assertEquals(0.0, partyBoost() - before, 1e-9, "对我方目标 (false case)");
    }

    @Test
    public void theSpecPinsTheShareAndTheDuration() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(wearer, battle.summonMemosprite(wearer), ally, 0, 0, null, battle,
                        com.laosun.aluminium.enums.SkillCategory.BPSKILL))) {
            if (!rule.id().startsWith("cone21050_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21050] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "8% at rank 1");
                Assertions.assertEquals(3, effect.getTurns(), "for 3 turns");
                Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
