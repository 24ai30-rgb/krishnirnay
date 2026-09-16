FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# Both model files live outside app/ — without this the container built and
# started fine but /v1/predict/risk-fusion and /v1/predict/pest 500'd/failed
# to start on first use (file not found). Only the lightweight exported
# formats ship here — never the original .pt (needs torch/ultralytics to
# even load, which this image doesn't install) or the source .keras.
COPY models/agricultural_risk_final.pkl ./models/agricultural_risk_final.pkl
COPY models/best.onnx ./models/best.onnx
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
