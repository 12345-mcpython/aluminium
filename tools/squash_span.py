"""Rewrite the item span into one commit per item (2026-10-02, user request: too many docs commits).

Mechanism: `git commit-tree`, NOT a rebase. Each group's tree is the tree of its NEWEST commit -- i.e. the repository
exactly as it stood when that item finished -- so the grouped commit is semantically the item, and no working-tree
operation (and no conflict resolution) is involved at all. Author/committer dates are taken from the group's newest
commit so the timeline stays readable.

\u26a0 Safety: a `backup/pre-squash` ref already points at the old head. This script only moves `refs/heads/main` at the
very end, and prints the old and new heads so the result can be checked before any push.

Groups are given as CUT LINES in the newest-first log (`window[i]` = the i-th line of `git log --pretty=format:'%h %s'`).
Each group runs from just after the previous cut down to its own cut line, so the newest commit of every group is the
one whose subject closes that item.
"""
import io
import os
import subprocess
import sys

BASE = "3965aa2d"                                  # the item-25 marker: everything after it is rewritten
OLD_HEAD = "d6631fd8"

# (cut line, inclusive, in the NEWEST-FIRST log) -> commit message
GROUPS = [
    (2, "post-52: the counter idiom, 1407's deferred-death registration, and the tbgd eidolon check"),
    (6, "item 51: 1208's eidolon two saves an ally while the zone is open (the sixth lethal-damage reader)"),
    (13, "item 50: the bloodfeud raises Max HP by a share of the CURRENT maximum (the derived spelling, measured)"),
    (17, "item 49: the bloodfeud is entered at a hundred charge and ended by a lethal blow"),
    (20, "item 48: Arlan's eidolon four survives a lethal blow at a quarter of his Max HP"),
    (23, "item 47: the talisman saves an ally who takes a lethal blow (the victim's own Max HP)"),
    (26, "item 46: Bailu heals a teammate who takes a lethal blow, at HER Max HP plus 480"),
    (30, "item 45: eidolon six lengthens the soulsteel when she lands a kill"),
    (39, "item 44: an explicit kind:all extends every buff on the unit, and the two blocked neighbours are registered"),
    (45, "item 43: a lethal blow asks the tables before it kills, for two characters"),
    (49, "item 42: the transformed form dispels its own debuffs before it strikes"),
    (55, "item 41: the transformed form is immune to control"),
    (63, "item 40: the last countdown turn ends the transformation, the state-end claim is measured, and "
         "resource-as-value is judged not worth building"),
    (71, "item 39: coreflame overflows by up to three, and the old ceiling test quotes the whole sentence"),
    (75, "item 38: being targeted grants coreflame, and an ally's skill raises her crit damage"),
    (84, "item 37: the transformed form heals after an attack"),
    (87, "item 36: the ultimate raises crit damage for three turns"),
    (90, "item 35: the transformation's stats are bound to the state, plus twenty percent res pen"),
    (93, "item 34: the transformation raises attack and max health, and 1408's sentence audit is on file"),
    (97, "item 33: the promotion dispels the control class by name"),
    (100, "item 32: the peerage raises skill crit damage by 72 percent"),
    (105, "item 31: an inserted cast cannot insert another, and it has an end (coup de main)"),
    (114, "item 30: the peerage pierces twenty percent more on skill damage, and the qi-xi spec is on file"),
    (121, "item 29: the peerage's ten percent res pen, made to stack (max_stacks is the key)"),
    (137, "item 28: 1407's new bud, one per point of HP lost, and the vocabulary measurement"),
    (145, "item 27: the cheer adds a 28 percent true-damage rider"),
    (174, "item 26: 8007's skill advances its target and lays the cheer (plus the true-damage reconnaissance)"),
]


def git(*args):
    out = subprocess.run(["git"] + list(args), capture_output=True, text=True)
    if out.returncode != 0:
        sys.stderr.write("git %s failed: %s\n" % (" ".join(args), out.stderr.strip()))
        raise SystemExit(1)
    return out.stdout


def main():
    raw = git("log", "--pretty=format:%H|%s", "%s..%s" % (BASE, OLD_HEAD)).splitlines()
    window = []                                    # newest-first
    for line in raw:
        sha, _, subject = line.partition("|")
        window.append((sha, subject))
    print("window commits: %d" % len(window))
    if len(window) < GROUPS[-1][0]:
        raise SystemExit("the window is shorter than the last group cut")

    # groups, oldest-first, each carrying the NEWEST commit of its span
    spans = []
    previous = 0
    for cut, message in GROUPS:
        members = window[previous:cut]              # newest-first inside the group
        spans.append((members[0][0], message))      # members[0] is the group's newest commit
        previous = cut
    spans.reverse()                                 # oldest group first
    print("new commits: %d" % len(spans))

    parent = BASE
    created = []
    for newest, message in spans:
        tree = git("rev-parse", "%s^{tree}" % newest).strip()
        when = git("show", "-s", "--format=%aI", newest).strip()
        msg_path = os.path.join(".git", "squash_msg.txt")
        io.open(msg_path, "w", encoding="utf-8", newline="\n").write(message + "\n")
        env = dict(os.environ, GIT_AUTHOR_DATE=when, GIT_COMMITTER_DATE=when)
        out = subprocess.run(["git", "commit-tree", tree, "-p", parent, "-F", msg_path],
                             capture_output=True, text=True, env=env)
        if out.returncode != 0:
            sys.stderr.write(out.stderr)
            raise SystemExit(1)
        parent = out.stdout.strip()
        created.append((parent, message))

    # the rewritten head must carry EXACTLY the same tree as the old head
    old_tree = git("rev-parse", "%s^{tree}" % OLD_HEAD).strip()
    new_tree = git("rev-parse", "%s^{tree}" % parent).strip()
    if old_tree != new_tree:
        sys.stderr.write("REFUSING: tree differs (%s vs %s)\n" % (old_tree, new_tree))
        raise SystemExit(1)
    print("tree identical: %s" % new_tree)

    git("update-ref", "refs/heads/main", parent, OLD_HEAD)
    print("main moved: %s -> %s" % (OLD_HEAD, parent))
    print("backup/pre-squash still points at: %s" % git("rev-parse", "backup/pre-squash").strip())
    print("new history (oldest first):")
    for sha, message in created:
        print("  %s %s" % (sha[:8], message))


main()
