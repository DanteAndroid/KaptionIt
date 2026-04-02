<p align="center">
  <strong>Transbee</strong>
</p>

<p align="center">
  <a href="#english">English</a> | <a href="#chinese">中文</a>
</p>

<p align="center">
  <a href="#english"><img src="screenshot.png" alt="Screenshot" width="528" height="512"></a>
</p>


---

<a name="english"></a>
## English

**Transbee** is a **desktop** app for **speech-to-text**, **translation**, and **bilingual subtitle
export** (SRT, VTT, TXT).

### Features

- **Transcription** powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`), with optional **VAD** for better segment boundaries  
- **Translation** via multiple engines: **Apple Translate** (macOS, on-device), **Google**, **DeepL**, and **OpenAI-compatible** custom LLM APIs  
- **Export**: subtitle format and content (original / translation / bilingual single or dual files)  

### Tech stack

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)  

### Build & run

```bash
./gradlew :composeApp:run
```

**Generate installers:**

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```


### Troubleshooting

#### macOS Build Failure (AppleTranslate)
If you encounter an error like `Process 'command 'swift'' finished with non-zero exit value 1` during `:composeApp:buildAppleTranslateMac`, it is often due to a stale Swift build cache after moving or renaming the project directory.

**Solution:**
Run the following command to clean the Swift package cache:
```bash
cd native/AppleTranslate && swift package clean && cd ../..
```
Then retry the build.

### License

This project is licensed under [**CC BY-ND 4.0**](https://creativecommons.org/licenses/by-nd/4.0/) ( [`LICENSE`](./LICENSE) ).

- You may **view**, **clone**, and **redistribute unmodified** copies with attribution.  
- You may **not** distribute **modified** versions of this work (no forks that change the code and are published as derivatives under this license).  

---

<a name="chinese"></a>
## 中文

**Transbee** 是一款支持 **语音转文字**、**翻译** 以及 **双语字幕导出** (SRT, VTT, TXT) 的 **桌面端**
应用。

### 功能特点

- **转录**：基于 [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (`whisper-cli`)，支持可选的 **VAD**（语音活动检测）以获得更好的断句效果。
- **翻译**：支持多种引擎：**Apple 翻译** (macOS 原生离线)、**Google**、**DeepL** 以及兼容 **OpenAI** 协议的自定义大模型 API。
- **导出**：支持多种字幕格式及内容选择（原文 / 译文 / 双语单文件或双文件）。

### 技术栈

- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Desktop / JVM)

### 运行与构建

```bash
./gradlew :composeApp:run
```

**生成安装包：**

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```


### 常见问题排查

#### macOS 构建失败 (AppleTranslate)
如果在执行 `:composeApp:buildAppleTranslateMac` 时遇到 `Process 'command 'swift'' finished with non-zero exit value 1` 错误，通常是因为移动或重命名了项目目录导致 Swift 构建缓存失效。

**解决方法：**
执行以下命令清理 Swift 包缓存：
```bash
cd native/AppleTranslate && swift package clean && cd ../..
```
然后重新尝试构建。

### 开源许可

本项目采用 [**CC BY-ND 4.0**](https://creativecommons.org/licenses/by-nd/4.0/) 许可协议 ( [`LICENSE`](./LICENSE) )。

- 您可以 **阅读**、**克隆** 并在保持署名的前提下 **重新分发** 未经修改的副本。
- 您 **不得** 分发此作品的 **修改版本**（即不支持发布修改代码后的衍生版本）。
