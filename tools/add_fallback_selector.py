# -*- coding: utf-8 -*-
"""Round 694: the fallback selector -- the engine half.

Three registered clauses need it (1220 reason 1, 1221 reason 2, 1305): 「若…目标被消灭则对敌方随机单体发动」.
That is exactly "the preferred target is dead, so take a random enemy instead". The engine already distinguishes
death from invulnerability (CanHit:88-90), so the predicate is isDeath() -- no HP approximation.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
NL = chr(10)


def bail(msg):
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) the selector becomes known at load time
OLD_SET = ('    private static final Set<String> TARGET_SELECTORS =' + NL
           + '            Set.of("party_first", "next_ally", "self", "target", "attacker", "all_allies", "party", "other_allies", "summon",' + NL
           + '                    "target_and_summon", "all_enemies", "lowest_hp_ally",' + NL
           + '            "random_enemy", "random_hit_enemy");')
NEW_SET = ('    private static final Set<String> TARGET_SELECTORS =' + NL
           + '            Set.of("party_first", "next_ally", "self", "target", "attacker", "all_allies", "party", "other_allies", "summon",' + NL
           + '                    "target_and_summon", "all_enemies", "lowest_hp_ally",' + NL
           + '            "random_enemy", "random_hit_enemy",' + NL
           + '            // \u2b50 \u300c\u82e5\u8ffd\u52a0\u653b\u51fb\u65bd\u653e\u524d\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9\u654c\u65b9\u968f\u673a\u5355\u4f53\u53d1\u52a8\u300d (2026-09-30; three registered readers:' + NL
           + '            // 1220 reason 1, 1221 reason 2, 1305). The preferred target is dead -> take a random enemy.' + NL
           + '            // CanHit:88-90 keeps death orthogonal to invulnerability, so this is the clause own wording.' + NL
           + '            "target_else_random_enemy");')
print('selector-set anchor: %d' % saved.count(OLD_SET))
if saved.count(OLD_SET) != 1:
    bail('the selector-set anchor is not unique')

# 2) and it resolves
OLD_CASE = '            case TARGET_RANDOM_ENEMY -> require('
NEW_CASE = ('''            // \u2b50 The fallback (2026-09-30): 「\u82e5\u2026\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9\u654c\u65b9\u968f\u673a\u5355\u4f53\u53d1\u52a8\u300d. The preferred target
            // is the trigger's own, and CanHit has a real "defeated" flag orthogonal to invulnerability (CanHit:88-90),
            // so a dead preferred target -- and only that -- falls through to the battle's seeded random opponent.
            case "target_else_random_enemy" -> {
                CanHit preferred = ctx.target();
                yield preferred != null && !preferred.isDeath()
                        ? preferred
                        : require(ctx.battle() == null ? null
                        : ctx.battle().randomOpponent(ctx.owner()), "target_else_random_enemy", ctx);
            }
            case TARGET_RANDOM_ENEMY -> require(''')
print('case anchor: %d' % saved.count(OLD_CASE))
if saved.count(OLD_CASE) != 1:
    bail('the case anchor is not unique')

patched = saved.replace(OLD_SET, NEW_SET, 1).replace(OLD_CASE, NEW_CASE, 1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(patched)
print('selector added to TARGET_SELECTORS and to resolveTarget')


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
                                               (m.get('message') or '')[:200]))
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: target_else_random_enemy -- the fallback three registered clauses need'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
