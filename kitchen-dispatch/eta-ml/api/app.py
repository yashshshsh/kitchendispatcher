from pathlib import Path

import joblib
import pandas as pd

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


# ============================================================
# PATHS
# ============================================================

BASE_DIR = Path(__file__).resolve().parent.parent

MODEL_PATH = (
    BASE_DIR
    / "models"
    / "eta_random_forest.joblib"
)


# ============================================================
# LOAD MODEL
# ============================================================

print("Loading ETA Random Forest model...")

try:
    model = joblib.load(MODEL_PATH)

except Exception as e:
    raise RuntimeError(
        f"Unable to load ETA model from {MODEL_PATH}: {e}"
    )

print("ETA model loaded successfully.")


# ============================================================
# FASTAPI APPLICATION
# ============================================================

app = FastAPI(
    title="Kitchen Dispatcher ETA ML Service",
    description="Machine Learning service for delivery ETA prediction",
    version="1.0.0"
)


# ============================================================
# REQUEST MODEL
# ============================================================

class ETAPredictionRequest(BaseModel):

    estimatedPreparationTime: int = Field(
        ...,
        gt=0,
        description="Estimated food preparation time in minutes"
    )

    riderToKitchenDistanceKm: float = Field(
        ...,
        ge=0,
        description="Distance between rider and kitchen"
    )

    kitchenToCustomerDistanceKm: float = Field(
        ...,
        ge=0,
        description="Distance between kitchen and customer"
    )

    totalDistanceKm: float = Field(
        ...,
        ge=0,
        description="Total delivery distance"
    )

    hourOfDay: int = Field(
        ...,
        ge=0,
        le=23,
        description="Hour of the day"
    )

    dayOfWeek: int = Field(
        ...,
        ge=0,
        le=6,
        description="Day of week where Monday = 0"
    )


# ============================================================
# RESPONSE MODEL
# ============================================================

class ETAPredictionResponse(BaseModel):

    predictedDeliveryMinutes: float


# ============================================================
# HEALTH CHECK
# ============================================================

@app.get("/health")
def health():

    return {
        "status": "UP",
        "service": "eta-ml",
        "model": "RandomForestRegressor"
    }


# ============================================================
# ETA PREDICTION
# ============================================================

@app.post(
    "/predict",
    response_model=ETAPredictionResponse
)
def predict_eta(
        request: ETAPredictionRequest
):

    try:

        input_data = pd.DataFrame([
            {
                "estimated_preparation_time":
                    request.estimatedPreparationTime,

                "rider_to_kitchen_km":
                    request.riderToKitchenDistanceKm,

                "kitchen_to_customer_km":
                    request.kitchenToCustomerDistanceKm,

                "total_distance_km":
                    request.totalDistanceKm,

                "hour_of_day":
                    request.hourOfDay,

                "day_of_week":
                    request.dayOfWeek
            }
        ])


        prediction = model.predict(
            input_data
        )


        predicted_minutes = round(
            float(prediction[0]),
            2
        )


        if predicted_minutes < 0:

            predicted_minutes = 0.0


        return ETAPredictionResponse(
            predictedDeliveryMinutes=
                predicted_minutes
        )


    except Exception as e:

        raise HTTPException(
            status_code=500,
            detail=f"ETA prediction failed: {str(e)}"
        )


# ============================================================
# ROOT
# ============================================================

@app.get("/")
def root():

    return {
        "service": "Kitchen Dispatcher ETA ML",
        "status": "running",
        "endpoints": [
            "GET /health",
            "POST /predict"
        ]
    }