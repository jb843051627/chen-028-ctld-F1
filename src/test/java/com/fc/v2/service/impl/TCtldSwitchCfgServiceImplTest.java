package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.conf.CtldProperties;
import com.fc.v2.common.exception.SwitchCfgException;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.service.ctld.SwitchCfgResolver;
import com.fc.v2.service.ctld.SwitchCfgRules;
import com.fc.v2.service.ctld.SwitchCfgScope;
import com.fc.v2.service.ctld.SwitchCfgSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第一/四/五节落库规矩的单测：校验、在途唯一、挂靠校准、三级册、重轧、双落地、旧行兼容。
 * 没有 Shiro 环境时落笔人落 “Task”，不影响规矩。
 */
@ExtendWith(MockitoExtension.class)
class TCtldSwitchCfgServiceImplTest {

    @Mock
    private TCtldSwitchCfgMapper switchMapper;
    @Mock
    private TCtldZoneBookMapper zoneBookMapper;
    @Mock
    private SwitchCfgSnapshot snapshot;
    @Mock
    private SwitchCfgResolver resolver;
    @Mock
    private SwitchCfgScope scope;
    @Mock
    private CtldProperties ctldProperties;

    @InjectMocks
    private TCtldSwitchCfgServiceImpl service;

    private TCtldZoneBook zone;
    private final Map<Long, TCtldSwitchCfg> backing = new HashMap<>();
    private long seq = 100;

    @BeforeEach
    void setUp() {
        // ServiceImpl#baseMapper 因泛型擦除收不到 @InjectMocks，手动塞进基类字段
        ReflectionTestUtils.setField(service, "baseMapper", switchMapper);

        zone = new TCtldZoneBook();
        zone.setId(0L);
        zone.setZoneNo("QC00");
        zone.setZoneName("北浚镇东片");
        zone.setZoneKind("甲层");
        zone.setStatus(0);
        zone.setDelFlag(0);
        org.mockito.Mockito.lenient().when(zoneBookMapper.selectOne(any())).thenReturn(zone);

        org.mockito.Mockito.lenient().when(switchMapper.insert(any(TCtldSwitchCfg.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                TCtldSwitchCfg r = inv.getArgument(0);
                if (r.getId() == null) {
                    r.setId(++seq);
                }
                backing.put(r.getId(), r);
                return 1;
            }
        });

        // update 按 UpdateWrapper 的 sql set 子句回写内存行（值在参数映射里），好让再取取到新状态
        org.mockito.Mockito.lenient().when(switchMapper.update(any(), any(Wrapper.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TCtldSwitchCfg> uw =
                        inv.getArgument(1);
                String sqlSet = uw.getSqlSet();
                Map<String, Object> params = uw.getParamNameValuePairs();
                Matcher assign = Pattern.compile("(\\w+)=#\\{ew\\.paramNameValuePairs\\.(\\w+)}")
                        .matcher(sqlSet);
                while (assign.find()) {
                    String column = assign.group(1);
                    Object value = params.get(assign.group(2));
                    for (TCtldSwitchCfg row : backing.values()) {
                        applyOne(row, column, value);
                    }
                }
                return 1;
            }
        });
    }

    private void applyOne(TCtldSwitchCfg row, String column, Object value) {
        Integer intVal = value instanceof Number ? ((Number) value).intValue() : null;
        String strVal = value == null ? null : value.toString();
        switch (column) {
            case "status":
                row.setStatus(intVal);
                break;
            case "hit_num":
                row.setHitNum(intVal);
                break;
            case "lack_num":
                row.setLackNum(intVal);
                break;
            case "sw_kind":
                row.setSwKind(intVal);
                break;
            case "gray_num":
                row.setGrayNum(intVal);
                break;
            case "crt_num":
                row.setCrtNum(intVal);
                break;
            case "del_flag":
                row.setDelFlag(intVal);
                break;
            case "content":
                row.setContent(strVal);
                break;
            case "sw_name":
                row.setSwName(strVal);
                break;
            case "zone_no":
                row.setZoneNo(strVal);
                break;
            case "zone_name":
                row.setZoneName(strVal);
                break;
            default:
                break;
        }
    }

    private TCtldSwitchCfg draft() {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setZoneId(0);
        r.setSwName("同季配对回话");
        r.setSwKind(2);
        r.setGrayNum(3);
        r.setCrtNum(8);
        r.setHitNum(5);
        return r;
    }

