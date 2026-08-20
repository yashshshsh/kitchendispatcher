import requests

URL = "http://localhost:8080/api/dispatches/eta/training-data/csv"

OUTPUT_FILE = "data/eta_training_data.csv"

print("Downloading ETA training data...")

response = requests.get(URL)

if response.status_code != 200:
    print("Failed to download dataset.")
    print("Status code:", response.status_code)
    print(response.text)
    exit(1)

with open(OUTPUT_FILE, "w", encoding="utf-8") as file:
    file.write(response.text)

print("Dataset downloaded successfully.")
print("Saved to:", OUTPUT_FILE)