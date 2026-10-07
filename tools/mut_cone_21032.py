"""Mutations for light cone 21032's pick memory (`one_of_no_repeat`) and its replace-with-one shape.

  no_memory         the remembered effect is not excluded from the pool            (engine)
  no_remember       the pick is never filed, so nothing is remembered              (engine)
  no_removal        the three `always` removals are dropped: effects accumulate    (content)
  separate_memories the turn-start rule gets its own memory name                   (content)
  unnamed_picks     the modifiers lose their names, so nothing can remove them     (content)
  wrong_value       the ATK effect is 20% instead of 10%                           (content)

Every variant must turn a judge red; `restore` puts the files back with `git checkout`, which is safe because this work is
committed (the rule the earlier round broke: commit first, then mutate).

Usage:
    python tools/mut_cone_21032.py <variant>
    python tools/mut_cone_21032.py restore
"""
import json
import pathlib
import subprocess
import sys

CONE = pathlib.Path('src/main/resources/light_cones/21032.json')
INTERPRETER = pathlib.Path('src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java')
MEMORY = 'cone21032_secret'

EXCLUDE = """                int last = picker.lastPick(memory);
                if (last >= 0) {
                    pool.removeIf(index -> index == last);
                }
"""
REMEMBER = """                    picker.rememberPick(memory, chosen);
"""


def load():
    return json.loads(CONE.read_text(encoding='utf-8'))


def save(data):
    CONE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def rules(data):
    return [rule for rank in data.values() for rule in rank]


def patch(path, old, new):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times in %s: %r' % (text.count(old), path, old[:70]))
    path.write_text(text.replace(old, new), encoding='utf-8')


def apply(variant):
    if variant == 'no_memory':
        patch(INTERPRETER, EXCLUDE, '')
    elif variant == 'no_remember':
        patch(INTERPRETER, REMEMBER, '')
    elif variant == 'no_removal':
        data = load()
        for rule in rules(data):
            rule['do'] = [effect for effect in rule['do'] if not effect.get('always')]
        save(data)
    elif variant == 'separate_memories':
        data = load()
        for rank in data.values():
            rank[1]['one_of_no_repeat'] = MEMORY + '_turn'
        save(data)
    elif variant == 'unnamed_picks':
        data = load()
        for rule in rules(data):
            for effect in rule['do']:
                effect.pop('buff', None)
        save(data)
    elif variant == 'wrong_value':
        data = load()
        for rule in rules(data):
            for effect in rule['do']:
                if effect.get('attribute') == 'ATTACK':
                    effect['percent'] = 0.20
        save(data)
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--', str(CONE), str(INTERPRETER)], check=True)
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
