package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's 忆灵技1 【乌云乌云快走开！】 (data slot 1 of ServantID 11409):
 * 「对敌方全体造成等同于<b>本场战斗风堇与小伊卡累计治疗数值</b> #1% 的风属性伤害并清空累计治疗数值的 50%」
 * (params {@code [0.1, 0.5] .. [0.28, 0.5]}; Rank 6 turns the 50% into 12%).
 *
 * <p>What this measures is the half that IS writable today: the damage is a share of the <b>tally</b>, read at the
 * memosprite's own level row ({@code percent_from_cast_param: 0} → 0.28 at Lv10), not a share of its ATK -- its ATK is
 * 0, so an ATK-shaped instance would deal nothing at all.
 *
 * <p>⚠ Two things this test does NOT cover, both registered in AGENT.md §3.7q:
 * <ul>
 *   <li>「清空累计治疗数值的 50%」（Rank 6 → 12%）-- {@code REMOVE_RESOURCE} requires an {@code amount} spelling and
 *       refuses a {@code scale} + {@code percent} share, so "half of the counter" has no wording yet;</li>
 *   <li>the memosprite's own data row still settles a {@code %ATK} instance, and with ATK = 0 that leaves the engine's
 *       <b>1-point floor</b>. The expected value below names that point instead of hiding it.</li>
 * </ul>
 */
public class HyacineRaincloudsTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final String TALLY = "小伊卡累计治疗";
    /** 「#1」 at the memosprite skill's level 10 row. */
    private static final double SHARE_AT_LV10 = 0.28;
    /** The data-driven instance's floor with ATK = 0 (measured). */
    private static final double DATA_FLOOR = 1.0;

    @Test
    public void theDamageIsTheShareOfTheTallyPlusTheDataFloor() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();

        double tally = hyacine.getResources().value(TALLY);
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        var enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        SkillExecutor.execute(battle, ika.skillAt(1), ika, List.of(enemy));
        battle.processRequests();
        double dealt = before - enemy.getCurrentHp();

        Assertions.assertEquals(tally * SHARE_AT_LV10 + DATA_FLOOR, dealt, Math.max(1e-6, tally * 1e-9),
                "「造成等同于累计治疗数值 #1% 的风属性伤害」: it is the TALLY that is multiplied, not the memosprite's "
                        + "ATK (which is 0). tally=" + tally + ", dealt=" + dealt);
    }
}
