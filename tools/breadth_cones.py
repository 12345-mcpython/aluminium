# -*- coding: utf-8 -*-
"""Round 351: a breadth judge -- select every shipped cone rule so the engine validates it."""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/EveryConeRuleIsSelectedTest.java'
NAME = 'EveryConeRuleIsSelectedTest'

ids = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(WORK + '/src/main/resources/light_cones/*.json'))
print('cones: %d' % len(ids))

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * BREADTH: every shipped light cone, every rank, every event -- selected, so the engine validates it.
 *
 * <p>\\u26a0 Why this exists (2026-09-30): a rule is validated only when it is SELECTED. Cone 23059 shipped with an
 * attribute the engine does not have (HP; it is HEALTH) and with a category test on SKILL_CAST (which carries no
 * category, so the rule could never fire), and the FULL SUITE WAS GREEN TWICE. Nothing selected those rules, so
 * nothing checked them. This test does nothing but select: it asks each table for its rules on every event, which is
 * what makes the engine parse and validate them. Behavioural judges live elsewhere; this one closes the "never
 * validated" hole.
 */
public class EveryConeRuleIsSelectedTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1210;

    private static final int[] CONES = {IDS};

    @Test
    public void everyConeRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int cone : CONES) {
            for (int rank = 1; rank <= 5; rank++) {
                Character wearer;
                try {
                    wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
                } catch (RuntimeException e) {
                    problems.add("cone " + cone + " rank " + rank + ": " + e.getMessage());
                    break;
                }
                var table = wearer.getTriggerTable();
                for (TriggerEvent event : TriggerEvent.values()) {
                    try {
                        selected += table.rulesFor(event).size();
                    } catch (RuntimeException e) {
                        problems.add("cone " + cone + " rank " + rank + " on " + event + ": " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("[breadth] cones=" + CONES.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("unvalidated or invalid rules:\\n" + String.join("\\n", problems));
        }
    }
}
'''.replace('{IDS}', '{' + ', '.join(str(i) for i in ids) + '}')
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('breadth judge written')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
    os.remove(p)
res = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                      '--console=plain'], cwd=WORK, capture_output=True, text=True,
                     encoding='utf-8', errors='replace')
print('focused exit: %d' % res.returncode)
out = ((res.stdout or '') + (res.stderr or ''))
for line in out.strip().split('\n')[-14:]:
    print('  RAW ' + line.strip()[:170])
for l in out.strip().split('\n'):
    if '[breadth]' in l:
        print('  ' + l.strip()[:160])
if res.returncode != 0:
    import xml.etree.ElementTree as ET
    p = WORK + '/build/test-results/test/TEST-com.laosun.aluminium.test.' + NAME + '.xml'
    if os.path.exists(p):
        for case in ET.parse(p).getroot().iter('testcase'):
            for k in ('failure', 'error'):
                n = case.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:900])
    os.remove(WORK + '/' + JREL)
    sys.exit(1)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    os.remove(WORK + '/' + JREL)
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
if subprocess.run(['git', 'status', '--porcelain', 'src'], cwd=WORK, capture_output=True,
                  text=True).stdout.strip():
    subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
else:
    print('coverage already current -- nothing to commit')
    sys.exit(0)
print(subprocess.run(['git', 'commit', '-m',
                      'test: select every shipped cone rule so the engine validates all of them'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
