"""Sweep the plan labels out of comments (2026-10-02). DRY by default; --apply to write.

Four rules, in this order, on comment text only (a state machine keeps string literals and text blocks out of reach):

  R1 whole parenthesised group of labels, anywhere      `(P1-4)`, `(P11-1, M-40)`        -> removed
  R2 leading label prefix of the comment body           `// P8-8: the gate is ...`      -> `// the gate is ...`
  R3 a trailing comment that is only labels             `x();  // P3-3`                  -> `x();`
  R4 a comment-only line left with nothing              `// P4-5`                        -> the line goes

Anything still mentioning a label after these is reported: those are the spots where the label is part of a sentence and needs a rewrite, not a deletion (`{@code P10-2}'s control state machine`).
"""
import io
import re
import subprocess
import sys

APPLY = "--apply" in sys.argv
LABEL = r"P\d{1,2}(?:-\d{1,2})?|M-\d{1,3}|Phase\s+\d+"
WHOLE_GROUP = re.compile(r"[ \t]*\((?:\s*(?:" + LABEL + r")\s*[,/]?)+\)")
LEAD = re.compile(r"^([ \t]*(?:(?:" + LABEL + r")[ \t]*[,/]?[ \t]*)+)(?:\((?:\s*(?:" + LABEL + r")\s*[,/]?)+\)[ \t]*)?[:)]?[ \t]*")
ONLY = re.compile(r"^[ \t]*(?:(?:" + LABEL + r")[ \t]*[,/]?[ \t]*)+(?:\((?:\s*(?:" + LABEL + r")\s*[,/]?)+\))?[ \t]*$")
ANY_LABEL = re.compile(LABEL)
MARKER = re.compile(r"^(\s*(?://|/\*+|\*+/?)[ \t]?)(.*)$")


def split_line(ln, st):
    code, comment = [], []
    i, n = 0, len(ln)
    while i < n:
        if st["block"]:
            k = ln.find("*/", i)
            end = n if k < 0 else k + 2
            comment.append(ln[i:end])
            i = end
            if k >= 0:
                st["block"] = False
            continue
        if st["text"]:
            k = ln.find('"""', i)
            end = n if k < 0 else k + 3
            code.append(ln[i:end])
            i = end
            if k >= 0:
                st["text"] = False
            continue
        if ln.startswith('"""', i):
            st["text"] = True
            code.append('"""')
            i += 3
            continue
        if ln[i] in "\"'":
            q = ln[i]
            k = i + 1
            while k < n:
                if ln[k] == "\\":
                    k += 2
                    continue
                if ln[k] == q:
                    k += 1
                    break
                k += 1
            code.append(ln[i:k])
            i = k
            continue
        if ln.startswith("//", i):
            comment.append(ln[i:])
            i = n
            continue
        if ln.startswith("/*", i):
            k = ln.find("*/", i + 2)
            if k < 0:
                st["block"] = True
                comment.append(ln[i:])
                i = n
            else:
                comment.append(ln[i:k + 2])
                i = k + 2
            continue
        k = i
        while k < n and ln[k] not in "\"'/":
            k += 1
        if k == i:
            k = i + 1
        code.append(ln[i:k])
        i = k
    return "".join(code), "".join(comment)


def fix_comment(comment):
    """Apply R1-R3 to one comment chunk; returns (new_comment, rules_applied)."""
    hits = 0
    m = MARKER.match(comment)
    if not m:
        return comment, 0
    head, body = m.group(1), m.group(2)
    # keep a trailing block-comment terminator out of the rewrite
    tail = ""
    tm = re.search(r"\*/\s*$", body)
    if tm:
        tail = body[tm.start():]
        body = body[:tm.start()]
    lead = re.match(r"[ \t]*", body).group(0)
    core = body[len(lead):]
    new = WHOLE_GROUP.sub("", core)
    if new != core:
        hits += 1
        core = new
    m2 = LEAD.match(core)
    if m2 and m2.end() > 0 and core[m2.end():].strip():
        core = core[m2.end():]
        hits += 1
    if ONLY.match(core):
        core = ""
        hits += 1
    return head + lead + core + tail, hits


files = [f for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
         if f.endswith(".java") and f.startswith("src/")]
removed = 0
touched = 0
dropped = 0
residue = []
for f in files:
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    st = {"block": False, "text": False}
    out = []
    changed = 0
    for ln in text.split("\n"):
        code, comment = split_line(ln, st)
        had_label = bool(comment and ANY_LABEL.search(comment))
        if had_label:
            new, hits = fix_comment(comment)
            if hits:
                changed += hits
                comment = new
            if ANY_LABEL.search(comment):
                residue.append((f, ln.strip()[:118]))
        # a comment emptied by the sweep goes away entirely: a bare `//` marker says nothing, and a javadoc ` *`
        # continuation line is blank on purpose, so only labels can trigger this
        if had_label and MARKER.match(comment) and not MARKER.match(comment).group(2).strip():
            if code.strip() == "":
                dropped += 1
                continue
            comment = ""
        out.append((code + comment) if (code or comment) else ln)
    if changed:
        removed += changed
        touched += 1
        if APPLY:
            io.open(f, "w", encoding="utf-8", newline="").write("\n".join(out))
print("%s: %d label spots on %d files; %d empty comment lines dropped; %d lines still mention a label"
      % ("APPLIED" if APPLY else "DRY RUN", removed, touched, dropped, len(residue)))
out = ["lines still mentioning a plan label after the sweep: %d" % len(residue), ""]
out += ["  %s: %s" % (f, t.encode("ascii", "replace").decode("ascii")) for f, t in residue[:60]]
io.open("tools/_labels_left.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
