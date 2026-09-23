# lingxi-qqclient · 云编译工程（GitHub Actions）

目的：在 GitHub 的免费构建机上，把 `QQClientService.java` 编译成 HBuilderX 用的
`lingxi-qqclient.aar`。**本机不需要装 Android Studio。**

## 这个工程会做什么
1. 检出代码；
2. 装 JDK 17 + Android SDK；
3. 从 GitHub 下载 nodejs-mobile 的 aar（构建机有 GitHub 访问，本机没有也没关系）；
4. 用 Gradle 编译 `:qqclient:assembleRelease` → 产出 aar；
5. 把 aar 上传为构建产物（Artifacts）。

## 用法（不装 git 也行，用网页上传）
1. 在 GitHub 新建一个**空仓库**（例如 `lingxi-qqclient`，不要勾 README）。
2. 把本目录**全部内容**拖进该仓库网页（Add file → Upload files，可拖文件夹）。
   > 注意要包含隐藏目录 `.github/workflows/build-aar.yml`（网页拖拽文件夹时会一起带上）。
3. 上传提交后，仓库「Actions」页会自动跑 `build-qqclient-aar` → 绿勾表示成功。
4. 进那次运行 → 底部 **Artifacts** → 下载 `lingxi-qqclient-aar`，解压得到 `qqclient-release.aar`。
5. 把该 aar 重命名为 `lingxi-qqclient.aar`，放到：
   `D:\灵犀项目\灵犀App\nativeplugins\lingxi-qqclient\android\lingxi-qqclient.aar`

## 之后
- HBuilderX 打开 `manifest.json` →「App原生插件配置」→「本地插件」→ 勾选 `lingxi-qqclient`（以及 `lingxi-keepalive` 若也编了）→ 重新云打包。
- 打包前记得先把 `icqq` 依赖放进插件 assets：
  `cd D:\灵犀项目\灵犀App\nativeplugins\lingxi-qqclient\assets\qqruntime && npm install icqq`

## 说明
- 本工程只编 Java（不含 assets/icqq，那些属于 HBuilderX 插件目录）。
- `qqclient/build.gradle` 里 `implementation files('libs/nodejs-mobile-release.aar')` 的 aar 由
  workflow 在构建时下载放入，仓库里不需要提交它。
- 若要改插件版本，改 `build.gradle` 里的 `versionName`/`versionCode` 概念在 aar 层不需要，
  HBuilderX 的 `package.json` 里版本号即可。
