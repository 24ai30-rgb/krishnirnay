# Model 1 — irrigation/water-stress (ONNX)

`model1_irrigation_rf.onnx` is **not present in this repo** — no trained model file was provided during the build described in the project plan. `OnnxModelRunner` handles this gracefully: it tries to load the asset, and if it's missing (or fails to load for any reason), `IrrigationRiskModel.predict()` returns `null`, and `DecisionEngine` falls back to its rule-based soil-moisture threshold instead. The app is fully functional without this file.

To wire up the real model:

1. Drop the trained `.onnx` file in this folder as `model1_irrigation_rf.onnx`.
2. Confirm its actual input feature order/scaling and output shape against `model1_metadata.json`'s placeholder contract — if it doesn't match `[soilMoisturePct, temperatureC, humidityPct, delayHours] -> [LOW, MEDIUM, HIGH] probabilities`, update `IrrigationRiskModel.kt` accordingly (that's the only file that assumes this contract).
3. Update `model1_metadata.json`'s `model_file_present` to `true` and correct the contract fields to match reality.
