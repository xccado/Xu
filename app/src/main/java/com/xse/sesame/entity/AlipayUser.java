package com.xse.sesame.entity;

import com.xse.sesame.util.Log;
import com.xse.sesame.util.idMap.UserIdMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlipayUser extends IdAndName {

    private String avatar;

    public AlipayUser(String i, String n) {
        id = i;
        name = n;
    }

    public AlipayUser(String i, String n, String avatar) {
        id = i;
        name = n;
        this.avatar = avatar;
    }

    public String getAvatar() {
        return avatar;
    }

    public static List<AlipayUser> getList() {
        return getList(user -> true);
    }

    public static List<AlipayUser> getList(Filter filterFunc) {
        List<AlipayUser> list = new ArrayList<>();
        Map<String, UserEntity> userIdMap = UserIdMap.getUserMap();
        for (Map.Entry<String, UserEntity> entry : userIdMap.entrySet()) {
            UserEntity userEntity = entry.getValue();
            try {
                if (filterFunc.apply(userEntity)) {
                    list.add(new AlipayUser(entry.getKey(), userEntity.getFullName(), userEntity.getAvatar()));
                }
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return list;
    }

    public interface Filter {

        Boolean apply(UserEntity user);

    }

}
