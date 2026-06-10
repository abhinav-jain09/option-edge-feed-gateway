# Volume Acceleration Logic

This document explains how volume acceleration is calculated and displayed in OptionsEdge.

The feed gateway does not calculate acceleration. The gateway only consumes final Kafka topics and forwards JSON messages to the UI over WebSocket.

Acceleration is calculated in the main `options-edge` application.

## Data Flow

```text
IBKR market data
-> options-edge raw snapshot
-> Kafka raw topic
-> RawToDisplayBridge
-> display topics
-> options-edge-feed-gateway
-> browser UI
```

## Per-Strike Acceleration

Per-strike acceleration is calculated in:

```text
/Users/abhinav/development/workspace/options-edge/src/app/kafka/RawToDisplayBridge.java
```

This is the value used by the UI table volume bars and blinking strike.

For each strike and each side, call and put are calculated separately.

The app keeps recent volume samples for each strike:

```text
timestamp -> cumulative volume
```

Then it calculates:

```text
current 1-minute rate = volume change over the latest 1 minute
previous 1-minute rate = volume change from 2 minutes ago to 1 minute ago
acceleration = current 1-minute rate - previous 1-minute rate
```

Example:

```text
Call volume 2 minutes ago: 100
Call volume 1 minute ago:  130
Call volume now:           190

previous rate = 130 - 100 = 30 contracts/min
current rate  = 190 - 130 = 60 contracts/min
acceleration  = 60 - 30 = +30
```

That means call volume is accelerating.

Negative example:

```text
previous rate = 60 contracts/min
current rate  = 20 contracts/min
acceleration  = 20 - 60 = -40
```

That means volume may still be increasing, but it is increasing slower than before.

## UI Fields

Each display snapshot contains these fields:

```json
{
  "callRate1m": 0.0,
  "putRate1m": 0.0,
  "callRateDelta2m": 0.0,
  "putRateDelta2m": 0.0,
  "maxRateDelta2m": 0.0
}
```

Meaning:

| Field | Meaning |
| --- | --- |
| `callRate1m` | Current 1-minute call volume rate for this strike |
| `putRate1m` | Current 1-minute put volume rate for this strike |
| `callRateDelta2m` | Call acceleration versus previous minute |
| `putRateDelta2m` | Put acceleration versus previous minute |
| `maxRateDelta2m` | Larger of call and put acceleration |

## UI Visual Logic

The table visual logic is in:

```text
/Users/abhinav/development/workspace/options-edge/src/app/web/assets/option-chain.js
```

The UI finds the highest positive acceleration among visible rows.

For call side:

```text
maxCallAcceleration = max(callRateDelta2m across visible rows)
```

For put side:

```text
maxPutAcceleration = max(putRateDelta2m across visible rows)
```

Each row gets a score:

```text
score = rowAcceleration / maxAcceleration * 100
```

Examples:

```text
max call acceleration = 40
row call acceleration = 20
score = 20 / 40 * 100 = 50%
```

```text
max put acceleration = 80
row put acceleration = 80
score = 80 / 80 * 100 = 100%
```

The score controls:

- acceleration bar width
- volume font size
- hot styling when score is high
- blink on the strongest acceleration strike

## Blink Logic

The UI finds the strongest strike by comparing:

```text
max(callRateDelta2m, putRateDelta2m)
```

The strike with the highest positive value blinks.

If two strikes have the same value, the lower strike wins.

## Total Volume Direction

Total call/put direction is calculated separately in:

```text
/Users/abhinav/development/workspace/options-edge/src/app/kafka/VolumeDirectionStreams.java
```

This is not the same as the per-strike table acceleration.

It maintains total call and put volume for the whole symbol and expiry:

```text
totalCallVolume = sum latest call volume across strikes
totalPutVolume  = sum latest put volume across strikes
```

Then it calculates:

```text
callRate = total call volume change / elapsed seconds
putRate  = total put volume change / elapsed seconds

callAcceleration = callRate change / elapsed seconds
putAcceleration  = putRate change / elapsed seconds
```

Direction is selected like this:

```text
if callAcceleration - putAcceleration >= 1.0:
    direction = CALL

if putAcceleration - callAcceleration >= 1.0:
    direction = PUT

otherwise:
    direction = FLAT
```

The threshold comes from:

```text
KAFKA_VOLUME_DIRECTION_MIN_ACCEL_DIFF
```

Default:

```text
1.0
```

## Gateway Role

The gateway sends already-calculated values to the UI.

Gateway message example:

```json
{
  "type": "snapshot",
  "data": {
    "symbol": "SPX",
    "expiry": "20260609",
    "strike": 7420,
    "callRate1m": 10.0,
    "putRate1m": 3.0,
    "callRateDelta2m": 7.0,
    "putRateDelta2m": 0.0
  }
}
```

The gateway does not change these values. It only broadcasts them.

