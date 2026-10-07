# -*- coding: utf-8 -*-
"""Find the loot box's actual weighted roll, and everything that sets its item index.

`SilverWolf999_00_RollItemIndex` only DISPATCHES on a value it does not compute: it switches on hash 350671164 and sets
`CurrentItem` to 1/2/3. So the roll itself (and the weights `Item1Chance`/`Item2Chance`/`Item3Chance`) live in whatever
task sets that value - this prints every Random/Weight/Roll-shaped task and every assignment to `CurrentItem` or to the
item-chance keys, with the path it sits at.

Usage:
    python tools/probe_1506_roll_source.py
"""
import io
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = pathlib.Path(r'E:\turnbasedgamedata\Config\ConfigAbility\Avatar\Avatar_SilverWolf999_00_Ability.json')
INTERESTING_TYPES = ('Random', 'Weight', 'Roll', 'Chance', 'Probability', 'Lottery')
INTERESTING_KEYS = ('CurrentItem', 'Item1Chance', 'Item2Chance', 'Item3Chance',
                    'TriggerItemChance', 'TriggerItemBaseChance')


def type_of(node):
    return str(node.get('$type', '')).rsplit('.', 1)[-1]


def key_of(node):
    key = node.get('DynamicKey')
    return key.get('Value') if isinstance(key, dict) else key


def walk(node, path, hits):
    if isinstance(node, dict):
        kind = type_of(node)
        if any(word in kind for word in INTERESTING_TYPES):
            hits.append(('TASK', kind, path, json.dumps(node, ensure_ascii=False)[:600]))
        if kind == 'SetDynamicValue' and key_of(node) in INTERESTING_KEYS:
            hits.append(('SET', key_of(node), path, json.dumps(node.get('Value'), ensure_ascii=False)[:400]))
        if '350671164' in json.dumps(node.get('Switch', ''), ensure_ascii=False) and kind == 'SwitchCaseByDynamicValue':
            hits.append(('SWITCH-ON-350671164', kind, path, ''))
        for key, value in node.items():
            walk(value, path + '/' + str(key), hits)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            walk(value, path + '[%d]' % index, hits)


def main():
    data = json.loads(SOURCE.read_text(encoding='utf-8'))
    hits = []
    walk(data, '', hits)
    print('%d hits\n' % len(hits))
    for kind, what, path, body in hits:
        print('%-20s %-22s %s' % (kind, what, path[-90:]))
        if body:
            print('    %s' % body.replace('\n', ' ')[:400])
    return 0


if __name__ == '__main__':
    sys.exit(main())
