package com.xse.sesame.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AccountSwitchState {
    private String account;
    private boolean armed;
    private boolean failed;
    private String roundStartAccount;
    private long idleSince = -1;
    private long cooldownSince = -1;

    synchronized void onActivate(String str) {
        if (str != null) {
            if (!str.isEmpty()) {
                this.roundStartAccount = str;
                this.account = str;
                this.armed = true;
                this.failed = false;
                this.idleSince = -1L;
                this.cooldownSince = -1L;
            }
        }
    }

    synchronized void onRound(String str) {
        if (str != null) {
            if (!str.isEmpty()) {
                if (this.roundStartAccount == null) {
                    this.roundStartAccount = str;
                }
                if (Objects.equals(this.account, str) && this.armed) {
                    return;
                }
                this.cooldownSince = -1L;
                this.account = str;
                this.armed = !this.failed;
                this.idleSince = -1L;
            }
        }
    }

    synchronized void startCooldown(long j) {
        this.cooldownSince = j;
        this.idleSince = -1L;
    }

    synchronized boolean isCoolingDown() {
        return this.cooldownSince >= 0;
    }

    synchronized boolean cooldownPending(long j, int i) {
        long j2 = this.cooldownSince;
        if (j2 < 0) {
            return false;
        }
        if (j < j2) {
            this.cooldownSince = j;
        }
        if (j - this.cooldownSince < intervalMillis(i)) {
            return true;
        }
        this.cooldownSince = -1L;
        return false;
    }

    synchronized void disabled() {
        this.account = null;
        this.roundStartAccount = null;
        this.cooldownSince = -1L;
        this.armed = false;
        this.failed = false;
        this.idleSince = -1L;
    }

    synchronized void fail() {
        this.armed = false;
        this.failed = true;
        this.idleSince = -1L;
    }

    synchronized boolean isRoundEnd(String str) {
        String str2;
        str2 = this.roundStartAccount;
        return str2 != null && str2.equals(str);
    }

    synchronized String getRoundStartAccount() {
        return this.roundStartAccount;
    }

    synchronized long targetIntervalMillis(String str, int i) {
        // 使用用户配置的切换间隔（秒 -> 毫秒），修复之前硬编码 15 秒导致设置失效
        return intervalMillis(i);
    }

    synchronized boolean ready(String str, boolean z, boolean z2, boolean z3, long j, int i, String str2) {
        try {
            if (!z) {
                disabled();
                return false;
            }
            if (this.armed && !this.failed) {
                if (!Objects.equals(this.account, str)) {
                    this.armed = false;
                    this.idleSince = -1L;
                    return false;
                }
                if (cooldownPending(j, i)) {
                    this.idleSince = -1L;
                    return false;
                }
                if (!z2 || z3) {
                    this.idleSince = -1L;
                    return false;
                }
                long j2 = this.idleSince;
                if (j2 < 0 || j < j2) {
                    this.idleSince = j;
                }
                if (j - this.idleSince < targetIntervalMillis(str2, i)) {
                    return false;
                }
                this.armed = false;
                return true;
            }
            return false;
        } catch (Throwable th) {
            throw th;
        }
    }

    synchronized String waitPhase(long j, int i, boolean z, String str) {
        if (this.failed) {
            return "PAUSED";
        }
        if (isCoolingDown()) {
            return "ROUND_COOLDOWN";
        }
        if (!z) {
            return "WAIT_TASKS";
        }
        return "COUNTDOWN";
    }

    synchronized void defer() {
        if (!this.failed) {
            this.armed = true;
        }
    }

    synchronized void waitForHome() {
        this.idleSince = -1L;
        defer();
    }

    static long intervalMillis(int i) {
        return ((long) Math.max(15, Math.min(AccountSwitchIntervalDraft.MAX_SECONDS, i))) * 1000;
    }

    static long timeoutMillis(int i) {
        return ((long) Math.max(15, Math.min(120, i))) * 1000;
    }

    public static boolean validUid(String str) {
        return str != null && str.matches("[A-Za-z0-9_@.+:-]{1,128}");
    }

    public static String next(List<String> list, String str) {
        if (list != null && list.size() <= 32 && validUid(str)) {
            ArrayList arrayList = new ArrayList();
            for (String str2 : list) {
                if (validUid(str2) && !arrayList.contains(str2)) {
                    arrayList.add(str2);
                }
            }
            Collections.sort(arrayList);
            if (arrayList.size() < 2) {
                return null;
            }
            int iIndexOf = arrayList.indexOf(str);
            for (int i = 1; i <= arrayList.size(); i++) {
                String str3 = (String) arrayList.get((iIndexOf + i) % arrayList.size());
                if (!str3.equals(str)) {
                    return str3;
                }
            }
        }
        return null;
    }
}
