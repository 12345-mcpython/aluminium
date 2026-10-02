# -*- coding: utf-8 -*-
"""Audit the op vocabulary: what the DISPATCH reads vs what the LOADER knows vs what content USES.

Why this tool exists (2026-10-02): GAPS.md's headline said "op **34**" and nobody could say what that number
counted -- the dispatch switch, the loader's closed set, or the names shipped content spells. They are three
different sets, and measuring one while writing the name of another is how a documentation drift survives.

It prints, without judging:
  * DISPATCH  -- the `case "NAME"` labels directly inside `switch (op) {` in TriggerInterpreter (block depth 1),
                 which is the engine's answer to "which ops exist";
  * LOADER    -- the labels inside the load-time validation switch (the closed set that gets a bespoke check;
                 anything else falls through to that switch's `default`), plus the names that reach that default;
  * CONTENT   -- every distinct `"op"` value in src/main/resources/**/*.json;
  * and the three set differences, each named, so a claim can cite which set it means.

Usage: python tools/audit_op_vocabulary.py
"""

import io
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
INTERPRETER = os.path.join(WORK, 'src', 'main', 'java', 'com', 'laosun', 'aluminium', 'models',
                           'TriggerInterpreter.java')
RESOURCES = os.path.join(WORK, 'src', 'main', 'resources')


def switch_body(lines, start_index):
    """Lines from a `switch ( ... ) {` line to the line that closes it (brace counting)."""
    depth = 0
    began = False
    body = []
    for i in range(start_index, len(lines)):
        line = lines[i]
        body.append(line)
        depth += line.count('{') - line.count('}')
        if '{' in line:
            began = True
        if began and depth == 0:
            return body
    raise SystemExit('unbalanced braces after line %d' % (start_index + 1))


def case_labels(lines, at_depth, indent):
    """`case "NAME"` labels whose line is indented exactly `indent` spaces (one level inside the switch)."""
    pad = ' ' * indent
    found = []
    for line in lines:
        if not line.startswith(pad) or line.startswith(pad + ' '):
            continue
        m = re.match(r'\s*case\s+"([A-Za-z_]+)"', line)
        if m:
            found.append(m.group(1))
    return found


def main():
    lines = io.open(INTERPRETER, encoding='utf-8').read().splitlines()
    switches = [i for i, l in enumerate(lines) if re.match(r'\s*switch\s*\(\s*op\s*\)', l)]
    if len(switches) != 2:
        raise SystemExit('expected exactly two `switch (op)` statements, found %d' % len(switches))
    loader_at, dispatch_at = switches[0], switches[1]

    loader = case_labels(switch_body(lines, loader_at), 0, len(lines[loader_at]) - len(lines[loader_at].lstrip()) + 4)
    dispatch = case_labels(switch_body(lines, dispatch_at), 0,
                           len(lines[dispatch_at]) - len(lines[dispatch_at].lstrip()) + 4)

    content = set()
    for root, _dirs, files in os.walk(RESOURCES):
        for name in files:
            if not name.endswith('.json'):
                continue
            path = os.path.join(root, name)
            try:
                doc = json.load(io.open(path, encoding='utf-8'))
            except Exception:
                continue
            for rules in ([doc] if isinstance(doc, list) else doc.values()):
                if not isinstance(rules, list):
                    continue
                for rule in rules:
                    if isinstance(rule, dict):
                        for effect in rule.get('do', []) or []:
                            if isinstance(effect, dict) and effect.get('op'):
                                content.add(effect['op'])

    loader_set, dispatch_set = set(loader), set(dispatch)
    print('DISPATCH  %d op names' % len(dispatch_set))
    print('  ' + ' '.join(sorted(dispatch_set)))
    print('LOADER    %d bespoke arms (everything else falls through to its `default`)' % len(loader_set))
    print('  ' + ' '.join(sorted(loader_set)))
    print('CONTENT   %d distinct op names in shipped rules' % len(content))
    print('  ' + ' '.join(sorted(content)))
    print()
    print('CONTENT - DISPATCH (a rule naming an op the engine never reads): %s'
          % (' '.join(sorted(content - dispatch_set)) or '(none)'))
    print('CONTENT - LOADER   (validated only by the default arm):           %s'
          % (' '.join(sorted(content - loader_set)) or '(none)'))
    print('DISPATCH - CONTENT (exists, no shipped rule uses it yet):         %s'
          % (' '.join(sorted(dispatch_set - content)) or '(none)'))
    print('LOADER - DISPATCH  (validated but unreachable -- must be empty):  %s'
          % (' '.join(sorted(loader_set - dispatch_set)) or '(none)'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
