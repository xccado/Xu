package com.xse.sesame.hook;

import java.util.Objects;

class AccountSwitchFlight {
    volatile boolean accepted;
    boolean cancelled;
    final long deadline;
    volatile boolean pageBlocked;
    volatile boolean returned;
    final String source;
    int stable;
    final String target;
    boolean timedOut;

    enum Outcome {
        WAIT,
        SUCCESS,
        REJECTED,
        LATE,
        CANCELLED
    }

    AccountSwitchFlight(String str, String str2, long j, long j2) {
        this.source = str;
        this.target = str2;
        this.deadline = j + j2;
    }

    boolean updateTimeout(long j) {
        if (this.timedOut || j < this.deadline) {
            return false;
        }
        this.timedOut = true;
        return true;
    }

    Outcome observe(long j, String str, String str2, boolean z) {
        updateTimeout(j);
        if (!this.returned || z || !AccountSwitchState.validUid(str) || !Objects.equals(str, str2)) {
            this.stable = 0;
            return Outcome.WAIT;
        }
        boolean zEquals = this.target.equals(str);
        boolean z2 = this.source.equals(str) && !this.accepted;
        if (!zEquals && !z2) {
            this.stable = 0;
            return Outcome.WAIT;
        }
        int i = this.stable + 1;
        this.stable = i;
        if (i < 2) {
            return Outcome.WAIT;
        }
        if (this.cancelled) {
            return Outcome.CANCELLED;
        }
        if (this.timedOut) {
            return Outcome.LATE;
        }
        return (zEquals && this.accepted) ? Outcome.SUCCESS : Outcome.REJECTED;
    }
}
