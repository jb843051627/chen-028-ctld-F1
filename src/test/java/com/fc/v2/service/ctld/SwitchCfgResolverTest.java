package com.fc.v2.service.ctld;

import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二节继承链取优、第三节两通道等价的单测。内存册用按层存行的假件顶。
 */
class SwitchCfgResolverTest {

    /** key = 层级|名目 */
    private final Map<String, List<TCtldSwitchCfg>> store = new HashMap<>();
    private final List<TCtldSwitchCfg> allRows = new ArrayList<>();
    private SwitchCfgResolver resolver;

    @BeforeEach
    void setUp() {
        SwitchCfgSnapshot fake = new SwitchCfgSnapshot() {
            @Override
            public TCtldSwitchCfg bySwNo(String swNo) {
                for (TCtldSwitchCfg row : allRows) {
                    if (swNo.equals(row.getSwNo())) {
                        return row;
                    }
                }
                return null;
            }

            @Override
            public List<TCtldSwitchCfg> atLevel(String levelKind, String swName, Integer zoneId, String zoneNo) {
                List<TCtldSwitchCfg> rows = store.get(levelKind + "|" + swName);
                if (rows == null) {
                    return new ArrayList<>();
                }
                List<TCtldSwitchCfg> hits = new ArrayList<>();
                for (TCtldSwitchCfg row : rows) {
                    if (SwitchCfgRules.LEVEL_COUNTY.equals(levelKind)) {
                        boolean zoneMatch = (zoneId != null && zoneId.equals(row.getZoneId()))
                                || (zoneNo != null && zoneNo.equals(row.getZoneNo()));
                        if (!zoneMatch) {
                            continue;
                        }
                    }
                    hits.add(row);
                }
                return hits;
            }
        };
        resolver = new SwitchCfgResolver();
        ReflectionTestUtils.setField(resolver, "snapshot", fake);
    }

    private TCtldSwitchCfg put(String level, String swNo, String swName, Integer kind, Integer gray,
                               Integer ver, Integer zoneId, String zoneNo) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setLevelKind(level);
        r.setSwNo(swNo);
        r.setSwName(swName);
        r.setSwKind(kind);
        r.setGrayNum(gray);
        r.setVerNo(ver);
        r.setZoneId(zoneId);
        r.setZoneNo(zoneNo);
        store.computeIfAbsent(level + "|" + swName, k -> new ArrayList<>()).add(r);
        allRows.add(r);
        return r;
    }

    @Test
    void emptyChainFallsBackToGrayZeroAllOld() {
        SwitchDecision d = resolver.resolve(7, "Z7", "同季配对回话", "P3");
        assertFalse(d.isNewCaliber());
        assertEquals(2, d.getSwKind());
        assertEquals(0, d.getGrayNum());
        assertEquals("系统兜底", d.getLevelKind());
        assertNull(d.getSwNo());
    }

    @Test
    void chainWalksCountyCityProvince() {
        put("省定", "PV", "两式并行", 1, null, 1, null, null);
        put("市定", "CY", "两式并行", 1, null, 1, null, null);
        // 县定没有 → 落市定
        SwitchDecision d1 = resolver.resolve(7, "Z7", "两式并行", "P8");
        assertEquals("CY", d1.getSwNo());
        assertEquals("市定", d1.getLevelKind());

        // 市定没有 → 落省定
        store.remove("市定|两式并行");
        SwitchDecision d2 = resolver.resolve(7, "Z7", "两式并行", "P8");
        assertEquals("PV", d2.getSwNo());

        // 县定立了行 → 市、省都不再说话
        put("县定", "CN", "两式并行", 0, null, 1, 7, "Z7");
        SwitchDecision d3 = resolver.resolve(7, "Z7", "两式并行", "P8");
        assertEquals("CN", d3.getSwNo());
        assertEquals("县定", d3.getLevelKind());
        assertFalse(d3.isNewCaliber());
    }

    @Test
    void bestOfParallelRowsIsHighVersionThenLateSwNo() {
        put("县定", "SW_A", "同季配对回话", 2, 2, 5, 7, "Z7");
        put("县定", "SW_B", "同季配对回话", 1, null, 9, 7, "Z7");
        assertEquals("SW_B", resolver.resolve(7, "Z7", "同季配对回话", "P9").getSwNo());

        // ver 并列：代号字面靠后那条说了算
        TCtldSwitchCfg c = put("县定", "SW_C", "同季配对回话", 0, null, 9, 7, "Z7");
        c.setStatus(null);
        assertEquals("SW_C", resolver.resolve(7, "Z7", "同季配对回话", "P9").getSwNo());
    }

    @Test
    void twoChannelsGiveTheSameAnswer() {
        TCtldSwitchCfg row = put("县定", "SW_X", "同季配对回话", 2, 3, 1, 7, "Z7");
        for (String plotNo : Arrays.asList("P0", "P2", "P3", "P9", "DK-2026-0")) {
            SwitchDecision byCode = resolver.decideBySwNo("SW_X", plotNo);
            SwitchDecision byTable = resolver.resolve(7, "Z7", "同季配对回话", plotNo);
            assertEquals(byCode.isNewCaliber(), byTable.isNewCaliber(),
                    "两通道对 " + plotNo + " 的回话必须一致");
            assertEquals(row.getSwNo(), byCode.getSwNo());
        }
    }

    @Test
    void oldChannelMissingRowFallsBackInsteadOfSilentlySwitching() {
        // 去年南片那类事故：表里没行，绝不能整套换口径，必须回兜底全旧
        SwitchDecision d = resolver.decideBySwNo("NEVER", "P0");
        assertFalse(d.isNewCaliber());
        assertEquals("系统兜底", d.getLevelKind());
        assertNull(resolver.getBySwNo("NEVER"));
    }

    @Test
    void legacyRowsWithNullKindReadAllOld() {
        TCtldSwitchCfg legacy = put("县定", "SW_OLD", "逐条人工签认", null, null, null, 7, "Z7");
        legacy.setStatus(null);
        assertFalse(resolver.decideBySwNo("SW_OLD", "P0").isNewCaliber());
        assertFalse(resolver.resolve(7, "Z7", "逐条人工签认", "P9").isNewCaliber());
    }
}
