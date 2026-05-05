# Advanced Light Plugin Concept

## Goal

Add a second WLED-oriented DM plugin named **Advanced Light** while keeping the existing simple **Lights** plugin available. Advanced Light targets WLED setups with multiple configured segments and controls each segment independently through the WLED JSON API over serial.

## Working Assumptions

- The plugin lives in the existing `light` Gradle module and is registered as an additional `DmPlugin` service provider.
- Runtime remains offline and JavaFX-only. No web UI, WebView, or network dependency is introduced.
- The existing `LightEffect` list remains the supported effect dropdown for the first implementation. WLED effects returned by ID that are not represented there fall back to `None` in the UI.
- Segment names, row order, color, effect, brightness, speed, and intensity are persisted under the standard TabletopControl config directory via `AppConfigPaths` and `SafeConfigIO`.
- On connect, the plugin sends `{"v":true}`, extracts the JSON object even if leading serial text such as `ADA` appears, fills the table from `state.seg`, merges persisted per-ID preferences, then sends the merged segment state back so remembered segment settings are applied.

## UI Plan

- A slim **Connection** toggle reveals serial settings similar to the simple light plugin:
  - serial port combo box
  - refresh button
  - baud rate field
  - connect/disconnect button
  - connection status
- A segment table lists:
  - persisted display name, defaulting to the segment ID
  - on/off checkbox, sent directly to that segment
  - edit selection checkbox, used by the shared controls
  - right-click context menu for rename, move up, and move down
- Shared editing controls apply to all rows with **Edit** checked:
  - color chooser using the core color wheel
  - brightness slider
  - effect dropdown using the basic light plugin's effect list
  - compact effect-parameter dropdown containing speed and intensity sliders
- A debug toggle shows or hides the serial console. When enabled, it logs transmitted JSON, received JSON query payloads, and serial failures.

## Serial / JSON Plan

- Extend the serial sender with read support for one complete JSON object after writes.
- Query command: `{"v":true}`.
- Robust response handling:
  - ignore all bytes before the first `{`
  - count balanced braces outside quoted strings
  - return once the first complete JSON object is received
  - this tolerates an `ADA` prefix or similar serial banner text
- Segment command format:
  - send `{"seg":[...]}` with one object per edited segment
  - each object includes `id`, `on`, `bri`, `col`, `fx`, `sx`, and `ix`
  - when an edit covers every known segment, send one segment command at a time with a 100ms delay between writes; this avoids WLED/serial edge cases where a full multi-segment array can be accepted but not visibly applied until a later segment toggle
  - full-segment writes use a latest-value round-robin queue, so slider drags rotate through segment IDs and keep only the newest pending value per segment

## Persistence Plan

File: `advanced-light.properties`

Fields:

- `version=1`
- `order=0,1,2`
- `segment.<id>.name=<name>`
- `segment.<id>.color=#RRGGBB`
- `segment.<id>.effect=<LightEffect enum name>`
- `segment.<id>.brightness=<0.0..1.0>`
- `segment.<id>.speed=<0..255>`
- `segment.<id>.intensity=<0..255>`

## Implementation Checklist

- [x] Concept plan created.
- [x] Segment and preference models.
- [x] Minimal WLED JSON parser for `{"v":true}` responses.
- [x] Segment command JSON builder.
- [x] Preference load/save tests.
- [x] WLED parser / command builder tests, including leading `ADA`.
- [x] Serial query/write support.
- [x] Advanced Light controller.
- [x] Advanced Light UI.
- [x] Service registration.
- [x] User documentation update.
- [x] Targeted Gradle tests.

## Later Enhancements

- Read WLED effect names dynamically when they are exposed reliably over serial.
- Add scene-save participation for advanced segment states if that becomes useful alongside global exit persistence.
- Support palettes and secondary/tertiary colors once the first segment workflow is stable.
