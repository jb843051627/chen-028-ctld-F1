package com.fc.v2.common.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ctld 配置台口径（第五节：一屏几条、分层怎么认、县局岗划归哪些分区，都由县局写在配置里）。
 *
 * @author fuce
 */
@Component
@ConfigurationProperties(prefix = "fuce.ctld")
public class CtldProperties {

    /** 配置台一屏几条，能一路往下翻 */
    private int switchPageSize = 10;

    /** 县局岗角色名：三层都看得见，但范围限于划归它的分区 */
    private List<String> countyRoles = new ArrayList<>(Arrays.asList("县局岗"));

    /** 不受分层范围约束的角色（系统管理员） */
    private List<String> adminRoles = new ArrayList<>(Arrays.asList("管理员"));

    /** 本层值守岗角色名 → 分区册层类别；值守岗只看得见挂靠本层的行 */
    private Map<String, String> dutyLayerRoles = new HashMap<String, String>() {{
        put("甲层值守岗", "甲层");
        put("乙层值守岗", "乙层");
        put("丙层值守岗", "丙层");
    }};

    /** 县局岗（或具体账号）划归的分区代号名单，逗号分隔；没列的账号不另收窄 */
    private Map<String, String> userZones = new HashMap<>();

    public int getSwitchPageSize() {
        return switchPageSize;
    }

    public void setSwitchPageSize(int switchPageSize) {
        this.switchPageSize = switchPageSize;
    }

    public List<String> getCountyRoles() {
        return countyRoles;
    }

    public void setCountyRoles(List<String> countyRoles) {
        this.countyRoles = countyRoles;
    }

    public List<String> getAdminRoles() {
        return adminRoles;
    }

    public void setAdminRoles(List<String> adminRoles) {
        this.adminRoles = adminRoles;
    }

    public Map<String, String> getDutyLayerRoles() {
        return dutyLayerRoles;
    }

    public void setDutyLayerRoles(Map<String, String> dutyLayerRoles) {
        this.dutyLayerRoles = dutyLayerRoles;
    }

    public Map<String, String> getUserZones() {
        return userZones;
    }

    public void setUserZones(Map<String, String> userZones) {
        this.userZones = userZones;
    }
}
