package com.fc.v2.service.ctld;

import com.fc.v2.model.custom.SwitchCfgQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五节三个查询通道：分区代号、切换名目认前缀与全等；三态只认全等。
 */
class SwitchCfgQueryBuilderTest {

    private String sql(SwitchCfgQuery q) {
        return SwitchCfgQueryBuilder.build(q).getSqlSegment();
    }

    @Test
    void zoneNoAndNameAcceptEqOrPrefix() {
        SwitchCfgQuery q = new SwitchCfgQuery();
        q.setZoneNo("QC0");
        q.setSwName("同季");
        String s = sql(q).toUpperCase();
        assertTrue(s.contains("ZONE_NO ="));
        assertTrue(s.contains("ZONE_NO LIKE"), "前缀匹配要认");
        assertTrue(s.contains("SW_NAME ="));
        assertTrue(s.contains("SW_NAME LIKE"));
    }

    @Test
    void swKindIsExactOnly() {
        SwitchCfgQuery q = new SwitchCfgQuery();
        q.setSwKind(2);
        String s = sql(q);
        assertTrue(s.contains("sw_kind ="));
        assertFalse(s.toUpperCase().contains("SW_KIND LIKE"), "三态只认全等");
    }

    @Test
    void emptyQueryOnlyFiltersDelFlag() {
        String s = sql(new SwitchCfgQuery()).toUpperCase();
        assertTrue(s.contains("DEL_FLAG ="));
        assertFalse(s.contains("LIKE"));
    }
}
