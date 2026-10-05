package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The dead dragon (2026-10-02): its panel states speed 165 flat and health = 100% of the [新蕊] cap, its ATTACK slot
 * carries the SUMMONER's Max HP, and 忆灵技能 1 deals 40% of that.
 *
 * <p>The first reader of the resource-based panel AND of the `attr:` spelling (a share of another of the master's
 * attributes, which the plain branch cannot express because it reads the SAME attribute the entry names).
 */
public class DragonPanelTest {
    /** The panel is a share of a battle-level RESOURCE, and the flat speed rides beside it. */
    @Test
    public void theDragonPanelFollowsNewbud() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec, "the dragon's spec must load from resources/memosprites/1407.json");
        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);

        Summon small = SummonFactory.memosprite(master, spec, name -> 1000);
        Assertions.assertEquals(1000, small.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "100% of a 1000-point 【新蕊】");

        Summon large = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(34000, large.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "and 100% of a 34,000-point one -- the panel follows the resource");
        Assertions.assertEquals(165, large.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the document states 165 speed, flat");
    }

    /** 忆灵技能 1 as the document states it, and the panel slot it scales off. */
    @Test
    public void theDragonSkillIsWhatTheDocumentSays() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec.attack(), "the skill must be stated (忆灵技能 1)");
        Assertions.assertEquals("Quantum", spec.attack().element(), "document: 量子属性伤害");
        Assertions.assertEquals("ATTACK", spec.attack().base(), "scales off this memosprite's ATTACK slot");
        Assertions.assertEquals(0.4, spec.attack().percent(), 1e-9, "40.00%, the level the prose quotes");
        Assertions.assertEquals("AoEAttack", spec.attack().shape(), "document: 全体攻击");
        Assertions.assertEquals(30, spec.attack().stance(), 1e-9, "document: 全体 30");

        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);
        Summon dragon = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(master.getAttribute(AttributeType.HEALTH).get(),
                dragon.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "and that slot carries the summoner's Max HP, so the hit is 40% of it");
    }

}
