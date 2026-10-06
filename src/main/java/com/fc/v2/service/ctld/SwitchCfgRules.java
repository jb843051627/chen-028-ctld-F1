package com.fc.v2.service.ctld;

import com.fc.v2.common.exception.SwitchCfgException;
import com.fc.v2.model.auto.TCtldSwitchCfg;

import java.util.List;

/**
 * 产地准出核验口径切换开关——落口径的死规矩（第一节、第二节、第三节）。
 * 全是无状态的纯算法，不碰库、不碰内存册，谁来问都按同一套读法算。
 *
 * @author fuce
 */
public final class SwitchCfgRules {

    /** 三态：一律走旧口径 */
    public static final int KIND_OLD = 0;
    /** 三态：一律走新口径 */
    public static final int KIND_NEW = 1;
    /** 三态：按桶推进 */
    public static final int KIND_GRAY = 2;

    /** 开桶个数只认 0 到 10 这一个量程 */
    public static final int GRAY_MIN = 0;
    public static final int GRAY_MAX = 10;

    /** 三级册：待上 */
    public static final int STATUS_PENDING = 0;
    /** 三级册：已上 */
    public static final int STATUS_UP = 1;
    /** 三级册：压行（尽头） */
    public static final int STATUS_LOCKED = 2;

    public static final String LEVEL_COUNTY = "县定";
    public static final String LEVEL_CITY = "市定";
    public static final String LEVEL_PROVINCE = "省定";
    public static final String LEVEL_DEFAULT = "系统兜底";

    /** 回话时顺链往上找的次序：县定 → 市定 → 省定，整链空着才落兜底 */
    public static final String[] LEVEL_CHAIN = {LEVEL_COUNTY, LEVEL_CITY, LEVEL_PROVINCE};

    private SwitchCfgRules() {
    }

    /**
     * 第一节：三态与开桶个数成对校验，错了点名是哪一栏、另一栏为什么不作数。
     * 合法时把开桶个数归置成规范值（0/1 两态一律清成 null，2 态原样留）。
     */
    public static void validateKindGray(TCtldSwitchCfg row) {
        Integer kind = row.getSwKind();
        if (kind == null || (kind != KIND_OLD && kind != KIND_NEW && kind != KIND_GRAY)) {
            throw new SwitchCfgException("开关三态一栏只认 0（一律旧）、1（一律新）、2（按桶推进）三个记法");
        }
        Integer gray = row.getGrayNum();
        if (kind == KIND_GRAY) {
            if (gray == null) {
                throw new SwitchCfgException("开桶个数一栏空着不作数：三态记 2（按桶推进）时，这一栏必填 0 到 10");
            }
            if (gray < GRAY_MIN || gray > GRAY_MAX) {
                throw new SwitchCfgException("开桶个数一栏只认 0 到 10：写 3 即尾数 0、1、2 落新口径");
            }
        } else if (gray != null) {
            // 0、1 两态没有桶这一说，填了数也不认，一并清掉
            throw new SwitchCfgException("开桶个数一栏不作数：三态记 0 或 1 时不按桶走，这一格必须留空");
        }
    }

    /**
     * 第四节：应覆盖／已切两处校验，返回重轧后的“还差几处”。每回落库之前都重轧一遍，
     * 结果盖掉上一轮落库的值。8 配 5 得 3；6 配 6 得 0。
     */
    public static int recountLack(Integer crtNum, Integer hitNum) {
        if (crtNum == null) {
            throw new SwitchCfgException("本项应覆盖处数一栏没填：这一行进不了库");
        }
        if (crtNum < 0) {
            throw new SwitchCfgException("本项应覆盖处数一栏不能是负数：这一行进不了库");
        }
        int hit = hitNum == null ? 0 : hitNum;
        if (hit < 0) {
            throw new SwitchCfgException("已切到新口径的处数一栏不能是负数：这一行进不了库");
        }
        if (hit > crtNum) {
            throw new SwitchCfgException("已切到新口径的处数一栏反超本项应覆盖处数：这一行进不了库");
        }
        return crtNum - hit;
    }

    /**
     * 第三节：只取地块码的最后一位数字定桶，0 到 9 共十个桶；不看登录的是哪一岗、
     * 不看请求从哪个通道进来。开了 grayNum 个桶时，尾数 0..grayNum-1 落新口径：
     * 3 个桶即尾数 0、1、2 走新；0 个桶等效全旧，10 个桶等效全新。
     */
    public static int bucketOf(String plotNo) {
        if (plotNo == null || plotNo.length() == 0) {
            throw new SwitchCfgException("地块码空着：取不到最后一位，定不了桶");
        }
        char tail = plotNo.charAt(plotNo.length() - 1);
        if (tail < '0' || tail > '9') {
            throw new SwitchCfgException("地块码最后一位不是 0 到 9 的数字，定不了桶：" + plotNo);
        }
        return tail - '0';
    }

    /** 这一行、这一处地块，是否走新口径。旧行（三态空着）与新行走同一套读法。 */
    public static boolean hitNew(TCtldSwitchCfg row, String plotNo) {
        if (row == null || row.getSwKind() == null) {
            return false;
        }
        int kind = row.getSwKind();
        if (kind == KIND_NEW) {
            return true;
        }
        if (kind == KIND_OLD) {
            return false;
        }
        int gray = row.getGrayNum() == null ? 0 : row.getGrayNum();
        return bucketOf(plotNo) < gray;
    }

    /**
     * 第二节：同一名目并立几行时的取优——先比改动次序（ver_no 大的先说话），
     * 次序并列再比代号的字面先后，靠后那条说了算。
     */
    public static TCtldSwitchCfg pickBest(List<TCtldSwitchCfg> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        TCtldSwitchCfg best = null;
        for (TCtldSwitchCfg row : rows) {
            if (best == null) {
                best = row;
                continue;
            }
            int cmp = verOf(row) - verOf(best);
            if (cmp == 0) {
                cmp = swNoOf(row).compareTo(swNoOf(best));
            }
            if (cmp > 0) {
                best = row;
            }
        }
        return best;
    }

    private static int verOf(TCtldSwitchCfg row) {
        return row.getVerNo() == null ? 0 : row.getVerNo();
    }

    private static String swNoOf(TCtldSwitchCfg row) {
        return row.getSwNo() == null ? "" : row.getSwNo();
    }

    /** 第二节：系统兜底行——三态 2 配 0 个桶，等效于全旧。 */
    public static TCtldSwitchCfg fallbackRow() {
        TCtldSwitchCfg row = new TCtldSwitchCfg();
        row.setSwKind(KIND_GRAY);
        row.setGrayNum(0);
        row.setLevelKind(LEVEL_DEFAULT);
        return row;
    }
}
