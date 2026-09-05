# Firebase Realtime Database schema & security rules

This is the contract both the Android app (`FirebaseFieldStateRepositoryImpl`) and any future ESP32 firmware code against. Realtime Database, not Firestore.

## Schema

```
/devices/{deviceId}/latest/
    soilMoisture: number     # percent, 0-100
    temperature: number      # celsius
    humidity: number         # percent, 0-100
    nitrogen: number|null    # ppm — Phase 2 sensor, absent on Phase 1 hardware
    phosphorus: number|null  # ppm — Phase 2 sensor
    potassium: number|null   # ppm — Phase 2 sensor
    ph: number|null          # Phase 2 sensor
    timestamp: number        # epoch millis, set by ESP32 on write
    batteryPct: number|null

/devices/{deviceId}/status/
    online: boolean
    lastSeenAt: number       # epoch millis

/devices/{deviceId}/history/{pushId}/
    ...same shape as latest/     # optional — only written if/when server-side Analytics trend
                                   # data is needed beyond the app's in-memory session ring buffer

/deviceOwners/{deviceId}: string   # uid of the owning user — used by security rules below
```

Field names are intentionally flat and match `SensorReading` in the app 1:1 (`core/data/model/SensorReading.kt`) so the Firebase-backed repository implementation is a straight parse, no remapping layer.

## Security rules

**Never ship the default "test mode" rules** (`.read`/`.write: true` for everyone, unauthenticated, which Firebase enables for 30 days on a fresh project) — that leaves farmer field data open to the entire internet and is an easy thing for a technically curious judge to notice.

Minimum bar for the hackathon demo (single seeded account, single demo device) — require authentication for any access:

```json
{
  "rules": {
    "devices": {
      "$deviceId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "deviceOwners": {
      ".read": "auth != null",
      ".write": false
    }
  }
}
```

Tighter, per-owner variant (worth moving to once there's more than one demo account/device):

```json
{
  "rules": {
    "devices": {
      "$deviceId": {
        ".read": "auth != null && root.child('deviceOwners').child($deviceId).val() === auth.uid",
        ".write": "auth != null && root.child('deviceOwners').child($deviceId).val() === auth.uid"
      }
    },
    "deviceOwners": {
      ".read": "auth != null",
      ".write": false
    }
  }
}
```

Apply these in the Firebase console under Realtime Database → Rules before ever writing real data — not as a later hardening pass.

## Client-side persistence

`FirebaseDatabase.setPersistenceEnabled(true)` is enabled in `FirebaseModule` for faster reconnect after a network blip. This is a transport-layer optimization only — the app's DataStore cache (`core/data/local/FieldStateCache.kt`) is the single authority for what the UI renders on cold start / in Offline Mode. ViewModels never read Firebase's local persistence cache directly.
