"""Restore the two names the wholesale rewrite dropped: the charge constant and the event import."""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
text = io.open(PATH, encoding="utf-8").read()

if "import com.laosun.aluminium.enums.TriggerEvent;" not in text:
    anchor = "import com.laosun.aluminium.enums.SkillType;"
    if text.count(anchor) != 1:
        sys.exit("REFUSING: the SkillType import appears %d times" % text.count(anchor))
    text = text.replace(anchor, anchor + "\nimport com.laosun.aluminium.enums.TriggerEvent;", 1)
    print("ok   the TriggerEvent import is back")

if 'String CHARGE =' not in text:
    anchor = 'private static final String BLOODFEUD = "\\\\u8840\\\\u4ec7";'
    if text.count(anchor) != 1:
        sys.exit("REFUSING: the BLOODFEUD constant appears %d times" % text.count(anchor))
    text = text.replace(anchor, anchor + '\n    private static final String CHARGE = "\\\\u5929\\\\u8d4b\\\\u5145\\\\u80fd";', 1)
    print("ok   the CHARGE constant is back")

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("done")
