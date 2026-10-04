import io, re
SHIPPED = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
MINE = "src/test/java/com/laosun/aluminium/test/HuohuoTalismanLosesATurnTest.java"
name = re.search(r'String STATE = "([^"]+)";', io.open(SHIPPED, encoding="utf-8").read()).group(1)
mine = io.open(MINE, encoding="utf-8").read()
before = mine.count("\\u7a79\\u547d")
mine = mine.replace("\\u7a79\\u547d", name)
io.open(MINE, "w", encoding="utf-8", newline="\n").write(mine)
print("replaced %d wrong escapes with the shipped literal (len %d)" % (before, len(name)))
