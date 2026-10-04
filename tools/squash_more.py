"""Clean several small segments in a row, three items each, verifying every step.

Same per-item replay as `squash_by_markers.py` (group tree = the tree of its newest commit, `git commit-tree`, the
working tree is never touched), driven in a loop:

  * the marker list is read from `backup/pre-squash`, i.e. the ORIGINAL history, so the boundaries are stable: the
    commits a segment is cut AT are always older than the frontier and therefore never rewritten, and their hashes stay
    valid from round to round;
  * each round rewrites `markers[i] .. markers[i+K]` -- whole items only, because both ends are item markers -- and
    replays everything newer unchanged;
  * every round refuses to move `main` unless its final tree is byte-identical to the tree it started from, so a bad
    grouping can only ever stop the loop, never corrupt content;
  * one backup ref is written before the first round.
"""
import io
import os
import re
import subprocess
import sys

ORIGINAL = "backup/pre-squash"      # the untouched pre-cleanup head
START_FRONTIER = "05b38cdd"         # the item-20 marker: rewriting happens strictly below it
ITEMS_PER_SEGMENT = 3
ROUNDS = 5
BACKUP = "backup/pre-more"
MARKER = re.compile(r"\bitem ships\b")


def git(*args, check=True):
    # \u26a0 encoding="utf-8" explicitly: the platform default here is GBK, and a commit subject containing Chinese kills the
    # decode with UnicodeDecodeError (measured -- it is what stopped this driver's first run).
    out = subprocess.run(["git"] + list(args), capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if check and out.returncode != 0:
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


def message_of(subject):
    text = subject.replace("docs: ", "", 1)
    text = text.replace(" item ships, ", " item: ").replace(" item ships", " item")
    return text


def markers():
    raw = git("log", "--pretty=format:%H|%s", ORIGINAL).splitlines()
    return [(line.partition("|")[0], line.partition("|")[2]) for line in raw if MARKER.search(line)]


def rewrite(segment_head, base, head):
    raw = git("log", "--pretty=format:%H|%s", "%s..%s" % (base, segment_head)).splitlines()
    segment = [(line.partition("|")[0], line.partition("|")[2]) for line in raw]
    found = [i for i, (_, subject) in enumerate(segment) if MARKER.search(subject)]
    if not found or found[0] != 0:
        raise SystemExit("segment does not start at an item marker")
    if found[-1] != len(segment) - len(segment[found[-1]:]) and found[-1] == 0 and len(found) == 1:
        pass
    cuts = [found[i + 1] - 1 for i in range(len(found) - 1)] + [len(segment) - 1]
    groups = [(segment[found[i]][0], message_of(segment[found[i]][1])) for i in range(len(found))]
    groups.reverse()

    tail = git("log", "--reverse", "--pretty=format:%H|%s", "%s..%s" % (segment_head, head)).splitlines()
    written = []
    parent = base
    for newest, message in groups:
        when = git("show", "-s", "--format=%aI", newest).strip()
        parent = commit_tree(git("rev-parse", "%s^{tree}" % newest).strip(), parent, message, when)
        written.append(message)
    for line in tail:
        sha, _, subject = line.partition("|")
        when = git("show", "-s", "--format=%aI", sha).strip()
        parent = commit_tree(git("rev-parse", "%s^{tree}" % sha).strip(), parent, subject, when)

    if git("rev-parse", "%s^{tree}" % parent).strip() != git("rev-parse", "%s^{tree}" % head).strip():
        raise SystemExit("REFUSING: the rewritten tree differs")
    git("update-ref", "refs/heads/main", parent, head)
    return parent, [m for m in written], len(segment), len(groups)


def main():
    marker_list = markers()
    print("markers found in the original history: %d" % len(marker_list))
    for index, (sha, subject) in enumerate(marker_list[:40]):
        print("   [%2d] %s %s" % (index, sha[:8], subject))
    tries = [i for i, (sha, _) in enumerate(marker_list) if sha.startswith(START_FRONTIER[:8])]
    if not tries:
        raise SystemExit("the start frontier is not an item marker")
    frontier_index = tries[0]
    head = git("rev-parse", "HEAD").strip()
    git("branch", "-f", BACKUP, head)
    print("backup %s -> %s" % (BACKUP, head[:8]))

    for round_number in range(1, ROUNDS + 1):
        # \u26a0 The marker chain ENDS (here: at the nineteenth item), so a segment may have fewer than K whole items left. Take
        # what is left rather than stopping: the base stays a marker either way, which is what keeps every group a whole item.
        remaining = len(marker_list) - 1 - frontier_index
        if remaining < 1:
            print("round %d: the marker chain ends here, so there is no whole segment left" % round_number)
            break
        step = min(ITEMS_PER_SEGMENT, remaining)
        segment_head = marker_list[frontier_index][0]
        base = marker_list[frontier_index + step][0]
        head = git("rev-parse", "HEAD").strip()
        new_head, messages, seg_size, group_count = rewrite(segment_head, base, head)
        print("round %d: %s..%s -> %d commits became %d | %s -> %s" %
              (round_number, base[:8], segment_head[:8], seg_size, group_count, head[:8], new_head[:8]))
        for message in messages:
            print("     %s" % message)
        frontier_index += step

    print("final head: %s" % git("rev-parse", "HEAD").strip())
    print("backup still at: %s" % git("rev-parse", BACKUP).strip())
    print("original head still reachable: %s" % git("rev-parse", ORIGINAL).strip())


main()
