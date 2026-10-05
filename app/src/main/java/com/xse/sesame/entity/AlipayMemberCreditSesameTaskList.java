package com.xse.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xse.sesame.util.Log;
import com.xse.sesame.util.idMap.ForestHuntIdMap;
import com.xse.sesame.util.idMap.MemberCreditSesameTaskListMap;

public class AlipayMemberCreditSesameTaskList extends IdAndName {
    private static List<AlipayMemberCreditSesameTaskList> list;

    public AlipayMemberCreditSesameTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayMemberCreditSesameTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : MemberCreditSesameTaskListMap.getMap().entrySet()) {
                list.add(new AlipayMemberCreditSesameTaskList(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    public static void remove(String id) {
        getList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
    }

}
