package com.fc.v2.service.ctld;

import com.fc.v2.common.exception.SwitchCfgException;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一/二/三/四节里纯算数部分的单测。
 */
class SwitchCfgRulesTest {

    private TCtldSwitchCfg row(Integer kind, Integer gray) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setSwKind(kind);
        r.setGrayNum(gray);
        return r;
    }

    // ---------------- 第一节：三态与桶数 ----------------

    @Test
    void kindGrayMismatchNamesTheField() {
        SwitchCfgException e1 = assertThrows(SwitchCfgException.class,
                () -> SwitchCfgRules.validateKindGray(row(2, null)));
        assertTrue(e1.getMessage().contains("开桶个数"), "三态2桶数空着，要点名开桶个数一栏");

        SwitchCfgException e2 = assertThrows(SwitchCfgException.class,
                () -> SwitchCfgRules.validateKindGray(row(0, 3)));
        assertTrue(e2.getMessage().contains("开桶个数") && e2.getMessage().contains("不作数"),
                "三态0/1填了桶数，要说明这一栏为什么不作数");

        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.validateKindGray(row(1, 0)));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.validateKindGray(row(9, null)));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.validateKindGray(row(2, 11)));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.validateKindGray(row(2, -1)));
    }

    @Test
    void kindGrayValidPairs() {
        SwitchCfgRules.validateKindGray(row(2, 0));
        SwitchCfgRules.validateKindGray(row(2, 3));
        SwitchCfgRules.validateKindGray(row(2, 10));
        SwitchCfgRules.validateKindGray(row(0, null));
        SwitchCfgRules.validateKindGray(row(1, null));
    }

    // ---------------- 第四节：重轧还差几处 ----------------

    @Test
    void lackIsRecountedEveryTime() {
        assertEquals(3, SwitchCfgRules.recountLack(8, 5));
        assertEquals(0, SwitchCfgRules.recountLack(6, 6));
        assertEquals(6, SwitchCfgRules.recountLack(6, null));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.recountLack(null, 1));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.recountLack(-1, 0));
        SwitchCfgException over = assertThrows(SwitchCfgException.class,
                () -> SwitchCfgRules.recountLack(5, 6));
        assertTrue(over.getMessage().contains("已切") && over.getMessage().contains("反超"));
    }

    // ---------------- 第三节：末位落桶 ----------------

    @Test
    void bucketIsLastDigitOnly() {
        assertEquals(3, SwitchCfgRules.bucketOf("DK-2026-3"));
        assertEquals(0, SwitchCfgRules.bucketOf("X0"));
        assertEquals(9, SwitchCfgRules.bucketOf("9"));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.bucketOf("DK-X"));
        assertThrows(SwitchCfgException.class, () -> SwitchCfgRules.bucketOf(""));
    }

    @Test
    void grayBucketsSendLowTailsToNew() {
        // 写 3 即尾数 0、1、2 走新
        TCtldSwitchCfg g3 = row(2, 3);
        assertTrue(SwitchCfgRules.hitNew(g3, "P0"));
        assertTrue(SwitchCfgRules.hitNew(g3, "P1"));
        assertTrue(SwitchCfgRules.hitNew(g3, "P2"));
        assertFalse(SwitchCfgRules.hitNew(g3, "P3"));
        assertFalse(SwitchCfgRules.hitNew(g3, "P9"));

        // 0 个桶等效全旧，10 个桶等效全新
        assertFalse(SwitchCfgRules.hitNew(row(2, 0), "P0"));
        for (int t = 0; t <= 9; t++) {
            assertTrue(SwitchCfgRules.hitNew(row(2, 10), "P" + t));
        }

        assertTrue(SwitchCfgRules.hitNew(row(1, null), "P5"));
        assertFalse(SwitchCfgRules.hitNew(row(0, null), "P5"));

        // 旧行：三态空着与新行走同一套读法，按全旧接
        assertFalse(SwitchCfgRules.hitNew(row(null, null), "P0"));
        assertFalse(SwitchCfgRules.hitNew(null, "P0"));
    }

    // ---------------- 第二节：取优与兜底 ----------------

    @Test
    void pickBestByVersionThenSwNo() {
        List<TCtldSwitchCfg> rows = new ArrayList<>();
        rows.add(cfg("SW1", 1));
        rows.add(cfg("SW2", 2));
        assertEquals("SW2", SwitchCfgRules.pickBest(rows).getSwNo());

        rows.clear();
        rows.add(cfg("SW9", 5));
        rows.add(cfg("SW1", 5));
        // 次序并列，代号字面靠后那条说了算
        assertEquals("SW9", SwitchCfgRules.pickBest(rows).getSwNo());

        assertNull(SwitchCfgRules.pickBest(new ArrayList<>()));
        assertNull(SwitchCfgRules.pickBest(null));
    }

    @Test
    void fallbackIsGrayZeroEquivalentOld() {
        TCtldSwitchCfg fb = SwitchCfgRules.fallbackRow();
        assertEquals(2, fb.getSwKind());
        assertEquals(0, fb.getGrayNum());
        assertEquals("系统兜底", fb.getLevelKind());
        assertFalse(SwitchCfgRules.hitNew(fb, "P0"));
    }

    private TCtldSwitchCfg cfg(String swNo, Integer verNo) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setSwNo(swNo);
        r.setVerNo(verNo);
        return r;
    }
}
