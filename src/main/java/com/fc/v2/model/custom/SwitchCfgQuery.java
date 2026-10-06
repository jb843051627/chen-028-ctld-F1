package com.fc.v2.model.custom;

/**
 * 配置台查行的三个通道（第五节）：分区代号、切换名目、开关三态。
 * 分区代号、切换名目两栏前缀匹配与全等匹配都认；三态只认全等。
 *
 * @author fuce
 */
public class SwitchCfgQuery {

    /** 分区代号（全等或前缀） */
    private String zoneNo;

    /** 切换名目（全等或前缀） */
    private String swName;

    /** 开关三态（全等） */
    private Integer swKind;

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public String getSwName() {
        return swName;
    }

    public void setSwName(String swName) {
        this.swName = swName;
    }

    public Integer getSwKind() {
        return swKind;
    }

    public void setSwKind(Integer swKind) {
        this.swKind = swKind;
    }
}
