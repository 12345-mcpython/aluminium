# -*- coding: utf-8 -*-
"""Mutation helper that decides the field's LAYER itself (round 252, discipline 142).

Three rounds in a row (272/274/275) I wrote `when` into an effect's `do` list because a caller flag was easy to
forget: the mutation "ran" and changed a field nobody reads, so a zero-red result looked like a judge gap when it
was a fake mutation. This helper removes the flag: it knows which fields live on a rule and which on an effect, and
it REFUSES anything it does not recognise instead of writing it somewhere harmless.

Rule-level:   on, on_any, when, id, cooldown, source, note
Effect-level: op, percent, amount, scale, turns, permanent, until, target, attribute, buff, max_stacks, per_stack,
              cap_amount, cap_scale, cap_percent, instance, damage_type, element, base_chance, resource, ...
"""

import json

RULE_LEVEL = {'on', 'on_any', 'when', 'id', 'cooldown', 'source', 'note'}
EFFECT_LEVEL = {
    'op', 'percent', 'amount', 'scale', 'turns', 'permanent', 'until', 'target', 'attribute', 'buff',
    'max_stacks', 'per_stack', 'cap_amount', 'cap_scale', 'cap_percent', 'instance', 'damage_type',
    'element', 'base_chance', 'resource', 'skill', 'ticks_on', 'buff_name', 'count', 'stack_cap',
}


def mutate(path, rank, rule_id, field, value, effect_op=None):
    """Set `field` on the named rule; returns the value read back from disk."""
    if field not in RULE_LEVEL and field not in EFFECT_LEVEL:
        raise SystemExit('mutation refused: field %r belongs to no known layer' % field)
    raw = json.load(open(path, encoding='utf-8'))
    touched = 0
    for rule in raw[rank]:
        if rule.get('id') != rule_id:
            continue
        if field in RULE_LEVEL:
            rule[field] = value
            touched += 1
        else:
            for do in rule.get('do', []):
                if effect_op is not None and do.get('op') != effect_op:
                    continue
                do[field] = value
                touched += 1
    if touched != 1:
        raise SystemExit('mutation refused: %s.%s touched %d targets' % (rule_id, field, touched))
    open(path, 'w', encoding='utf-8', newline='').write(json.dumps(raw, ensure_ascii=False, indent=2) + '\n')
    back = json.load(open(path, encoding='utf-8'))
    rule = next(r for r in back[rank] if r.get('id') == rule_id)
    landed = rule.get(field) if field in RULE_LEVEL else [d.get(field) for d in rule['do']]
    return rule, landed
