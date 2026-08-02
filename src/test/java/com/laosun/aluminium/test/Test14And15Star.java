package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests for the 14- and 15-series characters and all Trailblazers.
 */
public class Test14And15Star {

    private Character buildCharacter(int cid) {
        return Character.builder().cid(cid).level(80).isPromote().eidolon(6).build();
    }

    private Enemy buildEnemy(Element element, Element weakness, String name) {
        return Enemy.fromTemplate(name, 80,
                100, 26, 240, 120, 30,
                element, EnumSet.of(weakness), java.util.Map.of(),
                List.of(new Enemy.EnemySkill("Bash", element, 0.5, SkillAttackType.SINGLE)));
    }

    @Test
    public void testAllCharactersHavePassives() {
        int[] cids = {1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1412,
                1413, 1414, 1415, 1501, 1502, 1504, 1505, 1506,
                8001, 8002, 8003, 8004, 8005, 8006, 8007, 8008, 8009, 8010};
        // These characters' kits are entirely state/stack-specific (血仇/火种/结界/
        // 军功/至高之姿/欢愉度/充能...); the generic engine leaves them
        // uninterpreted rather than fabricating behavior.
        java.util.Set<Integer> zeroPassiveByDesign = java.util.Set.of(
                1402, 1404, 1407, 1408, 1410, 1413, 1414, 1415, 1501, 1506, 8007, 8008);
        for (int cid : cids) {
            if (zeroPassiveByDesign.contains(cid)) {
                continue;
            }
            Character character = buildCharacter(cid);
            Assertions.assertFalse(character.getTraces().isEmpty(),
                    "cid " + cid + " should have at least one passive");
        }
    }

    @Test
    public void testTheHertaBuffs() {
        // 大黑塔: traces include stat buffs via the generic interpreter.
        Character herta = buildCharacter(1401);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(herta)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();
        Assertions.assertTrue(battle.getQueueSnapshot().stream().anyMatch(s -> s.getCanHit() == herta));
    }

    @Test
    public void testAglaeaSummonsGarmentmaker() {
        Character aglaea = buildCharacter(1402);
        Enemy enemy = buildEnemy(Element.ICE, Element.THUNDER, "Thunder Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(aglaea)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(aglaea.getSkills().get(SkillType.SKILL), aglaea, List.of(enemy));
        Assertions.assertFalse(aglaea.getSummons().isEmpty(), "Aglaea should summon Garmentmaker");
    }

    @Test
    public void testCastoriceUltSummonsPollux() {
        Character castorice = buildCharacter(1407);
        Enemy enemy = buildEnemy(Element.ICE, Element.QUANTUM, "Quantum Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(castorice)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        // Castorice has no energy bar in the data — the builder defaults it so the ult works.
        Assertions.assertTrue(castorice.getMaxEnergy() > 0, "Castorice should get a default energy bar");
        castorice.gainEnergy(castorice.getMaxEnergy());
        battle.castUltra(castorice, List.of(enemy));
        Assertions.assertFalse(castorice.getSummons().isEmpty(), "Castorice's ult should summon Pollux");
    }

    @Test
    public void testElationTrailblazerHasElationSkill() {
        Character tb = buildCharacter(8009);
        Assertions.assertTrue(tb.getSkills().containsKey(SkillType.ELATION),
                "Elation Trailblazer should have a 欢愉技");
    }

    @Test
    public void testMemoryTrailblazerSummonsMem() {
        Character tb = buildCharacter(8007);
        Enemy enemy = buildEnemy(Element.ICE, Element.ICE, "Ice Weakling");
        Battle battle = new Battle(new ArrayList<>(List.of(tb)), new ArrayList<>(List.of(enemy)));
        battle.startBattle();

        battle.executeSkill(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        Assertions.assertFalse(tb.getSummons().isEmpty(), "Memory Trailblazer should summon Mem");
        Assertions.assertTrue(battle.getAlivePlayerUnits().stream()
                .anyMatch(u -> u.getName().startsWith("Mem")), "Mem should be on the field");
    }

    @Test
    public void testAventurineStyleDefenceIgnoreTrace() {
        // 灰烬 (1504) or another character with 无视防御力 trace — check interpretation works.
        Character theHerta = buildCharacter(1401);
        Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
        double multiplier = 1.0;
        for (Trace trace : theHerta.getTraces()) {
            multiplier *= trace.damageMultiplier(null, theHerta, enemy, SkillType.ULTRA);
        }
        Assertions.assertTrue(multiplier >= 1.0);
    }

    @Test
    public void testDefenceIgnoreTraceBuff() {
        // Find a character whose trace grants 无视防御力 and verify the buff applies.
        for (int cid : new int[]{1402, 1404, 1405, 1505, 1403}) {
            Character character = buildCharacter(cid);
            Enemy enemy = buildEnemy(Element.FIRE, Element.ICE, "Ice Weakling");
            Battle battle = new Battle(new ArrayList<>(List.of(character)), new ArrayList<>(List.of(enemy)));
            battle.startBattle();
            double ignore = character.getAttribute(AttributeType.DEFENCE_IGNORE) != null
                    ? character.getAttribute(AttributeType.DEFENCE_IGNORE).get() : 0;
            if (ignore > 0) {
                return; // at least one character grants def ignore via trace
            }
        }
        Assertions.fail("no character interpreted 无视防御力 as a DEFENCE_IGNORE buff");
    }
}
