package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 在世界尽头相会吧！ (light cone 22009), 叠影 1: 「队伍中每有 1 名「欢愉」命途的角色，使装备者的暴击伤害提高 6%。星神★阿哈在场时，
 * 装备者的暴击伤害额外提高 12%。」
 *
 * <p><b>What it needed.</b> The first clause is four mutually exclusive windows on {@code allies_with_path:欢愉} - the
 * same shape 1405's 必要的留白 trace uses, because the path count exists as a <b>condition</b> variable only, and a
 * same-attribute modifier replaces instead of adding (so each window states the absolute share, not a delta).
 *
 * <p><b>What it does not do.</b> 「星神★阿哈在场时」 is registered as a gap in the file's {@code _note}: the vocabulary
 * has no "some party member is this cid" condition - {@code any_ally} only asks {@code has_state}, and
 * {@code ally_cid:} / {@code allies_of_path:} are target SELECTORS, not conditions. Nothing is approximated.
 */
public class Cone22009WorldsEndTest {

    private static final int LEVEL = 80;
    private static final int CONE = 22009;
    /** 1001 三月七: not an Elation path, so the count is exactly the number of allies attached below. */
    private static final int WEARER = 1001;
    /** Three real Elation characters, used to raise the count one at a time. */
    private static final int[] ELATION = {1501, 1502, 1503};

    /** 「每有 1 名「欢愉」命途的角色」 - one ally ⇒ +6%, two ⇒ +12%, three ⇒ +18%. */
    @Test
    public void theCritDamageFollowsTheElationAllyCount() {
        Assertions.assertEquals(0.06, critDamageBonus(1), 1e-9, "1 名欢愉 ⇒ +6%");
        Assertions.assertEquals(0.12, critDamageBonus(2), 1e-9, "2 名 ⇒ +12%");
        Assertions.assertEquals(0.18, critDamageBonus(3), 1e-9, "3 名 ⇒ +18%");
    }

    /** The windows are exclusive, so the share is the total, never the sum of two windows. */
    @Test
    public void theWindowsDoNotSum() {
        Assertions.assertEquals(0.12 - 0.06, critDamageBonus(2) - critDamageBonus(1), 1e-9,
                "the second window states 12%, it does not add a second 6%");
    }

    /** A team with nobody on the Elation path grants nothing. */
    @Test
    public void aTeamWithNoElationPathGrantsNothing() {
        Assertions.assertEquals(0.0, critDamageBonus(0), 1e-9, "「队伍中每有 1 名」 needs at least one");
    }

    // ==================================================================

    /**
     * The wearer's CRIT DMG bonus at {@code startBattle}, with {@code count} Elation allies beside her.
     *
     * @param count how many Elation-path allies to attach (0 to 3)
     * @return the share added to her CRIT DMG (the attribute is 0.5 before anything is applied)
     */
    private static double critDamageBonus(int count) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true,
                Weapon.build(CONE, LEVEL, false, 1));
        List<Character> team = new ArrayList<>();
        team.add(wearer);
        for (int i = 0; i < count; i++) {
            team.add(CharacterFactory.create(ELATION[i], LEVEL));
        }
        Battle battle = new Battle(team, new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return wearer.getAttribute(AttributeType.CRIT_ATTACK).get() - BASE_CRIT_DAMAGE;
    }

    /** The engine's own 暴击伤害 base with nothing applied (50%), measured on a bare wearer. */
    private static final double BASE_CRIT_DAMAGE = 0.5;
}
