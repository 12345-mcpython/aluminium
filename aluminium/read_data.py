import json

characters: list[dict] = []

attackers: list[dict] = []

# skills: list[dict] = []


def read_data():
    global characters
    if not characters:
        with open("data/characters.json", encoding="utf-8") as f:
            for i in json.load(f):
                characters.append(i)

    global attackers
    if not attackers:
        with open("data/attackers.json", encoding="utf-8") as f:
            for i in json.load(f):
                attackers.append(i)

    # global skills
    # if not skills:
    #     with open("data/skill.json", encoding="utf-8") as f:
    #         for i in json.load(f):
    #             skills.append(i)
    #
    # for character in characters:
    #     character_skills = [i for i in skills if i['name'] == character['name']]
    #     character["skills"] = {}
    #     character["skills"]["skill"] = []
    #     character["skills"]['end'] = []
    #     character["skills"]['talent'] = []
    #     for i in character_skills:
    #         if i['attack_type'] == "战技":
    #             character["skills"]["skill"].append(i)
    #         if i['attack_type'] == "终结技":
    #             character["skills"]['end'].append(i)
    #         if i['attack_type'] == "天赋":
    #             character["skills"]['talent'].append(i)
