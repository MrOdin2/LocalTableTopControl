# Lighting plugins

The `light` module provides both the simple global **Lights** plugin and the WLED segment-oriented
**Advanced Light** plugin. Both communicate with the rest of TabletopControl only through core
EventBus events and work over serial without a runtime network dependency.

## Tracker turn cues

Advanced Light can react to the core `ActiveTokenChangedEvent` already used for map active-token
markers. The feature is disabled by default.

1. Connect Advanced Light so the WLED segments appear.
2. Enable **Enable tracker turn cues**.
3. Right-click a segment and use **Assign token(s)...** to select one or more PC tokens from the
   current scene.
4. Use **Configure turn cue...** to choose the WLED effect, color, brightness, speed, intensity,
   and either a whole-turn or timed duration.
5. Use **Unassign token(s)...** from the same row menu to remove links.

Assignments are stored on the Advanced Light segment under the
`flags.tabletopcontrol.assignedTokens` preference key. Tracker token data is not changed. The
segment's normal state is kept separately and restored when the turn changes or the timed cue ends.

See [UserDoc.html](UserDoc.html) for the complete operator guide.
