# KRISHINIRNAY — Phase 3B Implementation Report

Scope: fix the `DashboardViewModel` risk-masking bug identified in the Phase 3 audit. No other system touched.

---

## 1. Root Cause

`FieldState.toDashboardUiState()` (private helper in `DashboardViewModel.kt`) computes the dashboard's `overallRisk` via a priority `when` chain that blends three signals: sensor-critical thresholds, the separate ML risk-fusion classifier (`mlRisk`, from `RiskRepository.predictRisk`), and `decision.overallRisk` (the pure-Kotlin `DecisionEngine`'s own output).

Before this fix, the chain was:

```
criticalWaterRisk -> HIGH
extremeHeat -> HIGH
mlRisk == HIGH && confident -> HIGH
mediumWaterRisk && mlRisk != LOW -> MEDIUM
mediumHeat && mlRisk == LOW -> MEDIUM
mlRisk != null -> mlRisk        // <-- fires whenever ML has ever returned a value
else -> decision.overallRisk    // <-- decision.overallRisk only reached if mlRisk is null
```

Since Phase 3A, `decision.overallRisk` already correctly fuses pest risk, disease risk, region rules, and rain outlook (via `FieldDecisionResolver` → `DecisionEngine.evaluate`). But the `mlRisk != null -> mlRisk` branch sits *before* `decision.overallRisk` is ever consulted. Whenever the ML classifier had returned any value at all — including a plain `LOW` or `MEDIUM` — that branch fired first and `decision.overallRisk` was never read, even when it correctly said `HIGH` because of a detected pest or disease. The engine's real HIGH was silently discarded in favor of the ML class.

## 2. Files Inspected

- `app/src/main/java/com/krishinirnay/feature/dashboard/DashboardViewModel.kt` — the risk-selection logic and its `toDashboardUiState` conversion.
- `app/src/main/java/com/krishinirnay/feature/dashboard/DashboardUiState.kt` — confirmed `overallRisk` is the field the Dashboard UI actually renders.
- `app/src/main/java/com/krishinirnay/core/decision/DecisionEngine.kt`, `DecisionOutcome.kt` — confirmed `DecisionOutput.overallRisk` already correctly includes pest/disease/region/rain since Phase 3A (`maxRisk(listOf(waterStressRisk, heatRisk, cropHealthRisk, pestRisk))`).
- `app/src/main/java/com/krishinirnay/core/data/composite/FieldDecisionResolver.kt` — confirmed unchanged; Phase 3A's wiring is the thing whose output was being masked.
- `app/src/test/java/com/krishinirnay/data/*Test.kt`, `decision/DecisionEngineTest.kt` — the existing Phase 3A test suite, to confirm nothing there depends on `DashboardViewModel`'s internals and nothing needed updating.

## 3. Files Modified

- **`app/src/main/java/com/krishinirnay/feature/dashboard/DashboardViewModel.kt`** — inserted one new branch in the `overallRisk` `when` chain: `decision.overallRisk == RiskLevel.HIGH -> RiskLevel.HIGH`, placed immediately after the sensor-critical checks and before every ML-only branch. Also changed `toDashboardUiState` from `private` to `internal` so it can be exercised directly from a test without duplicating its logic.
- **`app/src/test/java/com/krishinirnay/feature/dashboard/DashboardViewModelTest.kt`** (new) — 6 regression tests, detailed below.

No other file was touched. `DecisionEngine`, `FieldDecisionResolver`, `RegionCropRuleRegistry`, the Mock/Live repositories, sensor DTOs, Retrofit contracts, and the ESP32/FastAPI pipeline are all unchanged from Phase 3A.

## 4. Exact Behavior Before the Fix

Given sensors in the healthy range (no sensor-critical branch fires) and an ML classifier result of `LOW` or `MEDIUM`:

- `decision.overallRisk = HIGH` (e.g. from a detected pest at a vulnerable crop stage) → Dashboard showed **LOW/MEDIUM** (whatever the ML class was). The real HIGH was invisible to the farmer.

## 5. Exact Behavior After the Fix

Same inputs:

