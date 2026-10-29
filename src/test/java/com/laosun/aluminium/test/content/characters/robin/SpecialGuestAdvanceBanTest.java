package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512's ultimate, the clause 「…且<b>无法使其他友方目标获得行动提前效果</b>，持续 2 回合」.
 *
 * <p><b>What kind of rule this is.</b> Every other number in the trigger language modifies a value; this one <b>bans an
 * action</b>. It is stated by the STATE the ultimate grants ({@code "blocks": "advance_to_others"} on the
 * {@code APPLY_BUFF}) and read by the op that would perform the banned action ({@code ADVANCE}): the ban belongs to the
 * unit <b>giving</b> the advance (it is that character's own ability that is locked), and it only stops advances aimed at
 * <b>others</b> - 「其他友方目标」 - so the holder may still be advanced itself.
 *
 * <p>The probe is her own ultimate, because it is a shipped rule that advances an ally.
 */
public class SpecialGuestAdvanceBanTest {

    private static final int ROBIN = 1512;
    private static final int CYRENE = 1415;
    private static final String GUEST = "特邀嘉宾";

    private record Scene(Battle battle, Character robin, Character cyrene, Skill ultimate) {
    }

    private static Scene scene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(robin, cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return new Scene(battle, robin, cyrene, robin.getSkills().get(SkillType.ULTRA));
    }

    /**
     * 【特邀嘉宾】 with the ban it carries in the data ({@code "blocks": "advance_to_others"}). The rule's own
     * {@code APPLY_BUFF} sets this; the test states it directly because what is on trial here is the READ, not the apply.
     */
    private static void giveGuestState(Character unit) {
        com.laosun.aluminium.buff.StateBuff state = new com.laosun.aluminium.buff.StateBuff(GUEST, 2);
        state.setBlocks(java.util.Set.of("advance_to_others"));
        unit.getBuffManager().addBuff(state);
    }

    private static double actionTime(Battle battle, CanHit unit) {
        return battle.queue().getHeap().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .mapToDouble(signal -> signal.nextActionTime)
                .findFirst().orElseThrow(() -> new AssertionError("the unit is not in the action bar"));
    }

    /**
     * With the state on the giver: the advance aimed at another ally is suppressed, while the rest of the ultimate still
     * happens (the state lands on the ally) - a ban on one action, not a cancelled cast.
     */
    @Test
    public void theGuestCannotAdvanceAnotherAlly() {
        Scene scene = scene();
        giveGuestState(scene.robin);
        double before = actionTime(scene.battle, scene.cyrene);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.cyrene)),
                "the cast itself is still allowed");

        Assertions.assertEquals(before, actionTime(scene.battle, scene.cyrene), 1e-9,
                "「无法使其他友方目标获得行动提前效果」: the ally did not move");
        Assertions.assertTrue(scene.cyrene.getBuffManager().hasState(GUEST),
                "and the rest of the ultimate still resolved (the state landed)");
    }

    /**
     * Without the state the very same cast does move the ally - the ban must not be a general "advances do not work".
     */
    @Test
    public void withoutTheStateTheAdvanceLands() {
        Scene scene = scene();
        double before = actionTime(scene.battle, scene.cyrene);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.cyrene)));

        Assertions.assertTrue(actionTime(scene.battle, scene.cyrene) < before,
                "an ungated advance still moves the ally: " + before + " -> "
                        + actionTime(scene.battle, scene.cyrene));
    }

    /**
     * 「<b>其他</b>友方目标」: an advance the holder gives ITSELF is not an advance of another, so it still lands.
     */
    @Test
    public void theGuestCanStillBeAdvanced() {
        Scene scene = scene();
        giveGuestState(scene.robin);
        double before = actionTime(scene.battle, scene.robin);

        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.robin, List.of(scene.robin)),
                "casting on herself is allowed by the engine (the document says another ally; the ban only covers others)");

        Assertions.assertTrue(actionTime(scene.battle, scene.robin) < before,
                "the holder's own advance is not banned: " + before + " -> " + actionTime(scene.battle, scene.robin));
    }
}
