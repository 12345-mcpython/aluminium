"""Classify every Han-containing string literal in non-comment Java code as a
human-facing MESSAGE (assertion / println / exception text) or as DATA
(a state/resource/condition spelling the engine must match).

Read-only analysis helper for the English message pass.
"""
import io
import os
import re

HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")

IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")

ASSERT = {"assertEquals", "assertTrue", "assertFalse", "assertNull", "assertNotNull",
          "assertSame", "assertNotSame", "assertThrows", "assertArrayEquals",
          "assertNotEquals", "assertAll", "fail", "assertDoesNotThrow", "assertInstanceOf"}
PRINT = {"println", "print", "printf", "format"}
DATA0 = {"hasState", "stacksOf", "value", "get", "entry", "of", "hasBuff", "stateOf",
         "amountOf", "buffStacks", "stacks", "resourceValue", "getStacks", "getValue",
         "put", "contains", "containsKey", "remove", "addState", "removeState",
         "hasStateName", "stacksOfBuff", "resource", "setResource", "getResource",
         "valueOf", "stateStacks", "layers", "stacksOfState", "partyResourceValue",
         "getOrDefault", "computeIfAbsent", "getBuff", "has", "indexOf", "keySet"}
EXC = re.compile(r"(Exception|Error|Throwable)$")


def tokens(text):
    """(kind, value, pos, line) for identifiers, punctuation and string literals."""
    out = []
    n = len(text)
    i = 0
    line = 1
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c in " \t\r":
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j < 0:
                break
            line += text.count("\n", i, j)
            i = j + 2
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            line += text.count("\n", i, j)
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            raw = text[i:j + 1]
            out.append(("str", raw, i, line))
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            out.append(("char", text[i:j + 1], i, line))
            i = j + 1
            continue
        m = IDENT.match(text, i)
        if m:
            out.append(("id", m.group(0), i, line))
            i = m.end()
            continue
        out.append(("p", c, i, line))
        i += 1
    return out


def classify(toks, k):
    """(callee, argindex, kind) for the string token at index k."""
    depth = 0
    j = k - 1
    open_idx = None
    while j >= 0:
        kind, val, _, _ = toks[j]
        if kind == "p" and val in ")]}":
            depth += 1
        elif kind == "p" and val in "([{":
            if depth == 0:
                open_idx = j
                break
            depth -= 1
        j -= 1
    if open_idx is None:
        return (None, None, "UNKNOWN")
    callee = None
    h = open_idx - 1
    if h >= 0 and toks[h][0] == "id":
        callee = toks[h][1]
        if h - 2 >= 0 and toks[h - 1][0] == "p" and toks[h - 1][1] == "." and \
                toks[h - 2][0] == "id" and toks[h - 2][1] == "new":
            callee = "new " + callee
        elif h - 1 >= 0 and toks[h - 1][0] == "id" and toks[h - 1][1] == "new":
            callee = "new " + callee
    # argument index: count top-level commas after open_idx
    d = 0
    idx = 0
    for t in range(open_idx + 1, k):
        kind, val, _, _ = toks[t]
        if kind == "p" and val in "([{":
            d += 1
        elif kind == "p" and val in ")]}":
            d -= 1
        elif kind == "p" and val == "," and d == 0:
            idx += 1
    if callee is None:
        return (None, idx, "UNKNOWN")
    if callee in PRINT:
        return (callee, idx, "MESSAGE")
    if callee in ASSERT:
        if callee in ("assertTrue", "assertFalse", "assertNull", "assertNotNull",
                      "assertThrows", "assertDoesNotThrow", "assertInstanceOf",
                      "assertSame", "assertNotSame", "assertAll", "fail"):
            return (callee, idx, "MESSAGE" if idx >= 1 or callee == "fail" else "DATA")
        return (callee, idx, "MESSAGE" if idx >= 2 else "DATA")
    if callee.startswith("new ") and EXC.search(callee[4:]):
        return (callee, idx, "MESSAGE" if idx == 0 else "DATA")
    if callee in DATA0:
        return (callee, idx, "DATA" if idx == 0 else "MESSAGE")
    return (callee, idx, "UNKNOWN")


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    buckets = {"MESSAGE": [], "DATA": [], "UNKNOWN": []}
    for dirpath, dirnames, filenames in os.walk(os.path.join(root, "src")):
        dirnames[:] = [d for d in dirnames if d not in ("build", "target", ".git")]
        for fn in sorted(filenames):
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, root).replace("\\", "/")
            text = io.open(p, encoding="utf-8", errors="replace").read()
            toks = tokens(text)
            for k, (kind, val, pos, line) in enumerate(toks):
                if kind != "str" or not HAN.search(val):
                    continue
                ls = text.rfind("\n", 0, pos) + 1
                le = text.find("\n", pos)
                le = len(text) if le < 0 else le
                whole = text[ls:le].strip()
                callee, idx, cls = classify(toks, k)
                buckets[cls].append((rel, line, callee, idx, val, whole))
    out = []
    for cls in ("MESSAGE", "UNKNOWN", "DATA"):
        out.append("=== %s : %d ===" % (cls, len(buckets[cls])))
        for rel, line, callee, idx, val, whole in buckets[cls]:
            out.append("%s:%d  [%s#%s]\n   LIT %s\n   LN  %s" %
                       (rel, line, callee, idx, val, whole[:260]))
        out.append("")
    io.open(os.path.join(root, "tools", "_en_class.txt"), "w", encoding="utf-8",
            newline="\n").write("\n".join(out))
    print("MESSAGE=%d UNKNOWN=%d DATA=%d" % (len(buckets["MESSAGE"]),
                                             len(buckets["UNKNOWN"]),
                                             len(buckets["DATA"])))


main()
