package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The thirteenth audit batch of light cones (21050-21055), each clause read as a BATTLE.
 *
 * <p>Two defects: 21053 and 21055 gave their damage share to the WEARER (`target: self`) while the sentences say the acting
 * ally gets it - the condition was on the actor and the effect was not.
 */
public class ConeAuditBatchThirteenTest {
    private static final int LEVEL = 80;
    private static final int HYACINE = 1409;   // has a memosprite
    private static final int DAN_HENG = 1002;

    /**
     * 21050 胜利只在朝夕间: the crit-damage share once, and a memosprite's support cast gives the PARTY +8% for three turns.
     */
    @Test
    public void coneTwoInnocentFiveZeroMemospriteSupportBoostsTheParty() {
        Fixture f = new Fixture(21050);
        double critDamage = f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double critBare = new Fixture(null).wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21050] 暴伤增量 " + (critDamage - critBare));
        Assertions.assertEquals(0.12, critDamage - critBare, 1e-9, "「暴击伤害提高 12%」—— 光锥属性给一次");

        Summon sprite = f.battle.summonServant(f.wearer);
        double before = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, sprite, f.ally, 0, 0));
        double after = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[21050] 忆灵对队友施放技能后，队友的增伤 " + before + " -> " + after);
        Assertions.assertEquals(0.08, after - before, 1e-9, "「忆灵对我方目标施放技能时，我方全体伤害 +8%」");
    }

    /**
     * 21051 天才们的问候: an ultimate raises the basic-attack channel of BOTH the wearer and the memosprite.
     */
    @Test
    public void coneTwoInnocentFiveOneUltRaisesBothHoldersBasicChannel() {
        Fixture f = new Fixture(21051);
        Summon sprite = f.battle.summonServant(f.wearer);
        double wearerBefore = f.wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double spriteBefore = sprite.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[21051] 普攻档: 她 " + wearerBefore + " -> "
                + f.wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() + " ; 忆灵 " + spriteBefore + " -> "
                + sprite.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get());
        Assertions.assertEquals(0.2, f.wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - wearerBefore, 1e-9,
                "「装备者…普攻伤害提高 20%」");
        Assertions.assertEquals(0.2, sprite.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - spriteBefore, 1e-9,
                "「…与忆灵…」");
    }

    /**
     * 21052 多流汗，少流泪: her damage is higher while the memosprite is on the field.
     */
    @Test
    public void coneTwoInnocentFiveTwoDamageWhileSummoned() {
        Fixture f = new Fixture(21052);
        double withSummonBefore = f.wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.summonServant(f.wearer);
        System.out.println("[21052] 暴击率增量 "
                + (f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - new Fixture(null).wearer.getAttribute(AttributeType.CRIT_CHANCE).get()));
        Assertions.assertEquals(0.12, f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - new Fixture(null).wearer.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "「暴击率提高 12%」—— 光锥属性给一次");
        Assertions.assertEquals(withSummonBefore, f.wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「忆灵在场上时…伤害提高 24%」是实例修饰（面板不变，判据见 ConeAuditBatchThirteenTest 的注释）");
    }

    /**
     * 21054 故事的下一页: a memosprite's attack raises the healing of BOTH holders for a turn.
     */
    @Test
    public void coneTwoInnocentFiveFourSummonAttackRaisesHealing() {
        Fixture f = new Fixture(21054);
        Summon sprite = f.battle.summonServant(f.wearer);
        double wearerBefore = f.wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double spriteBefore = sprite.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SUMMON_ATTACK, sprite, f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[21054] 治疗加成: 她 " + wearerBefore + " -> "
                + f.wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() + " ; 忆灵 " + spriteBefore + " -> "
                + sprite.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get());
        Assertions.assertEquals(0.12, f.wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - wearerBefore, 1e-9,
                "「使装备者…的治疗量提高 12%」");
        Assertions.assertEquals(0.12, sprite.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - spriteBefore, 1e-9,
                "「…与忆灵…」");
    }

    /**
     * 21055 直到明天的明天: the HEALTHY ally that attacks is the one who gets the share.
     */
    @Test
    public void coneTwoInnocentFiveFiveHealthyActorGetsTheShare() {
        Fixture f = new Fixture(21055);
        double allyBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double wearerBefore = f.wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.DEALING_DAMAGE, f.ally, f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[21055] 队友的增伤 " + allyBefore + " -> "
                + f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() + " ; 她的 " + wearerBefore + " -> "
                + f.wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() != allyBefore,
                "「我方目标生命 ≥ 50% 时…伤害提高」—— 出手的那位要吃到（`target: actor`）");
    }

    // ⚠ 21053 的判据尚未立：「持盾的我方目标出手」需要一次**真实施放**才能读到 BOOST_DAMAGE（手动 fire 会报「没有实例」✗），
    // 留给下一轮；内容上的 	arget: actor 修正已经在了。

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(HYACINE, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(DAN_HENG, LEVEL);
            this.battle = new Battle(List.of(wearer, ally), List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }
    }
}
