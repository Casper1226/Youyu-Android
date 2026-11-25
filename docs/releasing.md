# 发布 APK

1. 更新 `app/build.gradle.kts` 中的 `versionName`，并递增 `versionCode`。在对应提交运行 Android 工作流，确认单元测试、Android 15 设备测试和压缩后的发布包启动检查均通过。
2. 从该工作流下载 `unsigned-release`。在保管发布证书的机器上，用 Android SDK 签名。下面的命令会交互式读取密钥库口令。

```sh
apksigner sign --ks /path/to/release.p12 --out Youyu-Android.apk app-release-unsigned.apk
apksigner verify --verbose --print-certs Youyu-Android.apk
shasum -a 256 Youyu-Android.apk > SHA256SUMS.txt
```

3. 将标签指向实际构建的提交。在对应 GitHub Release 上传 `Youyu-Android.apk` 和 `SHA256SUMS.txt`，核对证书指纹后发布。所有版本沿用相同的 APK 文件名，README 的最新版下载链接可保持固定。

同一公开应用 ID 的后续版本应继续使用原发布签名。发布者应分别保管密钥库和口令的离线备份。CI 中的调试密钥仅用于模拟器测试。
