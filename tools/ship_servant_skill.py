import io, json, sys

# 1) the memosprite's own file gains the servant id and its skill row (level 10 = the row's 最高等级)
P = "src/main/resources/memosprites/1415.json"
d = json.load(io.open(P, encoding="utf-8"))
d["servant_id"] = 11415
d["skills"] = [{"slot": 8, "level": 10}]
d["note"] = (d.get("note", "") +
    " || 2026-10-02: servant_id = 11415 and one skill row (slot 8, level 10). A memosprite's skills are ordinary skills: the engine"
    " addresses them by (cid, slot) with cid = the SERVANT id, which is what makes skill_effects.json able to hold an entry for them.")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   memosprites/1415.json: servant_id + skills")

# 2) the delivery table gains the entry, shaped exactly like 1412's Rules entry
P2 = "src/main/resources/data/skill_effects.json"
t = json.load(io.open(P2, encoding="utf-8"))
if "11415" in t and "8" in t["11415"]:
    sys.exit("already there")
t["11415"] = {"8": {
    "effect": "Rules",
    "source": "1415 昔涟's memosprite 德谬歌, 忆灵技能 8 'Ode to Strife' (tbgd: ServantID 11415, 效果 辅助, 效果ID [10000001, 10000011]): "
              "the skill itself delivers nothing -- its work is done by the rule table (dispel control-class debuffs on Mydei, command "
              "him to cast, or advance him), exactly the shape 1412's 奇袭 needed.",
    "note": "The third kind (`Rules`) is what a skill whose work is elsewhere uses; it has no parameter row to be ambiguous about. "
            "Keyed by the SERVANT's cid and the data slot, because SkillEffects.forSkill keys on the SKILL's own cid and slot."
}}
io.open(P2, "w", encoding="utf-8", newline="\n").write(json.dumps(t, ensure_ascii=False, indent=2) + "\n")
print("ok   skill_effects.json: 11415 slot 8 = Rules")
