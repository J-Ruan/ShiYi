# 第三方组件

本项目自己的代码使用 MIT。第三方依赖及构建工具保留各自的协议，不受本项目 MIT 再授权。主要组件：

| 组件 | 协议 | 用途 |
|---|---|---|
| Kotlin、Coroutines、kotlinx.serialization | Apache-2.0 | 语言、异步与 JSON |
| AndroidX / Jetpack / Material 3 | Apache-2.0 | UI、数据库、导航、设置、EXIF、生物识别 |
| Coil | Apache-2.0 | 本地图片加载 |
| Gradle Wrapper / Gradle | Apache-2.0 | 构建工具 |
| JUnit 4 | EPL-1.0 | 仅测试 |
| Robolectric | MIT | 仅测试 |

Android SDK、设备系统 API 和开发 JDK 不打包进本应用，遵循各自的开发工具许可证。完整依赖树可在工程根目录用 `./gradlew :app:dependencies` 查看；版本以 Gradle 配置及 Compose BOM 为准。
