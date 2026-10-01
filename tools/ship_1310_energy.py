# -*- coding: utf-8 -*-
"""Round 566: close the self_energy_percent capability -- content, judge, and an engine mutation.

Content : character 1310 (流萤), TALENT clause 4 「当能量恢复至上限时解除自身所有负面效果」
          (authoritative text: E:\\turnbasedgamedata\\aluminium_texts\\1310_流萤.md:166, read 2026-09-30).
Judge   : shape + a matching() positive/negative pair (energy full => match, energy low => no match).
Mutation: flip the ratio in TriggerTable -- the negative case must notice.
"""

import glob
import io
import json
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1310.json'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerTable.java'
JREL = 'src/test/java/com/laosun/aluminium/test/Cid1310EnergyFullDispelTest.java'
NAME = 'Cid1310EnergyFullDispelTest'

RULE = {
    'on': 'ENERGY_GAINED',
    'id': 'talent_dispel_when_energy_full',
    'when': ['actor == self', 'self_energy_percent >= 1.0'],
    'do': [{'op': 'DISPEL', 'amount': 99, 'target': 'self'}],
    'source': ('流萤 天赋 茧式源火中枢（E:\\turnbasedgamedata\\aluminium_texts\\1310_流萤.md:166，2026-09-30 读）: '
               '「当能量恢复至上限时解除自身所有负面效果。」'),
    'note': ('⭐ 槽位已用权威文本核实 ✓：该句在**天赋**段内 ✓（同段还有「生命值越低受伤越低」「完全燃烧时效果抵抗 +30%」'
             '「战斗开始时若能量不足 50% 则恢复至 50%」✓）⇒ ⚠ **属于基础天赋，无星魂/行迹门槛** ✓ ⇒ 不加 `min_eidolon` ✓。'
             '⭐ 条件用本段新增的 **`self_energy_percent`** ✓（⚠ 该变量此前不存在 ✓，五处出货注记都把它列为阻碍 ✓：'
             '光锥 21017 第二句 ✓、1310 的 ③④ ✓、1215 星魂 2 ✓、21021 的筛选 ✓）；⚠ 事件 `ENERGY_GAINED` ✓'
             '（⚠ `1505` 在用 ✓）＋ `actor == self` ✓ 限定"是**她**回能"✓。'
             '⚠⚠ **`amount: 99` 是表达手法，不是原句数值** ✗：原句说「解除自身**所有**负面效果」✓，'
             '而 `DISPEL` 的语义是"移除**至多 `amount` 个**负面效果"✗（`TriggerInterpreter:74` ✓）⇒ '
             '⚠ 引擎**没有"所有"这个拼法** ✗ ⇒ ⭐ 取一个真实战斗中不可能达到的数量来表达"所有" ✓，'
             '⚠ 并在此**如实写明** ✓（⭐ 与 `1505` 用 `Integer.MAX_VALUE` 表示"永不满"完全同一手法 ✓）。'
             '⚠ **同段的另一句仍未写** ✗：「战斗开始时若能量不足 50% 则使其**恢复至 50%**」✗ —— '
             '⚠ 条件已可写 ✓（`self_energy_percent < 0.5` ✓），⚠ 但效果缺的是"**把能量设为上限的 X%**"这个 op ✗：'
             '⚠ 写成"加 50% 上限"会在能量 30% 时冲到 80% ✗（⚠ 没有症状的错误 ✓）⇒ 保持登记 ✓。'),
}
chars = json.load(io.open(WORK + '/' + CHAR, encoding='utf-8'))
if not isinstance(chars, list):
    print('REFUSING: characters/1310.json is not a list (%s)' % type(chars).__name__)
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in chars):
    print('already shipped')
    sys.exit(0)
chars.append(RULE)
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(
    json.dumps(chars, ensure_ascii=False, indent=2) + '\n')
print('content written: 1310 now has %d rules' % len(chars))

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 流萤 (1310) TALENT: 「当能量恢复至上限时解除自身所有负面效果」 (2026-09-30).
 *
 * <p>The first shipped reader of the {@code self_energy_percent} condition variable. matching() evaluates conditions,
 * so both directions are pinned: at full energy the rule matches, below full it must not. The engine mutation (flip
 * the ratio) is caught by the SECOND assertion -- with the ratio inverted a zero-energy unit reads Infinity.
 */
public class Cid1310EnergyFullDispelTest {
    private static final int CID = 1310;
    private static final int LEVEL = 80;

