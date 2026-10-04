"""The test helper sets JAVA field names, so the two keys have to be perStack / perStackLive."""
import io

PATH = "src/test/java/com/laosun/aluminium/test/PerStackLiveTest.java"
text = io.open(PATH, encoding="utf-8").read()
for old, new in (('"per_stack", "self_stacks:"', '"perStack", "self_stacks:"'),
                 ('"per_stack_live", Boolean.TRUE', '"perStackLive", Boolean.TRUE')):
    if text.count(old) != 1:
        raise SystemExit("REFUSING: %r appears %d times" % (old, text.count(old)))
    text = text.replace(old, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   the two keys are Java field names now")
