import requests

uid = input("Enter the uid to dump Enhance json (No weapon character will ignore): ")

# fuck code, fuck API

r = requests.get("https://avocado.wiki/v1/raw/info/" + uid)

print(r.json())
