package com.laosun.aluminium.test.content.kafka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1005 卡芙卡's talent: 「当卡芙卡的队友对敌方目标施放普攻后，卡芙卡立即发动追加攻击…并有 100% 的基础概率使受到攻击的敌方目标
 * 陷入与终结技相同的触电状态，持续 2 回合，该效果每回合只能触发 1 次」.
 *
 * <p>The rule shipped, but nothing judged it (the status row still lists it as unwritten). This test is the missing judge,
 * and it pins the three words the rule is built on - <b>teammate</b> (not herself), <b>basic attack</b> (not a skill), and
 * <b>once per turn</b>. Kafka's follow-ups are counted with a one-line marker rule on {@code FOLLOW_UP}, because a
 * follow-up is not visible in any attribute.
 */
public class KafkaTalentFollowUpTest {

    private static final int LEVEL = 80;
    private static final int KAFKA = 1005;
    private static final int ALLY = 1002;
    private static final String SHOCK = "触电";
    private static final String COUNTER = "追打次数";

    /**
     * An ally's basic attack makes Kafka follow up and shocks the target; a second one in the same turn does not.
     */
    @Test
    public void anAllyBasicTriggersOneFollowUpAndShock() {
        Fixture f = new Fixture();
        f.allyBasic();

        Assertions.assertEquals(1, f.followUps(), "one follow-up");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState(SHOCK), "「陷入与终结技相同的触电状态」");

        f.allyBasic();
        Assertions.assertEquals(1, f.followUps(), "「该效果每回合只能触发 1 次」");
    }

    /**
     * A skill (not a basic) and Kafka's own basic attack do not trigger it.
     */
    @Test
    public void aSkillOrKafkasOwnAttackDoesNot() {
        Fixture skill = new Fixture();
        skill.allySkill();
        Assertions.assertEquals(0, skill.followUps(), "「队友…施放普攻后」 - a skill is not a basic attack");

        Fixture own = new Fixture();
        own.kafkaBasic();
        Assertions.assertEquals(0, own.followUps(), "her own attack is not a teammate's");
    }

    /**
     * The per-turn limit is hers: after her turn ends, the next ally basic triggers again.
     */
    @Test
    public void theLimitIsPerTurn() {
        Fixture f = new Fixture();
        f.allyBasic();
        Assertions.assertEquals(1, f.followUps());
        f.kafkaTurn();
        f.allyBasic();
        Assertions.assertEquals(2, f.followUps(), "a new turn: the follow-up is available again");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character kafka;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            kafka = CharacterFactory.create(KAFKA, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            // A marker on FOLLOW_UP so the number of her follow-ups is countable.
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "ADD_STACK");
            TriggerSpecs.set(effect, "buff", COUNTER);
            TriggerSpecs.set(effect, "amount", 1.0);
            TriggerSpecs.set(effect, "maxStacks", 10);
            TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
            TriggerSpecs.set(effect, "target", "self");
            TriggerSpec marker = TriggerSpecs.rule("FOLLOW_UP", List.of("actor == self"), effect);
            TriggerSpecs.set(marker, "id", "witness_kafka_followup_counter");
            kafka.setTriggerTable(kafka.getTriggerTable().plus(new TriggerTable(KAFKA, List.of(marker))));

            battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void allyBasic() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        }

        private void allySkill() {
            battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(enemy));
        }

        private void kafkaBasic() {
            battle.castImmediate(kafka.getSkills().get(SkillType.COMMON), kafka, List.of(enemy));
        }

        /** Kafka's own turn boundary (the per-turn limiter counts her turns). */
        private void kafkaTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_END, kafka, null, 0, 0));
            kafka.tickTriggerCooldowns();   // the per-turn window opens on HER turn start, which the routine ticks
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, kafka, kafka, 0, 0));
        }

        private int followUps() {
            return kafka.getBuffManager().stacksOf(COUNTER);
        }
    }
}

