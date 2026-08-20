import pandas as pd

from sklearn.ensemble import RandomForestRegressor

from sklearn.model_selection import train_test_split

from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    r2_score
)

import joblib

import numpy as np


# ============================================================
# 1. LOAD DATASET
# ============================================================

DATASET_PATH = "data/eta_training_data.csv"

print("\nLoading dataset...")

df = pd.read_csv(DATASET_PATH)

print("Dataset loaded.")
print("Rows:", len(df))


# ============================================================
# 2. DEFINE FEATURES
# ============================================================

FEATURES = [
    "estimated_preparation_time",
    "rider_to_kitchen_km",
    "kitchen_to_customer_km",
    "total_distance_km",
    "hour_of_day",
    "day_of_week"
]


TARGET = "actual_delivery_minutes"


X = df[FEATURES]

y = df[TARGET]


# ============================================================
# 3. TRAIN / TEST SPLIT
# ============================================================

print("\nSplitting dataset...")

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    random_state=42
)


print("Training rows:", len(X_train))
print("Testing rows :", len(X_test))


# ============================================================
# 4. CREATE RANDOM FOREST
# ============================================================

print("\nCreating Random Forest model...")

model = RandomForestRegressor(
    n_estimators=200,
    random_state=42,
    max_depth=None,
    min_samples_split=2,
    min_samples_leaf=1,
    n_jobs=-1
)


# ============================================================
# 5. TRAIN
# ============================================================

print("Training model...")

model.fit(
    X_train,
    y_train
)

print("Training completed.")


# ============================================================
# 6. PREDICT
# ============================================================

print("\nGenerating predictions...")

predictions = model.predict(
    X_test
)


# ============================================================
# 7. EVALUATE
# ============================================================

mae = mean_absolute_error(
    y_test,
    predictions
)


rmse = np.sqrt(
    mean_squared_error(
        y_test,
        predictions
    )
)


r2 = r2_score(
    y_test,
    predictions
)


print("\n========================================")
print("ETA MODEL RESULTS")
print("========================================")

print(
    f"MAE  : {mae:.2f} minutes"
)

print(
    f"RMSE : {rmse:.2f} minutes"
)

print(
    f"R²   : {r2:.4f}"
)


# ============================================================
# 8. FEATURE IMPORTANCE
# ============================================================

print("\n========================================")
print("FEATURE IMPORTANCE")
print("========================================")

importance = model.feature_importances_

for feature, value in sorted(
        zip(FEATURES, importance),
        key=lambda x: x[1],
        reverse=True
):

    print(
        f"{feature:35} {value:.4f}"
    )


# ============================================================
# 9. SAVE MODEL
# ============================================================

MODEL_PATH = "models/eta_random_forest.joblib"

joblib.dump(
    model,
    MODEL_PATH
)


print("\n========================================")
print("MODEL SAVED")
print("========================================")

print(
    "Saved to:",
    MODEL_PATH
)