    private void seed(Long id, Integer status) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setId(id);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setZoneId(0);
        r.setZoneNo("QC00");
        r.setSwName("同季配对回话");
        r.setSwKind(1);
        r.setGrayNum(null);
        r.setCrtNum(8);
        r.setHitNum(2);
        r.setLackNum(6);
        backing.put(id, r);
        when(switchMapper.selectById(id)).thenAnswer(inv -> backing.get(id));
    }

    // ---------------- 第五节：挂靠 ----------------

    @Test
    void createRejectsUnknownZoneByField() {
        when(zoneBookMapper.selectOne(any())).thenReturn(null);
        SwitchCfgException e = assertThrows(SwitchCfgException.class, () -> service.create(draft()));
        assertTrue(e.getMessage().contains("分区代号") && e.getMessage().contains("对不上"));
        assertEquals(0, backing.size());
    }

    @Test
    void createRejectsWithdrawnZoneButOldRowsRemain() {
        zone.setStatus(1);
        SwitchCfgException e = assertThrows(SwitchCfgException.class, () -> service.create(draft()));
        assertTrue(e.getMessage().contains("并走撤出"));
    }

    @Test
    void createCalibratesZoneNoAndNameFromBook() {
        TCtldSwitchCfg r = draft();
        r.setZoneNo("OLD-CODE");
        r.setZoneName("屏上手敲的旧名");
        TCtldSwitchCfg saved = service.create(r);
        assertEquals("QC00", saved.getZoneNo());
        assertEquals("北浚镇东片", saved.getZoneName());
    }

    // ---------------- 第一节 + 第四节：建行 ----------------

    @Test
    void createValidatesRecountsAndStamps() {
        TCtldSwitchCfg saved = service.create(draft());
        assertEquals(3, saved.getLackNum(), "8 配 5 得 3，落库前重轧");
        assertEquals(SwitchCfgRules.STATUS_PENDING, saved.getStatus(), "新行一律待上");
        assertNotNull(saved.getSwNo());
        assertEquals("Task", saved.getCreateBy());
        assertEquals("Task", saved.getKindBy());
        verify(snapshot).reload();
    }

    @Test
    void createRejectsKindGrayMismatch() {
        TCtldSwitchCfg r = draft();
        r.setGrayNum(null); // 三态 2 桶数空着
        assertThrows(SwitchCfgException.class, () -> service.create(r));
        r.setSwKind(0);
        r.setGrayNum(2); // 三态 0 却填桶数
        assertThrows(SwitchCfgException.class, () -> service.create(r));
        assertEquals(0, backing.size());
    }

    @Test
    void createRejectsCrtHitViolations() {
        TCtldSwitchCfg r = draft();
        r.setCrtNum(null);
        assertThrows(SwitchCfgException.class, () -> service.create(r));
        r.setCrtNum(4);
        r.setHitNum(5); // 已切反超应覆盖
        assertThrows(SwitchCfgException.class, () -> service.create(r));
    }

    @Test
    void secondInFlightRowForSameZoneAndNameRejected() {
        when(switchMapper.selectCount(any())).thenReturn(1);
        SwitchCfgException e = assertThrows(SwitchCfgException.class, () -> service.create(draft()));
        assertTrue(e.getMessage().contains("一行在走"));
        assertEquals(0, backing.size());
    }

    // ---------------- 第四节：三级册 ----------------

    @Test
    void advanceWalksThreeStepsThenFreezes() {
        seed(1L, SwitchCfgRules.STATUS_PENDING);
        assertEquals(SwitchCfgRules.STATUS_UP, service.advance(1L).getStatus());
        assertEquals(SwitchCfgRules.STATUS_LOCKED, service.advance(1L).getStatus());
        assertThrows(SwitchCfgException.class, () -> service.advance(1L));
        assertThrows(SwitchCfgException.class, () -> service.revert(1L));
    }

    @Test
    void revertOnlyFromUpKeepsSingleRow() {
        seed(2L, SwitchCfgRules.STATUS_PENDING);
        assertThrows(SwitchCfgException.class, () -> service.revert(2L));

        seed(3L, SwitchCfgRules.STATUS_UP);
        int before = backing.size();
        TCtldSwitchCfg back = service.revert(3L);
        assertEquals(SwitchCfgRules.STATUS_PENDING, back.getStatus());
        assertEquals(before, backing.size(), "退回是同一行改档，不另起并排的新行");
        assertTrue(back.getContent().contains("退回待上"), "先前那一趟的痕迹照旧留在核验串里");
    }

    @Test
    void lockedRowCannotBeChangedOrWithdrawn() {
        seed(4L, SwitchCfgRules.STATUS_LOCKED);
        TCtldSwitchCfg edit = new TCtldSwitchCfg();
        edit.setId(4L);
        edit.setSwKind(0);
        assertThrows(SwitchCfgException.class, () -> service.change(edit));
        assertThrows(SwitchCfgException.class, () -> service.withdraw("4"));
        assertEquals(SwitchCfgRules.STATUS_LOCKED, backing.get(4L).getStatus());
    }

    @Test
    void legacyRowWithNullStatusIsTakenAsPendingNotLocked() {
        seed(5L, null);
        backing.get(5L).setSwKind(null);
        backing.get(5L).setGrayNum(null);
        // 碰一下不能报错，且能从待上正常送出——不能被当成已压行
        TCtldSwitchCfg up = service.advance(5L);
        assertEquals(SwitchCfgRules.STATUS_UP, up.getStatus());
    }

    @Test
    void changeShrinkingCaliberAndRecount() {
        seed(6L, SwitchCfgRules.STATUS_PENDING);
        TCtldSwitchCfg edit = new TCtldSwitchCfg();
        edit.setId(6L);
        edit.setSwKind(0); // 1 改回 0
        edit.setGrayNum(null);
        edit.setCrtNum(6);
        edit.setHitNum(6); // 6 配 6 得 0
        TCtldSwitchCfg out = service.change(edit);
        assertEquals(0, out.getSwKind());
        assertEquals(0, out.getLackNum());
        verify(snapshot).reload();
    }

    @Test
    void correctHitRecountsSecondRound() {
        seed(7L, SwitchCfgRules.STATUS_PENDING);
        TCtldSwitchCfg out = service.correctHit(7L, 2);
        assertEquals(6, out.getLackNum());
        assertThrows(SwitchCfgException.class, () -> service.correctHit(7L, 99), "已切反超不许落库");
    }

    @Test
    void legacyRowWithNullFieldsCanBeEditedWithoutTouchingZone() {
        TCtldSwitchCfg legacy = new TCtldSwitchCfg();
        legacy.setId(9L);
        legacy.setStatus(null);
        legacy.setDelFlag(0);
        legacy.setZoneId(7);
        legacy.setZoneNo("QC07");
        legacy.setZoneName("早已在册的片");
        legacy.setSwName("逐条人工签认");
        legacy.setSwKind(null);
        legacy.setGrayNum(null);
        legacy.setCrtNum(null);
        legacy.setHitNum(null);
        legacy.setLackNum(null);
        backing.put(9L, legacy);
        when(switchMapper.selectById(9L)).thenAnswer(inv -> backing.get(9L));

        // 挂靠的分区后来撤出：不改挂靠就不该被挡；三态、处数全空也不该碰一下报错
        org.mockito.Mockito.lenient().when(zoneBookMapper.selectOne(any())).thenReturn(null);
        TCtldSwitchCfg edit = new TCtldSwitchCfg();
        edit.setId(9L);
        edit.setSwName("两式并行");
        TCtldSwitchCfg out = service.change(edit);
        assertEquals("两式并行", out.getSwName());
        assertEquals("QC07", out.getZoneNo(), "没改挂靠，沿用原代号，不查分区册");
    }

    @Test
    void legacyRowMovingToWithdrawnZoneRejected() {
        TCtldSwitchCfg legacy = new TCtldSwitchCfg();
        legacy.setId(10L);
        legacy.setStatus(null);
        legacy.setDelFlag(0);
        legacy.setZoneId(7);
        legacy.setSwName("逐条人工签认");
        backing.put(10L, legacy);
        when(switchMapper.selectById(10L)).thenAnswer(inv -> backing.get(10L));

        TCtldZoneBook withdrawn = new TCtldZoneBook();
        withdrawn.setId(1L);
        withdrawn.setZoneNo("QC01");
        withdrawn.setStatus(1);
        when(zoneBookMapper.selectOne(any())).thenReturn(withdrawn);

        TCtldSwitchCfg edit = new TCtldSwitchCfg();
        edit.setId(10L);
        edit.setZoneNo("QC01");
        assertThrows(SwitchCfgException.class, () -> service.change(edit));
    }

    @Test
    void withdrawSoftDeletesWalkingRowThenReloads() {
        seed(8L, SwitchCfgRules.STATUS_PENDING);
        int rows = service.withdraw("8");
        assertEquals(1, rows);
        assertEquals(1, backing.get(8L).getDelFlag());
        verify(snapshot).reload();
    }
}