    private static Character wearer(Battle[] out) {
        Character c = CharacterFactory.create(CID, LEVEL);
        Battle b = new Battle(List.of(c), List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        b.startBattle();
        out[0] = b;
        return c;
    }

    private static int matched(Character owner, Battle battle) {
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, owner, 0, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        return owner.getTriggerTable().matching(TriggerEvent.ENERGY_GAINED, ctx).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList().size();
    }

    @Test
    public void theRuleStatesItsGuardAndItsEffect() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        // rulesFor does NOT evaluate conditions -- that is what makes it the shape test. matching() below is the
        // one that evaluates them, and it is why this test would have failed if it had used matching() at zero energy.
        var mine = c.getTriggerTable().rulesFor(TriggerEvent.ENERGY_GAINED).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList();
        Assertions.assertEquals(1, mine.size(), "the rule is installed");
        Assertions.assertEquals(List.of("actor == self", "self_energy_percent >= 1.0"),
                mine.getFirst().conditions().stream().map(x -> x.source()).toList(), "its guard");
        Assertions.assertEquals("DISPEL", mine.getFirst().effects().getFirst().getOp(), "its effect");
        System.out.println("[1310] shape ok");
    }

    @Test
    public void onlyAFullEnergyBarMatches() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        Battle b = rules[0];
        Assertions.assertTrue(c.getCurrentEnergy() < c.getMaxEnergy(),
                "a fresh unit starts below its maximum (got " + c.getCurrentEnergy() + "/" + c.getMaxEnergy() + ")");
        Assertions.assertEquals(0, matched(c, b),
                "below full energy the talent must NOT fire");
        c.gainEnergy(c.getMaxEnergy() * 2.0);
        Assertions.assertEquals(c.getMaxEnergy(), c.getCurrentEnergy(), 1e-9, "the grant clamps to the cap");
        Assertions.assertEquals(1, matched(c, b),
                "at full energy the talent must fire -- this is the assertion the engine mutation trips");
        System.out.println("[1310] energy guard ok: " + c.getCurrentEnergy() + "/" + c.getMaxEnergy());
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('judge written')
char_orig = io.open(WORK + '/' + CHAR, encoding='utf-8').read()
eng_orig = io.open(WORK + '/' + ENG, encoding='utf-8').read()


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


def focused():
    folder = WORK + '/build/test-results/test'
    for p in glob.glob(folder + '/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                        '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    reds = 0
    printed = []
    for p in glob.glob(folder + '/*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for el in root.iter():
            for chunk in (el.text, el.tail):
                for line in (chunk or '').split('\n'):
                    if line.strip().startswith('[1310]'):
                        printed.append(line.strip())
        reds += sum(1 for t in root.iter('testcase')
                    if t.find('failure') is not None or t.find('error') is not None)
    return r.returncode, reds, printed, ((r.stdout or '') + (r.stderr or ''))


code, reds, printed, out = focused()
print('focused exit: %d (reds=%d)' % (code, reds))
for l in printed[:3]:
    print('  ' + l[:170])
if code != 0:
    for l in out.strip().split('\n')[-12:]:
        print('  RAW ' + l.strip()[:175])
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for t in root.iter('testcase'):
            for k in ('failure', 'error'):
                n = t.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:300])
    io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(char_orig)
    os.remove(WORK + '/' + JREL)
    print('rolled back')
    sys.exit(1)

# ENGINE mutation: invert the ratio. A zero-energy unit then reads Infinity, which is >= 1.0 -> the negative
# assertion must go red.
mut_old = '(double) ctx.owner().getCurrentEnergy() / ctx.owner().getMaxEnergy();'
mut_new = '(double) ctx.owner().getMaxEnergy() / ctx.owner().getCurrentEnergy();'
if eng_orig.count(mut_old) != 1:
    print('REFUSING: mutation anchor matched %d times' % eng_orig.count(mut_old))
    io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(char_orig)
    os.remove(WORK + '/' + JREL)
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_orig.replace(mut_old, mut_new, 1))
print('engine mutated: current/max inverted')
_, reds2, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_orig)
print('engine restored; the ratio mutation is %s (reds=%s)' % ('red' if reds2 else 'GREEN (blind!)', reds2))
if reds2 == 0:
    io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(char_orig)
    os.remove(WORK + '/' + JREL)
    print('rolled back')
    sys.exit(1)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1310 talent dispels at full energy -- the first reader of self_energy_percent'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
