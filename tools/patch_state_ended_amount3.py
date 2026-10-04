"""Remove the in-loop announcement left behind by the first half of the patch.

The head of `removeState` now fires ONCE with the total, so the per-instance firing inside the loop has to go or the amount
is counted twice. Anchored by regex on the call itself (indentation-insensitive), and any `if (battle != null) { }` left
empty by the removal is dropped too.
"""
import io
import re

PATH = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
text = io.open(PATH, encoding="utf-8").read()

CALL = re.compile(r"[ \t]*if \(battle != null\) \{\s*\n"
                  r"[ \t]*battle\.fireStateEnded\(instance, buff\.getState\(\), "
                  r"instance\.getBuffManager\(\)\.stacksOf\(buff\.getState\(\)\)\);\s*\n"
                  r"[ \t]*\}\n")
text, count = CALL.subn("", text)
if count != 1:
    raise SystemExit("REFUSING: the in-loop block matched %d times" % count)
print("ok   the in-loop announcement is gone")

text2, empty = re.subn(r"[ \t]*if \(battle != null\) \{\s*\n[ \t]*\}\n", "", text)
print("ok   empty guards removed: %d" % empty)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text2)
