# Android controller input

The Android shell detects devices advertising gamepad or joystick sources. It observes standard Android key events and generic motion events, including left/right stick axes and trigger axes. The diagnostic status is intentionally separate from gameplay.

The eventual Bevy input adapter must:

- Map Android key codes to NVForever's existing action bindings rather than inventing a second gameplay model.
- Normalize stick axes to -1..1 and apply configurable dead zones.
- Handle triggers that report either signed or unsigned ranges.
- Handle controller add/remove/reconnect without leaving actions stuck.
- Preserve Android Back/system navigation behavior.
- Route rumble through Android vibrator APIs only when the connected device exposes supported vibration.
- Never consume or drop input before a native mapping is registered.

Until the native adapter exists, the launcher does not claim controller gameplay support. A visible controller connection or diagnostic event is not proof that the engine receives input.
