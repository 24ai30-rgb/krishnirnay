# KRISHINIRNAY Phase 4B Implementation Report

Scope: fuse the existing Fertilizer engine into the one authoritative decision pathway (`FieldDecisionResolver` → `DecisionEngine` → `DecisionOutput`). No new fertilizer logic was invented — `FertilizerAdvisor`'s rules are unchanged except for one real fix (rain-awareness). No Weather/Market/Local LLM/IVR/Government Schemes/Farmer Feedback work was touched.

---

## 1. Audit Result (before any code was touched)

- **Existing fertilizer code**: `core/fertilizer/{FertilizerAdvisor, FertilizerInput, FertilizerRecommendation}.kt` — a real, pure-Kotlin, offline rule engine (N→P→K deficiency check against generic thresholds). No `FertilizerRepository`/`FertilizerEngine` class exists.
- **Only production call site**: `CropAdvisoryViewModel.toAdvisoryUiState()` built a `FertilizerInput` from `FieldState.sensors` + `FarmerProfile` and called `FertilizerAdvisor.recommend()` **directly in the ViewModel** — confirmed zero references from `FieldDecisionResolver` or `DecisionEngine`.
- **`FertilizerInput.rainOutlook` existed but was dead** — accepted, never read by `FertilizerAdvisor.recommend()`.
- **Correct precedent already in the codebase**: Disease/Pest are wired exactly the way fertilizer needed to be — `DecisionInput.diseaseResult`/`pestResult` → `DecisionEngine`'s `cropHealthRiskFrom`/`pestRiskFrom` → `DecisionOutput.cropHealthRisk`/`pestRisk`, resolved once in `FieldDecisionResolver`.
- **Existing tests**: `FertilizerAdvisorTest.kt` (6 tests) only exercised `FertilizerAdvisor` in isolation — nothing proved it reached `DecisionOutput`.
- **Smallest correct change identified**: `DecisionInput` already carries `sensors` (moisture+NPK) and `cropStage`; `FieldDecisionResolver` already holds `ProfileRepository` (crop/soilType/cropVariety/farmAreaAcres/irrigationMethod). No new fields were needed on `DecisionInput`. The change: one new optional parameter on `DecisionEngine.evaluate()`, one new optional field on `DecisionOutput`, and `FieldDecisionResolver` computing the recommendation and passing it through.

## 2. Files Modified

- `core/fertilizer/FertilizerAdvisor.kt` — `recommend()` now chooses `TimingOutcome` based on `input.rainOutlook` (previously always `Within3Days` regardless of weather; the field was accepted but ignored).
- `core/data/model/DecisionOutput.kt` — added `val fertilizerRecommendation: FertilizerRecommendation? = null`.
- `core/decision/DecisionEngine.kt` — `evaluate()` gained a third parameter `fertilizerRecommendation: FertilizerRecommendation? = null` (default keeps every one of the 23 existing `DecisionEngineTest` call sites compiling and passing unchanged), passed straight into the `DecisionOutput` it constructs. No risk/recommendation computation changed.
- `core/data/composite/FieldDecisionResolver.kt` — builds a `FertilizerInput` from the same profile + sensors + rain outlook + crop stage already resolved for the risk computation, calls `FertilizerAdvisor.recommend()` (unchanged engine), and passes the result into `DecisionEngine.evaluate()`.
- `feature/advisory/CropAdvisoryViewModel.kt` — **deleted** the duplicate `FertilizerAdvisor.recommend()` call; now reads `decision.fertilizerRecommendation` directly (falling back to `InsufficientData` only if absent, e.g. from an older cached `FieldState`). No longer needs `ProfileRepository` at all — removed from its constructor.

**Not touched**: `DecisionInput.kt` (no new fields needed), `DecisionEngine`'s risk/recommendation/timing/benefit logic, `RegionCropRuleRegistry`, `RainOutlook`, sensor DTOs, `SensorApiService`/Retrofit contracts, FastAPI routes, ESP32 pipeline, Weather/Market persistence (Phase 4A), `DashboardViewModel`'s risk-fusion fix (Phase 3B), `RepositoryModule.kt`.

