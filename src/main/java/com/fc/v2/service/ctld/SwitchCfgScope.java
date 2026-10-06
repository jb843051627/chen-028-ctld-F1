package com.fc.v2.service.ctld;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.conf.CtldProperties;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.model.auto.TSysRole;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSysRoleService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第五节：配置台看得见什么，按层切。
 * 本层值守岗只看得见挂靠本层（分区册层类别）的行；县局岗三层都看得见，范围仍限于
 * 划归它的分区；管理员不收窄。视图空着是合法结果，绝不拿邻层的行凑数。
 *
 * @author fuce
 */
@Component
public class SwitchCfgScope {

    @Resource
    private CtldProperties ctldProperties;

    @Resource
    private ITSysRoleService sysRoleService;

    @Resource
    private TCtldZoneBookMapper zoneBookMapper;

    /**
     * 把当前登录人的可见范围叠进查询条件。范围收口后都返回 true；当前人一层都不靠时
     * 仍叠一个查不到的条件并返回 false（调用方直接给空视图，不放行全表）。
     */
    public boolean apply(QueryWrapper<TCtldSwitchCfg> wrapper) {
        TSysUser user = currentUser();
        Set<String> roleNames = roleNamesOf(user);
        String layer = layerOf(roleNames);
        List<String> layerZones = layer == null ? Collections.emptyList() : zonesOfLayer(layer);

        ScopeRestriction restriction = decide(
                user == null ? null : user.getUsername(), roleNames, layer, layerZones, ctldProperties);

        switch (restriction.getKind()) {
            case ALL:
            case COUNTY_ALL:
                return true;
            case COUNTY_ZONES:
                wrapper.and(w -> w.in("zone_no", restriction.getZoneNos()).or().isNull("zone_no"));
                return true;
            case LAYER:
                if (restriction.getZoneNos().isEmpty()) {
                    // 本层册里一个分区都没有：给空视图而不是全表
                    wrapper.eq("zone_no", "__NONE__");
                } else {
                    wrapper.in("zone_no", restriction.getZoneNos());
                }
                return true;
            case NONE:
            default:
                wrapper.eq("zone_no", "__NONE__");
                return false;
        }
    }

    /** 纯判定：给定账号、角色、本层分区名单，落哪一档可见范围。 */
    public static ScopeRestriction decide(String username, Set<String> roleNames, String layer,
                                          List<String> layerZoneNos, CtldProperties props) {
        Set<String> roles = roleNames == null ? Collections.emptySet() : roleNames;
        for (String admin : props.getAdminRoles()) {
            if (roles.contains(admin)) {
                return ScopeRestriction.all();
            }
        }
        boolean county = false;
        for (String countyRole : props.getCountyRoles()) {
            if (roles.contains(countyRole)) {
                county = true;
                break;
            }
        }
        if (county) {
            String assigned = username == null ? null : props.getUserZones().get(username);
            if (StringUtils.isNotEmpty(assigned)) {
                return ScopeRestriction.countyZones(splitZones(assigned));
            }
            return ScopeRestriction.countyAll();
        }
        if (layer != null) {
            return ScopeRestriction.layer(layerZoneNos == null ? Collections.emptyList() : layerZoneNos);
        }
        return ScopeRestriction.none();
    }

    private String layerOf(Set<String> roleNames) {
        for (Map.Entry<String, String> entry : ctldProperties.getDutyLayerRoles().entrySet()) {
            if (roleNames.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> zonesOfLayer(String layer) {
        List<TCtldZoneBook> zones = zoneBookMapper.selectList(new QueryWrapper<TCtldZoneBook>()
                .eq("del_flag", 0).eq("zone_kind", layer).select("zone_no"));
        List<String> zoneNos = new ArrayList<>();
        for (TCtldZoneBook zone : zones) {
            if (StringUtils.isNotEmpty(zone.getZoneNo())) {
                zoneNos.add(zone.getZoneNo());
            }
        }
        return zoneNos;
    }

    private static List<String> splitZones(String assigned) {
        List<String> list = new ArrayList<>();
        for (String part : Arrays.asList(assigned.split("[,，]"))) {
            String trimmed = part.trim();
            if (trimmed.length() > 0) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private TSysUser currentUser() {
        try {
            return ShiroUtils.getUser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Set<String> roleNamesOf(TSysUser user) {
        Set<String> names = new HashSet<>();
        if (user == null || user.getId() == null) {
            return names;
        }
        List<TSysRole> roles = sysRoleService.queryUserRole(user.getId());
        if (roles != null) {
            for (TSysRole role : roles) {
                if (StringUtils.isNotEmpty(role.getName())) {
                    names.add(role.getName());
                }
            }
        }
        return names;
    }
}
