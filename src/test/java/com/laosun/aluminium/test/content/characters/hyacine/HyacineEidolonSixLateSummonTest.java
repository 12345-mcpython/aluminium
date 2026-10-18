package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's Rank 6 aura, the half that was registered as missing: 「小伊卡<b>在场时</b>，我方全体目标的全属性抗性穿透
 * 提高 20%」.
 *
 * <h2>Why "在场时" is not "on arrival"</h2>
 * The aura used to be granted only when <b>小伊卡 herself</b> entered the field ({@code SUMMONED} + {@code actor ==
 * summon}), to whoever was standing at that moment. So a memosprite summoned <b>later</b> - 德谬歌, 晴空乐手, or
 * another team's 长夜 - never got it, even though 小伊卡 was on the field the whole time. The clause reads as a state
 * of the field, so every arrival while she is out must grant it.
 *
 * <p>The assertion is on the <b>late</b> arrival's own attribute: it is the only reading that fails when the aura is
 * tied to her arrival, and the control (小伊卡 already has it) pins that the fixture really is in the aura's state.
 */
public class HyacineEidolonSixLateSummonTest {

    private static final int HYACINE = 1409;
    private static final int CYRENE = 1415;
    private static final double EPS = 1e-9;

    @Test
    public void aMemospriteSummonedAfterIkaStillGetsTheAura() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(hyacine, cyrene), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out, so the aura is up");
        Assertions.assertEquals(0.2, ika.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "precondition: the aura reached the units standing when she arrived");

        // The LATE arrival: another character's memosprite, summoned after 小伊卡.
        battle.summonMemosprite(cyrene);
        battle.processRequests();
        Summon demiurge = (Summon) battle.memospriteOf(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 arrived after 小伊卡");

        Assertions.assertEquals(0.2, demiurge.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「小伊卡在场时」 is a state of the field: a memosprite that arrives later must get the 20% too");
        Assertions.assertEquals(0.2, hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and it is still not stacked on the units that already had it (replace, not add)");
    }

    @Test
    public void withoutRankSixNothingIsGranted() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(hyacine, cyrene), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        battle.summonMemosprite(cyrene);
        battle.processRequests();

        Summon demiurge = (Summon) battle.memospriteOf(cyrene);
        Assertions.assertEquals(0.0, demiurge.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "the aura is Rank 6's; at E0 a late arrival gets nothing");
    }
}
