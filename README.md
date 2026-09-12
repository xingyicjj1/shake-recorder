# 摇晃录音 (ShakeRecorder)

左右交替摇晃手机两次，自动打开并启动录音。后台服务常驻监听，锁屏也可用。

## 原理
- 后台前台服务 (`ShakeService`) 监听加速度传感器 X 轴
- `ShakeDetector` 在 2 秒窗口内检测到 4 次方向严格交替的单向摆动（左→右→左→右）即触发
- 触发后启动 `RecorderActivity` 自动开始录音，点「停止并保存」生成 `.m4a`

## 自行编译 (GitHub Actions)
1. 把本仓库推到 GitHub
2. 进入仓库 **Actions** → 选择 **Build APK** → **Run workflow**
3. 运行完成后在 **Artifacts** 下载 `shake-recorder-apk`（内含 `app-debug.apk`）
4. 手机开启「允许安装未知来源应用」后安装该 APK

## 本地编译
需要 Android SDK (cmdline-tools) + JDK 17 + Gradle 8.6：
```bash
export ANDROID_HOME=/path/to/sdk
gradle assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 使用
1. 打开 App，点「开启摇晃录音」，授予录音 / 通知权限
2. 按返回键回到桌面（服务在后台继续运行）
3. 左右交替摇晃手机两次 → 自动弹出录音界面
4. 点「停止并保存」，录音存于 `Android/data/com.example.shakerecorder/files/recordings/`

## 可调参数
`ShakeDetector.kt` 中：
- `THRESHOLD`：摇晃灵敏度（越小越灵敏）
- `WINDOW_MS`：允许完成动作的时间窗口
- `REQUIRED`：需要的单向摆动次数（默认 4 = 左右交替两次）
