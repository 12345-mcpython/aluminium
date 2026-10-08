"""Re-apply the self-removals to 1506's talent and ramp rules.

⚠⚠ Why this file exists at all: the first attempt WAS applied, then the mutation driver's `restore` ran
`git checkout -- characters/1506.json` before the work was committed, and wiped it. That is the disaster GAPS already
records from earlier in this session ("commit before mutating") repeating itself - the judges survived because they live in
another file, so the suite came back red with content that looked untouched. This script redoes the two edits, and the
commit comes immediately after.

Both fixes are the same shape: a rule that fires repeatedly and re-attaches a PERMANENT scaled modifier must take its own
named modifiers off first. `coexist: true` is NOT that - measured, its meaning is `setKeepsSiblings`, i.e. live beside
OTHER sources' modifiers on the same attribute.

Usage:  python tools/fix_1506_self_removals.py
"""
import io
import json
import pathlib

CONTENT = pathlib.Path('src/main/resources/characters/1506.json')
text = CONTENT.read_text(encoding='utf-8')


def swap(old, new, why):
    global text
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times (%s)' % (text.count(old), why))
    text = text.replace(old, new)


TALENT_OLD = '''      "do": [
        {
          "op": "MODIFY_ATTR",
          "attribute": "CRIT_CHANCE",
          "scale": "self_attr_above:隐藏分:0",'''
TALENT_NEW = '''      "do": [
        {
          "op": "REMOVE_STATE",
          "buff": "1506天赋-暴击率",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "REMOVE_STATE",
          "buff": "1506天赋-暴击伤害",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "MODIFY_ATTR",
          "attribute": "CRIT_CHANCE",
          "buff": "1506天赋-暴击率",
          "scale": "self_attr_above:隐藏分:0",'''

TALENT_SECOND_OLD = '''          "op": "MODIFY_ATTR",
          "attribute": "CRIT_ATTACK",
          "scale": "self_attr_above:CRIT_CHANCE:1.0",'''
TALENT_SECOND_NEW = '''          "op": "MODIFY_ATTR",
          "attribute": "CRIT_ATTACK",
          "buff": "1506天赋-暴击伤害",
          "scale": "self_attr_above:CRIT_CHANCE:1.0",'''

RAMP_OLD = '''      "do": [
        {
          "op": "MODIFY_ATTR",
          "attribute": "ALL_DAMAGE_TYPE_BOOST",
          "scale": "self_attr_above:隐藏分:60",'''
RAMP_NEW = '''      "do": [
        {
          "op": "REMOVE_STATE",
          "buff": "1506行迹-强化普攻隐藏分1",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "REMOVE_STATE",
          "buff": "1506行迹-强化普攻隐藏分2",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "REMOVE_STATE",
          "buff": "1506行迹-强化普攻隐藏分3",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "REMOVE_STATE",
          "buff": "1506行迹-强化普攻隐藏分4",
          "kind": "own",
          "target": "self"
        },
        {
          "op": "MODIFY_ATTR",
          "attribute": "ALL_DAMAGE_TYPE_BOOST",
          "buff": "1506行迹-强化普攻隐藏分1",
          "scale": "self_attr_above:隐藏分:60",'''

# The three remaining ramp terms get their names too, each keyed off its own scale line.
for index, (scale, name) in enumerate([
        ('self_attr_above:隐藏分:120",\n          "percent": -0.075', '1506行迹-强化普攻隐藏分2'),
        ('self_attr_above:隐藏分:120",\n          "percent": 0.075', '1506行迹-强化普攻隐藏分3'),
        ('self_attr_above:隐藏分:180",\n          "percent": 0.15', '1506行迹-强化普攻隐藏分4')], start=2):
    swap('          "attribute": "ALL_DAMAGE_TYPE_BOOST",\n          "scale": "%s' % scale,
         '          "attribute": "ALL_DAMAGE_TYPE_BOOST",\n          "buff": "%s",\n          "scale": "%s' % (name, scale),
         'ramp term %d name' % index)

NOTE_OLD = ('`coexist: true` 让规则**替换自己**上一次的贡献 ✓ 而不是叠加 ✓。')
NOTE_NEW = ('⚠⚠ **重算必须靠「先移除自己」** ✓ —— **实测**：`coexist: true` 的语义是 `setKeepsSiblings` ✓'
            '（**与同属性的兄弟修饰器共存** ✗），**不是**「替换自己上一次的贡献」✗ ⇒ 少了那两条 `REMOVE_STATE`'
            '（按**名字** ✓、`kind: own` ✓）时，每次【隐藏分】变化都会**再叠一层** ✗（实测：夹具「每点触发一次」时 100 点读出 '
            '**20.44** ✓，正好是 `0.004 × (1+2+…+100)` ✓）。⚠ 而 `coexist: true` **仍要保留** ✓：移除的是**自己** ✓，'
            '保留它才能让**别的来源**（光锥／遗器／队友）挂在同一属性上的修饰器不被顶掉 ✓。')

swap(TALENT_OLD, TALENT_NEW, 'talent removals')
swap(TALENT_SECOND_OLD, TALENT_SECOND_NEW, 'talent crit-damage name')
swap(RAMP_OLD, RAMP_NEW, 'ramp removals')
swap(NOTE_OLD, NOTE_NEW, 'talent note')

CONTENT.write_text(text, encoding='utf-8')
data = json.loads(CONTENT.read_text(encoding='utf-8'))
for rule in data['rules']:
    if rule['id'] in ('p1506_talent_hidden_becomes_crit', 'p1506_enhanced_basic_hidden_ramp'):
        ops = [effect['op'] for effect in rule['do']]
        print('%-40s %s' % (rule['id'], ops))
print('JSON OK, rules =', len(data['rules']))
