# -*- coding: utf-8 -*-
"""Round 548: the light-cone registry and its guard, mirroring the relic side.

The relic side has relic_sets/_unmodelled.json plus a guard in RelicTriggerTableTest. The cone side had neither, so
"5 cones are unwritten" lived only in prose (and was wrong: 170 vs 169). This gives it the same machine-checked form.

The guard's teeth: a registered cone must have NO rule file on the classpath. Writing one therefore forces the
author to delete the registry entry in the same commit -- the reclaim workflow, enforced.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
REG = 'src/main/resources/light_cones/_unmodelled.json'
JREL = 'src/test/java/com/laosun/aluminium/test/LightConeRegistryTest.java'
NAME = 'LightConeRegistryTest'

ENTRIES = {
    '20023': ('纵欢', 'Ability20023', 1,
              '「阿哈时刻发动时，使装备者的欢愉度提高 #1=16%…32%，持续到阿哈时刻结束。」'
              '（E:\\turnbasedgamedata: EquipmentConfig 20023 -> SkillID 20023 -> EquipmentSkillConfig -> '
              'TextMap/TextMapCHS.json, 2026-09-30）The effect maps to ELATION_DAMAGE_BOOST, which the engine HAS; '
              'the only missing piece is an EHA (阿哈时刻) event, and a duration scoped to "until EHA ends". '
              'Reader count: 1 (this cone only) -- below the bar, so registered rather than built.'),
    '21021': ('酣适', 'Ability21021', 1,
              '「当装备者的回合开始时，随机为 1 个当前能量百分比小于 #1=50% 的我方其他目标恢复 #2=8 点能量。」'
              'Needs the shared RANDOM-TARGET selector (reader count 2, with 21029) plus energy restoration and an '
              'energy-percentage filter. The selector is worth building; this cone waits for it.'),
    '21029': ('交手如交谈', 'Ability21029', 1,
              '「装备者施放普攻或战技后，对随机 1 个受到攻击的敌方目标造成等同于自身攻击力 #1=48% 的附加伤害。」'
              'Needs the shared RANDOM-TARGET selector (reader count 2, with 21021) drawn from ATTACK_FINISHED\'s '
              'frozen hit set, plus appended damage. ATTACK_FINISHED already carries that hit set.'),
    '21032': ('秘密', 'Ability21032', 1,
              '「战斗开始时以及当装备者回合开始时，随机生效 1 个效果。该效果生效时，替换上次的效果且本次不会与'
              '上次重复。效果包含：我方全体攻击力 +10%；暴击伤害 +12%；能量恢复效率 +6%。」'
              'Needs random EFFECT selection plus a history-dependent exclusion ("not the same as last time"). '
              'Reader count: 1.'),
    '21038': ('爆燃', 'Ability21038', 1,
              '「当装备者在单次受到攻击中累计损失的生命值超过最大生命值的 25%，或单次消耗自身生命值超过最大生命'
              '值的 25%，则立即回复等同于装备者生命上限 15% 的生命值，同时使装备者造成的伤害提高 25%，持续 2 回合。'
              '该效果每 3 回合只能触发 1 次。」'
              'Needs cumulative HP loss within one hit, HP spending, and a 3-turn cooldown. Reader count: 1.'),
}

reg = {k: [{'require': v[2], 'ability': v[1], 'name': v[0], 'reason': v[3]}] for k, v in ENTRIES.items()}
io.open(WORK + '/' + REG, 'w', encoding='utf-8', newline='').write(
    json.dumps(reg, ensure_ascii=False, indent=2) + '\n')
print('registry written: %d entries (%s)' % (len(reg), sorted(reg)))

JAVA = '''package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * <b>The light-cone registry guard</b> (2026-09-30). The mirror of the relic guard.
 *
 * <p>Every light cone whose ability is not authored must be REGISTERED in {@code light_cones/_unmodelled.json} with
 * the capability it is missing. Before this existed, "5 cones are unwritten" lived only in prose -- and the count
 * itself was wrong (170 vs the real 169 rows), because it came from a file that does not carry abilities at all
 * ({@code data/weapons.json} has zero rows with an ability id).
 *
 * <p><b>The teeth:</b> a registered cone must have NO rule file on the classpath. Authoring one therefore fails this
 * test until the registry entry is deleted in the same commit -- which is exactly how the six relic reclaims worked.
 */
public class LightConeRegistryTest {
    private static final String REGISTRY = "/light_cones/_unmodelled.json";

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> registry() {
        try (InputStream in = LightConeRegistryTest.class.getResourceAsStream(REGISTRY)) {
            Assertions.assertNotNull(in, REGISTRY + " is not on the classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Map<String, Object>>>>() { }.getType());
        } catch (Exception e) {
            throw new AssertionError("cannot read " + REGISTRY, e);
        }
    }

    private static boolean hasRuleFile(String id) {
        return LightConeRegistryTest.class.getResourceAsStream("/light_cones/" + id + ".json") != null;
    }

    @Test
    public void everyRegisteredLightConeIsUnwrittenAndSaysWhy() {
        Map<String, List<Map<String, Object>>> reg = registry();
        Assertions.assertEquals(
                new TreeSet<>(java.util.List.of("20023", "21021", "21029", "21032", "21038")),
                new TreeSet<>(reg.keySet()),
                "the registered set is pinned: a new cone must be added here, a built one removed");
        for (Map.Entry<String, List<Map<String, Object>>> e : reg.entrySet()) {
            String id = e.getKey();
            Assertions.assertFalse(e.getValue().isEmpty(), id + " must carry at least one entry");
            for (Map<String, Object> entry : e.getValue()) {
                Object reason = entry.get("reason");
                Assertions.assertNotNull(reason, id + " must say WHY it is unwritten");
                Assertions.assertTrue(reason.toString().length() > 80,
                        id + " must name the missing capability, and a one-liner is not enough: " + reason);
                Assertions.assertNotNull(entry.get("ability"), id + " must name the ability");
            }
            Assertions.assertFalse(hasRuleFile(id),
                    id + " IS written (a rule file exists), so it must be removed from the registry in the same "
                            + "commit -- that is how every relic reclaim worked");
        }
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('guard written')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


res = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                      '--console=plain'], cwd=WORK, capture_output=True, text=True,
                     encoding='utf-8', errors='replace')
print('focused exit: %d' % res.returncode)
if res.returncode != 0:
    for l in ((res.stdout or '') + (res.stderr or '')).strip().split('\n')[-12:]:
        print('  RAW ' + l.strip()[:175])
    import glob
    import xml.etree.ElementTree as ET
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                n = c.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:300])
    import os
    os.remove(WORK + '/' + JREL)
    os.remove(WORK + '/' + REG)
    print('rolled back')
    sys.exit(1)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: a light-cone registry and its guard -- a registered cone must have no rule file'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
