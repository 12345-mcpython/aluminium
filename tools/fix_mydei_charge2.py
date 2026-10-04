"""Insert CHARGE in the file's own escape style (the file stores \\uXXXX, not \\uXXXX-with-one-backslash)."""
import io
import re
import sys

PATH = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
text = io.open(PATH, encoding="utf-8").read()

if "String CHARGE =" in text:
    print("already there")
    raise SystemExit(0)

match = re.search(r'( *private static final String BLOODFEUD = "([^"]+)";)', text)
if match is None:
    sys.exit("REFUSING: the BLOODFEUD declaration was not found")
stored = match.group(2)
print("stored literal: %r" % stored)

two = "\\\\"          # two characters: backslash backslash
prefix = two if two in stored else "\\"
charge = "".join(prefix + "u%04X" % ord(ch) for ch in "\u5929\u8d4b\u5145\u80fd")
indent = " " * (len(match.group(1)) - len(match.group(1).lstrip()))
insert = '\n%sprivate static final String CHARGE = "%s";' % (indent, charge)
text = text[:match.end(1)] + insert + text[match.end(1):]
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   inserted: %s" % insert.strip())
