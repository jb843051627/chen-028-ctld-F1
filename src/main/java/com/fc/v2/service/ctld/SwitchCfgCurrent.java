package com.fc.v2.service.ctld;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.conf.redis.RedisService;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中备着的那一份「当前配置」。
 * 规矩：库改了，内存这一份必须同一趟一起改——只落一处，页面会拿旧版继续回话。
 * 进程内 Map 是回话当场读的那一份；Redis 是多实例/重启后的兜底，同键同值双写。
 *
 * @author fuce
 */
@Component
public class SwitchCfgCurrent {

    private static final Logger logger = LoggerFactory.getLogger(SwitchCfgCurrent.class);

    /** Redis 键前缀，冒号分段与项目既有缓存一致 */
    private static final String RK_COUNTY = "ctld:switch:cur:county:";
    private static final String RK_UPPER = "ctld:switch:cur:upper:";

    /** 县定行：分区代号 + 切换名目 → 当下说话的那一条 */
    private final ConcurrentHashMap<String, TCtldSwitchCfg> countyMap = new ConcurrentHashMap<String, TCtldSwitchCfg>();

    /** 市定／省定行：层级 + 切换名目 → 当下说话的那一条 */
    private final ConcurrentHashMap<String, TCtldSwitchCfg> upperMap = new ConcurrentHashMap<String, TCtldSwitchCfg>();

    @Resource
    private TCtldSwitchCfgMapper switchCfgMapper;

    @Autowired(required = false)
    private RedisService redisService;

    @PostConstruct
    public void warmUp() {
        try {
            List<TCtldSwitchCfg> all = switchCfgMapper.selectList(
                    new QueryWrapper<TCtldSwitchCfg>().eq("del_flag", 0));
            java.util.Map<String, List<TCtldSwitchCfg>> countySlots = new java.util.HashMap<String, List<TCtldSwitchCfg>>();
            java.util.Map<String, List<TCtldSwitchCfg>> upperSlots = new java.util.HashMap<String, List<TCtldSwitchCfg>>();
            for (TCtldSwitchCfg row : all) {
                if (row.getSwName() == null) {
                    continue;
                }
                if (SwitchCfgRule.LV_COUNTY.equals(row.getLevelKind()) && row.getZoneNo() != null) {
                    slot(countySlots, row.getZoneNo() + "|" + row.getSwName()).add(row);
                } else if (row.getLevelKind() != null) {
                    slot(upperSlots, row.getLevelKind() + "|" + row.getSwName()).add(row);
                }
            }
            for (java.util.Map.Entry<String, List<TCtldSwitchCfg>> e : countySlots.entrySet()) {
                int cut = e.getKey().indexOf('|');
                replaceCounty(e.getKey().substring(0, cut), e.getKey().substring(cut + 1),
                        SwitchCfgRule.pickWinner(e.getValue()));
            }
            for (java.util.Map.Entry<String, List<TCtldSwitchCfg>> e : upperSlots.entrySet()) {
                int cut = e.getKey().indexOf('|');
                replaceUpper(e.getKey().substring(0, cut), e.getKey().substring(cut + 1),
                        SwitchCfgRule.pickWinner(e.getValue()));
            }
            logger.info("切换开关当前配置预热完成，在位 {} 行", all.size());
        } catch (Exception e) {
            // 预热失败不能挡启动；当场问时还有 miss 回源
            logger.warn("切换开关当前配置预热失败，将在首次回话时回源：{}", e.getMessage());
        }
    }

    private static List<TCtldSwitchCfg> slot(java.util.Map<String, List<TCtldSwitchCfg>> slots, String key) {
        List<TCtldSwitchCfg> list = slots.get(key);
        if (list == null) {
            list = new java.util.ArrayList<TCtldSwitchCfg>();
            slots.put(key, list);
        }
        return list;
    }

    /** 库落定之后调用：内存与 Redis 两处同落。 */
    public void put(TCtldSwitchCfg row) {
        put(row, true);
    }

