"""The scoped reading measures against the BASE too, not against `atk0` (which already carries the trace's +50%)."""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/TransformationScopedStatsTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = "atk0 * 2.3"
NEW = "atk0 / 1.5 * 2.3"
if text.count(OLD) != 1:
    sys.exit("REFUSING: %r appears %d times" % (OLD, text.count(OLD)))
text = text.replace(OLD, NEW, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   the scoped reading divides the trace back out first")
for line in text.split("\n"):
    if "2.3" in line or "1.5" in line:
        print("   " + line.strip()[:118])
