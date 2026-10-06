package com.fc.v2.service.ctld;

import java.util.Collections;
import java.util.List;

/**
 * 第五节分层可见范围判定的结果（纯值，不沾 Shiro/SQL，便于单测）。
 *
 * @author fuce
 */
public class ScopeRestriction {

    public enum Kind {
        /** 管理员：不收窄，三层都看得见 */
        ALL,
        /** 县局岗且无划归名单：三层都看得见 */
        COUNTY_ALL,
        /** 县局岗有划归名单：限名单内分区（另放行无挂靠的行） */
        COUNTY_ZONES,
        /** 本层值守岗：只收挂靠这些分区代号的行；名单为空表示本层一个分区都没有，给空视图 */
        LAYER,
        /** 一层都不靠：一行也不该看见，绝不放全表凑数 */
        NONE
    }

    private final Kind kind;
    private final List<String> zoneNos;

    private ScopeRestriction(Kind kind, List<String> zoneNos) {
        this.kind = kind;
        this.zoneNos = zoneNos == null ? Collections.emptyList() : zoneNos;
    }

    public static ScopeRestriction all() {
        return new ScopeRestriction(Kind.ALL, null);
    }

    public static ScopeRestriction countyAll() {
        return new ScopeRestriction(Kind.COUNTY_ALL, null);
    }

    public static ScopeRestriction countyZones(List<String> zoneNos) {
        return new ScopeRestriction(Kind.COUNTY_ZONES, zoneNos);
    }

    public static ScopeRestriction layer(List<String> zoneNos) {
        return new ScopeRestriction(Kind.LAYER, zoneNos);
    }

    public static ScopeRestriction none() {
        return new ScopeRestriction(Kind.NONE, null);
    }

    public Kind getKind() {
        return kind;
    }

    public List<String> getZoneNos() {
        return zoneNos;
    }
}