    /**
     * 一处槽位有过增／改／撤／改挂之后，按库里现状重取说话的那一条
     * （并立几行由改动次序、代号裁决），库里空了就把内存格子一并腾空。
     */
    public void reloadCounty(String zoneNo, String swName) {
        List<TCtldSwitchCfg> rows = switchCfgMapper.selectList(new QueryWrapper<TCtldSwitchCfg>()
                .eq("del_flag", 0)
                .eq("zone_no", zoneNo).eq("sw_name", swName));
        replaceCounty(zoneNo, swName, SwitchCfgRule.pickWinner(rows));
    }

    public void reloadUpper(String levelKind, String swName) {
        List<TCtldSwitchCfg> rows = switchCfgMapper.selectList(new QueryWrapper<TCtldSwitchCfg>()
                .eq("del_flag", 0)
                .eq("level_kind", levelKind).eq("sw_name", swName));
        replaceUpper(levelKind, swName, SwitchCfgRule.pickWinner(rows));
    }

    private void replaceCounty(String zoneNo, String swName, TCtldSwitchCfg row) {
        String key = countyKey(zoneNo, swName);
        if (row == null) {
            countyMap.remove(key);
            if (redisService != null) {
                redisService.deleteObject(RK_COUNTY + key);
            }
            return;
        }
        countyMap.put(key, row);
        cache(true, RK_COUNTY + key, row);
    }

    private void replaceUpper(String levelKind, String swName, TCtldSwitchCfg row) {
        String key = upperKey(levelKind, swName);
        if (row == null) {
            upperMap.remove(key);
            if (redisService != null) {
                redisService.deleteObject(RK_UPPER + key);
            }
            return;
        }
        upperMap.put(key, row);
        cache(true, RK_UPPER + key, row);
    }

    private void put(TCtldSwitchCfg row, boolean toRedis) {
        if (row == null || row.getSwName() == null) {
            return;
        }
        if (SwitchCfgRule.LV_COUNTY.equals(row.getLevelKind())) {
            if (row.getZoneNo() == null) {
                return;
            }
            String key = countyKey(row.getZoneNo(), row.getSwName());
            TCtldSwitchCfg exists = countyMap.get(key);
            if (exists == null || SwitchCfgRule.pickWinner(java.util.Arrays.asList(exists, row)) == row) {
                countyMap.put(key, row);
                cache(toRedis, RK_COUNTY + key, row);
            }
        } else {
            String key = upperKey(row.getLevelKind(), row.getSwName());
            TCtldSwitchCfg exists = upperMap.get(key);
            if (exists == null || SwitchCfgRule.pickWinner(java.util.Arrays.asList(exists, row)) == row) {
                upperMap.put(key, row);
                cache(toRedis, RK_UPPER + key, row);
            }
        }
    }

    public TCtldSwitchCfg countyRow(String zoneNo, String swName) {
        String key = countyKey(zoneNo, swName);
        TCtldSwitchCfg row = countyMap.get(key);
        if (row == null) {
            row = redis(RK_COUNTY + key);
            if (row != null) {
                countyMap.put(key, row);
            }
        }
        return row;
    }

    public TCtldSwitchCfg upperRow(String levelKind, String swName) {
        String key = upperKey(levelKind, swName);
        TCtldSwitchCfg row = upperMap.get(key);
        if (row == null) {
            row = redis(RK_UPPER + key);
            if (row != null) {
                upperMap.put(key, row);
            }
        }
        return row;
    }

    private void cache(boolean toRedis, String redisKey, TCtldSwitchCfg row) {
        if (toRedis && redisService != null) {
            try {
                redisService.setCacheObject(redisKey, row);
            } catch (Exception e) {
                // Redis 抖动不挡库内改动；内存已落地，重启预热会补齐
                logger.warn("切换开关当前配置写 Redis 未成功：{}", e.getMessage());
            }
        }
    }

    private TCtldSwitchCfg redis(String key) {
        if (redisService == null) {
            return null;
        }
        try {
            Object obj = redisService.getCacheObject(key);
            return obj instanceof TCtldSwitchCfg ? (TCtldSwitchCfg) obj : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String countyKey(String zoneNo, String swName) {
        return zoneNo + "|" + swName;
    }

    private static String upperKey(String levelKind, String swName) {
        return (levelKind == null ? "" : levelKind) + "|" + swName;
    }
}
