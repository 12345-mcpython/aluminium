# -*- coding: utf-8 -*-
"""Round 621: effect-level repetition -- the `times` field, the repeat loop, and the load-time guard.

Nine shipped readers are registered on 「N 次伤害，每次对随机敌方单体」 (1009, 1214, 1302, 1312, 1513, 1505, 1510,
8005, 1221). resolveTargets is already called INSIDE the per-victim loop, so wrapping that loop in an outer
repetition loop makes every repetition re-draw its target -- which is exactly what 「每次对随机敌方单体」 means.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
SPEC = 'src/main/java/com/laosun/aluminium/beans/EffectSpec.java'
INTERP = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
orig = {}
for p in (SPEC, INTERP):
    orig[p] = io.open(WORK + '/' + p, encoding='utf-8').read()


def patch(path, old, new, tag):
    text = orig[path]
    n = text.count(old)
    print('%s anchor count: %d' % (tag, n))
    if n != 1:
        print('REFUSING: %s is not unique' % tag)
        sys.exit(1)
    orig[path] = text.replace(old, new, 1)


# ---- A: the field, right after per_target ---------------------------------------------------------------------
patch(SPEC,
      '    @SerializedName("per_target")\n    private Boolean perTarget;\n',
      '    @SerializedName("per_target")\n    private Boolean perTarget;\n'
      '\n'
      '    /**\n'
      '     * How many times this effect settles, <b>each time re-resolving its targets</b> -- 「额外造成 N 次伤害，\n'
      '     * 每次对随机敌方单体」, which nine shipped clauses are registered on (1009, 1214, 1302, 1312, 1513, 1505,\n'
      '     * 1510, 8005, 1221). Only {@code DAMAGE} reads it.\n'
      '     *\n'
      '     * <p><b>Why it is not {@link #perTarget}.</b> That one multiplies <i>one</i> settlement by the event\'s\n'
      '     * hit count ("hit three enemies, so ×3"); this one makes N <i>independent</i> settlements, each drawing\n'
      '     * its own target. Stating both would have two readings, so the pair is refused at load time -- the same\n'
      '     * house rule that refuses {@code scale} next to {@code per_target}.\n'
      '     */\n'
      '    @SerializedName("times")\n    private Integer times;\n',
      'A field')

# ---- B: the repeat loop ---------------------------------------------------------------------------------------
patch(INTERP,
      '            case "DAMAGE" -> {\n'
      '                // A list, like HEAL/SHIELD: 「对敌方全体」 is one effect that reaches several units, and the\n'
      '                // engine settles one instance per victim (that is what a group attack is here).\n'
      '                for (CanHit victim : resolveTargets(battle, effect, ctx)) {\n'
      '                    damage(battle, effect, ctx, victim);\n'
      '                }\n'
      '            }\n',
      '            case "DAMAGE" -> {\n'
      '                // A list, like HEAL/SHIELD: 「对敌方全体」 is one effect that reaches several units, and the\n'
      '                // engine settles one instance per victim (that is what a group attack is here).\n'
      '                // ⚠ `times` repeats the WHOLE settlement, and because resolveTargets is called INSIDE the outer\n'
      '                // loop, every repetition re-draws its target -- which is what 「每次对随机敌方单体」 means.\n'
      '                int times = effect.getTimes() == null ? 1 : effect.getTimes();\n'
      '                for (int repeat = 0; repeat < times; repeat++) {\n'
      '                    for (CanHit victim : resolveTargets(battle, effect, ctx)) {\n'
      '                        damage(battle, effect, ctx, victim);\n'
      '                    }\n'
      '                }\n'
      '            }\n',
      'B loop')

# ---- C: the load-time guard -----------------------------------------------------------------------------------
patch(INTERP,
      '            case "DAMAGE" -> {\n'
      '                // ? Two ways to state the multiplier',
      '            case "DAMAGE" -> {\n'
      '                // ⚠ `times` says "settle N independent times"; `per_target` says "multiply this one settlement\n'
      '                // by the event\'s hit count". Together they have two readings, so the pair is refused -- the\n'
      '                // same house rule that refuses `scale` next to `per_target`.\n'
      '                if (effect.getTimes() != null && effect.getTimes() < 1) {\n'
      '                    throw new IllegalArgumentException(\n'
      '                            "Op DAMAGE states \\"times\\" = " + effect.getTimes() + ", which settles nothing: "\n'
      '                                    + "it must be at least 1 (source: " + spec.getSource() + ")");\n'
      '                }\n'
      '                if (effect.getTimes() != null && Boolean.TRUE.equals(effect.getPerTarget())) {\n'
      '                    throw new IllegalArgumentException(\n'
      '                            "Op DAMAGE cannot combine \\"times\\" with \\"per_target\\": one repeats the whole "\n'
      '                                    + "settlement and the other multiplies a single one, so the pair has two "\n'
      '                                    + "readings (source: " + spec.getSource() + ")");\n'
      '                }\n'
      '                // ? Two ways to state the multiplier',
      'C guard')

for p, text in orig.items():
    io.open(WORK + '/' + p, 'w', encoding='utf-8', newline='').write(text)


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
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
                    print('  XMLFAIL ' + (m.get('message') or '')[:300])
    for p, text in orig.items():
        pass
    print('NOT rolled back automatically; revert with git checkout if the message points at these edits')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: effect-level repetition (times) -- nine registered clauses are blocked on it'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
