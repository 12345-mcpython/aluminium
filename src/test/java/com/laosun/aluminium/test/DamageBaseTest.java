package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The damage base an HP- or DEF-scaled skill must use (2026-09-29, round 14).
 *
 * <p>Asserted on the MAPPING, not on a damage ratio: `loss / share` is the base times the settlement zones, so comparing it against a raw attribute
 * measures the zones rather than the base (the first version of this test did exactly that and failed for both characters). The behavioural claim is
 * proven by the mutation that forces ATTACK: it makes the damage of every HP- and DEF-scaled skill wrong, which no test can hide because the
 * mapping is asserted here directly.
 */
public class DamageBaseTest {
    private static final int LYNX = 1110;
    private static final int AVENTURINE = 1304;
    private static final int DANHENG = 1002;
    private static final int LEVEL = 80;

    /** Note: "等同于琳希#1[i]%生命上限的伤害" -> HEALTH. */
    @Test
    public void aMaxHpSentenceNamesMaxHp() {
        Character lynx = CharacterFactory.create(LYNX, LEVEL);
        Assertions.assertEquals(AttributeType.HEALTH, lynx.getSkills().get(SkillType.COMMON).getData().damageBaseAttribute(),
                "「等同于…生命上限的伤害」");
    }

    /** Note: "等同于砂金100%防御力的伤害" -> DEFENCE. */
    @Test
    public void aDefenceSentenceNamesDefence() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Assertions.assertEquals(AttributeType.DEFENCE, aventurine.getSkills().get(SkillType.COMMON).getData().damageBaseAttribute(),
                "「等同于砂金100%防御力的伤害」");
    }

    /** Note: The ordinary case stays ATK: "等同于丹恒100%攻击力". */
    @Test
    public void anAttackSentenceStaysAttack() {
        Character danheng = CharacterFactory.create(DANHENG, LEVEL);
        Assertions.assertEquals(AttributeType.ATTACK, danheng.getSkills().get(SkillType.COMMON).getData().damageBaseAttribute(),
                "「等同于丹恒100%攻击力」");
    }
}
