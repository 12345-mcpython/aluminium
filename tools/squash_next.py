"""Rewrite one segment into one commit per work unit, with the group table in a FILE.

Why a file: the earlier drivers carried BASE / SEGMENT_HEAD / the group table inside the script, so every segment needed an
edit to the script itself -- and one `git checkout` on that script silently threw the table away. Now the driver is fixed
and each segment supplies its own table.

Usage:  python tools/squash_next.py <base> <segment_head> <backup-ref> <groups-file>

The groups file has one line per group:  <cut line in the newest-first log>|<message>

Same mechanism as before, and the same three guards:
  * each group's tree is the tree of its NEWEST commit, replayed with `git commit-tree` -- the working tree is never touched;
  * the rewrite is REFUSED unless the final tree is byte-identical to the tree it started from;
  * `refs/heads/main` is moved with an expected-old-value guard, after a backup ref is written.
"""
import io
import os
import subprocess
import sys


def git(*args):
    # \u26a0 encoding is explicit: the platform default here is GBK, and a Chinese commit subject kills the decode.
    out = subprocess.run(["git"] + list(args), capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        sys.stderr.write("git %s failed: %s\n" % (" ".join(args), out.stderr.strip()))
        raise SystemExit(1)
    return out.stdout


def commit_tree(tree, parent, message, when):
    path = os.path.join(".git", "replay_msg.txt")
    io.open(path, "w", encoding="utf-8", newline="\n").write(message.rstrip() + "\n")
    env = dict(os.environ, GIT_AUTHOR_DATE=when, GIT_COMMITTER_DATE=when)
    out = subprocess.run(["git", "commit-tree", tree, "-p", parent, "-F", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
    if out.returncode != 0:
        sys.stderr.write(out.stderr)
        raise SystemExit(1)
    return out.stdout.strip()


def read_groups(path):
    groups = []
    for line in io.open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cut, _, message = line.partition("|")
        groups.append((int(cut), message.strip()))
    return groups


def main():
    if len(sys.argv) != 5:
        raise SystemExit(__doc__)
    base, segment_head, backup_ref, groups_file = sys.argv[1:5]
    head = git("rev-parse", "HEAD").strip()
    git("rev-parse", "--verify", "%s^{commit}" % segment_head)
    git("rev-parse", "--verify", "%s^{commit}" % base)
    git("branch", "-f", backup_ref, head)
    print("backup %s -> %s" % (backup_ref, head[:8]))

    raw = git("log", "--pretty=format:%H|%s", "%s..%s" % (base, segment_head)).splitlines()
    segment = [line.partition("|")[::2] for line in raw]
    table = read_groups(groups_file)
    print("segment commits: %d | groups: %d" % (len(segment), len(table)))

    groups = []
    previous = 0
    for cut, message in table:
        members = segment[previous:cut]
        if not members:
            raise SystemExit("empty group at cut %d" % cut)
        groups.append((members[0][0], message))
        previous = cut
    if previous != len(segment):
        raise SystemExit("the table covers %d of %d commits" % (previous, len(segment)))
    groups.reverse()

    tail = git("log", "--reverse", "--pretty=format:%H|%s", "%s..%s" % (segment_head, head)).splitlines()
    for line in tail:
        sha, _, subject = line.partition("|")
        groups.append((sha, subject))
    print("commits to write: %d (segment %d + tail %d)" % (len(groups), len(table), len(tail)))

    parent = base
    written = []
    for newest, message in groups:
        when = git("show", "-s", "--format=%aI", newest).strip()
        parent = commit_tree(git("rev-parse", "%s^{tree}" % newest).strip(), parent, message, when)
        written.append((parent, message))

    if git("rev-parse", "%s^{tree}" % parent).strip() != git("rev-parse", "%s^{tree}" % head).strip():
        raise SystemExit("REFUSING: the rewritten tree differs")
    print("tree identical: %s" % git("rev-parse", "%s^{tree}" % parent).strip())
    git("update-ref", "refs/heads/main", parent, head)
    print("main moved: %s -> %s" % (head[:8], parent[:8]))
    for sha, message in written[:len(table)]:
        print("   %s %s" % (sha[:8], message[:100]))


main()
