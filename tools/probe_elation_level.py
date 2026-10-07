"""Which skill level does `elation_base` read, at each Eidolon rank?

The E4 measurement gave ratios that are not 2.0 (1.976 at the four-ally tier, 1.235 at the one-ally tier, and a change
at E3 where no rule of mine is gated), and the shape of that points at the BASE being different between the two
readings rather than at the doubling. `TriggerInterpreter.elationBase` reads the owner's ELATION_SKILL level, so this
prints that level (and every slot's level and table size) at ranks 0/3/4 - the same picture the level-bound probe drew
for the basic.
"""
import pathlib

JAVA = pathlib.Path("tools/_elation_level_probe.java.txt")
OUT = pathlib.Path("tools/_elation_level_probe.txt")
TEST = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/PearlElationLevelProbeTest.java")

BODY = '''package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** PROBE (not a judge): the effective level of every slot at several Eidolon ranks. */
public class PearlElationLevelProbeTest {
    @Test
    public void printLevels() {
        for (int rank : new int[] {0, 1, 2, 3, 4, 5, 6}) {
            Character pearl = CharacterFactory.create(1503, 80, false, null, null, rank);
            Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1502, 80), CharacterFactory.create(1204, 80)),
                    List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
            battle.startBattle();
            battle.processRequests();
            Character her = battle.characters.getFirst();
            StringBuilder line = new StringBuilder("[elation-level] E" + rank + ":");
            for (SkillType slot : new SkillType[] {SkillType.COMMON, SkillType.SKILL, SkillType.ULTRA,
                    SkillType.TALENT, SkillType.ELATION_SKILL, SkillType.ELATION_EXTRA}) {
                var skill = her.getSkills().get(slot);
                if (skill == null || skill.getData() == null) {
                    line.append("  ").append(slot).append("=absent");
                    continue;
                }
                int rows = skill.getData().getSkills() == null ? -1 : skill.getData().getSkills().size();
                line.append("  ").append(slot).append("=").append(her.skillLevel(skill)).append("/").append(rows);
            }
            System.out.println(line);
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
