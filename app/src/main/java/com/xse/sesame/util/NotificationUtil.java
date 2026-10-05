package com.xse.sesame.util;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import lombok.Getter;
import com.xse.sesame.data.RuntimeInfo;
import com.xse.sesame.model.normal.base.BaseModel;
import com.xse.sesame.util.idMap.UserIdMap;

public class NotificationUtil {
    private static Context context;
    private static final int NOTIFICATION_ID = 99;
    private static final String CHANNEL_ID = "com.xse.sesame.ANTFOREST_NOTIFY_CHANNEL";
    private static NotificationManager mNotifyManager;
    private static Notification.Builder builder;

    @Getter
    private static volatile long lastNoticeTime = 0;
    private static String contentText = "";
    /** 活跃任务计数，>0 表示有异步任务仍在执行。由 ModelTask 在 startTask/finally 里增减 */
    private static volatile int runningCount = 0;

    public static void trackTaskStart() {
        runningCount++;
    }

    public static void trackTaskEnd() {
        runningCount--;
        if (runningCount < 0) runningCount = 0;
    }

    public static int getRunningCount() {
        return runningCount;
    }

    public static void start(Context context) {
        try {
            NotificationUtil.context = context;
            NotificationUtil.stop();
            contentText = "启动中";
            mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setData(Uri.parse("alipays://platformapi/startapp?appId="));
            PendingIntent pi = PendingIntent.getActivity(context, 0, it,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "芝麻粒能量提醒",
                        NotificationManager.IMPORTANCE_LOW);
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationChannel.setShowBadge(false);
                mNotifyManager.createNotificationChannel(notificationChannel);
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context).setPriority(Notification.PRIORITY_LOW);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setCategory(Notification.CATEGORY_NAVIGATION);
            }
            builder
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.sym_def_app_icon))
                    .setSubText("芝麻粒")
                    .setAutoCancel(false)
                    .setContentIntent(pi);
            if (com.xse.sesame.data.AppConfig.INSTANCE.getEnableOnGoing()) {
                builder.setOngoing(true);
            }
            Notification mNotification = builder.build();
            if (context instanceof Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    mNotifyManager.notify(NOTIFICATION_ID, mNotification);
                } else {
                    ((Service) context).startForeground(NOTIFICATION_ID, mNotification);
                }
            } else {
                mNotifyManager.notify(NOTIFICATION_ID, mNotification);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void stop() {
        try {
            if (context instanceof Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ((Service) context).stopForeground(Service.STOP_FOREGROUND_REMOVE);
                } else {
                    ((Service) context).stopForeground(true);
                }
            } else {
                if (mNotifyManager != null) {
                    mNotifyManager.cancel(NOTIFICATION_ID);
                } else if (context != null) {
                    NotificationManager systemService = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (systemService != null) {
                        systemService.cancel(NOTIFICATION_ID);
                    }
                }
            }
            mNotifyManager = null;
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void updateStatusText(String status) {
        try {
            long forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime);
            if (forestPauseTime > System.currentTimeMillis()) {
                status = "触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime);
            }
            contentText = status;
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 下次执行时间，由 execDelayedHandler 设置，updateLastExecText 时一并写入 */
    private static volatile long nextExecTime = 0;

    public static void setNextExecTime(long nextExecTime) {
        NotificationUtil.nextExecTime = nextExecTime;
    }

    /** 通知栏显示「下次执行」时间（SX 定制，由 ApplicationHook 触发）；标题固定为模块名，时间写入内容行 */
    public static void updateNextExecText(long nextExecTime) {
        try {
            setNextExecTime(nextExecTime);
            contentText = nextExecTime > 0 ? "下次执行 " + TimeUtil.getTimeStr(nextExecTime) : "";
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /**
     * 所有任务完成时调用：内容行只保留「下次执行」时间（与 XRadiant 样式一致，不显示上次执行）。
     * 由 ModelTask.finally 中 runningCount == 0 时统一触发。
     */
    public static void updateLastExecText() {
        try {
            if (nextExecTime > 0) {
                contentText = "下次执行 " + TimeUtil.getTimeStr(nextExecTime);
                nextExecTime = 0;
            }
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void setStatusTextExec() {
        setStatusTextExec(null);
    }

    /** 执行中显示具体任务名，如「正在执行 森林」；任务名为空时退化为「执行中」 */
    public static void setStatusTextExec(String taskName) {
        try {
            contentText = StringUtil.isEmpty(taskName) ? "执行中" : "正在执行 " + taskName;
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /**
     * 任务运行中持续刷新通知，防止系统因「长时间无更新」将通知折叠/隐藏。
     * 每次调用只更新 lastNoticeTime，不改变文本内容。
     */
    public static void setRunning() {
        try {
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 通知栏标题固定为「Xse + 账号标识」，与 XRadiant 风格一致；uid 为空（UI 进程）时只显示模块名 */
    private static String buildTitle() {
        try {
            String label = UserIdMap.getAccountLabel(UserIdMap.getCurrentUid());
            return label == null ? "Xse" : "Xse " + label;
        } catch (Throwable t) {
            return "Xse";
        }
    }

    private static void sendText() {
        try {
            builder.setContentTitle(buildTitle());
            if (!StringUtil.isEmpty(contentText)) {
                builder.setContentText(contentText);
            }
            mNotifyManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

}
