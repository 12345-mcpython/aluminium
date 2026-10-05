# -*- coding: utf-8 -*-
"""Round 978: 1402's Interwoven Thread keeps only the newest holder -- with NO engine change.

The idiom is already shipped: REMOVE_STATE takes the name off every resolved target, then APPLY_BUFF puts it on the
new one. Order is the semantics.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
PATH = WORK + '/src/main/resources/characters/1402.json'
NAME = '间隙织线'   # 间隙织线

NOTE = (
    '⭐ 2026-09-30 转正：「【间隙织线】**仅对最新被施加的目标生效**」'
    '—— ⚠ **不需要新能力** ✓：`REMOVE_STATE` 先把这个名字从**全体**摘掉，'
    '再 `APPLY_BUFF` 给新目标 ⇒ ⚠ **顺序即语义** ✓。'
    '⚠ `TriggerInterpreter:2515` 的注释原话：*REMOVE_STATE takes the named state off every resolved target '
    '-- the only-the-newest-one-holds-it half of 星期日的【蒙福者】*；'
    '⚠ 而 `1215` 的注记说得更直白：*the state goes off everybody, then onto the new target '
    '-- there is no only-one-holder flag, the removal IS that clause*。'
    '⚠ 摘不存在的状态**不是错** ✓（`:2518` ✓）—— 每次施放都会跑一遍。'
)


def main():
    doc = json.load(io.open(PATH, encoding='utf-8'))
    rules = doc if isinstance(doc, list) else doc.get('rules')
    if rules is None:
        print('REFUSING: no rules list')
        return 1
    target = None
    for r in rules:
        if not isinstance(r, dict):
            continue
        for e in (r.get('do') or []):
            if e.get('op') == 'APPLY_BUFF' and NAME in str(e.get('buff')):
                target = r
                break
        if target is not None:
            break
    if target is None:
        print('REFUSING: no rule applies %s' % NAME)
        return 1
    print('found: id=%s on=%s when=%s' % (target.get('id'), target.get('on'), target.get('when')))
    print('  do before: %s' % json.dumps(target.get('do'), ensure_ascii=False)[:240])
    if any(e.get('op') == 'REMOVE_STATE' for e in target['do']):
        print('already carries the removal')
        return 0
    target['do'].insert(0, {'op': 'REMOVE_STATE', 'buff': NAME, 'target': 'all_enemies'})
    target['note'] = str(target.get('note') or '') + chr(10) + chr(10) + NOTE
    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
    print('  do after : %s' % json.dumps(target.get('do'), ensure_ascii=False)[:240])
    return 2


changed = main()
if changed == 1:
    sys.exit(1)
if changed == 2:
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
                        print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
        print('REFUSING to commit')
        sys.exit(1)
    print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
    subprocess.run(['git', 'add', 'src/main/resources/characters/1402.json'], cwd=WORK, check=True)
    print(subprocess.run(['git', 'commit', '-m',
                          "content: 1402's Interwoven Thread keeps only the newest holder, no engine change"],
                         cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
    subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
    print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                     text=True).stdout.strip() or 'clean'))
