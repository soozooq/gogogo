# V3.1：独立外部演示 APK 的 Host 代理界面

**后续版本：** 用户要求简化操作并继续推进，本轮已扩展为 [应用运行：导入一次、保存状态、统一回传](virtual-container-runtime.md)，版本 `2.4.26-tianwaitian-runtime`。下文保留 V3.1 首版过程；其中重建安全停止、每次重新选文件、分别下载两个工件的说明已由新版流程替代，不能按旧手顺要求用户逐项重跑。

2026-10-21，落实 [Codex 工单](tasks/codex-v3-1-external-demo-host-proxy.md)。当前条件为**无 Root、只有一部 Android 手机、没有电脑**；构建在云端进行。

## 能力与边界

主程序 `2.4.25-tianwaitian-v31-demo` / `2400093` 保持原应用 ID 和签名配置，可覆盖升级。新增独立 `externalDemo` application 模块：包名 `com.soozooq.tianwaitian.externaldemo`，版本 `1 / 1.0`，无需权限，有自己的 XML、文字、颜色、按钮计数及生命周期接口。

demo APK **不进入宿主资产或代码依赖**。用户选择文件并确认后，宿主校验配对 APK，再由其 DEX 创建 View 并使用该 APK 的独立资源表。实际启动的是已安装天外天的私有 Host Activity；demo 共享宿主进程、UID 和权限。它不是独立应用沙箱或系统原生启动未安装 Guest Activity，不代表微信、QQ、抖音运行或定位兼容。

## 准入与清理

- `generateContainerV31Trust` 先构建独立 demo，将完整 SHA-256 编入宿主，并生成 `app/build/generated/containerV31/external-demo-trust.json`。**host 与 demo 必须来自同一次 Preview 运行**；不同构建签名可能使字节不同，不能混配。
- 仅接受明确选择的 `content://` 文档，复制为私有只读文件，上限 8 MiB。完成写入和同步后先校验精确 SHA，再校验 ZIP、Manifest、DEX、资源、包名、版本、组件和权限，最后才创建独立 ClassLoader。
- 其他 APK 继续走 V3 只读归档，不执行 DEX。V3.1 不安装 demo、不使用宿主旧更新器的安装权限、不请求 Root/Shizuku、不读取第三方私有数据。
- Host 转发真实生命周期。正常退出需移除按钮监听与 View、释放资源 loader/provider、删除私有 APK；证据进入 V0 的独立 V3.1 附录。
- 旋转或系统重建后安全停止，不自动重执行文件；需返回 V0 重新进入并选择。保存状态不含 URI 或代码路径执行授权。
- OS 直接杀死进程可能留下私有 code-cache 副本；本版不自动执行残留副本，也不宣称已验证主进程冷恢复清理。
- PASS 要求实际资源/View、真实点击、生命周期和退出清理证据全部满足。等待、重建或清理不全均不能记为 PASS。

## 云端验证与产物

两条原有工作流均保留历史闸门及 Lint，新增 V3.1 静态契约、demo 单测和独立 APK 校验。Preview 分别上传 `gogogo-unified-labs-preview` 与 `tianwaitian-v31-external-demo`。

产物脚本核查 ZIP CRC、编译 Manifest、DEX **class_defs**、资源、签名及哈希，确认 demo 类和 APK 未嵌入宿主、精确 pin 已进入宿主 DEX。`ci/v31-artifacts.json` 记录实际代码 SHA、两个文件名/大小/SHA；这些不是手机运行证据。

云端可复现命令（JDK 17、SDK 36；用户手机无需执行）：

```bash
python3 ci/verify_tianwaitian_launcher.py
python3 ci/verify_container_fixture_contract.py
python3 ci/verify_container_v31_contract.py
python3 ci/test_container_v31_artifacts.py
bash ci/run_container_v0_jvm_smoke.sh
./gradlew build
python3 ci/verify_container_v31_artifacts.py \
  --host-dir app/build/outputs/apk/debug \
  --demo-dir externalDemo/build/outputs/apk/debug \
  --trust app/build/generated/containerV31/external-demo-trust.json \
  --sdk "$ANDROID_HOME" --commit "$(git rev-parse HEAD)" \
  --output ci/v31-artifacts.json
```

## 手机五步验收

1. 从同一次 **Build Preview APK** 的两个工件下载并解压主程序 APK 和 `externalDemo-debug.apk`。
2. 覆盖升级天外天；demo 保留为普通文件，不安装它。
3. V0 → **V3.1：选择配对演示 APK 并打开界面**，选择 demo，点“核验并运行演示”。
4. 点击演示计数按钮确认增加；可打开系统设置再返回，最后点“完成并退出”。
5. 回 V0 点“一键复制总报告”，回传整份记录，核查 V3.1 附录的 View、点击、生命周期与清理结果。

Android 13 vivo 真机仍需这部手机的实际回传。构建、静态契约、单测和 V0–V2.4 历史 PASS 均不能替代本版真机验收。
