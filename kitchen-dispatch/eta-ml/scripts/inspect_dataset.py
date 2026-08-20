import pandas as pd

FILE = "data/eta_training_data.csv"

df = pd.read_csv(FILE)

print("\n==============================")
print("ETA DATASET")
print("==============================")

print("Rows:", len(df))
print("Columns:", len(df.columns))

print("\nColumns:")
for column in df.columns:
    print("-", column)

print("\nFirst 5 rows:")
print(df.head())

print("\nMissing values:")
print(df.isnull().sum())

print("\nDataset statistics:")
print(df.describe())