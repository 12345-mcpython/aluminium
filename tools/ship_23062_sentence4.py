# -*- coding: utf-8 -*-
"""Round 810: 23062 随心, sentence 4 -- 「每消耗1点能量值，使本次终结技伤害提高 #3%，最多 #6%」.

It hangs on DEALING_DAMAGE (only that event hands over the instance a BOOST_DAMAGE can mutate), gated by
`from_skill ULTRA`, scaled by the cast_energy_spent source that shipped in b0ca23c, capped absolutely by cap_amount.
Values (llms-full.txt): #3 = 0.2/0.25/0.3/0.35/0.4%, #6 = 72/90/108/126/144%.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23062.json'
NL = chr(10)
NEW_ID = 'cone23062_energy_scale'
PER_POINT = {'1': 0.002, '2': 0.0025, '3': 0.003, '4': 0.0035, '5': 0.004}
CAP = {'1': 0.72, '2': 0.9, '3': 1.08, '4': 1.26, '5': 1.44}
SRC = ('光锥 23062 随心: 「使装备者的攻击力提高#1[i]%，能量恢复效率提高#2[f1]%。装备者施放终结技时，'
       '每消耗1点能量值，使本次造成的终结技伤害提高#3[f2]%，最多提高#6[i]%。…」')

path = WORK + '/' + CONE
doc = json.load(io.open(path, encoding='utf-8'))
touched = 0
for rank, rules in doc.items():
    if any(isinstance(r, dict) and r.get('id') == NEW_ID for r in rules):
        print('rank %s already shipped' % rank)
        continue
    rules.append({
        'on': 'DEALING_DAMAGE',
        'id': NEW_ID,
        'when': ['from_skill ULTRA'],
        'do': [{'op': 'BOOST_DAMAGE', 'scale': 'cast_energy_spent',
                'percent': PER_POINT[rank], 'cap_amount': CAP[rank]}],
        'source': SRC,
        'note': ('⭐ **第 4 句已出货** ✓（2026-09-30 ✓）：⚠ 「每消耗 1 点能量值…最多 #6%」✗ ⇒ ⚠ 三条现有词汇合起来 '
                 '✓：⚠ `scale: cast_energy_spent` ✗（⚠ 「每**消耗** 1 点」✓ —— ⚠ 本段新增的量纲 ✓，'
                 '⚠ 值由 `ULT_CAST` 携带 ✓ ⇒ ⚠ `Damage.castEnergySpent` ✓ ⇒ ⚠ 结算时读 ✓）＋ '
                 '⚠ `from_skill ULTRA` ✗（⚠ 「本次**终结技**」✓ —— ⚠ 事件的第 7 个参数就是施放类别 ✓）＋ '
                 '⚠ `cap_amount` ✗（⚠ 「**最多** #6%」✓ —— ⚠ **绝对量** ✓，⚠ 不是 `cap_scale`／`cap_percent` '
                 '那套"某属性的份额"✗）。⚠ 而 ⚠ 它**必须挂 `DEALING_DAMAGE`** ✗：⚠ `BOOST_DAMAGE` 改的是'
                 '**正在结算的那个实例** ✓，⚠ 装载器原话 *"that is the event that hands over what it changes"* ✓。'
                 '⚠ 算式 ⚠ `percent × scale + amount` ✗ ⇒ ⚠ 阶 ' + rank + ' 消耗 50 点 ⇒ ⚠ ' +
                 ('%.1f' % (PER_POINT[rank] * 50 * 100)) + '% ✓（⚠ 上限 ' + ('%.0f' % (CAP[rank] * 100)) + '% ✓）。'
                 '⚠ 数值取自 ⚠ `aluminium_texts\\llms-full.txt` ✗（⚠ 光锥**没有**单独文本文件 ✓）。'),
    })
    touched += 1
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('ranks touched: %d ; rules per rank now: %s' % (touched, {k: len(v) for k, v in doc.items()}))


def bail(msg):
    subprocess.run(['git', 'checkout', '--', CONE], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


s = run('test', '--rerun-tasks')
print('suite %d' % s.returncode)
if s.returncode != 0:
    import glob as g
    import xml.etree.ElementTree as E
    for p in g.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            r = E.parse(p).getroot()
        except Exception:
            continue
        for c in r.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    print('  FAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                               (m.get('message') or '')[:260]))
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 23062 scales the ultimate damage by the energy it spent, capped absolutely'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
