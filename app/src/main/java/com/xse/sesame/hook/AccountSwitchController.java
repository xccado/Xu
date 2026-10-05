package com.xse.sesame.hook;

import android.app.Activity;
import android.os.SystemClock;
import com.xse.sesame.data.task.ModelTask;
import com.xse.sesame.util.ClassUtil;
import com.xse.sesame.util.Log;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AccountSwitchController {
    private static String armedAccount;
    private static AccountSwitchFlight flight;
    private static TaskLifecycle.Freeze freeze;
    private static volatile Host host;
    private static String lastStatus;
    private static long nextCountCheck;
    private static long nextHistoryCheck;
    private static boolean previouslyEnabled;
    private static boolean watching;
    private static final ScheduledExecutorService CONTROL = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override // java.util.concurrent.ThreadFactory
        public final Thread newThread(Runnable runnable) {
            return AccountSwitchController.lambda$static$0(runnable);
        }
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final AccountSwitchState STATE = new AccountSwitchState();
    private static final HostAccountSwitchBridge BRIDGE = new HostAccountSwitchBridge();
    private static long lastActivation = -1;
    private static volatile List<String> cachedAccountUids = new ArrayList();

    public interface Host {
        String currentUid();

        boolean initialize(String str, TaskLifecycle.Freeze freeze);

        String readiness();

        void resume();
    }

    static Thread lambda$static$0(Runnable runnable) {
        Thread thread = new Thread(runnable, "Sesame-AccountSwitch");
        thread.setDaemon(true);
        return thread;
    }

    private AccountSwitchController() {
    }

    static synchronized void configure(Host host2) {
        host = host2;
        if (!watching) {
            watching = true;
            CONTROL.scheduleWithFixedDelay(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    AccountSwitchController.tick();
                }
            }, 1L, 1L, TimeUnit.SECONDS);
        }
    }

    public static void hostReady() {
        CONTROL.execute(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AccountSwitchController.lambda$hostReady$1();
            }
        });
    }

    static void lambda$hostReady$1() {
        if (isBusy()) {
            return;
        }
        try {
            List<HostAccountSwitchBridge.Account> listAccounts = BRIDGE.accounts();
            AccountSwitchAccountCount.publish(listAccounts.size());
            ArrayList arrayList = new ArrayList();
            Iterator<HostAccountSwitchBridge.Account> it = listAccounts.iterator();
            while (it.hasNext()) {
                arrayList.add(it.next().uid);
            }
            cachedAccountUids = arrayList;
            nextCountCheck = SystemClock.elapsedRealtime() + 30000;
            BRIDGE.probe();
            Log.switchLog("自动切号只读检查：接口已就绪，本机历史登录账号数=" + listAccounts.size());
            nextHistoryCheck = 0L;
        } catch (Throwable unused) {
            status("宿主切号接口尚未就绪，未执行切换");
        }
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    private static boolean homeReady() {
        try {
            Activity topActivity = SimplePageManager.getTopActivity();
            return topActivity != null && SimplePageManager.isAppForeground() && topActivity.hasWindowFocus() && ClassUtil.CURRENT_USING_ACTIVITY.equals(topActivity.getClass().getName());
        } catch (Throwable unused) {
            return false;
        }
    }

    private static boolean captchaPending() {
        try {
            Activity topActivity = SimplePageManager.getTopActivity();
            if (topActivity == null) {
                return false;
            }
            return AccountSwitchPagePolicy.blocksClass(topActivity.getClass().getName(), ClassUtil.CURRENT_USING_ACTIVITY);
        } catch (Throwable unused) {
            return true;
        }
    }

    public static void tick() {
        try {
            AccountSwitchSettings.Values values = AccountSwitchSettings.read();
            Host host2 = host;
            if (host2 == null) {
                phase("WAIT_HOST");
                return;
            }
            if (!values.enabled) {
                STATE.disabled();
                previouslyEnabled = false;
                AccountSwitchFlight accountSwitchFlight = flight;
                if (accountSwitchFlight != null) {
                    accountSwitchFlight.cancelled = true;
                }
            }
            if (flight != null) {
                phase("CONFIRMING");
                advanceFlight();
                return;
            }
            if (isBusy()) {
                phase("PAUSED");
                return;
            }
            long jElapsedRealtime = SystemClock.elapsedRealtime();
            if (jElapsedRealtime >= nextCountCheck || cachedAccountUids.isEmpty()) {
                nextCountCheck = jElapsedRealtime + 30000;
                try {
                    List<HostAccountSwitchBridge.Account> listAccounts = BRIDGE.accounts();
                    AccountSwitchAccountCount.publish(listAccounts.size());
                    ArrayList arrayList = new ArrayList();
                    Iterator<HostAccountSwitchBridge.Account> it = listAccounts.iterator();
                    while (it.hasNext()) {
                        arrayList.add(it.next().uid);
                    }
                    cachedAccountUids = arrayList;
                } catch (Throwable unused) {
                    AccountSwitchAccountCount.publish(-1);
                }
            }
            if (!values.enabled) {
                phase("DISABLED");
                return;
            }
            String str = host2.readiness();
            if (!"READY".equals(str)) {
                phase(str);
                return;
            }
            String strCurrentUid = host2.currentUid();
            if (!AccountSwitchState.validUid(strCurrentUid)) {
                phase("WAIT_IDENTITY");
                return;
            }
            if (!previouslyEnabled || lastActivation != values.activation) {
                STATE.onActivate(strCurrentUid);
                armedAccount = strCurrentUid;
                previouslyEnabled = true;
                lastActivation = values.activation;
                status("已开启轮询，本轮起始账号设置为 " + strCurrentUid);
            } else if (!Objects.equals(armedAccount, strCurrentUid)) {
                STATE.onRound(strCurrentUid);
                armedAccount = strCurrentUid;
            }
            long jElapsedRealtime2 = SystemClock.elapsedRealtime();
            boolean zIsAllTaskIdle = ModelTask.isAllTaskIdle();
            String next = AccountSwitchState.next(cachedAccountUids, strCurrentUid);
            if (next == null) {
                if (jElapsedRealtime2 >= nextHistoryCheck) {
                    try {
                        List<HostAccountSwitchBridge.Account> listAccounts2 = BRIDGE.accounts();
                        AccountSwitchAccountCount.publish(listAccounts2.size());
                        ArrayList arrayList2 = new ArrayList();
                        Iterator<HostAccountSwitchBridge.Account> it2 = listAccounts2.iterator();
                        while (it2.hasNext()) {
                            arrayList2.add(it2.next().uid);
                        }
                        cachedAccountUids = arrayList2;
                        next = AccountSwitchState.next(cachedAccountUids, strCurrentUid);
                    } catch (Throwable unused2) {
                    }
                    nextHistoryCheck = jElapsedRealtime2 + 30000;
                }
                if (next == null) {
                    phase("WAIT_HISTORY");
                    status("历史登录账号不足两个，保持当前账号");
                    STATE.defer();
                    return;
                }
            }
            boolean zHomeReady = homeReady();
            AccountSwitchState accountSwitchState = STATE;
            if (!accountSwitchState.ready(strCurrentUid, true, zIsAllTaskIdle, !zHomeReady, jElapsedRealtime2, values.seconds, next)) {
                phase(zHomeReady ? accountSwitchState.waitPhase(jElapsedRealtime2, values.seconds, zIsAllTaskIdle, next) : "WAIT_HOME");
                return;
            }
            if (captchaPending()) {
                phase("WAIT_CAPTCHA");
                accountSwitchState.defer();
                return;
            }
            if (jElapsedRealtime2 < nextHistoryCheck) {
                phase("WAIT_HISTORY");
                accountSwitchState.defer();
                return;
            }
            List<HostAccountSwitchBridge.Account> listAccounts3 = BRIDGE.accounts();
            AccountSwitchAccountCount.publish(listAccounts3.size());
            long j = jElapsedRealtime2 + 30000;
            nextCountCheck = j;
            ArrayList arrayList3 = new ArrayList();
            Iterator<HostAccountSwitchBridge.Account> it3 = listAccounts3.iterator();
            while (it3.hasNext()) {
                arrayList3.add(it3.next().uid);
            }
            cachedAccountUids = arrayList3;
            String next2 = AccountSwitchState.next(arrayList3, strCurrentUid);
            if (next2 == null) {
                phase("WAIT_HISTORY");
                status("历史登录账号不足两个，保持当前账号");
                STATE.defer();
                nextHistoryCheck = j;
                return;
            }
            HostAccountSwitchBridge hostAccountSwitchBridge = BRIDGE;
            if (!Objects.equals(hostAccountSwitchBridge.currentUid(), strCurrentUid)) {
                // 切号完成后 AuthService 与 SocialSdkContactService 两处 uid 存在短暂不同步，
                // 属瞬时状态，等待同步后重试，避免 STATE.fail() 造成永久暂停
                STATE.defer();
                phase("WAIT_IDENTITY");
                status("宿主账号信息未一致，等待同步后重试");
                return;
            }
            hostAccountSwitchBridge.probe();
            TaskLifecycle.Freeze freezeFreezeIfIdle = TaskLifecycle.freezeIfIdle();
            freeze = freezeFreezeIfIdle;
            if (freezeFreezeIfIdle == null) {
                STATE.defer();
                phase("WAIT_TASKS");
                return;
            }
            BUSY.set(true);
            if (AccountSwitchSettings.read().enabled && homeReady() && Objects.equals(host2.currentUid(), strCurrentUid) && Objects.equals(hostAccountSwitchBridge.currentUid(), strCurrentUid)) {
                HostAccountSwitchBridge.Account found = null;
                for (HostAccountSwitchBridge.Account account2 : listAccounts3) {
                    if (next2.equals(account2.uid)) {
                        found = account2;
                        break;
                    }
                }
                if (found == null) {
                    releaseFreeze();
                    STATE.fail();
                    return;
                }
                final HostAccountSwitchBridge.Account account = found;
                ModelTask.stopAllTask();
                final AccountSwitchFlight accountSwitchFlight2 = new AccountSwitchFlight(strCurrentUid, next2, SystemClock.elapsedRealtime(), AccountSwitchState.timeoutMillis(30));
                flight = accountSwitchFlight2;
                boolean zIsRoundEnd = STATE.isRoundEnd(next2);
                phase("SWITCHING");
                status(zIsRoundEnd ? "本轮任务已完成，正在返回首个账号" : "本账号任务已完成，正在切换到下一个账号");
                StringBuilder sb = new StringBuilder();
                sb.append("自动切号 -> ");
                sb.append(account.label == null ? account.uid : account.label);
                Log.switchLog(sb.toString());
                Thread thread = new Thread(new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        AccountSwitchController.lambda$tick$2(accountSwitchFlight2, account);
                    }
                }, "Sesame-AccountLogin");
                thread.setDaemon(true);
                try {
                    thread.start();
                    return;
                } catch (Throwable unused3) {
                    accountSwitchFlight2.accepted = false;
                    accountSwitchFlight2.returned = true;
                    return;
                }
            }
            releaseFreeze();
            STATE.waitForHome();
        } catch (Throwable unused4) {
            // 切号期间支付宝处于切换/重启加载态，反射调用可能瞬时失败；
            // 走 defer 自动恢复轮询，不再 STATE.fail() 永久暂停
            phase("PAUSED");
            STATE.defer();
            status("切号检查异常，稍后自动重试");
            if (flight == null) {
                releaseFreeze();
            }
        }
    }

    static void lambda$tick$2(AccountSwitchFlight accountSwitchFlight, HostAccountSwitchBridge.Account account) {
        try {
            if (homeReady()) {
                accountSwitchFlight.accepted = BRIDGE.switchTo(account);
            } else {
                accountSwitchFlight.pageBlocked = true;
                accountSwitchFlight.returned = true;
            }
        } catch (Throwable unused) {
            try {
                accountSwitchFlight.accepted = false;
            } finally {
                accountSwitchFlight.returned = true;
            }
        }
    }

    private static void advanceFlight() {
        AccountSwitchFlight accountSwitchFlight = flight;
        if (accountSwitchFlight.updateTimeout(SystemClock.elapsedRealtime())) {
            STATE.fail();
            status("切号确认超时，保持任务暂停并等待宿主明确结果");
        }
        if (accountSwitchFlight.returned) {
            if (accountSwitchFlight.pageBlocked) {
                flight = null;
                STATE.waitForHome();
                releaseFreeze();
                phase("WAIT_HOME");
                return;
            }
            boolean zInitialize = false;
            try {
                String strCurrentUid = BRIDGE.currentUid();
                AccountSwitchFlight.Outcome outcomeObserve = accountSwitchFlight.observe(SystemClock.elapsedRealtime(), strCurrentUid, host.currentUid(), captchaPending());
                if (outcomeObserve == AccountSwitchFlight.Outcome.WAIT) {
                    return;
                }
                boolean z = outcomeObserve == AccountSwitchFlight.Outcome.SUCCESS;
                try {
                    zInitialize = host.initialize(strCurrentUid, freeze);
                } catch (Throwable unused) {
                }
                flight = null;
                if (z && zInitialize) {
                    AccountSwitchState accountSwitchState = STATE;
                    boolean zEquals = strCurrentUid.equals(accountSwitchState.getRoundStartAccount());
                    accountSwitchState.onRound(strCurrentUid);
                    armedAccount = strCurrentUid;
                    if (zEquals) {
                        accountSwitchState.startCooldown(SystemClock.elapsedRealtime());
                        phase("ROUND_COOLDOWN");
                        status("已返回首个账号，开始切号冷却，当前账号任务正常运行");
                    } else {
                        status("切换成功，新账号配置已加载");
                    }
                    releaseFreeze();
                    return;
                }
                if (zInitialize) {
                    releaseFreeze();
                }
                STATE.fail();
                status(zInitialize ? "本次切号未完整确认，轮询已暂停" : "新账号初始化失败，轮询已暂停");
            } catch (Throwable unused2) {
                accountSwitchFlight.stable = 0;
            }
        }
    }

    private static void releaseFreeze() {
        TaskLifecycle.Freeze freeze2 = freeze;
        boolean z = freeze2 != null;
        TaskLifecycle.thaw(freeze2);
        freeze = null;
        BUSY.set(false);
        if (!z || host == null) {
            return;
        }
        host.resume();
    }

    private static void phase(String str) {
        try {
            AccountSwitchStatus.publish(str);
        } catch (Throwable unused) {
        }
    }

    private static void status(String str) {
        if (str.equals(lastStatus)) {
            return;
        }
        lastStatus = str;
        Log.switchLog("自动切号：" + str);
    }
}
