"""Every `TargetType` the game's abilities use, by value (2026-10-02).

Written because a question kept coming back: does the game express "the enemy that has just entered" as a target type? This answers the whole
family at once instead of grepping one file at a time.

Measured: 1519 files under `Config/ConfigAbility`, 18 distinct values -- Caster, SkillTargetEntityList, InherentTargetEntity, AllEnemy,
TeamFormation, AbilityTargetEntity, DarkTeamCenter, EnemyTeamCenter, AllDarkTeamMember, LightTeamCenter, AllTeamMember, AllLightTeamMember,
CustomTarget, Warning, SkillPointEntity, StanceBreakTargetEntity, FriendSelect, FriendServantSelect. None of them is about arrival, so
「新入场」 is not spelled that way; spot-checking `CustomTarget` / `InherentTargetEntity` (12 sites) found no arrival filter either.
"""
import collections
import io
import os
import re

ROOT = r"E:\turnbasedgamedata\Config\ConfigAbility"
pat = re.compile(r'"TargetType"\s*:\s*"([^"]+)"')

values = collections.Counter()
files = 0
for dirpath, _dirs, names in os.walk(ROOT):
    for n in names:
        if not n.endswith(".json") or "layout" in n:
            continue
        try:
            raw = open(os.path.join(dirpath, n), encoding="utf-8", errors="replace").read()
        except Exception:
            continue
        files += 1
        for v in pat.findall(raw):
            values[v] += 1

print("files scanned: %d ; distinct TargetType values: %d" % (files, len(values)))
for k, c in values.most_common():
    print("  %-42s %d" % (k, c))
print("")
print("any value about arrival: %s" % [k for k in values if re.search("new|enter|arriv|insert", k, re.I)])
