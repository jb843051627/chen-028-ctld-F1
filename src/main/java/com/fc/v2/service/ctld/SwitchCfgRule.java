package com.fc.v2.service.ctld;

import com.fc.v2.common.exception.CtldRuleException;
import com.fc.v2.model.auto.TCtldSwitchCfg;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 核验口径切换开关的死规矩（第一节取值、第二节取优、第三节落桶）。
 * 纯函数、不碰库不碰会话：同一处地块，无论谁问、从哪条通道问，结果必须同一个。
 *
 * @author fuce
 */
public final class SwitchCfgRule {

    private SwitchCfgRule() {
    }

    /** 开关三态 */
    public static final int KIND_OLD = 0;
    public static final int KIND_NEW = 1;
    public static final int KIND_BUCKET = 2;

    /** 三级步子：0 待上 → 1 已上 → 2 压行（走到头） */
    public static final int STEP_PENDING = 0;
    public static final int STEP_SENT = 1;
    public static final int STEP_LOCKED = 2;

    /** 定档层级 */
    public static final String LV_COUNTY = "县定";
    public static final String LV_CITY = "市定";
    public static final String LV_PROVINCE = "省定";
    public static final String LV_FALLBACK = "系统兜底";

    /** 开桶个数只认这一个量程 */
    public static final int GRAY_MIN = 0;
    public static final int GRAY_MAX = 10;

    /** 回话：0 走旧口径（镇级逐条人工签认） */
    public static final int ANSWER_OLD = 0;
    /** 回话：1 走新口径（系统按同季配对结果回话） */
    public static final int ANSWER_NEW = 1;

    /**
     * 并立几行取一条说话：先比改动次序（ver_no 数值大的先说话）；
     * 次序并列，再比代号字面先后，靠后那条说了算。任一项缺失按沉底处理，结果仍唯一。
     */
    public static TCtldSwitchCfg pickWinner(List<TCtldSwitchCfg> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return Collections.max(rows, WINNER);
    }

    private static final Comparator<TCtldSwitchCfg> WINNER = new Comparator<TCtldSwitchCfg>() {
        @Override
        public int compare(TCtldSwitchCfg a, TCtldSwitchCfg b) {
            int va = a.getVerNo() == null ? 0 : a.getVerNo();
            int vb = b.getVerNo() == null ? 0 : b.getVerNo();
            if (va != vb) {
                return va - vb;
            }
            String ca = a.getSwNo() == null ? "" : a.getSwNo();
            String cb = b.getSwNo() == null ? "" : b.getSwNo();
            return ca.compareTo(cb);
        }
    };

    /**
     * 第一节：一行怎么算数。三态与开桶个数必须配对，不合直接点名栏位回绝。
     */
    public static void validateKind(Integer swKind, Integer grayNum) {
        if (swKind == null || (swKind != KIND_OLD && swKind != KIND_NEW && swKind != KIND_BUCKET)) {
            throw new CtldRuleException("开关三态一栏不合：只认 0（一律旧口径）、1（一律新口径）、2（按桶推进）三种记法");
        }
        if (swKind == KIND_BUCKET) {
            if (grayNum == null) {
                throw new CtldRuleException("开桶个数一栏不合：三态记 2（按桶推进）却空着开桶个数，配不齐——"
                        + "开关三态这一栏因此也不作数，本行进不了库");
            }
            if (grayNum < GRAY_MIN || grayNum > GRAY_MAX) {
                throw new CtldRuleException("开桶个数一栏不合：只认 0 到 10 这一个量程");
            }
        } else if (grayNum != null) {
            throw new CtldRuleException("开桶个数一栏不合：三态记 0、1 时不按桶走，往开桶个数里填的数不作数——"
                    + "请清空该栏后再落行");
        }
    }

    /**
     * 第四节：三个处数怎么轧。应覆盖必填、非负、不得小于已切；已切不得反超。
     * 返回轧好的「还差几处」，客户端填的 lack_num 一概不收。
     */
    public static int reconcileLack(Integer crtNum, Integer hitNum) {
        if (crtNum == null) {
            throw new CtldRuleException("本项应覆盖处数一栏不合：没填或不是整数");
        }
        if (crtNum < 0) {
            throw new CtldRuleException("本项应覆盖处数一栏不合：不能是负数");
        }
        int hit = hitNum == null ? 0 : hitNum;
        if (hit < 0) {
            throw new CtldRuleException("已切到新口径的处数一栏不合：不能是负数");
        }
        if (hit > crtNum) {
            throw new CtldRuleException("已切到新口径的处数一栏不合：已切处数反超应覆盖处数，本行落不了库"
                    + "（本项应覆盖处数一栏也不得比已切处数还小）");
        }
        return crtNum - hit;
    }

    /**
     * 第三节：落桶。只取地块码最后一位数字定桶（0..9），
     * 不看岗位、不看通道；gray 个桶落新口径，写 3 即尾数 0、1、2 走新。
     */
    public static int bucketOf(String plotNo, int grayNum) {
        // 0 桶即全旧，不必也不该去抠地块码尾数（兜底行等效全旧，照样回话）
        if (grayNum <= GRAY_MIN) {
            return ANSWER_OLD;
        }
        if (plotNo == null || plotNo.length() == 0) {
            throw new CtldRuleException("地块码一栏不合：空码定不了桶");
        }
        char last = plotNo.charAt(plotNo.length() - 1);
        if (last < '0' || last > '9') {
            throw new CtldRuleException("地块码一栏不合：最后一位不是 0 到 9 的数字，定不了桶（" + plotNo + "）");
        }
        int bucket = last - '0';
        return bucket < grayNum ? ANSWER_NEW : ANSWER_OLD;
    }

    /**
     * 一行开关怎么回话。旧行三态空着（纸质簿搬进来的）按旧口径接，不许一碰就报错。
     */
    public static int answerOf(TCtldSwitchCfg row, String plotNo) {
        if (row == null || row.getSwKind() == null) {
            return ANSWER_OLD;
        }
        int kind = row.getSwKind();
        if (kind == KIND_NEW) {
            return ANSWER_NEW;
        }
        if (kind == KIND_OLD) {
            return ANSWER_OLD;
        }
        int gray = row.getGrayNum() == null ? 0 : row.getGrayNum();
        return bucketOf(plotNo, gray);
    }

    /**
     * 第二节：整条链都空着才落系统兜底——三态 2 配 0 个桶，等效全旧。
     */
    public static TCtldSwitchCfg fallbackRow() {
        TCtldSwitchCfg fb = new TCtldSwitchCfg();
        fb.setSwKind(KIND_BUCKET);
        fb.setGrayNum(0);
        fb.setLevelKind(LV_FALLBACK);
        fb.setSwName(LV_FALLBACK);
        return fb;
    }
}
