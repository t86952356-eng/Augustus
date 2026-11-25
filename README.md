<p align="center">
  <sub>Credits to <a href="https://github.com/yxzeroo/openaugustus">https://github.com/yxzeroo/openaugustus</a>, all features, issues and bugs fixed on this version with our b4.20.3 internal deobfuscated version. Just compare everything.</sub>
  <br>
  <sub>Disclaimer: Not all videos in <code>tests/</code> are recorded properly, as everything was automated by Gemini 4 Neon.</sub>
</p>

<p align="center">
  <img src="assets/ss.png" alt="Skid Solutions Banner" width="350" />
  <br><br>
  <a href="https://discord.gg/skidsolutions">
    <img src="https://img.shields.io/badge/Discord-Skid%20Solutions-18181b?style=for-the-badge&logo=discord&logoColor=ffffff&labelColor=09090b" alt="Discord" />
  </a>
  <img src="https://img.shields.io/badge/Version-b4.23.3-27272a?style=for-the-badge&labelColor=09090b" alt="Version b4.23.3" />
  <img src="https://img.shields.io/badge/Java-21-3f3f46?style=for-the-badge&logo=openjdk&logoColor=ffffff&labelColor=09090b" alt="Java 21" />
  <img src="https://img.shields.io/badge/Gradle-8.14.3-52525b?style=for-the-badge&logo=gradle&logoColor=ffffff&labelColor=09090b" alt="Gradle 8.14.3" />
</p>

<p align="center">
  <a href="https://discord.gg/skidsolutions">https://discord.gg/skidsolutions</a>
</p>

<p align="center">
  <img src="assets/divider.svg" alt="" width="720" />
</p>

<p align="center">
  <img src="assets/jak.jpg" alt="jak" height="150" />
  <img src="assets/hack.jpg" alt="hack" height="150" />
  <img src="assets/deobf2.png" alt="deobf2" height="150" />
  <img src="assets/deobf.jpg" alt="deobf" height="150" />
</p>

<p align="center">
  <img src="assets/divider.svg" alt="" width="720" />
</p>

## How to build

### Prerequisites
- **Java Development Kit**: JDK 8 (used via Gradle toolchain to compile and run the Minecraft 1.8.9 client)
- **Gradle Runtime**: JDK 17 or JDK 21 (Gradle wrapper 8.14.3 does not run on JDK 25+)

### Building OpenAugustus

Run from this folder:

```powershell
.\gradlew.bat clean build
```

Generated `build/libs/`:
- `openaugustus-b4.23.3.jar`

### Running

```powershell
# Run in local run/ directory (extracts natives and downloads Minecraft 1.8 assets automatically)
.\gradlew.bat runClient

# Or run using your system .minecraft directory
.\gradlew.bat runClientMinecraftDir
```

Or run `Start.java` / `StartMinecraftDirectory.java` directly from your IDE.


---

## Android build (FOLD launcher / FCL, GL4ES)

This branch produces an Android-optimized build of the client:

- **Removed** desktop-only libraries that cannot work on Android and only
  added weight or crash risk: ONNX Runtime (~93 MB of desktop natives,
  AI rotation now degrades gracefully), OpenJFX (no Android natives —
  the JavaFX music player is now a safe no-op and 33 MB of MP3 assets
  are gone), oshi + jna-platform (desktop system info / trash bin APIs),
  Discord-RPC natives for win/mac/linux-x86, and 24 MB of MLG meme GIFs.
- **Hardened** the startup path: `SystemTrayNotifier`, `ClientFileUtil`
  trash deletion, holiday music and the ONNX model loader can no longer
  throw `HeadlessException` / `UnsatisfiedLinkError` /
  `NoClassDefFoundError` during client init on a headless Android JVM.
- **Trimmed** the jar with `scripts/trim_android_jar.py`: ~11,900
  unreferenced fastutil classes removed (transitive closure kept),
  `112.8 MB -> 66.7 MB`.
- **Kept**: every module, ClickGUI, fonts, cosmetics, sounds (vanilla
  OpenAL path), ViaVersion stack, Baritone, OptiFine. The GL rendering
  pipeline is untouched — GL4ES compatible.

### Building

```bash
./gradlew clean compatibilityJar fatJar
python3 scripts/trim_android_jar.py
```

Install on Android (FOLD/FCL): put `OpenAugustus.jar` and
`OpenAugustus.json` into `<minecraft>/versions/OpenAugustus/`, select
version **OpenAugustus**, renderer **GL4ES**, any Java 8+ runtime
(jre-legacy recommended).

### Verification

- 24,955 of 25,033 classes link cleanly in a class-load audit; the 76
  remaining failures are optional netty/log4j integrations (conscrypt,
  jetty-alpn, disruptor...) that vanilla 1.8.9 never touches, plus
  LWJGL/JInput classes the launcher replaces.
- A headless smoke test boots the real client through `Main -> Minecraft
  -> startGame -> GameSettings` up to `Display` creation (fails only on
  the missing X server of the build sandbox, exactly like vanilla).
