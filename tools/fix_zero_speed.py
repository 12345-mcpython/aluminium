"""Two fixes: keep the four-argument Panel convenience constructor, and return the Summon type in the judge."""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/ZeroSpeedByAbilityTest.java"

text = io.open(SPEC, encoding="utf-8").read()
OLD = """        public Panel(String attribute, Double percent, Double flat) {
            this(attribute, percent, flat, null, null);
        }"""
NEW = """        public Panel(String attribute, Double percent, Double flat) {
            this(attribute, percent, flat, null, null);
        }

        /** The resource-derived panel (2026-10-02), with nothing said about abilities. */
        public Panel(String attribute, Double percent, Double flat, String source) {
            this(attribute, percent, flat, source, null);
        }"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the ctor anchor appears %d times" % text.count(OLD))
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the four-argument constructor is back")

text = io.open(JUDGE, encoding="utf-8").read()
for old, new in (("    private static Character summonOf(int cid) {", "    private static Summon summonOf(int cid) {"),
                 ("        Character unit = summonOf(1409);", "        Summon unit = summonOf(1409);"),
                 ("        Character unit = summonOf(1415);", "        Summon unit = summonOf(1415);")):
    if text.count(old) != 1:
        sys.exit("REFUSING: %r appears %d times" % (old.strip(), text.count(old)))
    text = text.replace(old, new, 1)
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge returns the summon type")
