package com.lingxi.qqclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.nodejs.mobile.NodeJS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

/**
 * 灵犀 · 内嵌 QQ 客户端服务
 * ---------------------------------------------------------------
 * 作用：前台服务（常驻通知 + 保活），在原生层用 nodejs-mobile 启动一个 Node 运行时，
 *       运行 assets/qqruntime/index.js（icqq 协议客户端）。该脚本在 127.0.0.1 开本地
 *       HTTP/SSE 服务，WebView 用 fetch 直接跟它通信（与 _pcgw/app.js 同路，只是把
 *       NapCat 换成内嵌的 icqq，不再需要外部设备 / Termux）。
 *
 * 依赖（需自行放入工程，见集成说明.md）：
 *   - android/libs/nodejs-mobile-release.aar   （提供 com.nodejs.mobile.NodeJS）
 *   - assets/qqruntime/ 下含 index.js + node_modules/icqq（构建前 npm install icqq）
 *
 * 注意：本服务只负责「启动并保活 Node 运行时」，真正的 QQ 登录/收发逻辑在 index.js。
 *       未集成该插件（或 aar 缺失）时，startForegroundService 会抛异常被捕获，不影响现有功能。
 */
public class QQClientService extends Service {
    private static final String CHANNEL_ID = "lingxi_qqclient";
    private static final int NOTIFY_ID = 1002;
    private static final String TAG = "lingxi_qqclient";
    private static boolean started = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFY_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFY_ID, buildNotification());
        startRuntime();
        return START_STICKY;
    }

    private synchronized void startRuntime() {
        if (started) return;
        started = true;
        try {
            File dest = new File(getFilesDir(), "qqruntime");
            copyAssetDir(getAssets(), "qqruntime", dest);   // 把 assets/qqruntime 拷到可读写目录
            File script = new File(dest, "index.js");
            if (!script.exists()) {
                Log.e(TAG, "qqruntime/index.js 不存在，请构建前 npm install icqq 并打入 assets");
                return;
            }
            // nodejs-mobile：以脚本所在目录为模块根 → index.js 的 require('icqq') 解析到 dest/node_modules
            NodeJS.startWithScriptFile(getApplicationContext(), script.getAbsolutePath());
            Log.i(TAG, "Node 运行时已启动: " + script.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "启动 Node 运行时失败", t);
        }
    }

    /** 递归把 assets 子目录复制到应用私有目录（assets 只读，Node 需要可写路径） */
    private void copyAssetDir(AssetManager am, String assetPath, File dest) throws IOException {
        InputStream in = null;
        try {
            in = am.open(assetPath);   // 能 open 成功 → 是文件
        } catch (IOException e) {
            in = null;                  // 抛异常 → 是目录
        }
        if (in != null) {
            dest.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return;
        }
        String[] children = am.list(assetPath);
        if (children == null) return;
        dest.mkdirs();
        for (String c : children) copyAssetDir(am, assetPath + "/" + c, new File(dest, c));
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "灵犀 QQ 客户端", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("运行内嵌 QQ 客户端以收发消息");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("灵犀 QQ 客户端运行中")
                .setContentText("正在运行内嵌 QQ，请勿清理后台")
                .setSmallIcon(android.R.drawable.ic_dialog_email) // TODO: 换成你自己的状态栏图标
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { NodeJS.stop(); } catch (Throwable ignored) {}
        // 尽力自拉活（系统强杀时可能失败，属正常）
        try {
            Intent i = new Intent(this, QQClientService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }
}
