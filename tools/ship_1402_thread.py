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
NAME = '\u95f4\u9699\u7ec7\u7ebf'   # 间隙织线

NOTE = (
    '\u2b50 2026-09-30 \u8f6c\u6b63\uff1a\u300c\u3010\u95f4\u9699\u7ec7\u7ebf\u3011**\u4ec5\u5bf9\u6700\u65b0\u88ab\u65bd\u52a0\u7684\u76ee\u6807\u751f\u6548**\u300d'
    '\u2014\u2014 \u26a0 **\u4e0d\u9700\u8981\u65b0\u80fd\u529b** \u2713\uff1a`REMOVE_STATE` \u5148\u628a\u8fd9\u4e2a\u540d\u5b57\u4ece**\u5168\u4f53**\u6458\u6389\uff0c'
    '\u518d `APPLY_BUFF` \u7ed9\u65b0\u76ee\u6807 \u21d2 \u26a0 **\u987a\u5e8f\u5373\u8bed\u4e49** \u2713\u3002'
    '\u26a0 `TriggerInterpreter:2515` \u7684\u6ce8\u91ca\u539f\u8bdd\uff1a*REMOVE_STATE takes the named state off every resolved target '
    '-- the only-the-newest-one-holds-it half of \u661f\u671f\u65e5\u7684\u3010\u8499\u798f\u8005\u3011*\uff1b'
    '\u26a0 \u800c `1215` \u7684\u6ce8\u8bb0\u8bf4\u5f97\u66f4\u76f4\u767d\uff1a*the state goes off everybody, then onto the new target '
    '-- there is no only-one-holder flag, the removal IS that clause*\u3002'
    '\u26a0 \u6458\u4e0d\u5b58\u5728\u7684\u72b6\u6001**\u4e0d\u662f\u9519** \u2713\uff08`:2518` \u2713\uff09\u2014\u2014 \u6bcf\u6b21\u65bd\u653e\u90fd\u4f1a\u8dd1\u4e00\u904d\u3002'
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
