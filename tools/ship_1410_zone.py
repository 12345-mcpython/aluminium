# -*- coding: utf-8 -*-
"""Round 1095: 1410's ultimate opens a zone -- two same-named debuffs, so both end together.

The old note was right that a plain `turns: 3` would leave the two debuffs behind after the zone ended. The answer
is not a new op: it is the SAME NAME, which is what `REMOVE_STATE`/the anchor sweep key on. Both debuffs carry
`buff: 结界`, the same `turns: 3`, and `ticksOn: "self"` -- so they expire together, and 「当海瑟音陷入无法战斗
状态时，结界也会被解除」 rides on `removeBuffsAnchoredTo` (that path was repaired in `b275250`).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
PATH = WORK + '/src/main/resources/characters/1410.json'
RID = 'ult_zone_opens_and_lowers'
ZONE = '\u7ed3\u754c'                      # 结界

NOTE = (
    '\u2b50 2026-09-30 \u8f6c\u6b63 \u2713\uff1a\u300c\u6d77\u745f\u97f3**\u5c55\u5f00\u7ed3\u754c**\uff0c\u4f7f\u654c\u65b9\u76ee\u6807'
    '\u653b\u51fb\u529b\u964d\u4f4e 15%\u3001\u9632\u5fa1\u529b\u964d\u4f4e 25%\u2026\u7ed3\u754c\u6301\u7eed 3 \u56de\u5408\uff0c'
    '\u81ea\u8eab\u6bcf\u56de\u5408\u5f00\u59cb\u65f6\u51cf 1\u3002\u5f53\u6d77\u745f\u97f3\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u65f6\uff0c'
    '\u7ed3\u754c\u4e5f\u4f1a\u88ab\u89e3\u9664\u300d\u2014\u2014 \u26a0 \u65e7\u767b\u8bb0\u62c5\u5fc3\u201c\u7528\u666e\u901a\u7684 `turns: 3` \u5199'
    '\u4f1a\u5728**\u7ed3\u754c\u7ed3\u675f\u540e**\u4ecd\u7559\u7740\u201d \u2717\u3002\u2b50 \u7b54\u6848\u4e0d\u662f\u65b0 op\uff0c\u800c\u662f**\u540c\u540d** \u2713\uff1a'
    '\u4e24\u53e5\u90fd\u5e26 `buff: \u7ed3\u754c` + \u540c\u4e00\u4e2a `turns: 3` + `ticksOn: self` \u21d2 \u65f6\u95f4\u4e00\u5230\u4e24\u53e5\u4e00\u8d77\u6ca1 \u2713\uff1b'
    '\u800c\u300c\u5979\u5012\u4e0b\u65f6\u7ed3\u754c\u4e5f\u88ab\u89e3\u9664\u300d\u7531 `removeBuffsAnchoredTo` \u627f\u62c5 \u2713'
    '\uff08\u26a0 \u540c\u6837\u4f9d\u8d56 (b)\uff1a`ticks_on` \u76f4\u5230 `b275250` \u624d\u771f\u6b63\u63a5\u5728 `MODIFY_ATTR` \u4e0a \u2713\uff09\u3002'
)


def main():
    doc = json.load(io.open(PATH, encoding='utf-8'))
    rules = doc if isinstance(doc, list) else doc.get('rules')
    if rules is None:
        print('REFUSING: no rules list')
        return 1
    if any(isinstance(r, dict) and r.get('id') == RID for r in rules):
        print('already shipped: %s' % RID)
        return 0
    do = [
        {'op': 'MODIFY_ATTR', 'attribute': 'ATTACK', 'percent': -0.15, 'turns': 3,
         'buff': ZONE, 'ticks_on': 'self', 'target': 'all_enemies'},
        {'op': 'MODIFY_ATTR', 'attribute': 'DEFENCE', 'percent': -0.25, 'turns': 3,
         'buff': ZONE, 'ticks_on': 'self', 'target': 'all_enemies'},
    ]
    rules.append({'on': 'ULT_CAST', 'id': RID, 'when': ['actor == self'], 'do': do, 'note': NOTE})
    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
    print('shipped %s (rules now %d)' % (RID, len(rules)))
    return 2


changed = main()
if changed == 1:
    sys.exit(1)
if changed != 2:
    sys.exit(0)


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
subprocess.run(['git', 'add', 'src/main/resources/characters/1410.json'], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                "content: 1410's zone opens with two same-named debuffs, so they end together"],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
