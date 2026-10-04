"""Put the four-argument Panel convenience constructor back (the shipped `MemospriteResourcePanelTest` calls it)."""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java"
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
if "this(attribute, percent, flat, source, null);" in text:
    sys.exit("already there")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the four-argument constructor is back")
