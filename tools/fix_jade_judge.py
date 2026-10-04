"""Fix: CRIT_ATTACK's baseValue is 0, so a share of it is Infinity. Compare the absolute delta for that one.

Measured: attackShare moved 0.185 -> 0.205 (exactly 0.005 x 4, the trace clause working), while critShare was Infinity
because CRIT DMG's base is zero -- the 50% everyone starts with is itself a modifier. The reading for it is therefore the
absolute difference.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        double attackShare = share(jade, AttributeType.ATTACK);
        double critShare = share(jade, AttributeType.CRIT_ATTACK);"""
NEW = """        double attackShare = share(jade, AttributeType.ATTACK);
        // CRIT DMG's base is 0 (the 50% everyone starts with is itself a modifier), so its reading is absolute
        double critShare = jade.getAttribute(AttributeType.CRIT_ATTACK).get();"""
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor 1")
text = text.replace(OLD, NEW, 1)

OLD2 = """        double critAfter = share(jade, AttributeType.CRIT_ATTACK);"""
NEW2 = """        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();"""
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

OLD3 = """        Assertions.assertEquals(0.024 * 4, critAfter - critShare, 1e-9,
                "and so does the crit-damage one");"""
NEW3 = """        Assertions.assertEquals(0.024 * 4, critAfter - critShare, 1e-9,
                "and so does the crit-damage one (absolute: its base is zero)");"""
if text.count(OLD3) != 1:
    raise SystemExit("REFUSING: anchor 3")
text = text.replace(OLD3, NEW3, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   the crit reading is absolute now")
