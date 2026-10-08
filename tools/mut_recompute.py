"""Mutations for the recompute shape (a re-firing permanent modifier must take itself off first).

  drop_1412_removal  1412's own-removal dropped (its modifier re-attaches every cast)   (content)
  unnamed_talent     the talent's modifier loses its `buff` name, so nothing matches it (content)

Both must turn the sweep in `RecomputeNotAccumulateTest` red. `restore` uses `git checkout` - safe only because the work is
committed first (that lesson cost a round: the driver once wiped uncommitted fixes while the judges, in another file,
survived).

Usage:
    python tools/mut_recompute.py <variant>
    python tools/mut_recompute.py restore
"""
import pathlib
import subprocess
import sys

CHARACTERS = pathlib.Path('src/main/resources/characters')
FILES = [CHARACTERS / '1412.json', CHARACTERS / '1506.json']


def patch(path, old, new):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times in %s' % (text.count(old), path))
    path.write_text(text.replace(old, new), encoding='utf-8')


def apply(variant):
    if variant == 'drop_1412_removal':
        patch(CHARACTERS / '1412.json', '''        {
          "op": "REMOVE_STATE",
          "buff": "军功-攻击力",
          "kind": "own",
          "target": "target"
        },
''', '')
    elif variant == 'unnamed_talent':
        patch(CHARACTERS / '1506.json', '''          "attribute": "CRIT_CHANCE",
          "buff": "1506天赋-暴击率",
''', '''          "attribute": "CRIT_CHANCE",
''')
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--'] + [str(path) for path in FILES], check=True)
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
