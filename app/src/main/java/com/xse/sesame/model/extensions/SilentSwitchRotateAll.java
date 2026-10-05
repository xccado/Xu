package com.xse.sesame.model.extensions;

import com.xse.sesame.hook.AccountSwitchState;
import com.xse.sesame.hook.HostAccountSwitchBridge;
import com.xse.sesame.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SilentSwitchRotateAll {

    private static final long SWITCH_INTERVAL_MS = 15000;
    private static final long SWITCH_WAIT_MS = 15000;

    /** 对外入口，与 ExtensionsHandle.handleRequest("silentSwitch","rotateAll",null) 等价 */
    public static void start() {
        HostAccountSwitchBridge bridge = new HostAccountSwitchBridge();
        try {
            List<HostAccountSwitchBridge.Account> listAccounts = bridge.accounts();
            String currentUid = bridge.currentUid();
            Log.switchLog("静默轮切：当前账号 userId=" + maskUid(currentUid) + "，本机历史账号数=" + listAccounts.size());
            if (listAccounts.size() < 2) {
                Log.switchLog("静默轮切：历史账号不足 2 个，无需轮切");
                return;
            }
            ArrayList<String> uidList = new ArrayList<>();
            LinkedHashMap<String, HostAccountSwitchBridge.Account> accountMap = new LinkedHashMap<>();
            for (HostAccountSwitchBridge.Account account : listAccounts) {
                uidList.add(account.uid);
                accountMap.put(account.uid, account);
            }
            int size = uidList.size();
            int success = 0;
            int fail = 0;
            for (int i = 1; i < size; i++) {
                String next = AccountSwitchState.next(uidList, currentUid);
                if (next == null) {
                    Log.switchLog("静默轮切：无法确定下一个账号，结束");
                    break;
                }
                HostAccountSwitchBridge.Account account = accountMap.get(next);
                if (account == null) {
                    fail++;
                    break;
                }
                Log.switchLog("静默轮切 " + i + "/" + (size - 1) + " -> " + account.label);
                if (bridge.switchTo(account)) {
                    long startTime = System.currentTimeMillis();
                    while (true) {
                        if (System.currentTimeMillis() - startTime >= SWITCH_WAIT_MS) {
                            fail++;
                            Log.switchLog("静默轮切：等待切换超时 " + account.label);
                            break;
                        }
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (next.equals(bridge.currentUid())) {
                            success++;
                            Log.switchLog("静默轮切：已切至 " + account.label);
                            break;
                        }
                    }
                    if (i < size - 1) {
                        try {
                            Thread.sleep(SWITCH_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    currentUid = next;
                } else {
                    fail++;
                    Log.switchLog("静默轮切：发起切换失败 " + account.label);
                }
            }
            Log.switchLog("静默轮切结束：成功 " + success + " / 失败 " + fail);
        } catch (Throwable th) {
            Log.switchLog("静默轮切异常: " + th);
            Log.printStackTrace("ExtensionsHandle", th);
        }
    }

    private static String maskUid(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (str.length() <= 4) {
            return "***";
        }
        return str.substring(0, 3) + "***" + str.substring(str.length() - 2);
    }
}
