package com.fc.v2.service.ctld;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第五节：行上冗余存的分区代号、分区全称必须与分区册一处口径。
 * 建行、改挂当时已按分区册回写；本件在启动时再把早年间存得“两处各说各话”的行统一校准。
 * 挂靠分区已并走撤出或册里查无的旧行不抹、不改——它们照常列得出来、算进在册处数。
 *
 * @author fuce
 */
@Component
public class SwitchCfgBookCalibrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SwitchCfgBookCalibrator.class);

    @Resource
    private TCtldSwitchCfgMapper switchCfgMapper;
    @Resource
    private TCtldZoneBookMapper zoneBookMapper;
    @Resource
    private SwitchCfgSnapshot snapshot;

    @Override
    public void run(ApplicationArguments args) {
        try {
            calibrate();
        } catch (Exception e) {
            // 库还没接上时不挡启动；等落库后的当场回写兜住一致性
            log.warn("分区册校准这一趟没跑成，先跳过：{}", e.getMessage());
        }
    }

    /** 扫全部在册行，把与分区册对不上的代号、全称依册子回写；返回校准了几行。 */
    public int calibrate() {
        List<TCtldZoneBook> zones = zoneBookMapper.selectList(
                new QueryWrapper<TCtldZoneBook>().eq("del_flag", 0));
        Map<Integer, TCtldZoneBook> book = new HashMap<>();
        for (TCtldZoneBook zone : zones) {
            if (zone.getId() != null) {
                book.put(zone.getId().intValue(), zone);
            }
        }

        List<TCtldSwitchCfg> rows = switchCfgMapper.selectList(
                new QueryWrapper<TCtldSwitchCfg>().eq("del_flag", 0));
        int fixed = 0;
        for (TCtldSwitchCfg row : rows) {
            TCtldZoneBook zone = row.getZoneId() == null ? null : book.get(row.getZoneId());
            if (zone == null) {
                // 已撤出/查无：旧行照留，绝不顺手抹掉
                continue;
            }
            boolean drift = !eq(row.getZoneNo(), zone.getZoneNo())
                    || !eq(row.getZoneName(), zone.getZoneName());
            if (drift) {
                switchCfgMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                        .eq("id", row.getId())
                        .set("zone_no", zone.getZoneNo())
                        .set("zone_name", zone.getZoneName()));
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("分区全称/代号依分区册校准 {} 行", fixed);
            snapshot.reload();
        }
        return fixed;
    }

    private boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
