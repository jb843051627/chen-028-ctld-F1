package com.fc.v2.service.ctld;

import com.fc.v2.common.conf.CtldProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第五节分层可见范围判定。
 */
class SwitchCfgScopeTest {

    private CtldProperties props() {
        CtldProperties p = new CtldProperties();
        p.getUserZones().put("county1", "QC00, QC02");
        return p;
    }

    private HashSet<String> roles(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    @Test
    void adminSeesAll() {
        assertEquals(ScopeRestriction.Kind.ALL,
                SwitchCfgScope.decide("root", roles("管理员"), null, null, props()).getKind());
    }

    @Test
    void countySeesAllThreeLayersWithoutRoster() {
        assertEquals(ScopeRestriction.Kind.COUNTY_ALL,
                SwitchCfgScope.decide("county9", roles("县局岗"), null, null, props()).getKind());
    }

    @Test
    void countyIsLimitedToAssignedZones() {
        ScopeRestriction r = SwitchCfgScope.decide("county1", roles("县局岗"), null, null, props());
        assertEquals(ScopeRestriction.Kind.COUNTY_ZONES, r.getKind());
        assertEquals(Arrays.asList("QC00", "QC02"), r.getZoneNos());
    }

    @Test
    void dutyOnlySeesOwnLayer() {
        ScopeRestriction r = SwitchCfgScope.decide("d1", roles("甲层值守岗"), "甲层",
                Arrays.asList("QC00", "QC05"), props());
        assertEquals(ScopeRestriction.Kind.LAYER, r.getKind());
        assertEquals(2, r.getZoneNos().size());
    }

    @Test
    void emptyLayerIsLegalEmptyViewNotNeighborRows() {
        ScopeRestriction r = SwitchCfgScope.decide("d2", roles("丙层值守岗"), "丙层",
                Collections.emptyList(), props());
        assertEquals(ScopeRestriction.Kind.LAYER, r.getKind());
        assertEquals(0, r.getZoneNos().size(), "本层册里空着是合法空视图，调用方拼查不到的条件，不拿邻层凑");
    }

    @Test
    void nobodyGetsFullTableWithoutAnyRole() {
        assertEquals(ScopeRestriction.Kind.NONE,
                SwitchCfgScope.decide("x", roles(), null, null, props()).getKind());
        assertEquals(ScopeRestriction.Kind.NONE,
                SwitchCfgScope.decide(null, null, null, null, props()).getKind());
    }
}
