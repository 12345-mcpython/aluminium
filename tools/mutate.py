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

RULE_LEVEL = {'on', 'on_any', 'when', 'id', 'cooldown', 'source', 'note',
              # the rest of a rule's own fields (round 31taught the tool `chance`, `per_turn`,
              # `once_per_battle` and `once_per_attack`: it refused them rather than writing them somewhere
              # harmless, which is right -- but the list has to keep up with the vocabulary).
              'chance', 'per_turn', 'once_per_battle', 'once_per_attack', 'per_attack', 'min_eidolon'}
EFFECT_LEVEL = {
    'op', 'percent', 'amount', 'scale', 'turns', 'permanent', 'until', 'target', 'attribute', 'buff',
    'max_stacks', 'per_stack', 'cap_amount', 'cap_scale', 'cap_percent', 'instance', 'damage_type',
    'element', 'base_chance', 'resource', 'skill', 'ticks_on', 'buff_name', 'count', 'stack_cap',
}


def mutate(path, rank, rule_id, field, value, effect_op=None, effect_attribute=None, every=False):
    """★ `every=True` accepts several matches (round 328): a rule that states the same effect for `self` AND
    `summon` is ONE clause with two targets, and "touch only one of them" is not a mutation of that clause."""
    """Set `field` on the named rule; returns the value read back from disk."""
    if field not in RULE_LEVEL and field not in EFFECT_LEVEL:
        raise SystemExit('mutation refused: field %r belongs to no known layer' % field)
    raw = json.load(open(path, encoding='utf-8'))
    touched = 0
    # ★ A content file is either {"1": [rules]} (cones, relic sets) or a bare [rules] list (characters): the
    # caller passes rank=None for the latter, and the helper used to index it with None and raise.
    rules_here = raw if isinstance(raw, list) else raw[rank]
    for rule in rules_here:
        if rule.get('id') != rule_id:
            continue
        if field in RULE_LEVEL:
            rule[field] = value
            touched += 1
        else:
            for do in rule.get('do', []):
                if effect_op is not None and do.get('op') != effect_op:
                    continue
                if effect_attribute is not None and do.get('attribute') != effect_attribute:
                    continue
                if field not in do:
                    # ⚠ Never INVENT a field: writing `percent` into an effect that has none would make the mutation
                    # meaningless (or worse, change the effect's shape). Only overwrite what the author wrote.
                    continue
                do[field] = value
                touched += 1
    if touched > 1 and not every:
        raise SystemExit('mutation refused: %s.%s touched %d targets (pass every=True to mean all of them)'
                         % (rule_id, field, touched))
    if touched == 0:
        raise SystemExit('mutation refused: %s.%s touched nothing' % (rule_id, field))
    open(path, 'w', encoding='utf-8', newline='').write(json.dumps(raw, ensure_ascii=False, indent=2) + '\n')
    back = json.load(open(path, encoding='utf-8'))
    rule = next(r for r in (back if isinstance(back, list) else back[rank])
                if r.get('id') == rule_id)
    landed = rule.get(field) if field in RULE_LEVEL else [d.get(field) for d in rule['do']]
    return rule, landed
