"""A commanded cast can name a memosprite's skill BY SLOT (2026-10-02).

The blocker this closes, measured: 1415's ode of genesis ends with 「…德谬歌立即获得1个额外回合并<b>自动施放【花与箭的舞曲】</b>」, and commanding that cast
failed with

    "德谬歌 has no SKILL skill, so a CAST_SKILL effect has nothing to read"

because `castSkill` looks the skill up as `actor.getSkills().get(SkillType.SKILL)` -- a CHARACTER table, keyed by SkillType. A memosprite keeps its
skills in `Summon.skillsByDataSlot`, keyed by the DATA SLOT (`skillAt(int)` / `setSkillAt(int, Skill)`), and 【花与箭的舞曲】 is its slot 1.

Two facts make the narrow fix safe, both read rather than assumed:
  * `EffectSpec` already carries `@SerializedName("skill_id") private Integer skillId` -- `REPLACE_SKILL` uses it for "the data row of the
    replacement skill";
  * `CAST_SKILL`'s load-time refusal (`requireNoRowArguments`) lists damage_param / damage_level / attribute / element / amount / scale / percent
    and does NOT include skill_id, so nothing refuses it today.

So: when the actor is a memosprite, fall back to its own slot table. The existing loud error stays for the case where even that finds nothing.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

ANCHOR = """        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = actor.getSkills().get(slot);"""
BLOCK = """        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = actor.getSkills().get(slot);
        // ⭐ A MEMOSPRITE keeps its skills by DATA SLOT, not by SkillType (2026-10-02): its table is `Summon.skillsByDataSlot`, and
        // 「自动施放【花与箭的舞曲】」 is its slot 1. Commanding that cast used to fail with "has no SKILL skill" because only
        // the character table was consulted. The slot is named with `skill_id`, the same field `REPLACE_SKILL` uses for a data row.
        if (skill == null && effect.getSkillId() != null && actor instanceof Summon from) {
            skill = from.skillAt(effect.getSkillId());
        }"""

if txt.count(ANCHOR) != 1:
    sys.exit("REFUSING: the lookup anchor occurs %d times -- nothing written" % txt.count(ANCHOR))

old_error = ('                    actor.getName() + " has no " + slot + " skill, so a CAST_SKILL effect has nothing to "\n'
             '                            + "read: the rule names the skill whose numbers the commanded cast uses");')
new_error = ('                    actor.getName() + " has no " + slot + " skill"\n'
             '                            + (effect.getSkillId() == null ? "" : " and no own skill at data slot " + effect.getSkillId())\n'
             '                            + ", so a CAST_SKILL effect has nothing to read: the rule names the skill whose numbers the "\n'
             '                            + "commanded cast uses");')
if txt.count(old_error) != 1:
    sys.exit("REFUSING: the error-message anchor occurs %d times" % txt.count(old_error))

txt = txt.replace(ANCHOR, BLOCK).replace(old_error, new_error)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   the lookup falls back to the memosprite slot table, and the error names the slot it tried")
