package com.xse.sesame.hook;

import com.xse.sesame.data.task.ModelTask;

public final class TaskLifecycle {

    public static final class Freeze {
    }

    public static void thaw(Freeze freeze) {
    }

    private TaskLifecycle() {
    }

    public static Freeze freezeIfIdle() {
        if (ModelTask.isAllTaskIdle()) {
            return new Freeze();
        }
        return null;
    }
}