## 3. Fertilizer Data Flow

```
FieldDecisionResolver.evaluate(sensors, ..., pestResult, deviceOnline)
    reads ProfileRepository.profile.value  (crop, cropVariety, soilType, farmAreaAcres, irrigationMethod)
    reads sensors                          (soilMoisturePct, nitrogenPpm, phosphorusPpm, potassiumPpm)
    reads rainOutlook                      (already computed for the risk engine, from WeatherRepository)
    reads cropStage                        (already computed for the risk engine, from profile.seedlingStage)
        ↓
    FertilizerInput(...)  — no new fields invented, reuses exactly what already existed
        ↓
    FertilizerAdvisor.recommend(FertilizerInput)  — unchanged rule engine
        ↓
    DecisionEngine.evaluate(input, ruleSet, fertilizerRecommendation)
        ↓
    DecisionOutput.fertilizerRecommendation
        ↓
CropAdvisoryViewModel reads FieldState.decision.fertilizerRecommendation directly
```

There is now exactly **one** call site for `FertilizerAdvisor.recommend()` in the whole app: `FieldDecisionResolver`. `CropAdvisoryViewModel` no longer computes agriculture logic — it renders structured data it's handed, matching the same discipline already used for `RecommendationOutcome`/`ReasonOutcome`/`TimingOutcome`.

## 4. How Fertilizer Reaches the Final Decision

`DecisionEngine.evaluate(input, ruleSet, fertilizerRecommendation)` places the already-computed `fertilizerRecommendation` directly into the `DecisionOutput` it returns — a genuine parameter the engine receives and carries through, not a value attached after the fact outside it. Both `MockFieldStateRepositoryImpl` and `LiveFieldStateRepositoryImpl` reach this through the identical, unmodified `FieldDecisionResolver.evaluate(...)` call already established in Phase 3A — no duplicate resolver, no second decision path.

## 5. Priority Behavior With Disease/Pest Risk

`fertilizerRecommendation` plays **no part** in `overallRisk`, `recommendation`, `waterStressRisk`, `heatRisk`, `cropHealthRisk`, or `pestRisk` — those are computed identically to before this phase, using none of the fertilizer inputs. This isn't an added safeguard bolted on; it's structural — the new field is appended to `DecisionOutput` and threaded through as an inert pass-through parameter, so there is no code path by which a fertilizer suggestion could influence risk severity. Verified explicitly by tests (§7, items 6/7/and the direct `DecisionEngineTest` check that `overallRisk`/`recommendation` are identical with and without a fertilizer recommendation present).

## 6. Offline / Unavailable Behavior

- **Weather unavailable**: `FertilizerAdvisor.timingFor(RainOutlook.UNKNOWN)` returns the conservative `Within3Days` default — never assumes dry conditions just because there's no weather signal. Verified by test.
- **Fertilizer data unavailable** (no NPK reading at all): `FertilizerAdvisor.recommend()` returns `InsufficientData` — a real, valid `FertilizerRecommendation` value, not null and not a guess. The rest of the `DecisionOutput` (e.g. a genuinely critical water-stress `HIGH`) is computed exactly as before, completely unaffected. Verified by test.
- **No dosage/cost is ever invented** — `FertilizerAdvisor`'s existing safety discipline (generic indicative ranges, mandatory safety note, no cost estimate since no real price source exists) is unchanged.

## 7. Tests Added / Modified

