FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# The Agricultural Risk model's pickle lives outside app/ — without this the
# container built and started fine but /v1/predict/risk-fusion 500'd on its
# first real request (file not found).
COPY models/agricultural_risk_final.pkl ./models/agricultural_risk_final.pkl
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
