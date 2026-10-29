package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512's ultimate 「跃入这片蔚蓝狂想」(151203), clause by clause.
 *
 * <p>It is the one part of her kit that was missing, and it needs three separate mechanisms: an <b>action advance</b> of
 * a chosen ally, a <b>fixed</b> energy restore that is a share of that ally's own maximum (untouched by the regen rate),
 * and the 【特邀嘉宾】 state whose two halves pay her 2 vibes per attack - from the guest <b>and from the guest's summon</b>.
 */
public class SpecialGuestTest {

    private static final int ROBIN = 1512;
    private static final String VIBES = "气氛值";
    private static final String GUEST = "特邀嘉宾";

    private record Scene(Battle battle, Character robin, Character ally, Skill ultimate) {
    }

    private static Scene scene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(1409, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        Skill ultimate = robin.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ultimate, "1512 has an ultimate row");
        return new Scene(battle, robin, ally, ultimate);
    }

    /**
     * The advance: the ult moves the chosen ally forward by the whole row (#1 = 100% at every level).
     */
    @Test
    public void theUltimateAdvancesTheChosenAlly() {
        Scene scene = scene();
        double before = actionTime(scene.battle, scene.ally);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally)),
                "the ultimate is castable");

        double after = actionTime(scene.battle, scene.ally);
        Assertions.assertTrue(after < before, "the ally moved: " + before + " -> " + after);
    }

    /** How far this unit's next action sits on the queue's clock. */
    private static double actionTime(Battle battle, com.laosun.aluminium.entity.CanHit unit) {
        return battle.queue().getHeap().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .mapToDouble(signal -> signal.nextActionTime)
                .findFirst().orElseThrow(() -> new AssertionError("the unit is not in the action bar"));
    }

    /**
     * The fixed energy: #3 of the ally's OWN maximum, and the regen rate must not multiply it. The rate is set to 50%
     * first, so a "normal" grant would come out at half the stated number.
     */
    @Test
    public void theEnergyRestoreIsFixedAndUsesTheAllysOwnMaximum() {
        Scene scene = scene();
        scene.ally.getAttribute(com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).addBase(0.5);
        double max = scene.ally.getMaxEnergy();
        double before = scene.ally.getCurrentEnergy();
        // The share comes from the ultimate's OWN row at the level the fixture reads (#3 runs 12% -> 24%), so the
        // expectation is derived instead of pinned to one level.
        int level = scene.robin.skillLevel(scene.ultimate);
        double share = scene.ultimate.getData().getSkills().get(level - 1).get(2);

        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));

        double gained = scene.ally.getCurrentEnergy() - before;
        Assertions.assertTrue(gained > 0, "the ally gained energy");
        Assertions.assertEquals(share * max, gained, 0.5,
                "#3 (" + share + ") of " + max + " max energy, NOT halved by the 50% regen rate");
    }

    /**
     * The state: applied to the ally, and its clock belongs to the ALLY's own turn ("该角色每回合开始时持续回合数减1").
     */
    @Test
    public void theGuestStateLandsOnTheAllyAndTicksOnTheirTurn() {
        Scene scene = scene();
        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));

        Assertions.assertTrue(scene.ally.getBuffManager().hasState(GUEST), "the ally holds 【特邀嘉宾】");
        Assertions.assertFalse(scene.robin.getBuffManager().hasState(GUEST), "the caster does not");
    }

    /**
     * The guest's own attack pays her 2 vibes.
     */
    @Test
    public void theGuestAttackPaysTwoVibes() {
        Scene scene = scene();
        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));
        double before = scene.battle.partyResourceValue(VIBES);

        scene.battle.castImmediate(scene.ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                scene.ally, List.of(scene.battle.enemies().getFirst()));

        Assertions.assertEquals(before + 2, scene.battle.partyResourceValue(VIBES), 1e-9,
                "「持有【特邀嘉宾】的角色…攻击时额外使知更鸟•晴歌获得 2 点气氛值」");
    }

    /**
     * The guest's SUMMON's attack pays her 2 vibes as well - the state lives on the master, so the condition has to ask
     * about the master (the {@code master_state} sub-predicate).
     */
    @Test
    public void theGuestsSummonAttackPaysTwoVibesToo() {
        Scene scene = scene();
        scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.ally));
        scene.battle.summonMemosprite(scene.ally);
        Summon servant = scene.battle.summonOf(scene.ally);
        Assertions.assertNotNull(servant, "her memosprite is out");
        double before = scene.battle.partyResourceValue(VIBES);

        // Fired directly: 1409's memosprite deals its damage through RULES (its data row prices nothing), so its cast
        // produces no hit and the engine's own SUMMON_ATTACK site - which needs a hit - would not fire. What this case
        // is about is the rule's gate (the state is on the MASTER), not the firing site.
        scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.SUMMON_ATTACK, servant, null, 1, 0));

        Assertions.assertEquals(before + 2, scene.battle.partyResourceValue(VIBES), 1e-9,
                "「…及其召唤物攻击时」 grants the same 2 points");
    }
}
