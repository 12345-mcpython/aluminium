"""Mutations for 1506's 40% 【好活当赏】 riders (one rule per slot: 普攻 1, 战技 2).

  percent       the shipped 0.4 becomes 0.1 on BOTH slots                    (content)
  drop_skill    the 战技 rule is deleted (its twin still pays)               (content)
  gate          the state gate is dropped (so it pays without 【好活当赏】)   (content)

⚠ Written as a FILE rather than an inline `python -c`: quoting a JSON fragment through PowerShell ate the quotes and the
"mutation" silently did not apply, so the run came back green and meant nothing.

Usage:
    python tools/mut_1506_rider.py <variant>
    python tools/mut_1506_rider.py restore
"""
import io
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path('src/main/resources/characters/1506.json')
BASIC = 'p1506_banger_basic_rider'
SKILL = 'p1506_banger_skill_rider'


def load():
    with io.open(CONTENT, encoding='utf-8') as handle:
        return json.load(handle)


def save(data):
    with io.open(CONTENT, 'w', encoding='utf-8') as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write('\n')


def apply(variant):
    data = load()
    rules = data['rules']
    if variant == 'percent':
        touched = 0
        for rule in rules:
            if rule.get('id') in (BASIC, SKILL):
                for effect in rule['do']:
                    if effect.get('percent') == 0.4:
                        effect['percent'] = 0.1
                        touched += 1
        if touched != 2:
            raise SystemExit('expected two 0.4 riders, touched %d' % touched)
    elif variant == 'drop_skill':
        before = len(rules)
        data['rules'] = [rule for rule in rules if rule.get('id') != SKILL]
        if len(data['rules']) != before - 1:
            raise SystemExit('the 战技 rider was not found')
    elif variant == 'gate':
        for rule in rules:
            if rule.get('id') in (BASIC, SKILL):
                rule['when'] = [c for c in rule['when'] if c != 'self has_state 好活当赏']
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
