package com.xse.sesame.hook;

import static com.xse.sesame.hook.SimplePageManager.addHandler;
import static com.xse.sesame.hook.SimplePageManager.enableWindowMonitoring;
import com.xse.sesame.hook.CaptchaHook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;

import com.xse.sesame.util.compat.XC_MethodHook;

import com.xse.sesame.util.XHelpers;
import com.xse.sesame.util.compat.XC_LoadPackage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.xse.sesame.util.compat.XC_MethodReplacement;
import com.xse.sesame.BuildConfig;
import com.xse.sesame.data.ConfigV2;
import com.xse.sesame.data.Model;
import com.xse.sesame.data.RunType;
import com.xse.sesame.data.TokenConfig;
import com.xse.sesame.data.ViewAppInfo;
import com.xse.sesame.data.task.BaseTask;
import com.xse.sesame.data.ModelGroup;
import com.xse.sesame.data.task.ModelTask;
import com.xse.sesame.entity.AlipayVersion;
import com.xse.sesame.entity.FriendWatch;
import com.xse.sesame.entity.RpcEntity;
import com.xse.sesame.model.base.TaskCommon;
import com.xse.sesame.model.extensions.TestRpc;
import com.xse.sesame.model.normal.base.BaseModel;
import com.xse.sesame.model.task.antMember.AntMemberExchange;
import com.xse.sesame.model.task.antMember.AntMemberRpcCall;
import com.xse.sesame.rpc.bridge.NewRpcBridge;
import com.xse.sesame.rpc.bridge.OldRpcBridge;
import com.xse.sesame.data.AppConfig;
import com.xse.sesame.rpc.bridge.RpcBridge;
import com.xse.sesame.rpc.bridge.RpcVersion;
import com.xse.sesame.rpc.intervallimit.RpcIntervalLimit;
import com.xse.sesame.util.ClassUtil;
import com.xse.sesame.util.FileUtil;
import com.xse.sesame.util.Log;
import com.xse.sesame.util.NotificationUtil;
import com.xse.sesame.util.PermissionUtil;
import com.xse.sesame.util.Statistics;
import com.xse.sesame.util.Status;
import com.xse.sesame.util.StringUtil;
import com.xse.sesame.util.TimeUtil;
import com.xse.sesame.util.idMap.UserIdMap;
import lombok.Getter;

import androidx.annotation.NonNull;

public class ApplicationHook {

    private static final String TAG = ApplicationHook.class.getSimpleName();

    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;

    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();

    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();

    @Getter
    private static ClassLoader classLoader = null;

    @Getter
    private static Object microApplicationContextObject = null;

    // 新增：全局静态变量，存储当前进程名
    public static String processName; // 供其他方法（如 startIfNeeded）调用

    /** 模块 App 自己的包名：须与 app/build.gradle 的 applicationId 一致，用于校验广播发送方 */
    private static final String MODULE_PACKAGE_NAME = "com.xse.sesame";
    /** adb shell 的 uid：`adb shell am broadcast` 以它发送，放行以便命令行调试 */
    private static final int SHELL_UID = 2000;

    @Getter
    private static Context context = null; // 全局上下文，对应 Kotlin 的 appContext
    @SuppressLint("StaticFieldLeak")
    private static volatile Service service; // 目标 Service 实例，也是 Context 子类

    @Getter
    private static AlipayVersion alipayVersion = new AlipayVersion("");

    @Getter
    private static volatile boolean hooked = false;

    private static volatile boolean init = false;

    /** 标记一次重载是否正在进行，避免重载期间被主线程反复丢后台线程造成重复初始化 */
    private static volatile boolean initializing = false;

    private static volatile Calendar dayCalendar;

    @Getter
    private static volatile boolean offline = false;

    @Getter
    private static final AtomicInteger reLoginCount = new AtomicInteger(0);

    @Getter
    private static Handler mainHandler;

    private static BaseTask mainTask;

    private static RpcBridge rpcBridge;

    @Getter
    private static RpcVersion rpcVersion;

    private static PowerManager.WakeLock wakeLock;

    private static PendingIntent alarm0Pi;

    private static XC_MethodHook.Unhook rpcRequestUnhook;

    private static XC_MethodHook.Unhook rpcResponseUnhook;

    // ---- 抓包(HTTP/WebView + MTOP)hook 句柄与安装标记, 用于卸载与幂等安装 ----
    private static XC_MethodHook.Unhook webviewCaptureUnhook;

    private static XC_MethodHook.Unhook okhttpCaptureUnhook;

    private static volatile boolean captureHooksInstalled = false;

    /** HTTP 层最近打印记录, 用于去重(MTOP 层与 okhttp 层可能同时命中同一请求) */
    private static volatile String lastHttpCaptureKey = "";

    /** MTOP 抓包 hook 安装标记 */
    private static volatile boolean mtopDumpHooksInstalled = false;

    /** MTOP 抓包重试次数(mtopsdk 启动早期可能尚未加载) */
    private static final AtomicInteger mtopRetryCount = new AtomicInteger(0);

