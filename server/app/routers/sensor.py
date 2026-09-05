from fastapi import APIRouter

router = APIRouter(
    prefix="/api",
    tags=["sensor"],
)


@router.get("/latest-sensor")
async def get_latest_sensor():
    """
    Latest ESP32 sensor values.

    Temporary values are used until the ESP32
    sensor endpoint is connected.
    """

    return {
        "data": {
            "temperature": 25.0,
            "humidity": 60.0,
            "soil_moisture": 50.0,
            "timestamp": None,
        }
    }