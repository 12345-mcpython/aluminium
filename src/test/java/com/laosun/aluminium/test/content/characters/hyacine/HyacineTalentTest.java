package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * 1409 风堇's talent: 「风堇或小伊卡提供治疗时，小伊卡造成的伤害提高 80%，持续 2 回合，最多可叠加 3 层」 (140904,
 * params [0.5, 1, 0.8, 2, 3]).
 *
 * <h2>The two traps this test documents</h2>
 * <ol>
 *   <li>{@code CanHit.heal(-x)} does <b>nothing</b> (it ignores a non-positive amount), so "wound the ally first" needs
 *       {@code consumeHp} - a first attempt healed a full-HP ally, produced no {@code HEALED}, and read 0 boost;</li>
 *   <li>at full HP a heal restores 0 and raises no event, so the ally has to be genuinely wounded before each cast.</li>
 * </ol>
 */
public class HyacineTalentTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;

    @Test
    public void eachHealRaisesTheMemospriteDamageByEightyUpToThreeLayers() {
        double none = ikachiBoost(0);
        double one = ikachiBoost(1);
        double two = ikachiBoost(2);
        double three = ikachiBoost(3);
        double four = ikachiBoost(4);
        Assertions.assertEquals(0.8, one - none, 1e-6, "one heal: +80%");
        Assertions.assertEquals(1.6, two - none, 1e-6, "two heals: +160%");
        Assertions.assertEquals(2.4, three - none, 1e-6, "three heals: +240%");
        Assertions.assertEquals(2.4, four - none, 1e-6, "「最多可叠加 3 层」: the fourth heal adds nothing");
    }

    private static double ikachiBoost(int heals) {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is on the field");
        for (int i = 0; i < heals; i++) {
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ika.skillAt(1), ika, List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: 小伊卡's hit settled");
        // ⚠ 2026-10-18: 忆灵技1 的「按累计治疗出伤害」现在是 CAST_SETUP 上的一条**规则**，它在数据那一发**之前**结算
        // ⇒ 第一个实例是那条规则的（它的攻击者是**风堇**，身上没有忆灵的天赋增伤），数据那一发排在它后面。这里读
        // **最后一个**实例 = 数据那一发，也就是这条用例原本要测的对象。规则实例的归属问题登记在 AGENT.md §3.7q。
        return settled.get(settled.size() - 1).breakdown().get("BoostArea");
    }
}
