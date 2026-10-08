import asyncio
from contextlib import asynccontextmanager
import socket
import threading
import time

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from app.config import get_settings
from app.routers import chat, disease, health, ivr, local_llm, market, pest, risk_fusion, weather
from app.services import gemini_proxy, local_llm_service


# =========================================================
# SENSOR DATA MODEL
# =========================================================

class SensorData(BaseModel):
    temperature: float
    humidity: float
    soil_moisture: float


class SensorResponse(BaseModel):
    data: SensorData | None = None


# =========================================================
# LATEST SENSOR DATA
# =========================================================

_latest_sensor: SensorData | None = None


# =========================================================
# UDP AUTO DISCOVERY
# =========================================================

DISCOVERY_PORT = 4210
DISCOVERY_MESSAGE = "KRISHINIRNAY_DISCOVER"
DISCOVERY_RESPONSE_PREFIX = "KRISHINIRNAY_SERVER:"


_discovery_socket = None
_discovery_thread = None
_discovery_stop_event = threading.Event()


# =========================================================
# GET CURRENT WIFI IP
# =========================================================

def get_local_ip() -> str | None:

    sock = socket.socket(
        socket.AF_INET,
        socket.SOCK_DGRAM,
    )

    try:

        # Used only to determine active network interface.
        sock.connect(
            ("8.8.8.8", 80)
        )

        return sock.getsockname()[0]

    except Exception as error:

        print(
            f"Could not determine local IP: {error}"
        )

        return None

    finally:

        sock.close()


# =========================================================
# UDP DISCOVERY SERVER
# =========================================================

def discovery_worker():

    global _discovery_socket

    try:

        # -------------------------------------------------
        # UDP SOCKET
        # -------------------------------------------------

        sock = socket.socket(
            socket.AF_INET,
            socket.SOCK_DGRAM,
        )

        sock.setsockopt(
            socket.SOL_SOCKET,
            socket.SO_REUSEADDR,
            1,
        )

        sock.settimeout(1.0)

        sock.bind(
            ("0.0.0.0", DISCOVERY_PORT)
        )

        _discovery_socket = sock

        print(
            f"UDP discovery listening on port "
            f"{DISCOVERY_PORT}"
        )

        # -------------------------------------------------
        # LISTEN
        # -------------------------------------------------

        while not _discovery_stop_event.is_set():

            try:

                data, client_address = sock.recvfrom(
                    1024
                )

            except socket.timeout:

                continue

            except OSError:

                break

            message = data.decode(
                "utf-8",
                errors="ignore",
            ).strip()

            print(
                f"UDP discovery request from "
                f"{client_address}: {message}"
            )

            # -------------------------------------------------
            # CHECK DISCOVERY REQUEST
            # -------------------------------------------------

            if message != DISCOVERY_MESSAGE:

                continue

            # -------------------------------------------------
            # FIND CURRENT IP
            # -------------------------------------------------

            local_ip = get_local_ip()

            if not local_ip:

                print(
                    "Discovery request received but "
                    "local IP could not be detected."
                )

                continue

            # -------------------------------------------------
            # SEND SERVER IP
            # -------------------------------------------------

            response = (
                f"{DISCOVERY_RESPONSE_PREFIX}"
                f"{local_ip}:8000"
            )

            try:

                sock.sendto(
                    response.encode("utf-8"),
                    client_address,
                )

                print(
                    f"Discovery response sent: "
                    f"{response}"
                )

            except Exception as error:

                print(
                    f"Discovery response failed: {error}"
                )

    except Exception as error:

        print()
        print(
            "=========================================="
        )
        print(
            "UDP DISCOVERY STARTUP FAILED"
        )
        print(
            "=========================================="
        )
        print(
            str(error)
        )
        print(
            "FastAPI will continue normally."
        )
        print(
            "=========================================="
        )
        print()

    finally:

        if _discovery_socket:

            try:
                _discovery_socket.close()
            except Exception:
                pass

            _discovery_socket = None


# =========================================================
# START UDP DISCOVERY
# =========================================================

def start_discovery_server():

    global _discovery_thread

    _discovery_stop_event.clear()

    _discovery_thread = threading.Thread(
        target=discovery_worker,
        daemon=True,
        name="KrishiNirnay-UDP-Discovery",
    )

    _discovery_thread.start()


# =========================================================
# STOP UDP DISCOVERY
# =========================================================

def stop_discovery_server():

    _discovery_stop_event.set()

    global _discovery_socket

    if _discovery_socket:

        try:
            _discovery_socket.close()
        except Exception:
            pass

        _discovery_socket = None


