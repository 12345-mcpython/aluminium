"""Keep ship_1506_speed_cap.py in step with the live rule: the scaled effect needs `coexist`.

Two effects on the SAME attribute and target are judged the same kind and evict each other (isSameKind compares attribute,
modifier type and source role - not the buff's name), so the flat 50% and the per-point term cannot both stand without
`coexist: true`. Measured: without it the trace read 0.38 (the per-point term alone) instead of 0.88.
"""
import pathlib

PATH = pathlib.Path("tools/ship_1506_speed_cap.py")
text = PATH.read_text(encoding="utf-8")

ANCHOR = '"max_points": 100,'
ADD = '\n            "coexist": True,'

if '"coexist"' in text:
    print("already carries coexist")
elif ANCHOR in text:
    text = text.replace(ANCHOR, ANCHOR + ADD, 1)
    PATH.write_text(text, encoding="utf-8")
    print("added coexist after max_points")
else:
    print("anchor not found:", ANCHOR)

assert '"coexist"' in PATH.read_text(encoding="utf-8"), "the edit did not land"
import ast

ast.parse(PATH.read_text(encoding="utf-8"))
print("parses OK")
