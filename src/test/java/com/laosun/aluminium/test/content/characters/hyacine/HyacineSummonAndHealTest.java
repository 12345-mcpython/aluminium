package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DebuffClass;
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
 * 1409 风堇's memosprite 小伊卡 and her skill's healing, both from the same two documents:
 *
 * <ul>
 *   <li>talent (140904): 「忆灵小伊卡初始拥有等同于风堇 <b>50%</b> 生命上限的生命上限」 - and {@code AvatarServantConfig}
 *       states {@code HPInherit "#1"} with param #1 = 0.5, so the two sources agree;</li>
 *   <li>skill (140902): 「为我方全体目标回复等同于风堇 <b>8%</b> 生命上限 + <b>160</b> 的生命值」 (params [0.08, 160,
 *       0.1, 200]).</li>
 * </ul>
 *
 * <p>Why the heal is worth measuring: the amount is a share of the <b>summoner's</b> Max HP, not of the healed ally's -
 * reading the wrong side gives a plausible-looking number of the wrong size, and this repository has been bitten by that
 * shape before (a memosprite's damage base read off the summoner doubled its hit).
 */
public class HyacineSummonAndHealTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;

    @Test
    public void ikachisMaxHpIsHalfOfHers() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        double hers = hyacine.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(hers * 0.5, ika.getMaxHp(), Math.max(1e-6, hers * 1e-9),
                "「等同于风堇 50% 生命上限的生命上限」 (hers=" + hers + ", its=" + ika.getMaxHp() + ")");
    }

    @Test
    public void theSkillHealsEightPercentOfHerMaxHpPlus160() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        double before = ally.getCurrentHp();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double healed = ally.getCurrentHp() - before;
        double expected = hyacine.getAttribute(AttributeType.HEALTH).get() * 0.08 + 160.0;
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「等同于风堇 8% 生命上限 + 160」 (healed=" + healed + ", expected=" + expected + ")");
    }

    @Test
    public void ikachiIsImmuneToBothDebuffClasses() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        Assertions.assertEquals(1.0, ika.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "「免疫负面效果」 -- control class (percent 1.0 IS immunity in this engine)");
        Assertions.assertEquals(1.0, ika.getBuffManager().debuffResistOf(DebuffClass.DOT), 1e-9,
                "「免疫负面效果」 -- damage-over-time class");
    }
}