"""Measure Pearl's Elation panel with and without the 洞察万物 rule, to find out what actually contributes.

The readings did not add up (a clean +0.32 was expected; the panel moved by +0.037), and a number with odd decimals
(0.54525) means several ratio modifiers are MULTIPLYING rather than adding. Rather than keep reasoning about it,
this removes the one rule under test and prints the panel both ways, plus every rule in her file that writes
ELATION_DAMAGE_BOOST - so the contribution is read, not derived.

It patches the content file, runs one judge that prints the panel, and restores the file from git.
"""
import json
import pathlib
import shutil
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = pathlib.Path("src/main/resources/characters/1503.json.probe")
PROBE = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/PearlElationPanelProbe.java")
INSIGHT = "trace_insight_raises_elation_at_2400_defence"

PROBE_SOURCE = '''package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe (not committed): prints Pearl's panel at a few Defence values. */
public class PearlElationPanelProbe {
    @Test
    public void printPanel() {
        for (double defence : new double[] {1200, 2300, 2400, 2500, 4000}) {
            Character pearl = CharacterFactory.create(1503, 80, false, null, null, 0);
            pearl.setAttribute(AttributeType.DEFENCE, new DoubleValue(defence));
            Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)),
                    List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
            battle.startBattle();
            battle.processRequests();
            Character her = battle.characters.getFirst();
            System.out.println("[panel] DEF=" + defence
                    + " elation=" + her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get()
                    + " outgoingHeal=" + her.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get()
                    + " effectRES=" + her.getAttribute(AttributeType.EFFECT_RESISTANCE).get());
        }
    }
}
'''


def restore():
    if BACKUP.exists():
        shutil.copyfile(BACKUP, TARGET)
        print("restored the content file from the probe backup")
    if PROBE.exists():
        PROBE.unlink()
    subprocess.run(["git", "checkout", "--", str(PROBE)], capture_output=True, text=True)


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    writers = []
    for rule in data["rules"]:
        for effect in rule.get("do", []):
            if effect.get("attribute") == "ELATION_DAMAGE_BOOST":
                writers.append((rule["id"], rule["on"], effect))
    print("== rules writing ELATION_DAMAGE_BOOST ==")
    for rule_id, event, effect in writers:
        print(f"  {rule_id} [{event}] {json.dumps(effect, ensure_ascii=False)}")
    print("  (in file order)")

    data["rules"] = [r for r in data["rules"] if r["id"] != INSIGHT]
    shutil.copyfile(TARGET, BACKUP)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    PROBE.write_text(PROBE_SOURCE, encoding="utf-8")

    print("\n== panel WITHOUT the insight rule ==")
    result = subprocess.run([".\\gradlew.bat", "test", "--tests", "*PearlElationPanelProbe*",
                             "--console=plain", "-q"], capture_output=True, text=True)
    out = pathlib.Path("build/test-results/test/TEST-com.laosun.aluminium.test.content.characters.PearlElationPanelProbe.xml")
    if out.exists():
        import xml.etree.ElementTree as ET
        root = ET.parse(out).getroot()
        system_out = root.find("system-out")
        for line in (system_out.text or "").splitlines():
            if line.strip().startswith("[panel]"):
                print("  " + line.strip())
    else:
        print("  probe did not run:", result.stdout[-500:], result.stderr[-500:])

    restore()
    return 0


if __name__ == "__main__":
    sys.exit(main())
