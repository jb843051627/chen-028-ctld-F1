package com.fc.v2.service.ctld;

/**
 * 一处地块这一问回的是哪套口径——按代号取值与整表取优两个通道共用同一个回话模样，
 * 谁来问、从哪个通道问，回的都是这一型。
 *
 * @author fuce
 */
public class SwitchDecision {

    /** true=走新口径（同季配对回话），false=走旧口径（镇级逐条人工签认） */
    private final boolean newCaliber;
    /** 最终答话行的三态 */
    private final int swKind;
    /** 最终答话行的开桶个数（按桶推进时才有） */
    private final Integer grayNum;
    /** 答话落在哪一层：县定／市定／省定／系统兜底 */
    private final String levelKind;
    /** 最终答话行的切换项代号（兜底行为空） */
    private final String swNo;

    public SwitchDecision(boolean newCaliber, int swKind, Integer grayNum, String levelKind, String swNo) {
        this.newCaliber = newCaliber;
        this.swKind = swKind;
        this.grayNum = grayNum;
        this.levelKind = levelKind;
        this.swNo = swNo;
    }

    public boolean isNewCaliber() {
        return newCaliber;
    }

    public int getSwKind() {
        return swKind;
    }

    public Integer getGrayNum() {
        return grayNum;
    }

    public String getLevelKind() {
        return levelKind;
    }

    public String getSwNo() {
        return swNo;
    }
}
