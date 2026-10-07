"""Repair the charge mutation script so `restore` uses its OWN backup, not `git checkout HEAD`.

The bug it caused: `git checkout HEAD -- 1503.json` reverts to the last COMMIT, and the charge rules were not committed
yet - so "restore" silently deleted them and took the file back to 52 rules. This project has that trap written down, and
my own mutation script still walked into it.
"""
import pathlib

PATH = pathlib.Path("tools/mut_pearl_charge.py")
text = PATH.read_text(encoding="utf-8")

text = text.replace("import subprocess\nimport sys", "import shutil\nimport sys")
text = text.replace(
    'TARGET = pathlib.Path("src/main/resources/characters/1503.json")',
    'TARGET = pathlib.Path("src/main/resources/characters/1503.json")\nBACKUP = TARGET.with_suffix(".json.bak")')

old_restore = (
    '    if len(sys.argv) == 2 and sys.argv[1] == "restore":\n'
    '        result = subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)\n'
    '        print("restored" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")\n'
    '        return 0')
new_restore = (
    '    if len(sys.argv) == 2 and sys.argv[1] == "restore":\n'
    '        if not BACKUP.exists():\n'
    '            print("no backup at", BACKUP)\n'
    '            return 1\n'
    '        shutil.copyfile(BACKUP, TARGET)\n'
    '        print("restored from", BACKUP)\n'
    '        return 0')
assert old_restore in text, "restore block not found"
text = text.replace(old_restore, new_restore)

old_apply = '    before = TARGET.read_text(encoding="utf-8")\n    save(data)'
new_apply = ('    if not BACKUP.exists():\n'
             '        shutil.copyfile(TARGET, BACKUP)\n'
             '    before = TARGET.read_text(encoding="utf-8")\n'
             '    save(data)')
assert old_apply in text, "apply block not found"
text = text.replace(old_apply, new_apply)

text = text.replace('        TARGET.write_text(before, encoding="utf-8")',
                    '        shutil.copyfile(BACKUP, TARGET)')

PATH.write_text(text, encoding="utf-8")

import ast
ast.parse(PATH.read_text(encoding="utf-8"))
print("repaired and parses OK")
print("still mentions git:", "git" in PATH.read_text(encoding="utf-8"))
