"""Mutations for 1506's talent (「每点【隐藏分】使暴击率提高 0.4%。暴击率达到 100% 后，其余每点改为暴击伤害提高 0.8%」).

  first_percent   0.004 -> 0.002 on the CRIT RATE half                       (content)
  second_percent  2 -> 1 on the CRIT DMG half                                (content)
  second_scale    the CRIT DMG half reads from CRIT_CHANCE 0.0 instead of 1.0 (content)
  drop_second     the CRIT DMG half is deleted                               (content)
  no_removal      the REMOVE_STATE pair dropped, so a re-fire piles on   (content)
  swap_order      the two effects swapped (the second reads the stale value)  (content)

Usage:
    python tools/mut_1506_talent.py <variant>
    python tools/mut_1506_talent.py restore
"""
import io
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path('src/main/resources/characters/1506.json')
RULE = 'p1506_talent_hidden_becomes_crit'


def load():
    with io.open(CONTENT, encoding='utf-8') as handle:
        return json.load(handle)


def save(data):
    with io.open(CONTENT, 'w', encoding='utf-8') as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write('\n')


def the_rule(data):
    for rule in data['rules']:
        if rule.get('id') == RULE:
            return rule
    raise SystemExit('rule %s not found' % RULE)


def apply(variant):
    data = load()
    rule = the_rule(data)
    effects = rule['do']
    if variant == 'first_percent':
        effects[0]['percent'] = 0.002
    elif variant == 'second_percent':
        effects[1]['percent'] = 1
    elif variant == 'second_scale':
        effects[1]['scale'] = 'self_attr_above:CRIT_CHANCE:0'
    elif variant == 'drop_second':
        rule['do'] = effects[:1]
    elif variant == 'no_removal':
        # ⚠ This is the variant that matters: without the named removals the two modifiers are re-attached on every
        # RESOURCE_CHANGED and PILE UP (measured on the first version: 20.44 for 100 points instead of 0.637).
        rule['do'] = [e for e in effects if e.get('op') != 'REMOVE_STATE']
    elif variant == 'swap_order':
        rule['do'] = [effects[1], effects[0]]
    else:
        return None
    save(data)
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--', str(CONTENT)], check=True)
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
