# -*- coding: utf-8 -*-
"""Round 1005: CAST_SKILL -- the auto-cast family's op, copied from commandSummon with three spots loosened.

The recipe is in GAPS (aggro 回收之七百八十五). The precedent's guardrails and its seven EnemySkill arguments are
copied verbatim, including `stanceFor(true)` -- its comment says why it cannot be dropped.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/resources/../main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
t = io.open(WORK + '/' + ENG, encoding='utf-8').read()


def sub(text, old, new, label):
    n = text.count(old)
    print('%s anchor: %d' % (label, n))
    if n != 1:
        print('REFUSING: %s anchor is not unique' % label)
        sys.exit(1)
    return text.replace(old, new, 1)


# 1) WIRED -- the op must be declared or the loader rejects any content that names it.
wired_old = '"COMMAND_SUMMON",'
wired_new = '"COMMAND_SUMMON", "CAST_SKILL",'
if t.count(wired_old) != 1:
    wired_old = '"COMMAND_SUMMON"'
    wired_new = '"COMMAND_SUMMON", "CAST_SKILL"'
t = sub(t, wired_old, wired_new, 'WIRED')

# 2) the execution branch, right beside its precedent.
t = sub(t,
        '            case "COMMAND_SUMMON" -> commandSummon(battle, effect, ctx);',
        '            case "COMMAND_SUMMON" -> commandSummon(battle, effect, ctx);' + NL
        + '            case "CAST_SKILL" -> castSkill(battle, effect, ctx);',
        'exec case')

# 3) the method, appended right after its precedent.
precedent_tail = ('        attack.execute(battle, summon, victims);' + NL
                  + '    }')
method = precedent_tail + NL + NL + (
    '    /**' + NL
    + '     * {@code CAST_SKILL}: the resolved target performs <b>one cast, right now</b>, with the numbers of the skill' + NL
    + '     * the rule names -- 「使其立即施放 1 次…」。' + NL
    + '     *' + NL
    + '     * <p>⚠ It is {@code commandSummon} with three spots loosened, and nothing else (see `aggro 回收之七百八十五`):' + NL
    + '     *' + NL
    + '     * <ol><li>the <b>actor</b> is the resolved target, not the owner’s summon;</li>' + NL
    + '     * <li>the <b>skill</b> is looked up on that actor, not on the rule owner;</li>' + NL
    + '     * <li>no {@code SUMMON_ATTACK} is announced -- that event belongs to a memosprite (the swing itself still' + NL
    + '     * announces itself through {@code EnemySkill.execute}).</li></ol>' + NL
    + '     *' + NL
    + '     * <p>⚠ <b>韧性必须自己带</b> —— 先例的原话：*when a cast is DELEGATED the executor expands no damage of its' + NL
    + '     * own, so the stance would otherwise be dropped on the floor*. ⚠ Do not drop {@code stanceFor(true)}.' + NL
    + '     */' + NL
    + '    private static void castSkill(Battle battle, EffectSpec effect, TriggerContext ctx) {' + NL
    + '        CanHit actor = require(resolveTarget(effect, ctx), "target", ctx);' + NL
    + '        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));' + NL
    + '        Skill skill = actor.getSkills().get(slot);' + NL
    + '        if (skill == null || skill.getData() == null) {' + NL
    + '            throw new IllegalStateException(' + NL
    + '                    actor.getName() + " has no " + slot + " skill, so a CAST_SKILL effect has nothing to "' + NL
    + '                            + "read: the rule names the skill whose numbers the commanded cast uses");' + NL
    + '        }' + NL
    + '        if (!skill.getData().getEffect().isDamaging()) {' + NL
    + '            throw new IllegalStateException(' + NL
    + '                    "CAST_SKILL effect points at " + slot + ", whose effect is "' + NL
    + '                            + skill.getData().getEffect() + " rather than a damaging one");' + NL
    + '        }' + NL
    + '        List<CanHit> victims = new ArrayList<>();' + NL
    + '        for (CanHit unit : battle.getOpponents(actor)) {' + NL
    + '            if (unit != null && !unit.isDeath()) {' + NL
    + '                victims.add(unit);' + NL
    + '            }' + NL
    + '        }' + NL
    + '        if (victims.isEmpty()) {' + NL
    + '            return;                                  // nothing left to hit: an empty battlefield, not a bad rule' + NL
    + '        }' + NL
    + '        EnemySkill attack = new EnemySkill(' + NL
    + '                skill.getData().getElement(),' + NL
    + '                multiplierOf(skill, effect, actor),' + NL
    + '                1,                                   // one segment, like the precedent' + NL
    + '                DamageType.NORMAL,' + NL
    + '                skill.getData().getEffect(),' + NL
    + '                AttributeType.fromString(effect.getAttribute()),' + NL
    + '                skill.getData().stanceFor(true));' + NL
    + '        attack.execute(battle, actor, victims);' + NL
    + '    }')
t = sub(t, precedent_tail, method, 'method')

io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t)
print('three edits applied')


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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:260]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if '.java:' in l or 'error:' in l or '错误' in l:
            print('  DIAG ' + l.strip()[:180])
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', ENG], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                'feat: CAST_SKILL lets a resolved target perform one cast, copied from commandSummon'],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
