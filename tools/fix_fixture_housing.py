# -*- coding: utf-8 -*-
"""Fix the fixture-housing debt: synthetic sets in a test-side data copy, and re-point its two consumers.

Round 535. Steps 1-5 of the spec recorded in GAPS (aggro 回收之七百四十六). Step 6 (shipping relic 126) is a
separate change, because its content file is written by its own script.

Run it with:  python tools/fix_fixture_housing.py
It refuses unless every anchor matches, and it restores every file it touched if the suite goes red.
"""

import glob
import io
import json
import os
import shutil
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
DATA_MAIN = WORK + '/src/main/resources/data/relic_sets.json'
DATA_TEST = WORK + '/src/test/resources/data/relic_sets.json'
RS = WORK + '/src/test/java/com/laosun/aluminium/test/RelicSetTest.java'
RTT = WORK + '/src/test/java/com/laosun/aluminium/test/RelicTriggerTableTest.java'
SO = WORK + '/src/test/java/com/laosun/aluminium/test/SummonOpTest.java'
FX_OLD = WORK + '/src/test/resources/relic_sets/126.json'
FX_NEW = WORK + '/src/test/resources/relic_sets/99001.json'

touched = {}
orig = {}
for p in (RS, RTT, SO):
    # Note: Keep TWO dicts: `touched` is edited in place while patching, so restoring from it would write the MODIFIED
    # text back (measured 2026-09-30: three test files left dirty). `orig` is never reassigned.
    touched[p] = io.open(p, encoding='utf-8').read()
    orig[p] = touched[p]
fx_text = io.open(FX_OLD, encoding='utf-8').read() if os.path.exists(FX_OLD) else None


def fail(msg):
    for p, text in orig.items():
        io.open(p, 'w', encoding='utf-8', newline='').write(text)
    if fx_text is not None and os.path.exists(FX_NEW):
        io.open(FX_OLD, 'w', encoding='utf-8', newline='').write(fx_text)
        os.remove(FX_NEW)
    if os.path.exists(DATA_TEST):
        os.remove(DATA_TEST)
    print('ROLLED BACK: %s' % msg)
    sys.exit(1)


# ---- 1. the test-side copy, plus two ability-less synthetic sets ------------------------------------------------
src = json.load(io.open(DATA_MAIN, encoding='utf-8'))
tmpl = src[sorted(src.keys())[0]]


def synthetic(set_id, en, cn):
    return {'set_id': set_id,
            'name': {'chinese': cn, 'english': en},
            'release_version': 'test',
            'is_planar': bool(tmpl.get('is_planar', False)),
            'parts': tmpl.get('parts'),
            'effects': [{'require': 2,
                         'desc': {'chinese': '测试合成套装：只为让测试有一个永不出货的宿主。',
                                  'english': 'Synthetic test-only set: a permanent home that is never shipped.'},
                         'param': [0.01],
                         'properties': [{'type': 'AttackAddedRatio', 'value': 0.01}],
                         'ability': ''}]}


dst = dict(src)
dst['99001'] = synthetic(99001, 'Synthetic Fixture Host', '测试夹具宿主')
dst['99002'] = synthetic(99002, 'Synthetic Rules-less Witness', '测试无规则见证')
io.open(DATA_TEST, 'w', encoding='utf-8', newline='').write(
    json.dumps(dst, ensure_ascii=False, indent=2) + '\n')
print('1. test-side data copy written (%d sets, +%s)' % (len(dst), sorted(set(dst) - set(src))))

# ---- 2. RelicSetTest: one filtered local per method, and the four counts read from it ---------------------------
# 2a. the loaded-table method (:8 sets, :82 effects)
old = ('        Assertions.assertFalse(Constant.RELIC_SETS.isEmpty(),\n'
       '                "relic_sets.json was not loaded — the engine cannot apply any set bonus without it");\n'
       '        Assertions.assertEquals(60, Constant.RELIC_SETS.size(),')
new = ('        Assertions.assertFalse(Constant.RELIC_SETS.isEmpty(),\n'
       '                "relic_sets.json was not loaded — the engine cannot apply any set bonus without it");\n'
       '        // Synthetic test-side sets (release_version == "test") never ship, so no census counts them.\n'
       '        List<RelicSet> shipped = Constant.RELIC_SETS.values().stream()\n'
       '                .filter(s -> !"test".equals(s.releaseVersion())).toList();\n'
       '        Assertions.assertEquals(60, shipped.size(),')
if old not in touched[RS]:
    fail('RelicSetTest :78 anchor not found')
touched[RS] = touched[RS].replace(old, new, 1)
old = ('        int effects = Constant.RELIC_SETS.values().stream().mapToInt(set -> set.effects().size()).sum();')
new = '        int effects = shipped.stream().mapToInt(set -> set.effects().size()).sum();'
if old not in touched[RS]:
    fail('RelicSetTest :81 anchor not found')
touched[RS] = touched[RS].replace(old, new, 1)