| # | Requirement | Test |
|---|---|---|
| 1, 3 | Fertilizer reaches `FieldDecisionResolver` and appears in the final `DecisionOutput` | `FieldDecisionResolverTest`: *a nitrogen-deficient soil reading produces a real fertilizer recommendation...* |
| 2 | Fertilizer reaches `DecisionEngine` | `DecisionEngineTest`: *a fertilizer recommendation passed in is carried through to the output unchanged*, *no fertilizer recommendation passed in leaves the field null, never invented* |
| 4 | Mock repository uses the same resolver | `MockFieldStateRepositoryImplTest` — `distinctiveDecision()` fixture extended with a real `fertilizerRecommendation`; the existing verbatim-delegation assertion now covers it too |
| 5 | Live repository uses the same resolver | `LiveFieldStateRepositoryImplTest` — new test *recordPestResult delegates verbatim to FieldDecisionResolver, fertilizer included* |
| 6 | Disease HIGH + fertilizer preserves HIGH | `FieldDecisionResolverTest`: *a HIGH disease risk is preserved alongside a real fertilizer recommendation* |
| 7 | Pest HIGH + fertilizer preserves HIGH | `FieldDecisionResolverTest`: *a HIGH pest risk is preserved alongside a real fertilizer recommendation* |
| 8 | Weather unavailable never invents weather | `FieldDecisionResolverTest`: *fertilizer timing never assumes dry weather when weather is UNAVAILABLE* |
| 9 | Fertilizer unavailable never destroys other decisions | `FieldDecisionResolverTest`: *insufficient fertilizer data never affects the rest of the decision* |
| — | Fertilizer never influences risk/recommendation (extra, direct engine-level proof) | `DecisionEngineTest`: *a fertilizer recommendation never influences overallRisk or recommendation* |

**Fix verified, not just asserted**: temporarily reverted `FieldDecisionResolver`'s pass-through call (`DecisionEngine.evaluate(input, ruleSet)` instead of `...,fertilizerRecommendation)`), reran the suite, and confirmed exactly the 5 fertilizer-fusion-dependent tests failed while every other test (including all Phase 3A/3B/4A tests) still passed. Restored the fix and reconfirmed all green with a full non-cached re-run (`--rerun-tasks`).

A pre-existing MockK pitfall was also hit and fixed while writing the new `LiveFieldStateRepositoryImplTest` case: a relaxed mock of `SensorApiService.getLatestSensor()` (which returns the concrete generic Retrofit `Response<T>`) produced an unreliable auto-generated answer. Fixed by giving it an explicit `coEvery { ... } throws ...` stub instead of relying on `relaxed = true` — the same defensive style already used by this file's original Phase 1 test.

## 8. Test Results

- `FieldDecisionResolverTest` + `DecisionEngineTest` + `MockFieldStateRepositoryImplTest` + `LiveFieldStateRepositoryImplTest` (the directly-affected files): all passing.
- Full suite (`./gradlew testDebugUnitTest --rerun-tasks`, fully re-executed, no cache): **77/77 passed** (68 pre-existing + 9 new).

## 9. Build Result

`./gradlew assembleDebug`: **BUILD SUCCESSFUL**.

## 10. Phase 3 / 4A Regression Verification

All pre-existing tests pass unmodified in logic (two fixture-only additions: `MockFieldStateRepositoryImplTest`'s and `LiveFieldStateRepositoryImplTest`'s stub `DecisionOutput`s gained a `fertilizerRecommendation` field, strengthening rather than changing what they prove). `RegionCropRuleRegistry`, `RainOutlook`, pest/disease escalation, the Phase 3B Dashboard risk-masking fix, and Phase 4A's Weather/Market persistence are all untouched and their tests remain green. The ESP32→FastAPI→Android sensor pipeline, `SensorApiService`, and every Retrofit/FastAPI contract are unchanged.

## 11. Remaining Phase 4 Work

- Fertilizer is not yet surfaced on the Dashboard itself — only on the Advisory screen (unchanged from before this phase; not requested this phase).
- Fertilizer recommendation is not persisted across a restart in `FieldStateCache` (deliberately out of scope — it's a pure function of already-persisted sensor/profile data, recomputed on the next tick, unlike the event-sourced pest/disease scan results that Phase 2 did persist).
- Government Schemes, Farmer Feedback, Local LLM, Offline Voice interfaces, IVR, and real Weather/Market providers remain exactly as characterized in `PHASE_4_AUDIT_REPORT.md` — untouched.

---

**Stopping here.** Not starting Weather API, Market API, Local LLM, IVR, Government Schemes, or Farmer Feedback in this turn.
