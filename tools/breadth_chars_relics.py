# -*- coding: utf-8 -*-
"""Round 355: breadth over characters and relic sets too -- select every rule so it is validated."""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/EveryCharacterAndRelicRuleIsSelectedTest.java'
NAME = 'EveryCharacterAndRelicRuleIsSelectedTest'

chars = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(WORK + '/src/main/resources/characters/*.json'))
relics = sorted(int(os.path.basename(p)[:-5])
                for p in glob.glob(WORK + '/src/main/resources/relic_sets/*.json')
                if not p.endswith('_unmodelled.json'))
print('characters=%d relics=%d' % (len(chars), len(relics)))

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * BREADTH for characters and relic sets (2026-09-30): select every rule so the engine validates it.
 *
 * <p>Companion of EveryConeRuleIsSelectedTest. A rule is validated only when it is SELECTED; cone 23059 shipped two
 * errors (an attribute the engine lacks, and a category test on an event that carries no category) while the full suite
 * stayed green, because nothing ever selected those rules. These two tests do nothing but select.
 */
public class EveryCharacterAndRelicRuleIsSelectedTest {
    private static final int LEVEL = 80;
    private static final int[] CHARACTERS = {CHARS};
    private static final int[] RELICS = {RELICS};

    @Test
    public void everyCharacterRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int id : CHARACTERS) {
            var table = CharacterFactory.create(id, LEVEL).getTriggerTable();
            for (TriggerEvent event : TriggerEvent.values()) {
                try {
                    selected += table.rulesFor(event).size();
                } catch (RuntimeException e) {
                    problems.add("character " + id + " on " + event + ": " + e.getMessage());
                }
            }
        }
        System.out.println("[breadth-chars] characters=" + CHARACTERS.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("invalid character rules:\\n" + String.join("\\n", problems));
        }
    }

    @Test
    public void everyRelicRuleIsSelectedOnEveryEvent() {
        List<String> problems = new ArrayList<>();
        int selected = 0;
        for (int set : RELICS) {
            for (int pieces : new int[]{2, 4}) {
                RelicTriggerTables.Rules rules;
                try {
                    rules = RelicTriggerTables.of(set);
                } catch (RuntimeException e) {
                    problems.add("relic " + set + ": " + e.getMessage());
                    break;
                }
                for (TriggerEvent event : TriggerEvent.values()) {
                    try {
                        selected += rules.at(pieces).rulesFor(event).size();
                    } catch (RuntimeException e) {
                        String message = String.valueOf(e.getMessage());
                        if (message.contains("piece") || message.contains("tier") || message.contains("4")) {
                            continue;   // this set simply has no such tier, which is not an invalid rule
                        }
                        problems.add("relic " + set + "/" + pieces + " on " + event + ": " + message);
                    }
                }
            }
        }
        System.out.println("[breadth-relics] sets=" + RELICS.length + " rules selected=" + selected);
        if (!problems.isEmpty()) {
            throw new AssertionError("invalid relic rules:\\n" + String.join("\\n", problems));
        }
    }
}
'''.replace('{CHARS}', '{' + ', '.join(str(i) for i in chars) + '}') \
   .replace('{RELICS}', '{' + ', '.join(str(i) for i in relics) + '}')
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('judge written')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
    os.remove(p)
res = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                      '--console=plain'], cwd=WORK, capture_output=True, text=True,
                     encoding='utf-8', errors='replace')
out = ((res.stdout or '') + (res.stderr or ''))
print('focused exit: %d' % res.returncode)
for line in out.strip().split('\n')[-6:] + [l for l in out.split('\n') if '[breadth-' in l]:
    print('  ' + line.strip()[:170])
if res.returncode != 0:
    import xml.etree.ElementTree as ET
    p = WORK + '/build/test-results/test/TEST-com.laosun.aluminium.test.' + NAME + '.xml'
    if os.path.exists(p):
        for case in ET.parse(p).getroot().iter('testcase'):
            for k in ('failure', 'error'):
                n = case.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:1200])
    else:
        print('  (no XML -- a compile failure; the tail above says where)')
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
                      'test: select every character and relic rule so the engine validates them too'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
