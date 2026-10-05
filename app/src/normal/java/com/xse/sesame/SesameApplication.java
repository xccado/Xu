package com.xse.sesame;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

import com.xse.sesame.data.RunType;
import com.xse.sesame.data.ViewAppInfo;

/**
 * 通用包模块进程 Application，兼容双框架。
 */
public class SesameApplication extends Application implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(this);
        } catch (Throwable ignored) {
            // 传统框架无 libxposed service
        }
    }

    @Override
    public void onServiceBind(XposedService service) {
        android.util.Log.i("SesameX", "onServiceBind framework=" + service.getFrameworkName()
                + " scope=" + service.getScope());
        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode());
    }

    @Override
    public void onServiceDied(XposedService service) {
        android.util.Log.i("SesameX", "onServiceDied");
        ViewAppInfo.setRunTypeByCode(RunType.DISABLE.getCode());
    }
}
