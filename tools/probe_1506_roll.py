# -*- coding: utf-8 -*-
"""Read the loot box's weighted pick out of the upstream ability data.

The document only says 「战技点较少时，触发概率提高…【隐藏分】较少时，触发概率提高」 - the numbers are not in the text.
They are in the game's own ability script: `SilverWolf999_00_RollItemIndex` is a task-list template inside
`Avatar_SilverWolf999_00_Ability.json`, and it sets chance keys (`Item2Chance`, ...) through nested predicate branches
keyed on `TDF_CurBP` (BP = 战技点) and other dynamic values.

This prints every `SetDynamicValue` together with the predicate chain that guards it, and dumps the whole template to
`tools/_1506_roll_template.json` so the reading can be checked line by line.

Usage:
    python tools/probe_1506_roll.py
"""
import io
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = pathlib.Path(r'E:\turnbasedgamedata\Config\ConfigAbility\Avatar\Avatar_SilverWolf999_00_Ability.json')
DUMP = pathlib.Path('tools/_1506_roll_template.json')
TEMPLATE = 'SilverWolf999_00_RollItemIndex'


def type_of(node):
    return str(node.get('$type', '')).rsplit('.', 1)[-1]


def describe_value(value):
    """A one-line reading of a DynamicValue reference: a literal, or a dynamic expression over hashes."""
    if value is None:
        return 'none'
    if not isinstance(value, dict):
        return repr(value)
    if value.get('IsDynamic'):
        expr = value.get('PostfixExpr', {})
        fixed = [f.get('Value') for f in expr.get('FixedValues', [])]
        hashes = expr.get('DynamicHashes', [])
        return 'expr(opcodes=%s, fixed=%s, hashes=%s)' % (expr.get('OpCodes'), fixed, hashes)
    return 'literal(%s)' % value.get('FixedValue', {}).get('Value')


def key_of(node):
    key = node.get('DynamicKey')
    if isinstance(key, dict):
        return key.get('Value')
    return key


def walk(node, guards, out):
    if isinstance(node, dict):
        t = type_of(node)
        if t == 'SetDynamicValue':
            out.append((key_of(node), describe_value(node.get('Value')), list(guards)))
            return
        if t == 'PredicateTaskList':
            pred = node.get('Predicate', {})
            guard = '%s %s %s' % (pred.get('DynamicKey', {}).get('Value') if isinstance(pred.get('DynamicKey'), dict)
                                  else pred.get('DynamicKey'), pred.get('CompareType'), describe_value(pred.get('CompareValue')))
            walk(node.get('SuccessTaskList', []), guards + ['IF ' + guard], out)
            walk(node.get('FailedTaskList', []), guards + ['ELSE of ' + guard], out)
            return
        if t == 'SwitchCaseByDynamicValue':
            switch = describe_value(node.get('Switch'))
            compare = node.get('CompareType')
            for case in node.get('CaseTaskList', []):
                walk(case.get('TaskList', []), guards + ['CASE %s %s %s' % (switch, compare, describe_value(case.get('Case')))], out)
            walk(node.get('DefaultTaskList', []), guards + ['DEFAULT of %s %s' % (switch, compare)], out)
            return
        for value in node.values():
            walk(value, guards, out)
    elif isinstance(node, list):
        for value in node:
            walk(value, guards, out)


def main():
    data = json.loads(SOURCE.read_text(encoding='utf-8'))
    templates = [t for t in data.get('GlobalTemplates', []) if t.get('Name') == TEMPLATE]
    if not templates:
        print('template %s not found; templates present: %s'
              % (TEMPLATE, [t.get('Name') for t in data.get('GlobalTemplates', [])][:20]))
        return 1
    template = templates[0]
    DUMP.write_text(json.dumps(template, ensure_ascii=False, indent=1), encoding='utf-8')
    print('dumped', DUMP)
    out = []
    walk(template, [], out)
    print('\n%d SetDynamicValue sites:\n' % len(out))
    for key, value, guards in out:
        print('SET %-16s = %s' % (key, value))
        for guard in guards:
            print('      %s' % guard)
        print()
    return 0


if __name__ == '__main__':
    sys.exit(main())
