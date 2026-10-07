"""What does the laughter multiplier actually read at the start of a battle?

This decides whether "笑点 is counted into 好活当赏" is ALREADY happening (in some form) or missing.

The Elation damage formula in the engine is documented as
`base x (1 + 欢愉度) x (1 + 增笑) x (1 + 笑点 x 5 / (笑点 + 240))`. If 笑点 were zero at the start - and nothing counts
it in - then that last factor would be 1.0 and a payout at t=0 should read as a clean `base x (1+度) x (1+增笑)`.
Measured earlier, the Elation skill's payout at the four-ally tier was 51.42 against a base of 171.74, which is NOT
that shape, so something else is in play and this prints it.
"""
import pathlib

OUT = pathlib.Path("tools/_laugh_probe.txt")
TEST = pathlib.Path("src/test/java/com/laosun/aluminium/test/engine/LaughMultiplierProbeTest.java")

BODY = '''package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** PROBE (not a judge): the laughter multiplier and every factor that feeds it. */
public class LaughMultiplierProbeTest {
    @Test
    public void printTheFactors() {
        Character pearl = CharacterFactory.create(1503, 80);
        Character elationA = CharacterFactory.create(1502, 80);
        Character elationB = CharacterFactory.create(1505, 80);
        Character hunter = CharacterFactory.create(1204, 80);
        Battle battle = new Battle(List.of(pearl, elationA, elationB, hunter),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        System.out.println("[laugh] party punchline holder value = " + battle.partyResource("笑点").value()
                + " ; partyResource non-null = " + (battle.partyResource("笑点") != null));
        System.out.println("[laugh] her own 好活当赏 = " + her.getResources().value("好活当赏")
                + " ; has = " + her.getResources().has("好活当赏"));
        System.out.println("[laugh] elationLaughMultiplier() = " + battle.elationLaughMultiplier());
        System.out.println("[laugh] her ELATION_DAMAGE_BOOST = "
                + her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get()
                + " ; AMP = " + her.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get());
        var elationSkill = her.getSkills().get(SkillType.ELATION_SKILL);
        System.out.println("[laugh] ELATION_SKILL level = " + her.skillLevel(elationSkill)
                + " ; base table row = " + Constant.ELATION_BASIC_LEVEL_DAMAGE.get(her.skillLevel(elationSkill)));
        // Arm the team, then measure the payout on ONE ally's basic attack.
        battle.fireTriggers(TriggerEvent.CAST_SETUP, her, her, 0, 0, SkillCategory.ELATION_DAMAGE);
        System.out.println("[laugh] after arming: multiplier = " + battle.elationLaughMultiplier()
                + " ; punchline = " + battle.partyResource("笑点").value());
        Character attacker = battle.characters.get(battle.characters.size() - 1);
        double before = battle.enemies.getFirst().getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker,
                List.of(battle.enemies.getFirst()));
        double dealt = before - battle.enemies.getFirst().getCurrentHp();
        System.out.println("[laugh] the armed basic attack dealt " + dealt);
        // The factor itself, across the counts a battle can reach - so "how much does folding 笑点 in matter" is a
        // number rather than a guess.
        for (int points : new int[] {0, 30, 60, 120, 240, 480, 960}) {
            battle.partyResource("笑点").setValue(0);
            battle.partyResource("笑点").gain(points);
            System.out.println("[laugh] with " + points + " punchline the factor is "
                    + battle.elationLaughMultiplier());
        }
    }
}
'''


def main():
    TEST.parent.mkdir(parents=True, exist_ok=True)
    TEST.write_text(BODY, encoding="utf-8")
    print("written", TEST)


if __name__ == "__main__":
    main()
