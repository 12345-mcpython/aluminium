"""One judge per §2 row, as the guard requires: cite the end-to-end one."""
import io
import sys

PATH = "EXPRESSION.md"
text = io.open(PATH, encoding="utf-8").read()
OLD = "`GiftCarriesTheLaughsTest`\u3001`KaiBuBaiTest` |"
NEW = "`GiftCarriesTheLaughsTest` |"
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the row names the end-to-end judge")
