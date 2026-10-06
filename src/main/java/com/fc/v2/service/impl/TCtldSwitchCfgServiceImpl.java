package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.CtldRuleException;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldUserZoneMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldUserZone;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.service.ITCtldSwitchCfgService;
import com.fc.v2.service.ITSysRoleService;
import com.fc.v2.service.ctld.SwitchCfgCurrent;
import com.fc.v2.service.ctld.SwitchCfgRule;
import com.fc.v2.shiro.util.ShiroUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 核验口径切换项 Service业务层处理。五节规矩按序落实：
 * 一、三态/开桶配对校验；二、县→市→省→兜底的取优链；三、地块码尾数落桶；
 * 四、处数轧差、三级步子、同槽单行、库与内存双落地；五、分区册校验与按层可见。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TCtldSwitchCfgServiceImpl
        extends ServiceImpl<TCtldSwitchCfgMapper, TCtldSwitchCfg>
        implements ITCtldSwitchCfgService {

    private static final String ROLE_COUNTY = "产地准出·县局岗";
    private static final String ROLE_CITY = "产地准出·市级值守";
    private static final String ROLE_PROVINCE = "产地准出·省级值守";

    @Resource
    private TCtldZoneBookMapper ctldZoneBookMapper;
    @Resource
    private TCtldUserZoneMapper ctldUserZoneMapper;
    @Resource
    private ITSysRoleService sysRoleService;
    @Resource
    private SwitchCfgCurrent current;

    @Override
    public TCtldSwitchCfg selectTCtldSwitchCfgById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    public List<TCtldSwitchCfg> selectTCtldSwitchCfgList(Wrapper<TCtldSwitchCfg> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<TCtldSwitchCfg> selectScopedList(Wrapper<TCtldSwitchCfg> queryWrapper, Long userId) {
        QueryWrapper<TCtldSwitchCfg> wrapper = queryWrapper == null
                ? new QueryWrapper<TCtldSwitchCfg>()
                : (QueryWrapper<TCtldSwitchCfg>) queryWrapper;
        wrapper.eq("del_flag", 0);

        Set<String> roles = roleNames(userId);
        Set<Long> zoneIds = assignedZoneIds(userId);
        boolean countyWide = roles.contains(ROLE_COUNTY);

        // 可见层级：县局岗三层都看得见；值守岗只认自己那一层；无岗位按县定值守处理
        List<String> upperLevels = new ArrayList<String>();
        boolean seeCounty = true;
        if (countyWide) {
            upperLevels.add(SwitchCfgRule.LV_PROVINCE);
            upperLevels.add(SwitchCfgRule.LV_CITY);
        } else {
            if (roles.contains(ROLE_PROVINCE)) {
                upperLevels.add(SwitchCfgRule.LV_PROVINCE);
                seeCounty = false;
            }
            if (roles.contains(ROLE_CITY)) {
                upperLevels.add(SwitchCfgRule.LV_CITY);
                seeCounty = false;
            }
        }

        final boolean countyVisible = seeCounty;
        wrapper.and(w -> {
            boolean any = false;
            if (!upperLevels.isEmpty()) {
                w.in("level_kind", upperLevels);
                any = true;
            }
            if (countyVisible) {
                // 县定行按划归范围收口；没划归任何分区就一条都不给
                // （视图空着是合法结果，不拿邻层凑）
                if (!zoneIds.isEmpty()) {
                    if (any) {
                        w.or();
                    }
                    w.in("level_kind", java.util.Collections.singletonList(SwitchCfgRule.LV_COUNTY))
                            .in("zone_id", zoneIds);
                    any = true;
                }
            }
            if (!any) {
                w.apply("1 = 0");
            }
        });
        // 并立几行台账默认先比改动次序、再比代号
        wrapper.orderByDesc("ver_no").orderByDesc("sw_no");
        List<TCtldSwitchCfg> rows = this.baseMapper.selectList(wrapper);
        calibrateZoneName(rows);
        return rows;
    }

    /**
     * 第五节：行上冗余的分区全称与分区册各说各话视为缺陷——
     * 列示时依分区册校准，并回写库行（分区册撤出/并走也不抹旧行，只校准名称）。
     */
    private void calibrateZoneName(List<TCtldSwitchCfg> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.Map<Long, TCtldZoneBook> bookCache = new java.util.HashMap<Long, TCtldZoneBook>();
        for (TCtldSwitchCfg row : rows) {
            if (!SwitchCfgRule.LV_COUNTY.equals(row.getLevelKind()) || row.getZoneId() == null) {
                continue;
            }
            TCtldZoneBook book = bookCache.get(row.getZoneId());
            if (!bookCache.containsKey(row.getZoneId())) {
                book = ctldZoneBookMapper.selectOne(new QueryWrapper<TCtldZoneBook>()
                        .eq("id", row.getZoneId()).eq("del_flag", 0));
                bookCache.put(row.getZoneId(), book);
            }
            if (book != null && book.getZoneName() != null
                    && (!book.getZoneName().equals(row.getZoneName())
                        || !book.getZoneNo().equals(row.getZoneNo()))) {
                String oldZoneNo = row.getZoneNo();
                row.setZoneNo(book.getZoneNo());
                row.setZoneName(book.getZoneName());
                this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                        .eq("id", row.getId())
                        .set("zone_no", book.getZoneNo())
                        .set("zone_name", book.getZoneName()));
                if (oldZoneNo != null && !oldZoneNo.equals(book.getZoneNo())) {
                    current.reloadCounty(oldZoneNo, row.getSwName());
                }
                refreshSlot(row.getLevelKind(), row.getZoneNo(), row.getSwName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTCtldSwitchCfg(TCtldSwitchCfg record) {
        if (record == null) {
            throw new CtldRuleException("这一行是空的，建不成");
        }
        String operator = currentOperator();

        // 第一节：三态与开桶个数配对
        SwitchCfgRule.validateKind(record.getSwKind(), record.getGrayNum());
        // 名目必填
        if (record.getSwName() == null || record.getSwName().trim().isEmpty()) {
            throw new CtldRuleException("切换名目一栏不合：空着立不了行");
        }
        record.setSwName(record.getSwName().trim());
        // 层级：默认县定
        if (record.getLevelKind() == null || record.getLevelKind().trim().isEmpty()) {
            record.setLevelKind(SwitchCfgRule.LV_COUNTY);
        }
        if (!SwitchCfgRule.LV_COUNTY.equals(record.getLevelKind())
                && !SwitchCfgRule.LV_CITY.equals(record.getLevelKind())
                && !SwitchCfgRule.LV_PROVINCE.equals(record.getLevelKind())) {
            throw new CtldRuleException("定档层级一栏不合：只认 省定／市定／县定 三档");
        }

        // 第五节：分区代号进分区册核对；县定行必须挂在册、未撤出的分区
        boolean countyLevel = SwitchCfgRule.LV_COUNTY.equals(record.getLevelKind());
        if (countyLevel) {
            TCtldZoneBook book = resolveZone(record.getZoneNo());
            record.setZoneId(book.getId());
            record.setZoneNo(book.getZoneNo());
            // 冗余的全称以分区册为准回写校准，不许两处各说各话
            record.setZoneName(book.getZoneName());
            assertZoneAssigned(book.getId(), operator);
        } else {
            record.setZoneId(null);
            record.setZoneNo(null);
            record.setZoneName(null);
        }

        // 第四节：同一片分区、同一个名目，同时只容一行在走（待上/已上都占位，压行才腾空）
        ensureSlotFree(record, null);

        // 第四节：三个处数，lack 当场轧，客户端填的一律不收
        record.setHitNum(record.getHitNum() == null ? 0 : record.getHitNum());
        record.setLackNum(SwitchCfgRule.reconcileLack(record.getCrtNum(), record.getHitNum()));

        // 系统排号，不留手填格子；步子由系统数，屏上录的档不作数
        Long newId = IdWorker.getId();
        record.setId(newId);
        record.setSwNo("SW" + newId);
        // 改动次序全局递增：压行归档的旧版本也压不过新立的行
        record.setVerNo(nextVerNo(null));
        record.setStatus(SwitchCfgRule.STEP_PENDING);
        record.setDelFlag(0);
        record.setKindBy(operator);
        record.setContent(null);
        record.setCreateBy(operator);
        record.setCreateTime(new Date());
        record.setUpdateTime(new Date());

        int rows = this.baseMapper.insert(record);
        if (rows <= 0) {
            return 0;
        }
        // 两处落笔都要点得出人
        assertOperatorStamped(record.getCreateBy(), record.getKindBy());
        // 第四节：库里落定，内存备着的那一份同趟刷新
        refreshSlot(record.getLevelKind(), record.getZoneNo(), record.getSwName());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTCtldSwitchCfg(TCtldSwitchCfg record) {
        if (record == null || record.getId() == null) {
            throw new CtldRuleException("找不到要改的那一行");
        }
        TCtldSwitchCfg old = this.baseMapper.selectById(record.getId());
        if (old == null || (old.getDelFlag() != null && old.getDelFlag() == 1)) {
            throw new CtldRuleException("这一行已经不在册，改不了");
        }
        // 第四节：压行即尽头，改不动；要改另起新的一条
        if (isLocked(old)) {
            throw new CtldRuleException("这一行已压行到了尽头：加不进去、改不动、也撤不下来，要改只能另起新的一条");
        }
        String operator = currentOperator();

        SwitchCfgRule.validateKind(record.getSwKind(), record.getGrayNum());
        if (record.getSwName() == null || record.getSwName().trim().isEmpty()) {
            throw new CtldRuleException("切换名目一栏不合：空着立不了行");
        }
        if (record.getLevelKind() == null
                || (!SwitchCfgRule.LV_COUNTY.equals(record.getLevelKind())
                && !SwitchCfgRule.LV_CITY.equals(record.getLevelKind())
                && !SwitchCfgRule.LV_PROVINCE.equals(record.getLevelKind()))) {
            throw new CtldRuleException("定档层级一栏不合：只认 省定／市定／县定 三档");
        }

        boolean countyLevel = SwitchCfgRule.LV_COUNTY.equals(record.getLevelKind());
        Long newZoneId = null;
        String newZoneNo = null;
        String newZoneName = null;
        if (countyLevel) {
            TCtldZoneBook book = resolveZone(record.getZoneNo());
            newZoneId = book.getId();
            newZoneNo = book.getZoneNo();
            newZoneName = book.getZoneName();
            // 无论改不改变挂靠，都核当前落笔的人仍拥有该分区
            assertZoneAssigned(book.getId(), operator);
        }

        // 改挂到别的分区／名目／层级：新槽位也得空着（排除本行）
        ensureSlotFree(buildSlotProbe(record.getLevelKind(), newZoneNo, record.getSwName()), record.getId());

        record.setHitNum(record.getHitNum() == null ? 0 : record.getHitNum());
        int lack = SwitchCfgRule.reconcileLack(record.getCrtNum(), record.getHitNum());

        UpdateWrapper<TCtldSwitchCfg> uw = new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", record.getId()).eq("del_flag", 0);
        uw.set("sw_name", record.getSwName().trim());
        uw.set("sw_kind", record.getSwKind());
        uw.set("gray_num", record.getGrayNum());
        uw.set("level_kind", record.getLevelKind());
        uw.set("zone_id", newZoneId);
        uw.set("zone_no", newZoneNo);
        uw.set("zone_name", newZoneName);
        uw.set("crt_num", record.getCrtNum());
        uw.set("hit_num", record.getHitNum());
        // 第四节：每回落库之前重轧一遍，结果盖掉上一轮的 lack
        uw.set("lack_num", lack);
        uw.set("eff_start", record.getEffStart());
        uw.set("eff_end", record.getEffEnd());
        uw.set("remark", record.getRemark());
        // 改动次序往后排一位（全局递增，不被已压行的旧版本反压）
        uw.set("ver_no", nextVerNo(old.getVerNo()));
        // 改三态的是哪一位：只有三态真的变了才挪这一笔，别的改动不动它
        if (old.getSwKind() == null || !old.getSwKind().equals(record.getSwKind())) {
            uw.set("kind_by", operator);
        }
        // 屏上录的档、手填的代号与核验串一律不作数：status/sw_no/content/createBy 不进 SET
        uw.set("update_by", operator);
        uw.set("update_time", new Date());

        int rows = this.baseMapper.update(null, uw);
        if (rows <= 0) {
            return 0;
        }

        // 旧槽位、新槽位（改挂时不是同一个）内存里都要重取
        refreshSlot(old.getLevelKind(), old.getZoneNo(), old.getSwName());
        refreshSlot(record.getLevelKind(), newZoneNo, record.getSwName());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advance(Long id) {
        TCtldSwitchCfg cur = mustGet(id);
        // 步子只能一级一级迈；压行是尽头
        int step = stepOf(cur);
        if (step >= SwitchCfgRule.STEP_LOCKED) {
            throw new CtldRuleException("这一行已经压行，到尽头了");
        }
        int next = step + 1;
        // 压行定形前，三态必须已经补正；纸质簿迁入的空三态旧行不能糊里糊涂压死
        if (next == SwitchCfgRule.STEP_LOCKED) {
            try {
                SwitchCfgRule.validateKind(cur.getSwKind(), cur.getGrayNum());
            } catch (CtldRuleException e) {
                throw new CtldRuleException("这一行还压不了：" + e.getMessage() + "；请先改正再往上送");
            }
        }
        UpdateWrapper<TCtldSwitchCfg> uw = new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0).set("status", next).set("update_time", new Date());
        if (next == SwitchCfgRule.STEP_LOCKED) {
            // 走到压行定形：核验串此刻生成，此后页面覆写不了
            String content = cur.getSwNo() + "-v" + (cur.getVerNo() == null ? 1 : cur.getVerNo())
                    + "-k" + cur.getSwKind() + "-g" + (cur.getGrayNum() == null ? 0 : cur.getGrayNum());
            uw.set("content", content);
        }
        int rows = this.baseMapper.update(null, uw);
        refreshSlot(cur.getLevelKind(), cur.getZoneNo(), cur.getSwName());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg revert(Long id) {
        TCtldSwitchCfg cur = mustGet(id);
        int step = stepOf(cur);
        // 只许 已上→待上 这一种退法；压行后不许退
        if (step >= SwitchCfgRule.STEP_LOCKED) {
            throw new CtldRuleException("已压行的行撤不回来；要改只能另起新的一条");
        }
        if (step < SwitchCfgRule.STEP_SENT) {
            throw new CtldRuleException("这一行还在待上，没有可退的一档");
        }
        // 退回是就地退：先前那趟的痕迹留在原行上，不另起并排的新行
        this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", SwitchCfgRule.STEP_PENDING).set("update_time", new Date()));
        refreshSlot(cur.getLevelKind(), cur.getZoneNo(), cur.getSwName());
        return this.baseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTCtldSwitchCfgByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        List<TCtldSwitchCfg> touched = new ArrayList<TCtldSwitchCfg>();
        for (Long id : idArr) {
            TCtldSwitchCfg cur = mustGet(id);
            if (isLocked(cur)) {
                throw new CtldRuleException("行「" + cur.getSwNo() + "」已压行，撤不下来");
            }
            touched.add(cur);
        }
        // 就地停住：软删，册子照留；占着的名额随之空出
        int n = this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                .in("id", Arrays.asList(idArr)).eq("del_flag", 0)
                .set("del_flag", 1).set("update_time", new Date()));
        for (TCtldSwitchCfg cur : touched) {
            refreshSlot(cur.getLevelKind(), cur.getZoneNo(), cur.getSwName());
        }
        return n;
    }

    @Override
    public int deleteTCtldSwitchCfgById(Long id) {
        return deleteTCtldSwitchCfgByIds(String.valueOf(id));
    }

    // ------------------------------------------------------------------
    // 第三节：两条问话通道。按代号取值是原通道，签名与返回冻结；
    // 整表取优是另起的等价方法，两条通道对同一处地块必须给同一个答复。
    // ------------------------------------------------------------------

    @Override
    public Integer getByCode(String zoneNo, String swName, String plotNo) {
        return resolve(zoneNo, swName, plotNo).getAnswer();
    }

    @Override
    public SwitchAnswer resolve(String zoneNo, String swName, String plotNo) {
        if (swName == null || swName.trim().isEmpty()) {
            throw new CtldRuleException("切换名目一栏不合：空着没法回话");
        }
        TCtldSwitchCfg row = speakingRow(zoneNo, swName);
        Integer bucket = null;
        int answer;
        String level;
        String swNo;
        if (row == null) {
            // 整条链都空着才落兜底：三态 2 配 0 桶，等效全旧
            TCtldSwitchCfg fb = SwitchCfgRule.fallbackRow();
            answer = SwitchCfgRule.answerOf(fb, plotNo);
            level = SwitchCfgRule.LV_FALLBACK;
            swNo = null;
        } else {
            answer = SwitchCfgRule.answerOf(row, plotNo);
            level = row.getLevelKind();
            swNo = row.getSwNo();
            if (row.getSwKind() != null && row.getSwKind() == SwitchCfgRule.KIND_BUCKET
                    && plotNo != null && plotNo.length() > 0) {
                char last = plotNo.charAt(plotNo.length() - 1);
                if (last >= '0' && last <= '9') {
                    bucket = last - '0';
                }
            }
        }
        return new SwitchAnswer(answer, level, swNo, bucket);
    }

    /**
     * 第二节取优链：县定没有→市定→省定→兜底。
     * 并立几行先比改动次序（大的说话），再比代号字面先后（靠后的说话）。
     */
    private TCtldSwitchCfg speakingRow(String zoneNo, String swName) {
        TCtldSwitchCfg row = null;
        if (zoneNo != null && !zoneNo.trim().isEmpty()) {
            row = current.countyRow(zoneNo, swName);
            if (row == null) {
                row = winnerOf(new QueryWrapper<TCtldSwitchCfg>()
                        .eq("del_flag", 0).eq("level_kind", SwitchCfgRule.LV_COUNTY)
                        .eq("zone_no", zoneNo).eq("sw_name", swName));
                if (row != null) {
                    current.put(row);
                }
            }
        }
        if (row == null) {
            row = current.upperRow(SwitchCfgRule.LV_CITY, swName);
            if (row == null) {
                row = winnerOf(new QueryWrapper<TCtldSwitchCfg>()
                        .eq("del_flag", 0).eq("level_kind", SwitchCfgRule.LV_CITY)
                        .eq("sw_name", swName));
                if (row != null) {
                    current.put(row);
                }
            }
        }
        if (row == null) {
            row = current.upperRow(SwitchCfgRule.LV_PROVINCE, swName);
            if (row == null) {
                row = winnerOf(new QueryWrapper<TCtldSwitchCfg>()
                        .eq("del_flag", 0).eq("level_kind", SwitchCfgRule.LV_PROVINCE)
                        .eq("sw_name", swName));
                if (row != null) {
                    current.put(row);
                }
            }
        }
        return row;
    }

    private TCtldSwitchCfg winnerOf(QueryWrapper<TCtldSwitchCfg> wrapper) {
        return SwitchCfgRule.pickWinner(this.baseMapper.selectList(wrapper));
    }

    // ------------------------------------------------------------------
    // 私有规矩
    // ------------------------------------------------------------------

    /** 改动次序取下一档：库内最大值与本行现值取大者加一，保证全局单调向前 */
    private Integer nextVerNo(Integer currentVer) {
        Integer max = this.baseMapper.selectObjs(new QueryWrapper<TCtldSwitchCfg>()
                .select("IFNULL(MAX(ver_no),0) AS mv")).stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> Integer.valueOf(o.toString()))
                .findFirst().orElse(0);
        int base = Math.max(max, currentVer == null ? 0 : currentVer);
        return base + 1;
    }

    private TCtldSwitchCfg mustGet(Long id) {
        if (id == null) {
            throw new CtldRuleException("没指明是哪一行");
        }
        TCtldSwitchCfg cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag() == 1)) {
            throw new CtldRuleException("这一行不在册");
        }
        return cur;
    }

    /** 步数：旧行（纸质簿迁入，status 空着）按待上接住，不许当成已压行，也不许一碰就报错 */
    private int stepOf(TCtldSwitchCfg row) {
        return row.getStatus() == null ? SwitchCfgRule.STEP_PENDING : row.getStatus();
    }

    private boolean isLocked(TCtldSwitchCfg row) {
        return row.getStatus() != null && row.getStatus() == SwitchCfgRule.STEP_LOCKED;
    }

    /** 分区册核对：代号寻不着直接点名是代号这一栏对不上；撤出/并走的段不再收新行 */
    private TCtldZoneBook resolveZone(String zoneNo) {
        if (zoneNo == null || zoneNo.trim().isEmpty()) {
            throw new CtldRuleException("分区代号一栏不合：县定行必须挂靠一个在册分区，这一栏空着");
        }
        TCtldZoneBook book = ctldZoneBookMapper.selectOne(new QueryWrapper<TCtldZoneBook>()
                .eq("zone_no", zoneNo.trim()).eq("del_flag", 0));
        if (book == null) {
            throw new CtldRuleException("分区代号一栏不合：代号「" + zoneNo + "」在分区册里寻不着，建行建不成");
        }
        if (book.getStatus() != null && book.getStatus() == 1) {
            throw new CtldRuleException("分区代号一栏不合：「" + zoneNo + "」这一段已并走撤出，再也收不进新行"
                    + "（并走之前立的旧行照常在册，不受影响）");
        }
        return book;
    }

    private void assertZoneAssigned(Long zoneId, String operator) {
        // 不论哪一层岗位，建/改挂县定行都只许落在划归到自己名下的分区
        Set<Long> ids = assignedZoneIds(ShiroUtils.getUserId());
        if (!ids.contains(zoneId)) {
            throw new CtldRuleException("分区代号一栏不合：该分区没有划归到你名下，范围外的行立不了"
                    + "（视图空着是合法结果，不许拿邻层的行凑数）");
        }
    }

    private TCtldSwitchCfg buildSlotProbe(String levelKind, String zoneNo, String swName) {
        TCtldSwitchCfg probe = new TCtldSwitchCfg();
        probe.setLevelKind(levelKind);
        probe.setZoneNo(zoneNo);
        probe.setSwName(swName);
        return probe;
    }

    /**
     * 同槽位只容一行在走：status 为空（旧行）或 0/1 都算占位，到压行（2）才腾空。
     * 南片北片各报一遍是不同槽；同一分区换人重报仍被这一条挡住。
     */
    private void ensureSlotFree(TCtldSwitchCfg probe, Long excludeId) {
        String slotKey = (SwitchCfgRule.LV_COUNTY.equals(probe.getLevelKind())
                ? "C:" + probe.getZoneNo() : probe.getLevelKind()) + ":" + probe.getSwName().trim();
        synchronized (slotLock(slotKey)) {
            QueryWrapper<TCtldSwitchCfg> q = new QueryWrapper<TCtldSwitchCfg>()
                    .eq("del_flag", 0)
                    .and(w -> w.isNull("status").or().in("status",
                            Arrays.asList(SwitchCfgRule.STEP_PENDING, SwitchCfgRule.STEP_SENT)))
                    .eq("sw_name", probe.getSwName().trim());
            if (SwitchCfgRule.LV_COUNTY.equals(probe.getLevelKind())) {
                q.eq("level_kind", SwitchCfgRule.LV_COUNTY).eq("zone_no", probe.getZoneNo());
            } else {
                q.eq("level_kind", probe.getLevelKind()).isNull("zone_no");
            }
            if (excludeId != null) {
                q.ne("id", excludeId);
            }
            Integer cnt = this.baseMapper.selectCount(q);
            if (cnt != null && cnt > 0) {
                throw new CtldRuleException("同一分区、同一个切换名目已有一行在走（还没压行、也未停住），"
                        + "第二行立不起来；等它压行或就地停住腾出名额再报");
            }
        }
    }

    /** 按槽位取锁对象：同一槽位串行，不同槽位不互挡（南片北片可同日报） */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> slotLocks =
            new java.util.concurrent.ConcurrentHashMap<String, Object>();

    private Object slotLock(String key) {
        Object lock = slotLocks.get(key);
        if (lock == null) {
            Object fresh = new Object();
            Object prev = slotLocks.putIfAbsent(key, fresh);
            lock = prev == null ? fresh : prev;
        }
        return lock;
    }

    private void refreshSlot(String levelKind, String zoneNo, String swName) {
        if (levelKind == null) {
            return;
        }
        if (SwitchCfgRule.LV_COUNTY.equals(levelKind) && zoneNo != null) {
            current.reloadCounty(zoneNo, swName);
        } else {
            current.reloadUpper(levelKind, swName);
        }
    }

    private Set<String> roleNames(Long userId) {
        Set<String> names = new HashSet<String>();
        if (userId == null) {
            return names;
        }
        try {
            sysRoleService.queryUserRole(userId).forEach(r -> {
                if (r.getName() != null) {
                    names.add(r.getName());
                }
            });
        } catch (Exception ignore) {
            // 取不到岗位按无岗位处理（视图空着是合法结果，不拿邻层凑数）
        }
        return names;
    }

    private Set<Long> assignedZoneIds(Long userId) {
        Set<Long> ids = new HashSet<Long>();
        if (userId == null) {
            return ids;
        }
        List<TCtldUserZone> list = ctldUserZoneMapper.selectList(new QueryWrapper<TCtldUserZone>()
                .eq("user_id", userId).eq("del_flag", 0));
        for (TCtldUserZone uz : list) {
            ids.add(uz.getZoneId());
        }
        return ids;
    }

    private String currentOperator() {
        String name;
        try {
            name = ShiroUtils.getLoginName();
        } catch (Exception e) {
            name = null;
        }
        if (name == null || name.trim().isEmpty()) {
            throw new CtldRuleException("指认不出当前落笔的人，这件事不算办完");
        }
        return name;
    }

    private void assertOperatorStamped(String createBy, String kindBy) {
        if (createBy == null || createBy.trim().isEmpty()
                || kindBy == null || kindBy.trim().isEmpty()) {
            throw new CtldRuleException("建行的人、改三态的人有一处点不出来，这件事不算办完");
        }
    }
}