# =========================================================
# FASTAPI LIFESPAN
# =========================================================

@asynccontextmanager
async def lifespan(app: FastAPI):

    # =====================================================
    # LOADED MODELS (for /health only)
    # =====================================================
    # The real Disease model is loaded once, lazily by
    # app.services.disease_ai.disease_model at import time; it used to also
    # be loaded a second time here via a separate DiseaseModel() instance
    # (app.services.disease_model) whose only consumer was this list — i.e.
    # the same ~21MB Keras model held in memory twice for no behavioral
    # reason. Removed: this is just a version label, not a live handle.

    app.state.models_loaded = [
        "disease-v1",
        "agricultural-risk-v1",
    ]


    # =====================================================
    # CURRENT NETWORK IP
    # =====================================================

    local_ip = get_local_ip()


    # =====================================================
    # START UDP DISCOVERY
    # =====================================================

    start_discovery_server()

    # =====================================================
    # PRELOAD THE LOCAL LLM (background — never blocks startup)
    # =====================================================
    # Without this the first question after startup (or after Ollama's idle
    # unload) waits ~80s for the model to load. warm_up() never raises.

    warm_up_task = asyncio.create_task(local_llm_service.warm_up())


    # =====================================================
    # SERVER INFORMATION
    # =====================================================

    print()
    print(
        "=================================================="
    )
    print(
        "       KRISHINIRNAY FASTAPI SERVER"
    )
    print(
        "=================================================="
    )

    print(
        f"Local IP       : {local_ip}"
    )

    print(
        "HTTP Port      : 8000"
    )

    print(
        f"UDP Discovery  : {DISCOVERY_PORT}"
    )

    print(
        "Discovery Name : KRISHINIRNAY_DISCOVER"
    )

    print()
    print(
        "ESP32 can automatically discover this server."
    )

    print(
        "Sensor API     : POST /api/sensor-data"
    )

    print(
        "Latest Sensor  : GET /api/latest-sensor"
    )

    print(
        "=================================================="
    )
    print()


    try:

        yield

    finally:

        # =================================================
        # STOP UDP DISCOVERY
        # =================================================

        stop_discovery_server()

        # =================================================
        # CLOSE GEMINI
        # =================================================

        await gemini_proxy.close_client()


# =========================================================
# CREATE FASTAPI APP
# =========================================================

def create_app() -> FastAPI:

    settings = get_settings()

    app = FastAPI(
        title="KrishiNirnay Inference Server",
        version="0.1.0",
        lifespan=lifespan,
    )


    # =====================================================
    # CORS
    # =====================================================

    origins = [
        origin.strip()
        for origin in settings.cors_origins.split(",")
        if origin.strip()
    ]

    if origins:

        app.add_middleware(
            CORSMiddleware,
            allow_origins=origins,
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )


    # =====================================================
    # EXISTING ROUTERS
    # =====================================================

    app.include_router(
        health.router,
        prefix="",
    )

    app.include_router(
        disease.router
    )

    app.include_router(
        chat.router
    )

    app.include_router(
        pest.router
    )

    app.include_router(
        risk_fusion.router
    )

    app.include_router(
        weather.router
    )

    app.include_router(
        market.router
    )

    app.include_router(
        local_llm.router
    )

    app.include_router(
        ivr.router
    )


    # =====================================================
    # ESP32 → FASTAPI
    # =====================================================

    @app.post(
        "/api/sensor-data"
    )
    async def receive_sensor_data(
        sensor: SensorData,
    ):

        global _latest_sensor

        _latest_sensor = sensor

        print()
        print(
            "=========================================="
        )
        print(
            "      ESP32 SENSOR DATA RECEIVED"
        )
        print(
            "=========================================="
        )

        print(
            f"Temperature : {sensor.temperature} °C"
        )

        print(
            f"Humidity    : {sensor.humidity} %"
        )

        print(
            f"Soil Moist. : {sensor.soil_moisture} %"
        )

        print(
            "=========================================="
        )
        print()

        return {
            "status": "ok",
            "message": "Sensor data received",
            "data": sensor.model_dump(),
        }


    # =====================================================
    # ANDROID → LATEST SENSOR
    # =====================================================

    @app.get(
        "/api/latest-sensor"
    )
    async def get_latest_sensor():

        return {
            "data": (
                _latest_sensor.model_dump()
                if _latest_sensor is not None
                else None
            )
        }


    return app


# =========================================================
# APPLICATION INSTANCE
# =========================================================

app = create_app()