- `decision.overallRisk = HIGH`, regardless of what the ML classifier returned (or whether it returned anything at all) → Dashboard shows **HIGH**.
- `decision.overallRisk = LOW`/`MEDIUM` with a corresponding ML result → unchanged fusion behavior (ML and sensor-based blending for non-HIGH cases is untouched).
- No ML result yet (`apiRisk = null`) → falls through to `decision.overallRisk` exactly as before, for both HIGH and non-HIGH cases.

## 6. Tests Added (`DashboardViewModelTest.kt`, 6 tests)

| Test | Proves |
|---|---|
| `a HIGH decision is never masked by an ML risk of LOW` | Required Test 1 exactly (ML=LOW, Decision=HIGH → HIGH) |
| `a pest-driven HIGH decision is never masked by an ML risk of MEDIUM` | Required Test 2 exactly (ML=MEDIUM, Decision=HIGH via `pestRisk=HIGH` → HIGH, and `uiState.pestRisk` itself is preserved) |
| `ML=LOW and Decision=LOW yields LOW` | Required Test 3 (LOW case) |
| `ML=MEDIUM and Decision=MEDIUM yields MEDIUM` | Required Test 3 (MEDIUM case) |
| `no ML result yet leaves a valid MEDIUM decision untouched` | Required Test 4 (no unrelated heuristic replaces a valid non-HIGH decision) |
| `no ML result yet still surfaces a real HIGH decision` | Required Test 4, HIGH variant |

**Fix verified, not just asserted**: before finalizing, I temporarily reverted the new branch, reran this test file, and confirmed exactly the two masking-scenario tests failed (`a HIGH decision is never masked...`, `a pest-driven HIGH decision is never masked...`) while the other four still passed — proving the tests actually exercise the bug rather than passing vacuously. The fix was then restored and the full suite re-verified green.

## 7. Test Results

- `DashboardViewModelTest` alone: 6/6 passed.
- Full suite (`./gradlew test`): **60/60 passed** (54 pre-existing from Phases 1–3A + 6 new). No existing test — Phase 3A's `FieldDecisionResolverTest`, `MockFieldStateRepositoryImplTest`, `LiveFieldStateRepositoryImplTest`, `DecisionEngineTest`, etc. — needed any change.

## 8. Build Result

- `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.

## 9. Confirmation Phase 3A Behavior Remains Intact

- `FieldDecisionResolver`, `RegionCropRuleRegistry`, `VidarbhaCottonRules`, `RainOutlook`, and both `MockFieldStateRepositoryImpl`/`LiveFieldStateRepositoryImpl`'s delegation to the resolver are byte-for-byte unchanged.
- All 9 Phase 3A tests (`FieldDecisionResolverTest` ×5 relevant + `MockFieldStateRepositoryImplTest` + `LiveFieldStateRepositoryImplTest`) pass unmodified.
- No second decision engine was created; `DashboardViewModel` still consumes `decision.overallRisk`/`decision.pestRisk`/etc. as already-computed values — the fix only changed *when* that value is consulted in the existing fusion chain, not how it's produced.
- Sensor pipeline, Retrofit/API contracts, ESP32/FastAPI integration: not touched.

## 10. Remaining Blockers / Out of Scope (unchanged, not addressed in this phase)

- Weather, Market, Local LLM, IVR, Marathi, Farmer Feedback, Government Scheme matching — untouched, per explicit instruction.
- The Dashboard's ad hoc sensor-critical thresholds (`extremeHeat = temp >= 40°C`, `mediumHeat = temp >= 35°C`) still don't numerically match `DecisionEngine`'s own thresholds (`38°C`/`33°C`) — a pre-existing inconsistency, unrelated to the masking bug, not touched here to keep this fix minimal.
- `RegionCropRuleRegistry`/`cropStage`/`rainOutlook` still only affect `decision.overallRisk` upstream (via `FieldDecisionResolver`, Phase 3A) — this phase only ensured that value, once computed, can't be masked on the way to the screen.

---

**Stopping here per the Phase 3B stop condition.** Not starting Phase 4, Local LLM, IVR, Weather API, Market API, Government Scheme matching, or Farmer Feedback.
