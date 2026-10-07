# -*- coding: utf-8 -*-
"""Decode the loot box's PostfixExpr formulas (the game's stack VM), so the weight table can be read.

The opcode table comes from the earlier data-export session (`session-ses_f7ec.md`, which built
`E:/code/python/export_skill_segments.py`):

    0x00 GetFixed#i    operand = index into FixedValues
    0x01 GetGlobal#i   operand = index into DynamicHashes   (a SEMANTIC key, globally consistent)
    0x02 Add   0x03 Sub   0x04 Mul   0x05 Div
    0x0A / 0x0E / 0x10  functions whose exact semantics were left OPEN there (rendered `op0A`/`op0E`/`op10`)
    0x11 Return

Usage:
    python tools/decode_1506_expr.py [template-name]
"""
import base64
import io
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = pathlib.Path(r'E:\turnbasedgamedata\Config\ConfigAbility\Avatar\Avatar_SilverWolf999_00_Ability.json')
DEFAULT_TEMPLATE = 'SilverWolf999_00_RollItemIndex'
BINARY = {0x02: '+', 0x03: '-', 0x04: '*', 0x05: '/'}
UNKNOWN_BINARY = {0x0A: 'op0A', 0x10: 'op10'}
UNKNOWN_UNARY = {0x0E: 'op0E'}


def decode(expr):
    """Render one PostfixExpr as a readable expression, keeping unknowns visible."""
    if not expr:
        return '(none)'
    raw = expr.get('OpCodes', '')
    if not raw:
        return '(no opcodes)'
    ops = base64.b64decode(raw + '=' * (-len(raw) % 4))
    fixed = [f.get('Value') for f in expr.get('FixedValues', [])]
    hashes = expr.get('DynamicHashes', [])

    def fmt(item):
        return item

    stack = []
    index = 0
    while index < len(ops):
        op = ops[index]
        index += 1
        if op == 0x00:
            stack.append('F[%s]' % fixed[ops[index]])
            index += 1
        elif op == 0x01:
            stack.append('Dyn[%s]' % hashes[ops[index]])
            index += 1
        elif op in BINARY:
            b = stack.pop() if stack else '?'
            a = stack.pop() if stack else '?'
            stack.append('(%s %s %s)' % (a, BINARY[op], b))
        elif op in UNKNOWN_BINARY:
            b = stack.pop() if stack else '?'
            a = stack.pop() if stack else '?'
            stack.append('%s(%s, %s)' % (UNKNOWN_BINARY[op], a, b))
        elif op in UNKNOWN_UNARY:
            a = stack.pop() if stack else '?'
            stack.append('%s(%s)' % (UNKNOWN_UNARY[op], a))
        elif op == 0x11:
            break
        else:
            stack.append('op%02X' % op)
    return ' ; '.join(stack) + '   [bytes=%s]' % ops.hex()


def walk(node, path, out):
    if isinstance(node, dict):
        if 'PostfixExpr' in node and isinstance(node['PostfixExpr'], dict):
            out.append((path, node['PostfixExpr']))
        for key, value in node.items():
            walk(value, path + '/' + str(key), out)
    elif isinstance(node, list):
        for i, value in enumerate(node):
            walk(value, path + '[%d]' % i, out)


def main():
    template = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TEMPLATE
    data = json.loads(SOURCE.read_text(encoding='utf-8'))
    found = [t for t in data.get('GlobalTemplates', []) if t.get('Name') == template]
    if not found:
        print('template not found:', template)
        return 1
    out = []
    walk(found[0], '', out)
    print('%d PostfixExpr sites in %s\n' % (len(out), template))
    for path, expr in out:
        print('%-64s %s' % (path[-64:], decode(expr)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
