import json
import os


class ValueProvider:
    def __init__(self, from_json):
        self.from_json = from_json
        self.content = {}
        self.active = False

    def init(self):
        if self.active:
            print(f"This ValueProvider {self.from_json} already init!")
        if not os.path.exists(f"data/{self.from_json}"):
            print(f"Unable to find json data {self.from_json}.")
            return
        with open(f"data/{self.from_json}", encoding="utf-8") as f:
            self.content = json.load(f)
        self.active = True
        return self

    def read(self, *args):
        if not self.active:
            print(f"This ValueProvider \"{self.from_json}\" is not init.")
            return
        m = self.content
        for i in args:
            m = m[i]
        return m
