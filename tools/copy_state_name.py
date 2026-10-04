import io, re, sys
SHIPPED = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
MINE = "src/test/java/com/laosun/aluminium/test/HuohuoTalismanLosesATurnTest.java"
shipped = io.open(SHIPPED, encoding="utf-8").read()
m = re.search(r'String STATE = "([^"]+)";', shipped)
if not m:
    sys.exit("REFUSING: no STATE literal in the shipped judge")
name = m.group(1)
print("copied name: %r (len %d)" % (name, len(name)))
mine = io.open(MINE, encoding="utf-8").read()
old = re.search(r'String STATE = "[^"]+";', mine).group(0)
io.open(MINE, "w", encoding="utf-8", newline="\n").write(mine.replace(old, 'String STATE = "%s";' % name, 1))
print("ok   my judge uses the shipped literal")
