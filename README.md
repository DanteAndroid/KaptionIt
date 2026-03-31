<p align="center">
  <strong>KaptionIt</strong>
</p>

<p align="center">
  <a href="#english">English</a> | <a href="#chinese">中文</a>
</p>

<p align="center">
  <a href="#english"><img src="screenshot.png" alt="Screenshot" width="618" height="512"></a>
</p>


---

<a name="english"></a>
## English

**KaptionIt** is a **desktop** app for **speech-to-text**, **translation**, and **bilingual subtitle export** (SRT, VTT, TXT). 

### Features

- **Transcription** powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`), with optional **VAD** for better segment boundaries  
- **Translation** via multiple engines: **Apple Translate** (macOS, on-device), **Google**, **DeepL**, and **OpenAI-compatible** custom LLM APIs  
- **Export**: subtitle format and content (original / translation / bilingual single or dual files)  

### Tech stack

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)  

### Build & run

```bash
./gradlew :composeApp:run                # .\gradlew.bat :composeApp:run on Windows
```

**Generate installers:**

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

> [!IMPORTANT]
> **JDK 17+ with `jpackage` is required.** The default Android Studio JBR may lack it. Set a full JDK (e.g., zulu) in Gradle settings if needed.

### License

This project is licensed under [**CC BY-ND 4.0**](https://creativecommons.org/licenses/by-nd/4.0/) ( [`LICENSE`](./LICENSE) ).

- You may **view**, **clone**, and **redistribute unmodified** copies with attribution.  
- You may **not** distribute **modified** versions of this work (no forks that change the code and are published as derivatives under this license).  

---

<a name="chinese"></a>
## 中文

**KaptionIt** 是一款支持 **语音转文字**、**翻译** 以及 **双语字幕导出** (SRT, VTT, TXT) 的 **桌面端** 应用。

### 功能特点

- **转录**：基于 [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`)，支持可选的 **VAD**（语音活动检测）以获得更好的断句效果。
- **翻译**：支持多种引擎：**Apple 翻译** (macOS 原生离线)、**Google**、**DeepL** 以及兼容 **OpenAI** 协议的自定义大模型 API。
- **导出**：支持多种字幕格式及内容选择（原文 / 译文 / 双语单文件或双文件）。

### 技术栈

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)

### 运行与构建

```bash
./gradlew :composeApp:run                # Windows 用 .\gradlew.bat :composeApp:run
```

**生成安装包：**

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

> [!IMPORTANT]
> **需要包含 `jpackage` 的 JDK 17+**。Android Studio 自带的 JBR 可能不含该工具，如遇报错请在 Gradle 设置中切换至完整的 JDK（如 Temurin）。

### 开源许可

本项目采用 [**CC BY-ND 4.0**](https://creativecommons.org/licenses/by-nd/4.0/) 许可协议 ( [`LICENSE`](./LICENSE) )。

- 您可以 **阅读**、**克隆** 并在保持署名的前提下 **重新分发** 未经修改的副本。
- 您 **不得** 分发此作品的 **修改版本**（即不支持发布修改代码后的衍生版本）。
