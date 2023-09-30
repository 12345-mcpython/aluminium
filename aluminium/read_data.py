import json

characters: list[dict] = []

attackers: list[dict] = []


def read_datas():
    global characters
    if not characters:
        with open("data/characters.json", encoding="utf-8") as f:
            character_json = json.load(f)
            # name mt level health defensive attack
            for i in character_json:
                characters.append(i)

    global attackers
    if not attackers:
        with open("data/attackers.json", encoding="utf-8") as f:
            attacker_json = json.load(f)
            for i in attacker_json:
                attackers.append(i)
