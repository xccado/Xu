package com.xse.sesame.util.compat;

import java.lang.reflect.Member;

import com.xse.sesame.util.XHelpers;

/**
 * API 102 临时迁移兼容层：桥接旧 {@code XposedBridge.hookMethod} 到 {@link com.xse.sesame.util.XHelpers}。
 */
public class XposedBridge {

    public static void hookMethod(Member member, XC_MethodHook callback) {
        XHelpers.hookMember(member, callback);
    }
}
