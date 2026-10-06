package com.fc.v2.service.ctld;

import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第五节：行上冗余的分区代号/全称依分区册校准；撤出与查无的旧行不动、不抹。
 */
@ExtendWith(MockitoExtension.class)
class SwitchCfgBookCalibratorTest {

    @Mock
    private TCtldSwitchCfgMapper switchMapper;
    @Mock
    private TCtldZoneBookMapper zoneBookMapper;
    @Mock
    private SwitchCfgSnapshot snapshot;
    @InjectMocks
    private SwitchCfgBookCalibrator calibrator;

    private TCtldZoneBook zone(long id, String no, String name) {
        TCtldZoneBook z = new TCtldZoneBook();
        z.setId(id);
        z.setZoneNo(no);
        z.setZoneName(name);
        return z;
    }

    private TCtldSwitchCfg row(long id, Integer zoneId, String no, String name) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setId(id);
        r.setZoneId(zoneId);
        r.setZoneNo(no);
        r.setZoneName(name);
        return r;
    }

    @Test
    void driftsAreRepairedAndMemoryReloadedOnce() {
        when(zoneBookMapper.selectList(any())).thenReturn(Arrays.asList(zone(1, "QC00", "在册正名")));
        when(switchMapper.selectList(any())).thenReturn(Arrays.asList(
                row(11, 1, "OLD", "旧名各说各话"),
                row(12, 1, "QC00", "在册正名"),
                row(13, 9, "GONE", "已撤出片"),
                row(14, null, null, null)));
        when(switchMapper.update(any(), any())).thenReturn(1);

        int fixed = calibrator.calibrate();
        assertEquals(1, fixed, "只有代号/全称对不上的那一行校准");
        verify(snapshot).reload();
        // 撤出/查无的旧行不抹不改
        verify(switchMapper, never()).deleteById(any());
    }
}
