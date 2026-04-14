# Transbee

[English](#english) | [中文](#中文)

## English

Transbee is a desktop app for speech-to-text, translation, bilingual subtitle export, and document OCR/translation. It brings transcription, multi-engine translation, terminology-aware correction, and flexible export together in a single workflow.

### Features

- Transcription: powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`), with optional VAD (voice activity detection) for cleaner segmentation
- Translation correction: glossary-based correction helps improve subtitle quality and maintain terminology consistency
- Translation: supports Apple Translate (native on-device on macOS), Google, DeepL, Gemini, and custom LLM APIs compatible with the OpenAI protocol
- Document processing: supports document recognition and translation for PDF and other formats
- Export: supports subtitle formats such as `SRT`, `VTT`, and `TXT`, with source / translation / bilingual output options

### Tech Stack

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)

### Run

```bash
./gradlew :composeApp:run
```

### Build

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

### macOS Note

If `:composeApp:buildAppleTranslateMac` fails after moving the project directory, clear the Swift build cache and retry:

```bash
cd native/AppleTranslate && swift package clean && cd ../..
```

### License

[CC BY-ND 4.0](https://creativecommons.org/licenses/by-nd/4.0/). See [LICENSE](/Users/l/AI/Transbee/LICENSE).

- You may view, clone, and redistribute unmodified copies with attribution.
- You may not distribute modified versions of this project.

---

## 中文

Transbee 是一款桌面端应用，支持语音转文字、翻译、双语字幕导出，以及文档识别与翻译。在一条工作流中整合转录、多引擎翻译、术语纠错与灵活导出。

### 功能特点

- 转录：基于 [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`) 进行音视频转写，并支持可选 VAD（语音活动检测）以优化断句效果
- 翻译纠错：支持基于术语表的纠错与术语一致性控制，提升字幕翻译质量
- 翻译：支持 Apple Translate（macOS 原生离线）、Google、DeepL、Gemini，以及兼容 OpenAI 协议的自定义大模型 API
- 文档处理：支持 PDF 等文档的识别与翻译流程
- 导出：支持 `SRT`、`VTT`、`TXT` 等字幕格式，并支持原文 / 译文 / 双语等输出形式

### 技术栈

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)

### 运行

```bash
./gradlew :composeApp:run
```

### 打包

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

### macOS 提示

如果在移动项目目录后执行 `:composeApp:buildAppleTranslateMac` 失败，通常是 Swift 构建缓存失效。先清缓存再重试：

```bash
cd native/AppleTranslate && swift package clean && cd ../..
```

### 许可

项目采用 [CC BY-ND 4.0](https://creativecommons.org/licenses/by-nd/4.0/)，见 [LICENSE](/Users/l/AI/Transbee/LICENSE)。

- 允许在保留署名的前提下阅读、克隆和分发未经修改的副本。
- 不允许分发修改后的项目版本。
