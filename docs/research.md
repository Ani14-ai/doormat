# Implementation evidence — 19 October 2026

The following were read during this build. Documentation supports choosing these components; it does not certify this app.

| Source | Finding and design implication |
|---|---|
| [Samsung India: Call screening and Text call](https://www.samsung.com/in/support/mobile-devices/how-to-use-the-call-screening-feature-with-ai-assistant-on-the-samsung-galaxy-s26-series/) | Documents Call assist → Call screening → Text call and speaking typed replies. Written for S26; user's S24 screenshot shows the matching initial interface, but later controls remain unobserved. |
| [AutoTextCall](https://github.com/lucianovk/autotextcall) | Public Samsung UI automation precedent. It is not evidence that this new interface or background Shizuku implementation works. No code from this repository is incorporated. |
| [LiteRT-LM Android documentation](https://ai.google.dev/edge/litert-lm/android) | Engine initialization, CPU backend, ConversationConfig, system instructions, synchronous generation. |
| [LiteRT-LM Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt) | Confirms maxNumTokens, maxOutputToken, and sampling options used in the adapter. |
| [Qwen model card](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct) | Android CPU quantized model benchmarks are reported on an S25 Ultra. These are not S24 measurements and are not claimed as this app's performance. |
| [Selected model file](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/blob/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm) | Approximately 1.6 GB, SHA256 c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e; repository revision 5e6e7a5. File not downloaded in this environment. |
| [Android audio sharing](https://developer.android.com/media/platform/sharing-audio-input) | Ordinary installed apps do not get general cellular-call audio capture from ordinary microphone permission. This design uses Samsung's existing speech path. |
| [Shizuku setup](https://shizuku.rikka.app/guide/setup/) | Wireless debugging setup and reboot limitations. Shizuku is not a normal runtime permission or a guarantee of OEM behavior. |
| [Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance) | Sensitive permissions plus internet sideloading can cause the earlier exact block. Renaming is not the fix, and this project's changed permission set does not guarantee acceptance. |

Model/runtime versions are pinned in source. Verify dependency compatibility in an actual build before distributing. Licenses: original project code MIT; Shizuku MIT; LiteRT-LM Apache 2.0; selected model repository declares Apache 2.0. Model weights and runtime binaries are not bundled in this checkpoint.
