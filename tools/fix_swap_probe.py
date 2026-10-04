"""Fix the probe's accessor: the panel is read through `getAttribute`."""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/SwapReachesCastProbeTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = '                + " ; character max hp=" + him.getMaxHp() + " ; attack=" + him.getAttack());'
NEW = ('                + " ; character max hp=" + him.getMaxHp()\n'
       '                + " ; attack=" + him.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get());')
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the panel is read through getAttribute")
