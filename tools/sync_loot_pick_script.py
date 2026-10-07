"""Sync the shipping script with the rider's new value, so a re-ship matches the file."""
import pathlib

PATH = pathlib.Path("tools/ship_1506_loot_pick.py")
text = PATH.read_text(encoding="utf-8")
OLD = '"percent": 0.2,'
NEW = '"percent": 5.0,'
print("anchor count:", text.count(OLD))
PATH.write_text(text.replace(OLD, NEW), encoding="utf-8")
import ast

ast.parse(PATH.read_text(encoding="utf-8"))
print("script synced and parses")
