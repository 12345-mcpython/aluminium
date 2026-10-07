# -*- coding: utf-8 -*-
"""Dump one subtree of the loot box's roll template as JSON, so the branch structure can be read directly.

Usage:
    python tools/dump_1506_roll_part.py <template-index> <task-index>
    python tools/dump_1506_roll_part.py 3 2
"""
import io
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = pathlib.Path(r'E:\turnbasedgamedata\Config\ConfigAbility\Avatar\Avatar_SilverWolf999_00_Ability.json')
TEMPLATE = 'SilverWolf999_00_RollItemIndex'


def main():
    template_index = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    task_index = int(sys.argv[2]) if len(sys.argv) > 2 else 2
    data = json.loads(SOURCE.read_text(encoding='utf-8'))
    templates = [i for i, t in enumerate(data.get('GlobalTemplates', [])) if t.get('Name') == TEMPLATE]
    if not templates:
        print('template not found')
        return 1
    template = data['GlobalTemplates'][templates[0]]
    tasks = template['TaskList']
    print('top-level tasks: %s' % [(i, str(n.get('$type', '')).rsplit('.', 1)[-1]) for i, n in enumerate(tasks)])
    print()
    print(json.dumps(tasks[task_index], ensure_ascii=False, indent=1))
    return 0


if __name__ == '__main__':
    sys.exit(main())
