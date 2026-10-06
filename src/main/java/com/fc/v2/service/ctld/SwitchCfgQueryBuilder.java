package com.fc.v2.service.ctld;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.custom.SwitchCfgQuery;
import com.fc.v2.util.StringUtils;

/**
 * 第五节配置台查行条件：只查未删的行；分区代号、切换名目两栏前缀匹配与全等匹配都认；
 * 开关三态只认全等。分层范围由 {@link SwitchCfgScope} 另叠。
 *
 * @author fuce
 */
public final class SwitchCfgQueryBuilder {

    private SwitchCfgQueryBuilder() {
    }

    public static QueryWrapper<TCtldSwitchCfg> build(SwitchCfgQuery query) {
        QueryWrapper<TCtldSwitchCfg> wrapper = new QueryWrapper<TCtldSwitchCfg>()
                .eq("del_flag", 0);
        if (query != null) {
            matchEqOrPrefix(wrapper, "zone_no", query.getZoneNo());
            matchEqOrPrefix(wrapper, "sw_name", query.getSwName());
            if (query.getSwKind() != null) {
                wrapper.eq("sw_kind", query.getSwKind());
            }
        }
        wrapper.orderByDesc("create_time");
        return wrapper;
    }

    private static void matchEqOrPrefix(QueryWrapper<TCtldSwitchCfg> wrapper, String column, String value) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        wrapper.and(w -> w.eq(column, value).or().likeRight(column, value));
    }
}