    /** MTOP dump 去重窗口: 同一 builder 在窗口内只打印一次, 防止 build 兜底与 sync/async 双通道重复刷屏 */
    private static final Map<Object, Long> mtopDumpRecent =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, Long>());

    private static final long MTOP_DUMP_DEDUP_MS = 1000L;

    private static BroadcastReceiver broadcastReceiver = null;

    private static volatile boolean broadcastReceiverRegistered = false;

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 先提取进程名并赋值给全局变量
        processName = lpparam.processName; // 新增：将 Xposed 提供的进程名赋值给全局变量
        // 支付宝子进程(小程序容器/沙箱等)只安装抓包 hook, 不跑模型与任务
        if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName)
                && !ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
            handleSubProcessLoadPackage(lpparam.classLoader);
            return;
        }
        if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName) && ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
            if (hooked) {
                return;
            }
            classLoader = lpparam.classLoader;

            XHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    context = (Context) param.args[0];
                    alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                    try {
                        AlipayMiniMarkHelper.init(classLoader);
                        AuthCodeHelper.init(classLoader);
                        // 启动时不再调用 getAuthCode：返回值本就被丢弃，而它在当前支付宝版本上必然失败
                        //（自建实例未走宿主依赖注入，内部 facade 为 null），只会在日志里留下噪音
                        // 直接同步调用：此前试过的异步写法未采用，勿据旧注释以为此处不阻塞
                        initSimplePageManager();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    super.afterHookedMethod(param);
                }
            });
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion", classLoader.loadClass(ClassUtil.H5PAGE_NAME), Map.class, String.class, XC_MethodReplacement.returnConstant(false));
                Log.i(TAG, "hook matchVersion successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook matchVersion err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Activity onResume");
                        // 回首页立即让自动切号调度器重新探测（切号成功/离线恢复后触发下一轮）
                        AccountSwitchController.hostReady();
                        String targetUid = getUserId();
                        if (targetUid == null) {
                            Log.record("用户未登录");
                            Toast.show("用户未登录");
                            return;
                        }
                        if (!init) {
                            // 重载在后台线程执行，加载成功后会自行置 init=true；此处无需依赖返回值
                            initHandler(true);
                            return;
                        }
                        String currentUid = UserIdMap.getCurrentUid();
                        if (!targetUid.equals(currentUid)) {
                            if (currentUid != null) {
                                ApplicationHook.getMainHandler().postDelayed(() -> {
                                    Log.record("用户已切换");
                                    Toast.show("用户已切换");
                                    initHandler(true);
                                }, 1000);
                                return;
                            }
                            UserIdMap.initUser(targetUid);
                        }
                        if (offline) {
                            offline = false;
                            execHandler();
                            ((Activity) param.thisObject).finish();
                            Log.i(TAG, "Activity reLogin");
                        }
                    }
                });
                Log.i(TAG, "hook login successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook login err:", t);
            }
            try {
                XHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate", new XC_MethodHook() {

                    @SuppressLint({"WakelockTimeout", "UnsafeDynamicallyLoadedCode"})
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 1. 获取目标 Service 实例（appService 是 Service 子类，也是 Context 类型）
                        Service appService = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                            return;// 非目标 Service，直接返回，保障只处理支付宝前台服务
                        }

                        // 2. 兜底赋值全局 context（对应 Kotlin appContext 的二次赋值）
                        context = appService.getApplicationContext(); // 获取应用全局上下文，更新全局变量
                        service = appService; // 存储 Service 实例，供后续复用

                        // 3. 调用 registerBroadcastReceiver，传入参数 Context（appService）
                        // 这里的 appService 就是对应 Kotlin registerBroadcastReceiver(appContext!!) 的参数
                        registerBroadcastReceiver(appService);

                        // 主动通知 App 本模块已被 LSPosed 启用并注入支付宝，用于显示「已激活」
                        try {
                            appService.sendBroadcast(new Intent("com.xse.sesame.status"));
                        } catch (Throwable t) {
                            // 广播失败会导致 UI 迟迟显示「未激活」，留一行便于排查
                            Log.i(TAG, "发送激活状态广播失败: " + t);
                        }

                        Log.i(TAG, "Service onCreate");
                        context = appService.getApplicationContext();
                        service = appService;
                        // 自动切号：接入宿主桥（读账号/切号走反射接口），调度器每秒 tick 轮询
                        AccountSwitchController.configure(new AccountSwitchController.Host() {
                            @Override
                            public String currentUid() {
                                // 宿主当前实际登录账号（AuthService.userId）
                                return ApplicationHook.getUserId();
                            }

                            @Override
                            public boolean initialize(String uid, TaskLifecycle.Freeze freeze) {
                                // 切号成功：登记目标账号 uid 并重载其配置（restart 广播 -> initHandler(true)）
                                UserIdMap.initUser(uid);
                                restartByBroadcast();
                                return true;
                            }

                            @Override
                            public String readiness() {
                                // reLogin 熔断：离线重连/未登录期间暂停自动切号，避免切号打断重试链
                                if (isOffline() || getUserId() == null) {
                                    return "WAIT_IDENTITY";
                                }
                                return "READY";
                            }

                            @Override
                            public void resume() {
                                // 解冻后恢复任务执行
                                ModelTask.startAllTask(false);
                            }
                        });
                        AccountSwitchController.hostReady();
                        mainHandler = new Handler(Looper.getMainLooper());
                        mainTask = BaseTask.newInstance("MAIN_TASK", new Runnable() {

                            private volatile long lastExecTime = 0;

                            @Override
                            public void run() {
                                int checkInterval = 0;
                                try {
                                    checkInterval = BaseModel.getCheckInterval().getValue();
                                    if (!init) {
                                        return;
                                    }
                                    Log.record("应用版本：" + alipayVersion.getVersionString());
                                    Log.record("模块版本：" + modelVersion);
                                    Log.record("开始执行");
                                    if (lastExecTime + 2000 > System.currentTimeMillis()) {
                                        Log.record("执行间隔较短，跳过执行");
                                        execDelayedHandler(checkInterval);
                                        return;
                                    }
                                    updateDay();
                                    String targetUid = getUserId();
                                    String currentUid = UserIdMap.getCurrentUid();
                                    if (targetUid == null || currentUid == null) {
                                        Log.record("用户为空，放弃执行");
                                        reLogin();
                                        return;
                                    }
                                    if (!targetUid.equals(currentUid)) {
                                        Log.record("开始切换用户");
                                        Toast.show("开始切换用户");
                                        reLogin();
                                        return;
                                    }
                                    lastExecTime = System.currentTimeMillis();
                                    try {
                                        FutureTask<Boolean> checkTask = new FutureTask<>(AntMemberRpcCall::check);
                                        Thread checkThread = new Thread(checkTask);
                                        checkThread.start();
                                        if (!checkTask.get(10, TimeUnit.SECONDS)) {
                                            long waitTime = 10000 - System.currentTimeMillis() + lastExecTime;
                                            if (waitTime > 0) {
                                                Thread.sleep(waitTime);
                                            }
                                            Log.record("执行失败：检查超时");
                                            reLogin();
                                            return;
                                        }
                                        reLoginCount.set(0);
                                    } catch (InterruptedException | ExecutionException |
                                             TimeoutException e) {
                                        Log.record("执行失败：检查中断");
                                        reLogin();
                                        return;
                                    } catch (Exception e) {
                                        Log.record("执行失败：检查异常");
                                        reLogin();
                                        Log.printStackTrace(TAG, e);
                                        return;
                                    }
                                    TaskCommon.update();
                                    ModelTask.startAllTask(false);
                                    lastExecTime = System.currentTimeMillis();

                                    try {
                                        List<String> execAtTimeList = BaseModel.getExecAtTimeList().getValue();
                                        if (execAtTimeList != null) {
                                            Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime);
                                            Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime + checkInterval);
                                            for (String execAtTime : execAtTimeList) {
                                                Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                                                if (execAtTimeCalendar != null && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0 && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                                                    Log.record("设置定时执行:" + execAtTime);
                                                    execDelayedHandler(execAtTimeCalendar.getTimeInMillis() - lastExecTime);
                                                    FileUtil.clearLog();
                                                    return;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.err(TAG, "execAtTime err:", e);
                                    }

                                    execDelayedHandler(checkInterval);
                                    FileUtil.clearLog();
                                } catch (Exception e) {
                                    Log.record("执行异常:");
                                    Log.printStackTrace(e);
                                } finally {
                                    // 单链之下"本轮没人排期"就等于永久停摆：兜住 !init、各处 return 与未捕获异常
                                    if (!tickScheduled) {
                                        execDelayedHandler(checkInterval > 0 ? checkInterval : FALLBACK_INTERVAL);
                                    }
                                }
                            }
                        });
                        dayCalendar = Calendar.getInstance();
                        Statistics.load();
                        FriendWatch.load();
                        // 重载在后台线程执行，加载成功后会自行置 init=true；此处无需依赖返回值
                        initHandler(true);
                    }
                });
                Log.i(TAG, "hook service onCreate successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook service onCreate err:", t);
            }
            try {
                XHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Service service = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName())) {
                            return;
                        }
                        Log.record("支付宝前台服务被销毁");
                        NotificationUtil.updateStatusText("支付宝前台服务被销毁");
                        destroyHandler(true);
                        FriendWatch.unload();
                        Statistics.unload();
                        restartByBroadcast();
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook service onDestroy err:", t);
            }
            // ---- 宿主的前后台询问：默认仍按原逻辑"谎报"，唯独风控/滑块链路在真实后台时如实回答 ----
            // 原先这四个 hook 一律哄宿主"你在前台"，模块的后台任务（H5/RPC）才跑得动；
            // 副作用是滑块验证也被判定为可展示，而后台拿不到可见窗口 →
            // 滑块界面出不来、验证流程一直等用户滑动 → 切回支付宝即卡死。
            // 现在改为 after 阶段观察真值：先让宿主的原方法跑完，再从返回值里读真实前后台状态，
            // 只有"真在后台 + 询问方是风控/滑块链路"才说实话。
            // 注意不能用 callOriginal() 取真值：自研兼容层从不给 MethodHookParam.originalCall 赋值，
            // before 阶段调它必抛 IllegalStateException（日志里的"取真实前后台状态失败"即此事）。
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 1 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 2 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackgroundV2", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 3 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk", classLoader.loadClass("android.content.Context"), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(answerAtFrontDeskQuestion(param));
                    }
                });
                Log.i(TAG, "hook MiscUtils successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook MiscUtils err:", t);
            }
            hooked = true;
            Log.i(TAG, "load success: " + lpparam.packageName);
        }
    }

    private static void setWakenAtTimeAlarm() {
        try {
            unsetWakenAtTimeAlarm();
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                    Log.record("设置定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.err(TAG, "setWakenAt0 err:", e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                Calendar nowCalendar = Calendar.getInstance();
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, i, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = i + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                    Log.record("设置定时唤醒:" + wakenAtTimeKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.err(TAG, "setWakenAtTime err:", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "setWakenAtTimeAlarm err:", e);
        }
    }

    private static void unsetWakenAtTimeAlarm() {
        try {
            for (Map.Entry<String, PendingIntent> entry : wakenAtTimeAlarmMap.entrySet()) {
                try {
                    String wakenAtTimeKey = entry.getKey();
                    PendingIntent wakenAtTimePendingIntent = entry.getValue();
                    if (unsetAlarmTask(wakenAtTimePendingIntent)) {
                        wakenAtTimeAlarmMap.remove(wakenAtTimeKey);
                        Log.record("取消定时唤醒:" + wakenAtTimeKey);
                    }
                } catch (Exception e) {
                    Log.err(TAG, "unsetWakenAtTime err:", e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.record("取消定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.err(TAG, "unsetWakenAt0 err:", e);
            }
        } catch (Exception e) {
            Log.err(TAG, "unsetWakenAtTimeAlarm err:", e);
        }
    }

    @SuppressLint("WakelockTimeout")
    /**
     * 切换账号 / 首启的重载入口。
     * 重载（force=true）包含大量文件 IO、整份配置 JSON 反序列化、反射建 Model、逐 Model 装 Hook，
     * 这些若在「主线程」同步执行会把支付宝界面卡住（表现为"切号卡死不动"）。
     * 因此这里只做需要 UI 反馈的快速前置检查，真正的重活统一交给 {@link #runInit} 在后台线程执行。
     */
    private Boolean initHandler(Boolean force) {
        if (service == null) {
            return false;
        }
        // 快速前置检查：未登录 / 无闹钟权限，留在调用线程同步返回（Toast 内部已切主线程，后台调用也安全）
        if (force) {
            String userId = getUserId();
            if (userId == null) {
                Log.record("用户未登录");
                Toast.show("用户未登录");
                return false;
            }
            if (!PermissionUtil.checkAlarmPermissions()) {
                Log.record("支付宝无闹钟权限");
                mainHandler.postDelayed(() -> {
                    if (!PermissionUtil.checkOrRequestAlarmPermissions(context)) {
                        android.widget.Toast.makeText(context, "请授予支付宝使用闹钟权限", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }, 2000);
                return false;
            }
        }
        // 主线程调用则丢到后台线程执行，避免卡 UI；广播重启等已在后台线程的场景直接同步执行
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (initializing) {
                return false;
            }
            initializing = true;
            final Boolean f = force;
            new Thread(() -> runInit(f), "Sesame-InitHandler").start();
            return null;
        }
        return runInit(force);
    }

    /**
     * 真正执行重载，必须在非主线程调用。UI 相关（Toast / 权限提示）已内部切回主线程，
     * 故整体跑在后台线程是安全的。
     * synchronized 保证同一时刻只有一处重载，防止切号与首启 / 广播重启并发触发重复初始化。
     */
    private synchronized Boolean runInit(Boolean force) {
        destroyHandler(force);
        try {
            if (force) {
                String userId = getUserId();
                if (userId == null) {
                    Log.record("用户未登录");
                    Toast.show("用户未登录");
                    return false;
                }

                //调用 startIfNeeded 方法，参数与 Kotlin 保持一致
                ModuleHttpServerManager.getInstance().startIfNeeded(8080, "ET3vB^#td87sQqKaY*eMUJXP", processName, "com.eg.android.AlipayGphone");

                UserIdMap.initUser(userId);
                Model.initAllModel();
                Log.record("模块版本：" + modelVersion);
                Log.record("开始加载");
                ConfigV2.load(userId);

                boolean enableModule = Model.getModel(BaseModel.class).getEnableField().getValue();
                if (!enableModule) {
                    Log.record("芝麻粒已禁用");
                    Toast.show("芝麻粒已禁用");
                    return false;
                }
                if (com.xse.sesame.data.AppConfig.INSTANCE.getBatteryPerm() && !init && !PermissionUtil.checkBatteryPermissions()) {
                    Log.record("支付宝无始终在后台运行权限");
                    mainHandler.postDelayed(() -> {
                        if (!PermissionUtil.checkOrRequestBatteryPermissions(context)) {
                            android.widget.Toast.makeText(context, "请授予支付宝终在后台运行权限", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }, 2000);
                }
                if (AppConfig.INSTANCE.getNewRpc()) {
                    rpcBridge = new NewRpcBridge();
                } else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                if (BaseModel.getStayAwake().getValue()) {
                    try {
                        PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                        wakeLock.acquire();
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                }
                setWakenAtTimeAlarm();
                // 抓包(ariver 容器 RPC + mtopsdk MTOP + okhttp/WebView 兜底)统一安装, 记录并入「抓包记录」
                if (isCaptureEnabled()) {
                    ensureDebugLogOn();
                    installAllCaptureHooks(classLoader);
                }
                NotificationUtil.start(service);
                CaptchaHook.setupHook(classLoader);
                Model.bootAllModel(classLoader);
                Status.load();
                TokenConfig.load();
                updateDay();
                BaseModel.initData();
                BaseModel.initRpcRequest();
                Log.record("加载完成");
                Toast.show("芝麻粒加载成功");
                init = true;
            }
            offline = false;
            execHandler();
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "startHandler err:", th);
            Toast.show("芝麻粒加载失败");
            return false;
        } finally {
            // 无论成功/失败/未登录，都复位守卫，允许后续（如切号）再次触发重载
            initializing = false;
        }
    }

    /**
     * 抓包总开关(启动期判定): 「开启抓包(基于新接口)」或「抓包记录」任一打开即安装抓包 hook。
     * <p>hook 只负责记录, 是否可见由日志分项开关控制; 「抓包记录」打开即写入 debug 日志。
     */
    private static boolean isCaptureEnabled() {
        if (!BaseModel.getNewRpc().getValue()) {
            return false;
        }
        return BaseModel.getDebugMode().getValue()
                || Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableDebugLog());
    }

    /**
     * 运行期(收到 reloadConfig 广播后)判定: 以 AppConfig(已落盘, 跨进程即时可见)为准,
     * 避免读到注入进程内存里尚未刷新的 BaseModel 旧值。
     */
    private static boolean isCaptureEnabledRuntime() {
        return Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableDebugLog())
                || BaseModel.getDebugMode().getValue();
    }

    /**
     * 抓包记录可见性兜底: 抓包内容走 Log.debug 写「抓包记录」, 若该项开关关闭则自动打开,
     * 否则 hook 装上了记录也永远为空。
     */
    private static void ensureDebugLogOn() {
        try {
            if (!Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableDebugLog())) {
                AppConfig.INSTANCE.setEnableDebugLog(true);
                AppConfig.save();
                Log.i(TAG, "auto enable debug log for capture");
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /** 安装全部抓包 hook: ariver 容器 RPC + mtopsdk MTOP + okhttp/WebView 兜底; 全部只读记录, 不改动请求。 */
    private static void installAllCaptureHooks(ClassLoader cl) {
        if (cl == null) {
            return;
        }
        installAriverRpcCaptureHooks(cl);
        installMtopDumpHooks(cl);
        installHttpCaptureHooks(cl);
    }

    /**
     * 安装 ariver 容器 RPC 抓包 hook(RpcBridgeExtension.rpc 请求 + DefaultBridgeCallback.sendJSONResponse 响应),
     * 覆盖支付宝小程序 my.call/XRiver 链路; 记录写入「抓包记录」。
     */
    private static void installAriverRpcCaptureHooks(ClassLoader cl) {
        if (cl == null || rpcRequestUnhook != null || rpcResponseUnhook != null) {
            return;
        }
        try {
            rpcRequestUnhook = XHelpers.findAndHookMethod("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", cl, "rpc", String.class, boolean.class, boolean.class, String.class, cl.loadClass(ClassUtil.JSON_OBJECT_NAME), String.class, cl.loadClass(ClassUtil.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, cl.loadClass("com.alibaba.ariver.app.api.App"), cl.loadClass("com.alibaba.ariver.app.api.Page"), cl.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), cl.loadClass("com.alibaba.ariver.engine.api.bridge.extension" + ".BridgeCallback"), new XC_MethodHook() {

                @SuppressLint("WakelockTimeout")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object[] args = param.args;
                    Object object = args[15];
                    Object[] recordArray = new Object[4];
                    recordArray[0] = System.currentTimeMillis();
                    recordArray[1] = args[0];
                    recordArray[2] = args[4];
                    rpcHookMap.put(object, recordArray);
                }

                @SuppressLint("WakelockTimeout")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.args[15];
                    Object[] recordArray = rpcHookMap.remove(object);
                    if (recordArray != null) {
                        Log.debug("记录\n时间: " + recordArray[0] + "\n方法: " + recordArray[1] + "\n参数: " + recordArray[2] + "\n数据: " + recordArray[3] + "\n");
                    } else {
                        Log.debug("删除记录ID: " + object.hashCode());
                    }
                }

            });
            Log.debug(TAG + ", hook record request successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook record request err:", t);
        }
        try {
            rpcResponseUnhook = XHelpers.findAndHookMethod("com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback", cl, "sendJSONResponse", cl.loadClass(ClassUtil.JSON_OBJECT_NAME), new XC_MethodHook() {

                @SuppressLint("WakelockTimeout")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.thisObject;
                    Object[] recordArray = rpcHookMap.get(object);
                    if (recordArray != null) {
                        recordArray[3] = String.valueOf(param.args[0]);
                    }
                }

            });
            Log.debug(TAG + ", hook record response successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook record response err:", t);
        }
    }

    /** 卸载全部抓包 hook(抓包开关关闭时调用)。 */
    private static void uninstallCaptureHooks() {
        if (rpcResponseUnhook != null) {
            try {
                rpcResponseUnhook.unhook();
            } catch (Throwable ignore) {
            }
            rpcResponseUnhook = null;
        }
        if (rpcRequestUnhook != null) {
            try {
                rpcRequestUnhook.unhook();
            } catch (Throwable ignore) {
            }
            rpcRequestUnhook = null;
        }
        if (webviewCaptureUnhook != null) {
            try {
                webviewCaptureUnhook.unhook();
            } catch (Throwable ignore) {
            }
            webviewCaptureUnhook = null;
        }
        if (okhttpCaptureUnhook != null) {
            try {
                okhttpCaptureUnhook.unhook();
            } catch (Throwable ignore) {
            }
            okhttpCaptureUnhook = null;
        }
        captureHooksInstalled = false;
        lastHttpCaptureKey = "";
        mtopDumpHooksInstalled = false;
    }

    /**
     * 安装 MTOP 抓包 hook: hook mtopsdk MtopBuilder 的全部 syncRequest/asyncRequest 重载,
     * 打印请求 api/版本/dataText 与响应 retCode/retMsg, 覆盖支付宝内所有走 mtopsdk 的接口
     * (蚂蚁森林等页面走的就是 MTOP; 含 customDomain=mtop.ele.me 的饿了么链路), 无需 root 抓包工具。
     * <p>仅记录, 不改动请求/响应; mtopsdk 启动早期未加载时自动重试。
     */
    private static void installMtopDumpHooks(final ClassLoader cl) {
        if (mtopDumpHooksInstalled || cl == null) {
            return;
        }
        try {
            Class<?> mtopBuilderClazz = XHelpers.findClassIfExists("mtopsdk.mtop.intf.MtopBuilder", cl);
            if (mtopBuilderClazz == null) {
                Log.i(TAG, "mtopsdk.mtop.intf.MtopBuilder not ready, retry later");
                scheduleMtopRetry(cl);
                return;
            }
            int overloads = hookAllMtopSendMethods(mtopBuilderClazz);
            if (overloads == 0) {
                // 发送方法一个都没挂上, 退化为 hook 静态 Mtop.build(MtopBuilder)
                Log.i(TAG, "no sync/async send method hooked, enable Mtop.build fallback");
                hookMtopBuild(cl);
            }
            mtopDumpHooksInstalled = true;
            Log.debug(TAG + ", install mtop dump hooks successfully, sendOverloads=" + overloads);
        } catch (Throwable t) {
            Log.err(TAG, "install mtop dump hooks err:", t);
        }
    }

    /** MtopBuilder 延迟重试: 启动早期 mtopsdk 可能尚未加载, 每 4s 重试一次, 最多 5 次。 */
    private static void scheduleMtopRetry(final ClassLoader cl) {
        if (mtopRetryCount.getAndIncrement() >= 5) {
            return;
        }
        try {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    installMtopDumpHooks(cl);
                }
            }, 4000L);
        } catch (Throwable t) {
            Log.err(TAG, "schedule mtop retry err:", t);
        }
    }

    /** MtopBuilder 上名为 syncRequest/asyncRequest(含各类重载/变体) 的全部方法逐一挂 hook。 */
    private static int hookAllMtopSendMethods(Class<?> clazz) {
        int hooked = 0;
        try {
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                String n = m.getName();
                if (!n.equals("syncRequest") && !n.equals("asyncRequest")
                        && !n.startsWith("syncRequest") && !n.startsWith("asyncRequest")) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    XHelpers.hookMember(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            logMtopRequestDump(param.thisObject, "MTOP");
                            logMtopResponseDump(param);
                        }
                    });
                    hooked++;
                } catch (Throwable t) {
                    Log.i(TAG, "hook overload " + n + " err: " + t);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "enumerate MtopBuilder methods err:", t);
        }
        return hooked;
    }

    /** 兜底: hook 静态 Mtop.build(MtopBuilder), 保证任何版本/任何发送路径都能捕获请求。 */
    private static void hookMtopBuild(ClassLoader cl) {
        try {
            Class<?> mtopClazz = XHelpers.findClassIfExists("mtopsdk.mtop.intf.Mtop", cl);
            if (mtopClazz == null) {
                return;
            }
            int hooked = 0;
            for (java.lang.reflect.Method m : mtopClazz.getDeclaredMethods()) {
                if (!m.getName().equals("build") || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    XHelpers.hookMember(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 仅对真正的 MtopBuilder 参数 dump, 避免 Mtop.build 内部半成品请求刷出空 REQ
                            if (param.args != null && param.args.length > 0 && param.args[0] != null
                                    && param.args[0].getClass().getName().equals("mtopsdk.mtop.intf.MtopBuilder")) {
                                logMtopRequestDump(param.args[0], "MTOP");
                            }
                        }
                    });
                    hooked++;
                } catch (Throwable t) {
                    Log.i(TAG, "hook Mtop.build err: " + t);
                }
            }
            Log.i(TAG, "hook Mtop.build fallback overloads=" + hooked);
        } catch (Throwable t) {
            Log.err(TAG, "locate Mtop.build err:", t);
        }
    }

    /** 打印 MTOP 请求的 api/版本/环境字段/完整 dataText(对照外部抓包软件可视化信息)。 */
    private static void logMtopRequestDump(Object builder, String tag) {
        if (builder == null) {
            return;
        }
        // 同一 builder 短时间窗口内只打印一次, 防止 Mtop.build 兜底与 sync/async 双通道重复刷屏
        long now = System.currentTimeMillis();
        Long last = mtopDumpRecent.get(builder);
        if (last != null && (now - last) < MTOP_DUMP_DEDUP_MS) {
            return;
        }
        mtopDumpRecent.put(builder, now);
        try {
            Object req = getMtopRequest(builder);
            String api = "", ver = "", data = "";
            if (req != null) {
                api = tryGetStr(req, "getApiName");
                ver = tryGetStr(req, "getVersion");
                data = tryGetStr(req, "getData", "getDataText");
            }
            if (data.length() == 0) {
                data = tryGetStr(builder, "getData");
            }
            String host = tryGetStr(builder, "getCustomHost", "getCustomDomain");
            String wua = tryGetStr(builder, "getNeedWua");
            String ttid = tryGetStr(builder, "getTtid");
            // getter 反射失败(宿主版本字段/方法改名)时, 退化为字段级反射: 直接从 req/builder 的字段里捞 api/data
            String extra = "";
            if (api.length() == 0 && data.length() == 0) {
                if (req != null) {
                    extra = dumpObjectFields(req);
                }
                if (extra.isEmpty()) {
                    extra = dumpObjectFields(builder);
                }
                if (api.length() == 0) {
                    api = pickField(extra, "apiName", "api");
                }
                if (data.length() == 0) {
                    data = pickField(extra, "data", "reqData", "requestData", "req");
                }
            }
            StringBuilder sb = new StringBuilder("\n[" + tag + " REQ]");
            if (api.length() > 0) {
                sb.append(" api=").append(api);
            }
            if (ver.length() > 0) {
                sb.append(" v=").append(ver);
            }
            sb.append(" host=").append(host)
                    .append(" wua=").append(wua)
                    .append(" ttid=").append(ttid);
            sb.append("\n").append(data);
            if (extra.length() > 0) {
                sb.append("\n").append(extra);
            }
            // 组装后仍是纯空壳(任何字段都没有) -> 跳过, 避免空 REQ 刷屏
            if (api.length() == 0 && ver.length() == 0 && data.length() == 0
                    && host.length() == 0 && wua.length() == 0 && ttid.length() == 0) {
                return;
            }
            Log.debug(sb.toString());
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /** 反射枚举对象全部字段(含父类、私有), 拼接 " 字段名=值"; 单字段过长或不可读时安全跳过。 */
    private static String dumpObjectFields(Object obj) {
        if (obj == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        java.util.Set<String> seen = new java.util.HashSet<String>();
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    try {
                        if (seen.contains(f.getName())) {
                            continue;
                        }
                        seen.add(f.getName());
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v == null) {
                            continue;
                        }
                        String s = String.valueOf(v);
                        if (s.length() == 0 || s.length() > 2000) {
                            continue;
                        }
                        if (sb.length() + s.length() > 8000) {
                            break;
                        }
                        sb.append(' ').append(f.getName()).append('=').append(s);
                    } catch (Throwable ignore) {
                    }
                }
            } catch (Throwable ignore) {
            }
            c = c.getSuperclass();
        }
        return sb.toString();
    }

    /** 从 dumpObjectFields 的输出里捞指定语义字段的值(按候选字段名), 用于 getter 失效时兜底。 */
    private static String pickField(String dump, String... fieldNames) {
        if (dump == null || dump.isEmpty()) {
            return "";
        }
        for (String fn : fieldNames) {
            int idx = dump.indexOf(' ' + fn + '=');
            if (idx < 0) {
                continue;
            }
            int start = idx + fn.length() + 2;
            int end = dump.indexOf(' ', start);
            if (end < 0) {
                end = dump.length();
            }
            String v = dump.substring(start, end).trim();
            if (v.length() > 0) {
                return v;
            }
        }
        return "";
    }

    /** 判断类上是否存在无参同名方法, 用于反射前安全校验。 */
    private static boolean hasNoArgMethod(Class<?> clazz, String name) {
        if (clazz == null) {
            return false;
        }
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (name.equals(m.getName()) && m.getParameterTypes().length == 0) {
                        return true;
                    }
                }
            } catch (Throwable ignore) {
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /** 打印 MTOP 同步响应。asyncRequest 的返回值是 mtopsdk.mtop.common.ApiID(仅是请求句柄, 无 getRetCode),
     *  非响应对象直接跳过, 避免每次异步请求都反射失败刷 ERROR 栈。 */
    private static void logMtopResponseDump(XC_MethodHook.MethodHookParam param) {
        try {
            Object resp = param.getResult();
            if (resp == null || !hasNoArgMethod(resp.getClass(), "getRetCode")) {
                return;
            }
            StringBuilder sb = new StringBuilder("\n[MTOP RESP]");
            sb.append(" retCode=").append(safeStr(XHelpers.callMethod(resp, "getRetCode")));
            sb.append(" retMsg=").append(safeStr(XHelpers.callMethod(resp, "getRetMsg")));
            try {
                Object djson = XHelpers.callMethod(resp, "getDataJsonObject");
                sb.append("\n").append(safeStr(djson));
            } catch (Throwable ignore) {
            }
            Log.debug(sb.toString());
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /** 依次调用多个候选 getter, 返回第一个非空值的字符串形式; 全部失败返回空串。 */
    private static String tryGetStr(Object obj, String... getterNames) {
        if (obj == null) {
            return "";
        }
        for (String gn : getterNames) {
            try {
                Object v = XHelpers.callMethod(obj, gn);
                if (v != null) {
                    return safeStr(v);
                }
            } catch (Throwable ignore) {
            }
        }
        return "";
    }

    /** 取 MtopBuilder 的 MtopRequest: 优先 getRequest(), 失败则按字段类型名反射查找。 */
    private static Object getMtopRequest(Object builder) {
        if (builder == null) {
            return null;
        }
        try {
            Object r = XHelpers.callMethod(builder, "getRequest");
            if (r != null) {
                return r;
            }
        } catch (Throwable ignore) {
        }
        try {
            for (java.lang.reflect.Field f : builder.getClass().getDeclaredFields()) {
                if (f.getType().getName().contains("MtopRequest")) {
                    f.setAccessible(true);
                    Object r = f.get(builder);
                    if (r != null) {
                        return r;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static String safeStr(Object o) {
        if (o == null) {
            return "";
        }
        try {
            return String.valueOf(o);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 安装 HTTP/WebView 层兜底抓包 hook(全 try/catch, 任一失败不影响其它 hook 与模块启动)。
     * <p>覆盖不走 mtopsdk 的 HTTP 链路(WebView XHR / OkHttp 直连), 与 MTOP 通道互为兜底, 记录写入「抓包记录」。
     */
    private static void installHttpCaptureHooks(final ClassLoader cl) {
        if (captureHooksInstalled || cl == null) {
            return;
        }
        captureHooksInstalled = true;

        // 1) WebViewClient.shouldInterceptRequest: 抓取 H5 页面发起的网络请求(URL 级)
        try {
            webviewCaptureUnhook = XHelpers.findAndHookMethod(
                    "android.webkit.WebViewClient", cl, "shouldInterceptRequest",
                    android.webkit.WebView.class, android.webkit.WebResourceRequest.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object req = param.args[1];
                                if (req == null) {
                                    return;
                                }
                                String url = String.valueOf(XHelpers.callMethod(req, "getUrl"));
                                String method = String.valueOf(XHelpers.callMethod(req, "getMethod"));
                                Log.debug("[WebView] " + method + " " + url + "\n");
                            } catch (Throwable t) {
                                Log.printStackTrace(t);
                            }
                        }
                    });
            Log.debug(TAG + ", hook webview shouldInterceptRequest successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook webview shouldInterceptRequest err:", t);
        }

        // 2) OkHttp3 Interceptor: 抓取 native HTTP 请求(含 URL/响应码), 覆盖 mtop 等普通 HTTP
        try {
            Class<?> okHttpClientClazz = XHelpers.findClassIfExists("okhttp3.OkHttpClient", cl);
            if (okHttpClientClazz != null) {
                okhttpCaptureUnhook = XHelpers.findAndHookMethod(
                        okHttpClientClazz, "newBuilder",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    addOkHttpCaptureInterceptor(param.getResult());
                                } catch (Throwable t) {
                                    Log.printStackTrace(t);
                                }
                            }
                        });
                Log.debug(TAG + ", hook okhttp newBuilder successfully");
                // 兜底: 直接 new OkHttpClient.Builder() 的构建路径, 确保任何 client 都能挂上抓包 Interceptor
                try {
                    Class<?> okHttpBuilderClazz = XHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", cl);
                    if (okHttpBuilderClazz != null) {
                        XHelpers.findAndHookConstructor(okHttpBuilderClazz, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    addOkHttpCaptureInterceptor(param.thisObject);
                                } catch (Throwable t) {
                                    Log.printStackTrace(t);
                                }
                            }
                        });
                        Log.debug(TAG + ", hook OkHttpClient.Builder() ctor successfully");
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "hook okhttp builder ctor err:", t);
                }
            } else {
                Log.debug(TAG + ", okhttp3.OkHttpClient not found, skip okhttp capture");
            }
        } catch (Throwable t) {
            Log.err(TAG, "hook okhttp newBuilder err:", t);
        }
    }

    /**
     * 往 okhttp3.Builder 追加一个抓包 Interceptor, 记录请求 URL 与响应码。
     * 借用反射调用 Builder.addInterceptor(Interceptor), Interceptor 本身用 InvocationHandler 动态代理实现,
     * 避免直接依赖 okhttp3 类型。
     */
    private static void addOkHttpCaptureInterceptor(Object builder) throws Throwable {
        if (builder == null) {
            return;
        }
        ClassLoader cl = builder.getClass().getClassLoader();
        Class<?> interceptorClazz = XHelpers.findClassIfExists("okhttp3.Interceptor", cl);
        Class<?> responseClazz = XHelpers.findClassIfExists("okhttp3.Response", cl);
        if (interceptorClazz == null || responseClazz == null) {
            return;
        }
        Object proxy = Proxy.newProxyInstance(
                interceptorClazz.getClassLoader(),
                new Class<?>[]{interceptorClazz},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (args != null && args.length == 1 && "intercept".equals(method.getName())) {
                            Object chain = args[0];
                            // 先无条件放行原链路并取得响应, 日志部分再 try/catch, 绝不因抓包失败破坏请求
                            Object request = XHelpers.callMethod(chain, "request");
                            Object response = XHelpers.callMethod(chain, "proceed", request);
                            try {
                                String methodName = String.valueOf(XHelpers.callMethod(request, "method"));
                                String url = String.valueOf(XHelpers.callMethod(request, "url"));
                                String code = "";
                                try {
                                    code = String.valueOf(XHelpers.callMethod(response, "code"));
                                } catch (Throwable ignore) {
                                }
                                // mtop 层与 okhttp 层可能同时命中同一请求, 连续重复则去重
                                String key = methodName + " " + url;
                                if (!key.equals(lastHttpCaptureKey)) {
                                    lastHttpCaptureKey = key;
                                    Log.debug("[HTTP] " + methodName + " " + url + " -> " + code + "\n");
                                }
                            } catch (Throwable t) {
                                Log.printStackTrace(t);
                            }
                            return response;
                        }
                        return null;
                    }
                });
        XHelpers.callMethod(builder, "addInterceptor", proxy);
    }

    /**
     * 支付宝子进程(小程序容器/沙箱等)入口: 仅安装抓包 hook, 不跑模型与任务。
     * <p>部分小程序(如饿了么果园)的请求发生在这些进程, 主进程 hook 覆盖不到, 这里统一纳入抓包范围。
     */
    private static void handleSubProcessLoadPackage(final ClassLoader cl) {
        try {
            try {
                AppConfig.load();
            } catch (Throwable ignore) {
            }
            if (!isCaptureEnabledRuntime()) {
                return;
            }
            installAllCaptureHooks(cl);
            Log.debug(TAG + ", sub-process capture hooks installed: " + processName);
        } catch (Throwable t) {
            Log.err(TAG, "handleSubProcessLoadPackage err:", t);
        }
    }

    private synchronized static void destroyHandler(Boolean force) {
        try {
            if (force) {
                if (service != null) {
                    stopHandler();
                    BaseModel.destroyData();
                    Status.unload();
                    NotificationUtil.stop();
                    RpcIntervalLimit.clearIntervalLimit();
                    ConfigV2.unload();
                    Model.destroyAllModel();
                    UserIdMap.unload();
                }
                uninstallCaptureHooks();
                if (wakeLock != null) {
                    wakeLock.release();
                    wakeLock = null;
                }
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
            } else {
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
            Log.err(TAG, "stopHandler err:", th);
        }
    }

    private static void execHandler() {
        startMainTask();
    }

    /** 起跳失败或取不到间隔时的兜底排期间隔（下限 1 分钟，避免自旋） */
    private static final long FALLBACK_INTERVAL = 60_000;

    /** 起跳的公共实现：线程起不来等异常不能逃到宿主主线程（执行槽已由 BaseTask 归还） */
    private static void startMainTask() {
        try {
            if (Boolean.TRUE.equals(mainTask.startTask(false)) || tickScheduled) {
                return;
            }
            // 没跑起来（check 不通过等）又无人排期：兜底续排，否则链断
            execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), FALLBACK_INTERVAL));
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /** 是否已有待发的下一跳：单链之下既用它兜底续排，又不覆盖本轮更早的显式排期（定时执行、reLogin 快重试） */
    private static volatile boolean tickScheduled = false;

    /** 待发 tick 的预定触发时刻，仅用于"排期被覆盖"的日志归因 */
    private static volatile long scheduledExecAt = 0;

    /** 延迟触发的一跳：提成常量才能在下一次排期前 removeCallbacks 掉旧链（重载/切号会重建 mainTask） */
    private static final Runnable MAIN_TICK = () -> {
        tickScheduled = false;
        scheduledExecAt = 0;
        try {
            NotificationUtil.setRunning();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        startMainTask();
    };

    private static void execDelayedHandler(long delayMillis) {
        // 调度时立即记录下次执行时间，所有任务完成时 updateLastExecText 会一并写入
        try {
            NotificationUtil.updateNextExecText(System.currentTimeMillis() + delayMillis);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        long newExecAt = System.currentTimeMillis() + delayMillis;
        if (tickScheduled) {
            // 后写覆盖前写（如 reLogin 覆盖"定时执行"）会让原排期静默失效，记一行便于归因
            Log.record("排期被覆盖：原定 " + TimeUtil.getTimeStr(scheduledExecAt) + " → 改为 " + TimeUtil.getTimeStr(newExecAt));
        }
        // 先摘掉上一条排期，否则每次重载/切号都会多留一条幽灵链，每个 interval 触发两次
        mainHandler.removeCallbacks(MAIN_TICK);
        mainHandler.postDelayed(MAIN_TICK, delayMillis);
        scheduledExecAt = newExecAt;
        tickScheduled = true;
    }

    private static void stopHandler() {
        mainTask.stopTask();
        ModelTask.stopAllTask();
        // 一并清掉待发 tick：否则重载后新周期第一轮会误判「已排期」而跳过续排，下一跳由上一周期的时刻决定
        mainHandler.removeCallbacks(MAIN_TICK);
        tickScheduled = false;
        scheduledExecAt = 0;
    }

    public static void updateDay() {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                setWakenAtTimeAlarm();
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Statistics.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            FriendWatch.updateDay();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    @SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.i("setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + (operation == null ? "" : operation.toString()));
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "setAlarmTask err:", th);
        }
        return false;
    }

    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "unsetAlarmTask err:", th);
        }
        return false;
    }

    /**
     * 替换 RPC 实现（离线模式、诊断、单元测试注入替身用）；传 null 表示回到默认的支付宝 RPC 桥。
     * <p>不注入时行为与原先完全一致：一律转发给 startHandler 里创建的 {@code rpcBridge}。
     * <p>注入替身后，各 RpcCall 构造出的请求体（method + data）会原样交给替身，
     * 因此可以在不连真机的情况下检查请求体本身是否正确。
     */
    public static void setRpcBridge(RpcBridge bridge) {
        rpcBridge = bridge;
    }

    /**
     * 确保 rpcBridge 可用；为 null（如前台服务被系统销毁后 destroyHandler 清空且未重建）时
     * 按当前配置懒初始化重建。返回 false 表示仍不可用，调用方应返回失败而非 NPE。
     */
    private static synchronized boolean ensureRpcBridge() {
        if (rpcBridge != null) {
            return true;
        }
        try {
            if (com.xse.sesame.data.AppConfig.INSTANCE.getNewRpc()) {
                rpcBridge = new NewRpcBridge();
            } else {
                rpcBridge = new OldRpcBridge();
            }
            rpcBridge.load();
            rpcVersion = rpcBridge.getVersion();
            Log.i(TAG, "rpcBridge lazily initialized");
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "lazy init rpcBridge err:", t);
            rpcBridge = null;
            return false;
        }
    }

    public static String requestString(RpcEntity rpcEntity) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(rpcEntity, 3, -1);
    }

    public static String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(rpcEntity, tryCount, retryInterval);
    }

    public static String requestString(String method, String data) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(method, data);
    }

    public static String requestString(String method, String data, String relation) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(method, data, relation);
    }

    public static String requestString(String method, String data, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(method, data, tryCount, retryInterval);
    }

    public static String requestString(String method, String data, String relation, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestString(method, data, relation, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(RpcEntity rpcEntity) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(rpcEntity, 3, -1);
    }

    public static RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(rpcEntity, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(String method, String data) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(method, data);
    }

    public static RpcEntity requestObject(String method, String data, String relation) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(method, data, relation);
    }

    public static RpcEntity requestObject(String method, String data, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(method, data, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(String method, String data, String relation, int tryCount, int retryInterval) {
        if (!ensureRpcBridge()) {
            return null;
        }
        return rpcBridge.requestObject(method, data, relation, tryCount, retryInterval);
    }

    public static void reLoginByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.reLogin"));
        } catch (Throwable th) {
            Log.err(TAG, "sesame sendBroadcast reLogin err:", th);
        }
    }

    public static void restartByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.err(TAG, "sesame sendBroadcast restart err:", th);
        }
    }

    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }

    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            return microApplicationContextObject = XHelpers.callMethod(XHelpers.callStaticMethod(XHelpers.findClass("com.alipay.mobile.framework.AlipayApplication", classLoader), "getInstance"), "getMicroApplicationContext");
        }
        return microApplicationContextObject;
    }

    public static Object getServiceObject(String service) {
        try {
            return XHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.err(TAG, "getServiceObject err", th);
        }
        return null;
    }

    public static Object getUserObject() {
        try {
            return XHelpers.callMethod(getServiceObject(XHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()), "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.err(TAG, "getUserObject err", th);
        }
        return null;
    }

    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.err(TAG, "getUserId err", th);
        }
        return null;
    }

    public static void reLogin() {
        mainHandler.post(() -> {
            if (reLoginCount.get() < 5) {
                execDelayedHandler(reLoginCount.getAndIncrement() * 5000L);
            } else {
                execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(ClassUtil.PACKAGE_NAME, ClassUtil.CURRENT_USING_ACTIVITY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            offline = true;
            context.startActivity(intent);
        });
    }

    private class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Receiver 为运行时注册，Android 13+ 必须 RECEIVER_EXPORTED（模块 App 与支付宝是不同
            // UID，跨进程送达只能靠导出），所以"任意应用都能触发 restart/reLogin"只能在此按 uid 拦
            if (!isTrustedBroadcastSender(context, this)) {
                Log.record("广播来源不在白名单，已忽略#" + action);
                return;
            }
            Log.i("sesame broadcast action:" + action + " intent:" + intent);
            if (action != null) {
                switch (action) {
                    case "com.eg.android.AlipayGphone.sesame.restart":
                        String userId = intent.getStringExtra("userId");
                        if (StringUtil.isEmpty(userId) || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                            BroadcastReceiver.PendingResult r = goAsync();
                            new Thread(() -> {
                                try {
                                    initHandler(true);
                                } catch (Throwable th) {
                                    Log.printStackTrace(TAG, th);
                                }
                                r.finish();
                            }, "Sesame-Restart").start();
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.execute":
                        // 配置页"执行"按钮会带 group（ModelGroup 的 code）：BASE＝全部任务，
                        // 其余只跑该分组的任务；不带 group 时保持原行为（整轮执行）。
                        String groupCode = intent.getStringExtra("group");
                        BroadcastReceiver.PendingResult r2 = goAsync();
                        new Thread(() -> {
                            try {
                                if (StringUtil.isEmpty(groupCode)) {
                                    initHandler(false);
                                } else if (ModelGroup.BASE == ModelGroup.getByCode(groupCode)) {
                                    ModelTask.stopAllTask();
                                    ModelTask.startAllTask(false);
                                    Log.record("开始执行全部任务");
                                } else {
                                    ModelTask.stopAllTask();
                                    int count = ModelTask.startGroupTask(groupCode);
                                    Log.record("开始执行分组【" + ModelGroup.getName(groupCode) + "】任务: " + count + " 个");
                                }
                            } catch (Throwable th) {
                                Log.printStackTrace(TAG, th);
                            }
                            r2.finish();
                        }, "Sesame-Execute").start();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reLogin":
                        reLogin();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.status":
                        try {
                            Log.i(TAG, "broadcast: recv query, send active status");
                            context.sendBroadcast(new Intent("com.xse.sesame.status"));
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame sendBroadcast status err:", th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.rpctest":
                        try {
                            String method = intent.getStringExtra("method");
                            String data = intent.getStringExtra("data");
                            String type = intent.getStringExtra("type");
                            // Log.record("收到测试消息:\n方法:" + method + "\n数据:" + data + "\n类型:" + type);
                            TestRpc.start(method, data, type);
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame rpctest err:", th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reloadConfig":
                        // UI 侧修改日志开关等共享配置后通知本进程重载,使开关即时生效
                        try {
                            AppConfig.load();
                            // 抓包开关改动后无需重启支付宝: 立即安装/卸载抓包 hook,
                            // 否则注入进程内存里仍是旧开关, 钩子不会装上/不会生效
                            if (isCaptureEnabledRuntime()) {
                                ensureDebugLogOn();
                                installAllCaptureHooks(classLoader);
                            } else {
                                uninstallCaptureHooks();
                            }
                            Log.i(TAG, "reload AppConfig from UI");
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame reloadConfig err:", th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.memberExchange":
                        // 模块 UI（MiuixSelectionEditActivity）运行在模块自身进程，没有支付宝宿主环境
                        // （classLoader/context/rpcBridge 均为 null），不能直接调用 RPC。
                        // 这里在支付宝进程内执行兑换，并把结果通过广播回传给 UI 进程展示。
                        try {
                            String memberName = intent.getStringExtra("name");
                            String memberRequestId = intent.getStringExtra("requestId");
                            BroadcastReceiver.PendingResult r3 = goAsync();
                            new Thread(() -> {
                                try {
                                    String result;
                                    int balance = AntMemberExchange.queryPointBalance();
                                    if (balance < 0) {
                                        result = "查询积分失败";
                                    } else {
                                        int consumed = AntMemberExchange.exchangeSingleTarget(memberName, balance, 300);
                                        result = consumed > 0 ? ("兑换成功，消耗 " + consumed + " 积分") : ("兑换失败或积分不足");
                                    }
                                    Intent reply = new Intent("com.xse.sesame.memberExchangeResult");
                                    reply.putExtra("requestId", memberRequestId);
                                    reply.putExtra("result", result);
                                    context.sendBroadcast(reply);
                                } catch (Throwable th) {
                                    Log.err(TAG, "sesame memberExchange err:", th);
                                    try {
                                        Intent reply = new Intent("com.xse.sesame.memberExchangeResult");
                                        reply.putExtra("requestId", memberRequestId);
                                        reply.putExtra("result", "兑换异常: " + th.getMessage());
                                        context.sendBroadcast(reply);
                                    } catch (Throwable ignored) {
                                    }
                                }
                                r3.finish();
                            }, "Sesame-MemberExchange").start();
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame memberExchange dispatch err:", th);
                        }
                        break;
                }
            }
        }
    }

    /**
     * 校验广播发送方是否可信。
     * <p>
     * 该 Receiver 由运行时注册，Android 13 起必须带 {@code RECEIVER_EXPORTED}：模块 App
     * （{@code com.xse.sesame}）与支付宝是两个不同 UID，跨进程送达只能靠导出，
     * 所以"任意应用都能触发 restart / reLogin"只能在收到广播后按发送方 uid 拦。
     * <p>
     * 白名单：本进程（支付宝自己发的，含由系统代发的 PendingIntent）、模块 App、adb shell（调试用）。
     * <p>
     * 发送方 uid 取自 {@code BroadcastReceiver.getSentFromUid()}——该 API 自 Android 14（API 34）起
     * 提供。低版本无从判定，直接放行以免误伤；Android 14+ 上若返回 {@code Process.INVALID_UID}
     * （广播由系统代发时取不到来源）同样放行并记日志，因为闹钟触发的定时执行走的就是这条路径，
     * 收紧会让定时任务失效。
     *
     * @return true 表示可信，继续处理
     */
    private static boolean isTrustedBroadcastSender(Context context, BroadcastReceiver receiver) {
        try {
            // 发送方 uid 只有 Android 14+ 的 getSentFromUid 能取到；低版本无从判定，放行保持原行为
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return true;
            }
            int uid = receiver.getSentFromUid();
            if (uid == Process.myUid() || uid == SHELL_UID) {
                return true;
            }
            if (uid == Process.INVALID_UID) {
                // 系统代发的广播（如闹钟到点触发 PendingIntent 的定时执行）取不到来源；
                // 这里放行以免定时任务失效，是本校验唯一的松口
                Log.record("广播来源无法判定，按放行处理");
                return true;
            }
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (String pkg : packages) {
                    if (MODULE_PACKAGE_NAME.equals(pkg)) {
                        return true;
                    }
                }
            }
            Log.record("广播发送方不可信，已忽略#uid=" + uid);
            return false;
        } catch (Throwable t) {
            // 校验本身出错时放行：宁可退回改动前的行为，也不让 restart/reloadConfig 这类正常
            // 流程因校验异常而失效（出错原因已记日志，便于排查）
            Log.err(TAG, "校验广播发送方失败:", t);
            return true;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver(Context context) {
        try {
            if (broadcastReceiverRegistered && broadcastReceiver != null) {
                try {
                    context.unregisterReceiver(broadcastReceiver);
                    broadcastReceiverRegistered = false;
                    Log.i(TAG, "hook unregisterBroadcastReceiver successfully");
                } catch (Throwable t) {
                    Log.err(TAG, "hook unregisterBroadcastReceiver err:", t);
                }
            }

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reloadConfig");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.memberExchange");

            broadcastReceiver = new AlipayBroadcastReceiver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastReceiver, intentFilter);
            }
            broadcastReceiverRegistered = true;
            Log.i(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            Log.err(TAG, "hook registerBroadcastReceiver err:", th);
        }
    }

    // 滑块验证hook注册
    private void initSimplePageManager() {
        if (shouldEnableSimplePageManager()) {
            enableWindowMonitoring(classLoader);
            addHandler("com.alipay.mobile.nebulax.xriver.activity.XRiverActivity", new Captcha1Handler());
            addHandler("com.eg.android.AlipayGphone.AlipayLogin", new Captcha2Handler());
        }
    }

    /**
     * 检查目标应用版本是否需要启用SimplePageManager功能
     *
     * @return true表示版本低于等于10.6.58.99999，需要启用；false表示不需要
     */
    private boolean shouldEnableSimplePageManager() {
        if (alipayVersion.toString().isEmpty()) {
            return false;
        }

        AlipayVersion maxSupported = new AlipayVersion("10.6.58.99999");
        if (alipayVersion.compareTo(maxSupported) > 0) {
            // 只有在不支持时才打印警告
            Log.record("目标应用版本[" + alipayVersion.getVersionString() + "]高于[10.6.58.99999]不支持自动过滑块验证");
            return false;
        }

        return true;
    }

    // ----------------------------------------------------------------
    // 宿主前后台询问的回答
    // ----------------------------------------------------------------

    /** 是否已提示过"如实回答"（该事件会反复出现，只留一次痕） */
    private static volatile boolean honestAnswerLogged;
    /** 是否已提示过"取真实状态失败"（失败原因通常固定，避免刷屏） */
    private static volatile boolean originalCallFailedLogged;

    /**
     * 回答宿主的 {@code isInBackground()}：默认仍按原行为谎报 false（"不在后台"），
     * 只有当宿主**真的**在后台、且询问方是风控/滑块链路时如实回答 true
     * ——否则宿主会在后台尝试展示滑块界面，界面出不来、验证流程一直等用户滑动，切回支付宝即卡死。
     */
    private static boolean answerInBackgroundQuestion(XC_MethodHook.MethodHookParam param) {
        Boolean reallyInBackground = observedBooleanResult(param);
        if (Boolean.TRUE.equals(reallyInBackground) && isRiskControlCaller()) {
            noteHonestAnswerForRiskControl();
            return true;
        }
        return false;
    }

    /**
     * 回答宿主的 {@code isAtFrontDesk()}：默认仍按原行为谎报 true（"在前台"），
     * 只有当宿主**真的**不在前台、且询问方是风控/滑块链路时如实回答 false。
     */
    private static boolean answerAtFrontDeskQuestion(XC_MethodHook.MethodHookParam param) {
        Boolean atFrontDesk = observedBooleanResult(param);
        if (Boolean.FALSE.equals(atFrontDesk) && isRiskControlCaller()) {
            noteHonestAnswerForRiskControl();
            return false;
        }
        return true;
    }

    /**
     * 取宿主原方法的真实返回值（after 阶段兼容层已把真值写入 {@code param.result}）。
     *
     * <p>之所以用观察而非 {@code param.callOriginal()}：自研兼容层从未给
     * {@code MethodHookParam.originalCall} 赋值，任何 hook 里调 {@code callOriginal()}
     * 都只会抛 IllegalStateException，真值只能在 after 阶段从返回值里读。
     *
     * @return 真值；取不到（异常 / 非布尔）时返回 null，调用方按原行为谎报
     */
    private static Boolean observedBooleanResult(XC_MethodHook.MethodHookParam param) {
        try {
            Object result = param.getResult();
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Throwable t) {
            if (!originalCallFailedLogged) {
                originalCallFailedLogged = true;
                Log.err(TAG, "读取真实前后台状态失败，继续按原行为谎报:", t);
            }
            return null;
        }
    }

    /**
     * 询问方是否来自风控/滑块链路（{@code com.alipay.rdssecuritysdk} 等）。
     * <p>只在**真实后台**时才会走到这里，因此不影响前台热路径；只看最上面若干帧，够用且便宜。
     */
    private static boolean isRiskControlCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int limit = Math.min(stack.length, 12);
        for (int i = 3; i < limit; i++) {
            String className = stack[i].getClassName();
            if (className.startsWith("com.alipay.rdssecuritysdk")
                    || className.contains("captcha") || className.contains("Captcha")) {
                return true;
            }
        }
        return false;
    }

    private static void noteHonestAnswerForRiskControl() {
        if (honestAnswerLogged) {
            return;
        }
        honestAnswerLogged = true;
        // 用 other 日志：该事件是"宿主在后台要展示风控/滑块界面"的直接证据，而 other 日志默认开启、便于核对；
        // 运行日志（Log.record）受「查看运行日志」开关控制，很多用户是关着的，写在那里等于看不见
        Log.other("风控/滑块链路在后台询问前后台状态：已如实回答，避免在后台创建滑块界面（界面出不来、切回支付宝卡死）");
    }
}
