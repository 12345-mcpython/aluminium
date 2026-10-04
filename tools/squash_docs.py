"""Fold docs-only commits into the functional commit they belong to, mechanically.

The earlier drivers grouped a window by hand, which cost a lot of reading per segment. This one needs no reading at all,
because the rule is mechanical and stated:

  * a group is one NON-docs commit followed by every docs-only commit that came after it, up to the next non-docs commit;
  * the folded docs subjects are kept as the commit BODY (one bullet each), so the record is not lost -- the subject stays
    a single clean line and `git log --oneline` reads one line per unit of work;
  * docs-only commits that sit BEFORE the first non-docs commit of the range attach to that first commit (one boundary
    inaccuracy per segment, documented here rather than hidden).

Every non-docs commit is a group of its own, so the newer, already-squashed history (whose subjects are my own) passes
through this untouched, one commit each.

Safety is the same three guards as before: backup ref first, tree compared byte-for-byte and the ref moved only if it
matches, `update-ref` with an expected old value.

Usage:  python tools/squash_docs.py <base> <backup-ref>
        (the range is base..HEAD; the tail beyond any earlier segment is preserved automatically)
"""
import io
import os
import subprocess
import sys


def git(*args):
    out = subprocess.run(["git"] + list(args), capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        sys.stderr.write("git %s failed: %s\n" % (" ".join(args), out.stderr.strip()))
        raise SystemExit(1)
    return out.stdout


def commit_tree(tree, parent, message, when):
    path = os.path.join(".git", "fold_msg.txt")
    io.open(path, "w", encoding="utf-8", newline="\n").write(message.rstrip() + "\n")
    env = dict(os.environ, GIT_AUTHOR_DATE=when, GIT_COMMITTER_DATE=when)
    out = subprocess.run(["git", "commit-tree", tree, "-p", parent, "-F", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
    if out.returncode != 0:
        sys.stderr.write(out.stderr)
        raise SystemExit(1)
    return out.stdout.strip()


def is_docs(subject):
    return subject.startswith("docs:") or subject.startswith("docs+") or subject.startswith("docs ")


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    base, backup_ref = sys.argv[1:3]
    head = git("rev-parse", "HEAD").strip()
    git("rev-parse", "--verify", "%s^{commit}" % base)
    git("branch", "-f", backup_ref, head)
    print("backup %s -> %s" % (backup_ref, head[:8]))

    raw = git("log", "--reverse", "--pretty=format:%H|%s", "%s..%s" % (base, head)).splitlines()
    commits = [line.partition("|")[::2] for line in raw]
    print("commits in range: %d" % len(commits))

    # build groups: (anchor_sha, [folded docs subjects])
    groups = []
    waiting = []                      # docs seen before any anchor in this range
    for sha, subject in commits:
        if is_docs(subject):
            if groups:
                groups[-1][1].append(subject)
            else:
                waiting.append(subject)
            continue
        groups.append((sha, list(waiting)))
        waiting = []
    if waiting:
        # every commit in the range was docs: keep them as one group under the first one
        if not groups and commits:
            groups.append((commits[-1][0], waiting))
        else:
            groups[-1][1].extend(waiting)
    folded = sum(len(extra) for _, extra in groups)
    print("groups: %d | docs folded: %d" % (len(groups), folded))

    parent = base
    written = 0
    for sha, extra in groups:
        subject = git("show", "-s", "--format=%s", sha).strip()
        body = "".join("\n* " + line for line in extra)
        when = git("show", "-s", "--format=%aI", sha).strip()
        message = subject + ("\n" + body if body else "")
        parent = commit_tree(git("rev-parse", "%s^{tree}" % sha).strip(), parent, message, when)
        written += 1

    if git("rev-parse", "%s^{tree}" % parent).strip() != git("rev-parse", "%s^{tree}" % head).strip():
        raise SystemExit("REFUSING: the rewritten tree differs")
    print("tree identical: %s" % git("rev-parse", "%s^{tree}" % parent).strip())
    git("update-ref", "refs/heads/main", parent, head)
    print("main moved: %s -> %s (%d commits written)" % (head[:8], parent[:8], written))


main()
