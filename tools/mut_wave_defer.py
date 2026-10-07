"""Mutations for the wave-held box (`defer_until_wave`).

  no_defer          the field is ignored: the box always fires where it was triggered   (engine)
  no_delivery       a held trigger is never delivered at the wave                        (engine)
  reroll_at_delivery delivery rolls the (already decayed) chance again                    (engine)
  content_no_field  the box stops stating the field                                       (content)

Every variant must turn a judge red; `restore` puts the files back with `git checkout`, which is safe because this work is
committed.

Usage:
    python tools/mut_wave_defer.py <variant>
    python tools/mut_wave_defer.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path('src/main/resources/characters/1506.json')
BATTLE = pathlib.Path('src/main/java/com/laosun/aluminium/Battle.java')
INTERPRETER = pathlib.Path('src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java')
BOX = 'p1506_zone_loot_box_on_a_spent_point'

DEFER = """            if (rule.deferUntilWave() && !hasLivingOpponent(battle, owner)) {
                battle.deferUntilWave(rule, ctx.withRule(rule.id()));
            } else {
                apply(battle, rule, ctx.withRule(rule.id()));
            }
"""
DRAIN = """                for (DeferredTrigger trigger : held) {
                    TriggerInterpreter.apply(this, trigger.rule(), trigger.context());
                    fired++;
                }
"""
REROLL = """                for (DeferredTrigger trigger : held) {
                    CanHit picker = trigger.context().owner();
                    double chance = picker == null ? trigger.rule().chance()
                            : picker.currentRuleChance(trigger.rule().id(), trigger.rule().chance());
                    if (chance < 1 && !rollChance(chance)) {
                        continue;
                    }
                    TriggerInterpreter.apply(this, trigger.rule(), trigger.context());
                    fired++;
                }
"""


def patch(path, old, new):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times in %s: %r' % (text.count(old), path, old[:70]))
    path.write_text(text.replace(old, new), encoding='utf-8')


def apply(variant):
    if variant == 'no_defer':
        patch(INTERPRETER, DEFER, '            apply(battle, rule, ctx.withRule(rule.id()));\n')
    elif variant == 'no_delivery':
        patch(BATTLE, DRAIN, '')
    elif variant == 'reroll_at_delivery':
        patch(BATTLE, DRAIN, REROLL)
    elif variant == 'content_no_field':
        data = json.loads(CONTENT.read_text(encoding='utf-8'))
        rule = next(r for r in data['rules'] if r['id'] == BOX)
        rule.pop('defer_until_wave', None)
        CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--', str(CONTENT), str(BATTLE), str(INTERPRETER)], check=True)
        print('restored with git checkout')
        return 0
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    applied = apply(sys.argv[1])
    if applied is None:
        print('unknown variant:', sys.argv[1])
        return 2
    print('applied', applied)
    return 0


if __name__ == '__main__':
    sys.exit(main())
