import pandas as pd

import joblib


MODEL_PATH = "models/eta_random_forest.joblib"


print("Loading trained model...")

model = joblib.load(
    MODEL_PATH
)


# ============================================================
# NEW DELIVERY
# ============================================================

new_delivery = pd.DataFrame([
    {
        "estimated_preparation_time": 20,
        "rider_to_kitchen_km": 1.5,
        "kitchen_to_customer_km": 5.0,
        "total_distance_km": 6.5,
        "hour_of_day": 19,
        "day_of_week": 5
    }
])


prediction = model.predict(
    new_delivery
)


predicted_minutes = round(
    prediction[0],
    2
)


print("\n================================")
print("ETA PREDICTION")
print("================================")

print(
    "Predicted delivery time:",
    predicted_minutes,
    "minutes"
)