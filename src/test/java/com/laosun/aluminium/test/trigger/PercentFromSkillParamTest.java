package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `percent_from_skill_param: "<SKILLTYPE>:<index>"`.
 *
 * <p>Reader: 1403 Tribbie's ultimate, whose zone rider deals damage equal to "#3% of his Max HP" on somebody else's attack. #3 lives in HIS ultimate and runs
 * with level (0.06 -> 0.15), so neither `percent_from_cast_param` (the skill that produced the event) nor a literal can say it.
 *
 * <p>Two readings: the share is multiplied by Max HP as the sentence says, and the INDEX is load-bearing -- the neighbouring member of the same row is a
 * different number, so a reader that grabbed the wrong member cannot pass.
 *
 * <p>The first attempt at this crashed with an NPE on a null `amount`, because `modifyAttr` asked "is a share stated?" with a condition that did not know this
 * spelling -- the very line its own comment warns about. This judge is what caught it.
 */
public class PercentFromSkillParamTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void theShareComesFromHisOwnUltimateAtItsOwnLevel() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(tribbie, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();

        Skill ultra = tribbie.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ultra, "precondition: 1403 has an ULTRA skill");
        var row = ultra.getData().getSkills().get(tribbie.skillLevel(ultra) - 1);
        double maxHp = tribbie.getAttribute(AttributeType.HEALTH).get();
        double expected = row.get(2) * maxHp;
        double neighbour = row.get(1) * maxHp;

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "self_attr:HEALTH");
        TriggerSpecs.set(effect, "percentFromSkillParam", "ULTRA:2");
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        tribbie.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), effect))));

        double before = tribbie.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START);
        battle.processRequests();
        double gained = tribbie.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[skill_share] row = " + row + " ; maxHp = " + maxHp + " ; expected " + expected
                + " (neighbour " + neighbour + ") ; gained " + gained);

        Assertions.assertEquals(expected, gained, Math.abs(expected) * 1e-6,
                "「等同于缇宝 #3% 生命上限」 (equal to #3% of Tribbie's Max HP)-- #3 of HIS ultimate, times Max HP");
        Assertions.assertNotEquals(neighbour, gained, Math.abs(expected) * 1e-6, "and the index is load-bearing");
    }
}
