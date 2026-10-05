package com.xse.sesame.util.idMap;

import java.util.Map;

import com.xse.sesame.util.FileUtil;

public class CooperationIdMap {

    private static final StringMapStore STORE = new StringMapStore(FileUtil::getCooperationIdMapFile);

    public static Map<String, String> getMap() {
        return STORE.getMap();
    }

    public static void add(String key, String value) {
        STORE.add(key, value);
    }

    public static void remove(String key) {
        STORE.remove(key);
    }

    public static void load(String userId) {
        STORE.load(userId);
    }

    public static boolean save(String userId) {
        return STORE.save(userId);
    }

}