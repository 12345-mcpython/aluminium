"""Rewrite one more segment, splitting it automatically at the item markers.

Same replay machinery as `squash_segment.py` (group tree = the tree of the group's newest commit, `git commit-tree`,
nothing touches the working tree), but the groups are now DERIVED instead of hand-written:

  * every commit whose subject matches `<ordinal> item ships` CLOSES an item, so a group runs from that marker
    backwards to the commit just before the next (older) marker;
  * each group's message is built from its own marker subject -- 「docs: the twenty-third item ships, the transformation
    countdown」 becomes 「twenty-third item: the transformation countdown」 -- so the message states what that item was,
    taken from the record itself rather than paraphrased by hand;
  * the segment must START just after an earlier marker, which is what makes every group a whole item.

Everything after the segment is replayed unchanged (tree, subject, date). The rewrite is refused unless the final tree
is byte-identical to the current head's tree.
"""
import io
import os
import re
import subprocess
import sys

BASE = "05b38cdd"            # the item-20 marker: the commit just before this segment
SEGMENT_HEAD = "66cc6aa2"    # the item-23 marker: the segment's last commit
BACKUP = "backup/pre-squash-3"
MARKER = re.compile(r"\bitem ships\b")


def git(*args):
    out = subprocess.run(["git"] + list(args), capture_output=True, text=True)
    if out.returncode != 0:
        sys.stderr.write("git %s failed: %s\n" % (" ".join(args), out.stderr.strip()))
        raise SystemExit(1)
    return out.stdout


def commit_tree(tree, parent, message, when):
    path = os.path.join(".git", "replay_msg.txt")
    io.open(path, "w", encoding="utf-8", newline="\n").write(message.rstrip() + "\n")
    env = dict(os.environ, GIT_AUTHOR_DATE=when, GIT_COMMITTER_DATE=when)
    out = subprocess.run(["git", "commit-tree", tree, "-p", parent, "-F", path],
                         capture_output=True, text=True, env=env)
    if out.returncode != 0:
        sys.stderr.write(out.stderr)
        raise SystemExit(1)
    return out.stdout.strip()


def message_of(subject):
    text = subject.replace("docs: ", "", 1)
    text = text.replace(" item ships, ", ": ").replace(" item ships", "")
    return text


def groups_of(segment):
    """segment is newest-first [(sha, subject)]; returns newest-first [(sha, message)]."""
    markers = [i for i, (_, subject) in enumerate(segment) if MARKER.search(subject)]
    if not markers:
        raise SystemExit("no item marker in the segment")
    if markers[0] != 0:
        raise SystemExit("the newest commit of the segment is not an item marker")
    cuts = []
    for next_marker in markers[1:]:
        cuts.append(next_marker - 1)               # 0-based index of the last commit of the group above it
    cuts.append(len(segment) - 1)
    out = []
    for cut, marker_index in zip(cuts, markers):
        out.append((segment[marker_index][0], message_of(segment[marker_index][1])))
    return out, [c + 1 for c in cuts]


def main():
    head = git("rev-parse", "HEAD").strip()
    git("rev-parse", "--verify", "%s^{commit}" % SEGMENT_HEAD)
    git("branch", "-f", BACKUP, head)
    print("backup %s -> %s" % (BACKUP, head[:8]))

    raw = git("log", "--pretty=format:%H|%s", "%s..%s" % (BASE, SEGMENT_HEAD)).splitlines()
    segment = [line.partition("|")[::2] for line in raw]
    print("segment commits: %d" % len(segment))

    seg_groups, cuts = groups_of(segment)
    print("derived groups: %d at cuts %s" % (len(seg_groups), cuts))
    for sha, message in seg_groups:
        print("   from %s -> %s" % (sha[:8], message))

    groups = list(reversed(seg_groups))            # oldest first
    tail = git("log", "--reverse", "--pretty=format:%H|%s", "%s..%s" % (SEGMENT_HEAD, head)).splitlines()
    for line in tail:
        sha, _, subject = line.partition("|")
        groups.append((sha, subject))
    print("total groups to write: %d (segment %d + tail %d)" % (len(groups), len(seg_groups), len(tail)))

    parent = BASE
    written = []
    for newest, message in groups:
        tree = git("rev-parse", "%s^{tree}" % newest).strip()
        when = git("show", "-s", "--format=%aI", newest).strip()
        parent = commit_tree(tree, parent, message, when)
        written.append((parent, message))

    old_tree = git("rev-parse", "%s^{tree}" % head).strip()
    new_tree = git("rev-parse", "%s^{tree}" % parent).strip()
    if old_tree != new_tree:
        sys.stderr.write("REFUSING: tree differs (%s vs %s)\n" % (old_tree, new_tree))
        raise SystemExit(1)
    print("tree identical: %s" % new_tree)
    git("update-ref", "refs/heads/main", parent, head)
    print("main moved: %s -> %s" % (head[:8], parent[:8]))


main()
