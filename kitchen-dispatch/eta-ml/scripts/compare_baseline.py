import pandas as pd

import joblib

import numpy as np

from sklearn.model_selection import train_test_split

from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    r2_score
)


# ============================================================
# CONFIGURATION
# ============================================================

DATASET_PATH = "data/eta_training_data.csv"

MODEL_PATH = "models/eta_random_forest.joblib"


FEATURES = [
    "estimated_preparation_time",
    "rider_to_kitchen_km",
    "kitchen_to_customer_km",
    "total_distance_km",
    "hour_of_day",
    "day_of_week"
]


TARGET = "actual_delivery_minutes"


# ============================================================
# 1. LOAD DATA
# ============================================================

print("\nLoading dataset...")

df = pd.read_csv(
    DATASET_PATH
)

print(
    "Total rows:",
    len(df)
)


# ============================================================
# 2. PREPARE FEATURES
# ============================================================

X = df[FEATURES]

y = df[TARGET]


# ============================================================
# 3. USE SAME TEST SPLIT AS TRAINING
# ============================================================

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    random_state=42
)


# ============================================================
# 4. LOAD TRAINED ML MODEL
# ============================================================

print("\nLoading trained Random Forest model...")

model = joblib.load(
    MODEL_PATH
)


# ============================================================
# 5. ML PREDICTIONS
# ============================================================

print("Generating ML predictions...")

ml_predictions = model.predict(
    X_test
)


# ============================================================
# 6. BASELINE PREDICTIONS
# ============================================================

print("Generating baseline predictions...")


baseline_predictions = (
    X_test["estimated_preparation_time"]
    +
    (
        X_test["total_distance_km"]
        / 30.0
        * 60.0
    )
)


# ============================================================
# 7. CALCULATE ML METRICS
# ============================================================

ml_mae = mean_absolute_error(
    y_test,
    ml_predictions
)


ml_rmse = np.sqrt(
    mean_squared_error(
        y_test,
        ml_predictions
    )
)


ml_r2 = r2_score(
    y_test,
    ml_predictions
)


# ============================================================
# 8. CALCULATE BASELINE METRICS
# ============================================================

baseline_mae = mean_absolute_error(
    y_test,
    baseline_predictions
)


baseline_rmse = np.sqrt(
    mean_squared_error(
        y_test,
        baseline_predictions
    )
)


baseline_r2 = r2_score(
    y_test,
    baseline_predictions
)


# ============================================================
# 9. CALCULATE IMPROVEMENT
# ============================================================

mae_improvement = (
    (
        baseline_mae
        - ml_mae
    )
    /
    baseline_mae
) * 100


rmse_improvement = (
    (
        baseline_rmse
        - ml_rmse
    )
    /
    baseline_rmse
) * 100


# ============================================================
# 10. DISPLAY RESULTS
# ============================================================

print("\n")
print("================================================")
print("       BASELINE vs ML ETA COMPARISON")
print("================================================")


print("\nBASELINE ETA")
print("--------------------------------")

print(
    f"MAE  : {baseline_mae:.2f} minutes"
)

print(
    f"RMSE : {baseline_rmse:.2f} minutes"
)

print(
    f"R²   : {baseline_r2:.4f}"
)


print("\nML ETA - RANDOM FOREST")
print("--------------------------------")

print(
    f"MAE  : {ml_mae:.2f} minutes"
)

print(
    f"RMSE : {ml_rmse:.2f} minutes"
)

print(
    f"R²   : {ml_r2:.4f}"
)


print("\nIMPROVEMENT")
print("--------------------------------")

print(
    f"MAE improvement  : {mae_improvement:.2f}%"
)

print(
    f"RMSE improvement : {rmse_improvement:.2f}%"
)


print("\n================================================")


# ============================================================
# 11. WINNER
# ============================================================

if ml_mae < baseline_mae:

    print(
        "RESULT: ML ETA performs better than baseline."
    )

else:

    print(
        "RESULT: Baseline ETA performs better than ML."
    )


print("================================================")