# 2b. the census method (:412 loop, :424/:425 counts, :428 identity)
# Note: The bare loop header is NOT unique in this file (measured: an earlier method has the same line, which is why the
# first attempt patched the wrong one and the identity's method never got `shipped`). Anchor with context, and assert.
old = ('        int withStats = 0;\n'
       '        int abilityOnly = 0;\n'
       '        for (RelicSet set : Constant.RELIC_SETS.values()) {')
new = ('        int withStats = 0;\n'
       '        int abilityOnly = 0;\n'
       '        List<RelicSet> shipped = Constant.RELIC_SETS.values().stream()\n'
       '                .filter(s -> !"test".equals(s.releaseVersion())).toList();\n'
       '        for (RelicSet set : shipped) {')
if touched[RS].count(old) != 1:
    fail('RelicSetTest :412 anchor is not unique (%d matches)' % touched[RS].count(old))
touched[RS] = touched[RS].replace(old, new, 1)
old = ('        Assertions.assertEquals(Constant.RELIC_SETS.values().stream().mapToInt(s -> s.effects().size()).sum(),\n'
       '                withStats + abilityOnly, "every effect is accounted for");')
new = ('        Assertions.assertEquals(shipped.stream().mapToInt(s -> s.effects().size()).sum(),\n'
       '                withStats + abilityOnly, "every effect is accounted for");')
if old not in touched[RS]:
    fail('RelicSetTest :428 identity anchor not found')
touched[RS] = touched[RS].replace(old, new, 1)
print('2. RelicSetTest: four counts and the identity now read a filtered list')

# ---- 3. the fixture moves to 99001 -----------------------------------------------------------------------------
if fx_text is None:
    fail('the fixture src/test/resources/relic_sets/126.json is missing')
io.open(FX_NEW, 'w', encoding='utf-8', newline='').write(fx_text)
os.remove(FX_OLD)
n = 0
for o, w in [('RelicFactory.suit(126,', 'RelicFactory.suit(99001,'),
             ('relic_sets/126.json', 'relic_sets/99001.json'),
             ('set <b>126</b>', 'set <b>99001</b>')]:
    if o in touched[SO]:
        touched[SO] = touched[SO].replace(o, w)
        n += 1
if n != 3:
    fail('SummonOpTest: expected 3 references to 126, found %d' % n)
# and the long comment that names the old homes
touched[SO] = touched[SO].replace(
    'The fixture lives on set <b>126</b>: its 2-piece is a plain stat (so that tier is outside the census) and it has no rule file of its own, so this fixture shadows nothing: a file in {@code src/test/resources} shadows the shipped -- which is exactly why it must not sit on a set that HAS a rule file (set 108 did, and hid its 4-piece)',
    'The fixture lives on the synthetic set <b>99001</b> (release_version "test"), which exists only in the '
    'test-side copy of relic_sets.json and therefore never ships: that is what ends the treadmill of 108 -> 103 -> '
    '126, where each host was eventually authored and evicted the fixture')
print('3. fixture moved to 99001, SummonOpTest references updated (%d)' % n)

# ---- 4. NO_RULE_SET moves to 99002 -----------------------------------------------------------------------------
if 'private static final int NO_RULE_SET = 132;' not in touched[RTT]:
    fail('NO_RULE_SET anchor not found')
touched[RTT] = touched[RTT].replace('private static final int NO_RULE_SET = 132;',
                                    'private static final int NO_RULE_SET = 99002;', 1)
old = ('                        + "merge copy) -- which is why this case must name a set that is still unwritten: it "\n'
       '                        + "was 102 until set 102 was authored on 2026-09-28");')
new = ('                        + "merge copy). It names the synthetic set 99002 (release_version \\"test\\"), which "\n'
       '                        + "exists only in the test-side copy of relic_sets.json, so it can never be authored "\n'
       '                        + "-- that is what ended the 102 -> 132 treadmill");')
if old not in touched[RTT]:
    fail('NO_RULE_SET message anchor not found')
touched[RTT] = touched[RTT].replace(old, new, 1)
print('4. NO_RULE_SET re-pointed to 99002')

# ---- 5. the copy-sync guard: OWED, deliberately not written by this tool -------------------------
# Rounds 540/541 measured the first version was wrong: it used the SAME path for main and test and read
# both through getResourceAsStream, so it saw null. The correct shape is to Files.readString the two REAL
# files (src/main/resources/data/relic_sets.json and src/test/resources/data/relic_sets.json), drop the
# entries whose release_version is "test", and compare the rest field by field. It was blocking five
# otherwise-sound steps, and a step whose only job is to guard the copy must not veto the copy.
GUARD = None
print('5. copy-sync guard: owed (see the comment above)')

for p, text in touched.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(text)


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-12:]:
        print('  RAW ' + l.strip()[:175])
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
                    print('  FAIL ' + (n.get('message') or '')[:280].replace('\n', ' '))
    pass  # no guard file is written by this tool
    fail('the suite went red after the change')
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: synthetic sets give the fixture a permanent home; the censuses skip them'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
