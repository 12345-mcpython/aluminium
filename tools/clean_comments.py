"""Take the decorative symbols out of comments, leaving plain ASCII prose (2026-10-02).

Only comment text is rewritten. A small state machine walks each line so that string literals, char literals and text blocks are never touched -- mistaking code for a comment would be damage,
so every ambiguity resolves in favour of "leave it alone".

The mapping, all of it meaning-preserving:
  warning sign            -> "Note:"
  star / check / cross / blocked / play markers -> removed (they decorate, they do not say anything)
  arrow, double arrow     -> "to", "so"
  multiply, <=, >=, ~=, !=, +-  -> ASCII equivalents
  box drawing             -> "-", "|", "+"
  curly and CJK quotes    -> straight quotes, with the CJK pair nested so inner quotes become single
  em dash / en dash / bullets -> "-"
  bold markers (**)       -> removed, so the result is plain text

Game text inside quotes keeps its characters: the same string is the identifier content JSON's `source`/`note` carry, so translating it would break traceability. Translating the Chinese PROSE
around those quotes is a separate pass.
"""
import io
import re
import subprocess
import sys

DECOR = "\u2b50\u2605\u2606\u2705\u274c\u26d4\u2713\u2717\u25b6\u25b2\u2610\u2316\u26a1\u1f6a7\U0001F6A7"
WARN = "\u26a0"

CHAR_MAP = {
    "\u00d7": " x ", "\u2264": "<=", "\u2265": ">=", "\u2248": "~=", "\u2260": "!=", "\u00b1": "+/-",
    "\u2013": "-", "\u00b7": "-", "\u2022": "-",
    "\u2500": "-", "\u2502": "|", "\u2514": "+", "\u2518": "+",
    "\u2019": "'", "\u2018": "'", "\u201c": '"', "\u201d": '"',
    "\u3010": "[", "\u3011": "]",
}
SPACED = [("\u2014", " - "), ("\u2192", " to "), ("\u21d2", " so "), ("\u2190", " from ")]


def clean_comment_text(s):
    """Transform one comment body (the text AFTER the // or leading *)."""
    # protect a trailing block-comment terminator
    tail = ""
    m = re.search(r"\*/\s*$", s)
    if m:
        tail = s[m.start():]
        s = s[:m.start()]
    # keep the body's own indentation: javadoc tables are laid out with it, and collapsing it
    # would silently reformat them
    lead = re.match(r"[ \t]*", s).group(0)
    body = s[len(lead):]
    s = body
    # CJK quote brackets: outermost -> double, nested -> single
    out = []
    depth = 0
    for ch in s:
        if ch == "\u300c":
            out.append('"' if depth == 0 else "'")
            depth = min(depth + 1, 2)
        elif ch == "\u300d":
            depth = max(depth - 1, 0)
            out.append('"' if depth == 0 else "'")
        else:
            out.append(ch)
    s = "".join(out)
    for k, v in CHAR_MAP.items():
        s = s.replace(k, v)
    for k, v in SPACED:
        s = re.sub(r"\s*" + re.escape(k) + r"\s*", v, s)
    s = s.replace(WARN, "Note:")
    before = s
    s = re.sub("[" + re.escape(DECOR) + r"]\s?", "", s)
    if s != before:
        s = re.sub(r"[ \t]{2,}", " ", s)
    s = s.replace("**", "")
    return lead + s + tail


def segments(line, st):
    """Split a Java line into (is_comment, text) pieces. Ambiguity resolves to is_comment=False."""
    segs = []
    i = 0
    n = len(line)
    while i < n:
        if st["block"]:
            j = line.find("*/", i)
            end = n if j < 0 else j + 2
            segs.append((True, line[i:end]))
            i = end
            if j >= 0:
                st["block"] = False
            continue
        if st["text"]:
            j = line.find('"""', i)
            end = n if j < 0 else j + 3
            segs.append((False, line[i:end]))
            i = end
            if j >= 0:
                st["text"] = False
            continue
        c = line[i]
        if line.startswith('"""', i):
            st["text"] = True
            segs.append((False, '"""'))
            i += 3
            continue
        if c in "\"'":
            j = i + 1
            while j < n:
                if line[j] == "\\":
                    j += 2
                    continue
                if line[j] == c:
                    j += 1
                    break
                j += 1
            segs.append((False, line[i:j]))
            i = j
            continue
        if line.startswith("//", i):
            segs.append((True, line[i:]))
            i = n
            continue
        if line.startswith("/*", i):
            j = line.find("*/", i + 2)
            if j < 0:
                st["block"] = True
                segs.append((True, line[i:]))
                i = n
            else:
                segs.append((True, line[i:j + 2]))
                i = j + 2
            continue
        j = i
        while j < n and line[j] not in "\"'/":
            j += 1
        if j == i:
            j = i + 1            # a lone '/', as in `/=`: consume it so the walk always advances
        segs.append((False, line[i:j]))
        i = j
    return segs


PREFIX = re.compile(r"^(\s*(?:/\*\*?|\*/|//|\*)\s?)")


def rewrite_java(text):
    st = {"block": False, "text": False}
    out = []
    for line in text.split("\n"):
        pieces = segments(line, st)
        buf = []
        for is_comment, seg in pieces:
            if not is_comment:
                buf.append(seg)
                continue
            m = PREFIX.match(seg)
            if m:
                buf.append(m.group(1) + clean_comment_text(seg[m.end():]))
            else:
                buf.append(clean_comment_text(seg))
        out.append("".join(buf))
    return "\n".join(out)


def rewrite_python(text):
    """Only `#` comments, and only when the `#` is outside every string on the line."""
    out = []
    st = {"text": False}
    for line in text.split("\n"):
        if st["text"]:
            # inside a docstring: look for its end, do not touch
            j = line.find('"""')
            if j >= 0:
                st["text"] = False
            out.append(line)
            continue
        # walk to find an unquoted '#'
        i = 0
        n = len(line)
        cut = None
        while i < n:
            if line.startswith('"""', i):
                st["text"] = True
                break
            if line[i] in "\"'":
                q = line[i]
                j = i + 1
                while j < n:
                    if line[j] == "\\":
                        j += 2
                        continue
                    if line[j] == q:
                        j += 1
                        break
                    j += 1
                i = j
                continue
            if line[i] == "#":
                cut = i
                break
            i += 1
        if cut is None:
            out.append(line)
        else:
            out.append(line[:cut] + clean_comment_text(line[cut:]))
    return "\n".join(out)


files = subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
nj = np_ = 0
for f in files:
    if f.endswith(".java"):
        try:
            text = io.open(f, encoding="utf-8").read()
        except Exception:
            continue
        fixed = rewrite_java(text)
        if fixed != text:
            io.open(f, "w", encoding="utf-8", newline="").write(fixed)
            nj += 1
    elif f.endswith(".py"):
        try:
            text = io.open(f, encoding="utf-8").read()
        except Exception:
            continue
        fixed = rewrite_python(text)
        if fixed != text:
            io.open(f, "w", encoding="utf-8", newline="").write(fixed)
            np_ += 1
print("rewrote comments in %d java files and %d python files" % (nj, np_))
