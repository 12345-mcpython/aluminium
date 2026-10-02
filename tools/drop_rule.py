# -*- coding: utf-8 -*-
"""Remove rules by id -- the ROLLBACK half of the shipping tools.

Usage: python tools/drop_rule.py <content-file> <rule-id> [<rule-id> ...]

Why it exists: every shipping tool in this directory only ever APPENDS, so the one thing the project's own discipline
asks for -- "an ability that is not finished must be rolled back and recorded" -- had no tool. Hand-editing a rule out
of a 100-line file whose notes are longer than its rules is how a stray comma ships.

It refuses (exit 1, no write) when:
  * the path is not one of the two content shapes (a bare [rules] list, or {"1": [rules], ...});
  * an id matches nothing -- a rollback that removed nothing must not look like it worked;
  * any rule would be left with an empty "do" list (that is a different edit, not a removal).
It prints what it removed, as the acceptance material for the round that ran it: the ids, the events they hung on, and
the ops they carried, so the round's report can say what left the tree.
"""

import io
import json
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    path, wanted = argv[1], list(dict.fromkeys(argv[2:]))
    doc = json.load(io.open(path, encoding='utf-8'))
    ranks = [None] if isinstance(doc, list) else sorted(doc.keys())

    removed = []
    for rank in ranks:
        rules = doc if rank is None else doc[rank]
        if not isinstance(rules, list):
            print('REFUSING: %s[%s] is not a rules list' % (path, rank))
            return 1
        kept = []
        for rule in rules:
            if isinstance(rule, dict) and rule.get('id') in wanted:
                ops = [d.get('op') for d in rule.get('do', [])]
                removed.append((rule.get('id'), rule.get('on'), ops))
                continue
            kept.append(rule)
        if not kept:
            print('REFUSING: that would empty %s[%s]' % (path, rank))
            return 1
        if rank is None:
            doc = kept
        else:
            doc[rank] = kept

    missing = [w for w in wanted if w not in [r[0] for r in removed]]
    if missing:
        print('REFUSING: nothing matched %s in %s (tree untouched)' % (missing, path))
        return 1

    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)                      # parse before writing: a broken rollback never lands
    io.open(path, 'w', encoding='utf-8', newline='').write(text)
    for rid, event, ops in removed:
        print('removed %s: on=%s do=%s' % (rid, event, ops))
    left = sum(len(doc) if isinstance(doc, list) else len(doc[r]) for r in ranks)
    print('%s: %d rule(s) left' % (path, left))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
