package com.fc.v2.service.ctld;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存中备着的那一份当前配置（第四节：改完要在库里这一行与内存这一份两处同时落地）。
 * 回话通道（按代号取值、整表取优）一律读本件，不临库；任何落库动作之后由业务层调
 * {@link #reload()} 当场重轧。读多写一，快照整体替换。
 *
 * @author fuce
 */
@Component
public class SwitchCfgSnapshot {

    private static final Logger log = LoggerFactory.getLogger(SwitchCfgSnapshot.class);

    @Resource
    private TCtldSwitchCfgMapper switchCfgMapper;

    private volatile List<TCtldSwitchCfg> rows = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            // 库还没接上时不挡启动；此时内存册空着，回话一律走兜底（等效全旧），等首次落库后重轧
            log.warn("切换开关内存册初次装载落空，先按空册回话：{}", e.getMessage());
        }
    }

    /** 库里落定之后，把内存这一份整体重轧一遍。 */
    public synchronized void reload() {
        List<TCtldSwitchCfg> latest = switchCfgMapper.selectList(
                new QueryWrapper<TCtldSwitchCfg>().eq("del_flag", 0));
        this.rows = Collections.unmodifiableList(new ArrayList<>(latest));
    }

    /** 当前在册（未撤下）的全部行。 */
    public List<TCtldSwitchCfg> all() {
        return rows;
    }

    /** 按代号取值通道：代号唯一指一行；旧行代号空着自然取不到。 */
    public TCtldSwitchCfg bySwNo(String swNo) {
        if (swNo == null) {
            return null;
        }
        TCtldSwitchCfg hit = null;
        for (TCtldSwitchCfg row : rows) {
            if (swNo.equals(row.getSwNo())) {
                hit = row;
            }
        }
        return hit;
    }

    /**
     * 某一层、某一名目下并立的行。县定行按分区收口；旧行层级空着的，按县定一并接进链头。
     * 市定、省定行管一片，不按分区切。
     */
    public List<TCtldSwitchCfg> atLevel(String levelKind, String swName, Integer zoneId, String zoneNo) {
        List<TCtldSwitchCfg> hits = new ArrayList<>();
        for (TCtldSwitchCfg row : rows) {
            if (!swName.equals(row.getSwName())) {
                continue;
            }
            String lv = row.getLevelKind();
            boolean levelMatch = levelKind.equals(lv)
                    || (SwitchCfgRules.LEVEL_COUNTY.equals(levelKind) && lv == null);
            if (!levelMatch) {
                continue;
            }
            if (SwitchCfgRules.LEVEL_COUNTY.equals(levelKind)) {
                boolean zoneMatch = zoneId != null && zoneId.equals(row.getZoneId())
                        || zoneNo != null && zoneNo.equals(row.getZoneNo());
                if (!zoneMatch) {
                    continue;
                }
            }
            hits.add(row);
        }
        return hits;
    }
}
