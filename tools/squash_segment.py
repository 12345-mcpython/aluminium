"""Rewrite ONE more segment into one commit per item, and replay everything after it unchanged.

Generalises `squash_span.py`: the first version could only rewrite a segment that sat at the TIP of the branch. This
one takes a segment in the MIDDLE of history -- `BASE..SEGMENT_HEAD` -- rebuilds it as one commit per item, then replays
every commit after `SEGMENT_HEAD` on top, one group each, keeping its own tree, subject and date.

Mechanism is unchanged and still never touches the working tree: each group's tree is the tree of the group's NEWEST
commit (`git commit-tree`), so the grouped commit is exactly the repository as of that moment.

Safety:
  * a backup ref is created before anything moves;
  * the final tree is compared with the current head's tree and the rewrite is REFUSED if they differ;
  * `refs/heads/main` is moved with an expected-old-value guard.
"""
import io
import os
import subprocess
import sys

BASE = "66cc6aa2"            # the commit just before the segment: the item-23 marker
SEGMENT_HEAD = "3965aa2d"    # the item-25 marker: the segment's last commit
BACKUP = "backup/pre-squash-2"

# (cut line in the NEWEST-FIRST segment log, inclusive) -> message
SEGMENT_GROUPS = [
    (30, "item 25: 1512's memosprite panel is 70 percent HP and 180 percent speed"),
    (53, "item 24: the Aha ending's extra turn"),
]


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


def main():
    head = git("rev-parse", "HEAD").strip()
    git("rev-parse", "--verify", "%s^{commit}" % SEGMENT_HEAD)   # must resolve, or the rewrite has no end
    git("branch", "-f", BACKUP, head)
    print("backup %s -> %s" % (BACKUP, head[:8]))

    # the segment, newest-first
    raw = git("log", "--pretty=format:%H|%s", "%s..%s" % (BASE, SEGMENT_HEAD)).splitlines()
    segment = [line.partition("|")[::2] for line in raw]
    print("segment commits: %d" % len(segment))

    groups = []                                   # newest-first, (newest_commit, message)
    previous = 0
    for cut, message in SEGMENT_GROUPS:
        members = segment[previous:cut]
        if not members:
            raise SystemExit("empty group at cut %d" % cut)
        groups.append((members[0][0], message))
        previous = cut
    if previous != len(segment):
        raise SystemExit("the group table does not cover the segment (%d of %d)" % (previous, len(segment)))
    groups.reverse()                              # oldest first

    # everything after the segment keeps its own commit: one group each, same subject and date
    tail = git("log", "--reverse", "--pretty=format:%H|%s", "%s..%s" % (SEGMENT_HEAD, head)).splitlines()
    for line in tail:
        sha, _, subject = line.partition("|")
        groups.append((sha, subject))
    print("total groups to write: %d (segment %d + tail %d)" % (len(groups), len(SEGMENT_GROUPS), len(tail)))

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
    print("newest 6 commits:")
    for sha, message in written[-6:]:
        print("  %s %s" % (sha[:8], message))


main()
