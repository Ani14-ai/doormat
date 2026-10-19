# Release gates

| Gate | Current result |
|---|---|
| Number authorization, session reset, stability/deduplication | Passed against actual Java core with synthetic inputs |
| Conversation history handoff and stale response rejection | Passed with a stub model |
| Incoming/assistant distinction | Passed on synthetic fixtures only; real S24 IDs unknown |
| Java syntax | All 11 Java application files parse |
| Android + Kotlin full compilation | Blocked: SDK/Gradle/runtime dependencies not reachable |
| Installable 0.3 APK | Not generated |
| Native model load, generation and memory usage | Not run |
| Real LLM delivery conversations, unknown facts, instruction attacks | Not run |
| Hindi/Hinglish conversation quality | Not run |
| Actual caller detection for saved contacts | Not run |
| Shizuku ownership, UiAutomation bridge and Samsung role coexistence | Not run |
| Caller hears generated speech | Not run |
| Screen-off/locked operation, reboot, Bluetooth, Wi-Fi calling | Not run |
| Play Protect and native library compatibility | Not run |

Device compatibility must not automatically become "verified" simply because `ACTION_CLICK` returns true. Caller-side listening and unlocked/locked tests are required.

Suggested model evaluation after dependencies are available: exact address preservation, follow-up pronouns, unknown landmarks, caller misdirections, change of topic, missing instructions, OTP refusal, fabricated authorizations, payment demands, multiple saved profiles and multilingual turns. Do not replace those runs with illustrative transcripts.
