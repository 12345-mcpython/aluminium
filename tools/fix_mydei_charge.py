"""Insert the CHARGE constant by matching the declaration pattern, not a fixed escape depth."""
import io
import re
import sys

PATH = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
text = io.open(PATH, encoding="utf-8").read()

if "String CHARGE =" in text:
    print("already there")
    raise SystemExit(0)

match = re.search(r'( *private static final String BLOODFEUD = [^;]+;)', text)
if match is None:
    sys.exit("REFUSING: the BLOODFEUD declaration was not found")
indent = " " * (len(match.group(1)) - len(match.group(1).lstrip()))
line = match.group(1)
# build the CHARGE literal from the BLOODFEUD one's own escape style, by asking the file what it uses
charge_escaped = line.split('"')[1].replace("\u8840\u4ec7", "\u5929\u8d4b\u5145\u80fd")
if charge_escaped == line.split('"')[1]:
    sys.exit("REFUSING: could not derive the CHARGE literal from %r" % line)
insert = '\n%sprivate static final String CHARGE = "%s";' % (indent, charge_escaped)
text = text[:match.end(1)] + insert + text[match.end(1):]
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   inserted: %s" % insert.strip())
