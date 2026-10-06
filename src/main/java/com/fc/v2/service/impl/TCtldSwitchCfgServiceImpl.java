package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.SwitchCfgException;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.model.custom.SwitchCfgQuery;
import com.fc.v2.service.ITCtldSwitchCfgService;
import com.fc.v2.service.ctld.SwitchCfgQueryBuilder;
import com.fc.v2.service.ctld.SwitchCfgResolver;
import com.fc.v2.service.ctld.SwitchCfgRules;
import com.fc.v2.service.ctld.SwitchCfgScope;
import com.fc.v2.service.ctld.SwitchCfgSnapshot;
import com.fc.v2.service.ctld.SwitchDecision;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 核验口径切换项 Service。五节规矩集中在 {@link SwitchCfgRules} 与本类：
 * 校验/挂靠/轧差/三级册/在途唯一/双落地。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TCtldSwitchCfgServiceImpl
        extends ServiceImpl<TCtldSwitchCfgMapper, TCtldSwitchCfg>
        implements ITCtldSwitchCfgService {

    @Resource
    private TCtldZoneBookMapper zoneBookMapper;
    @Resource
    private SwitchCfgSnapshot snapshot;
    @Resource
    private SwitchCfgResolver resolver;
    @Resource
    private SwitchCfgScope scope;

    @Override
    public TCtldSwitchCfg selectTCtldSwitchCfgById(Long id) {
        return this.baseMapper.selectById(id);
    }

    @Override
    public List<TCtldSwitchCfg> selectList(SwitchCfgQuery query) {
        QueryWrapper<TCtldSwitchCfg> wrapper = SwitchCfgQueryBuilder.build(query);

        // 分层范围：值守岗只看本层、县局岗限划归分区；一层都不靠给空视图，不拿邻层凑数
        if (!scope.apply(wrapper)) {
            return new ArrayList<>();
        }
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg create(TCtldSwitchCfg record) {
        if (record == null) {
            throw new SwitchCfgException("一行内容都没有：建行建不成");
        }

        // 第五节：挂靠对不对，分区册说了算；并以分区册校准代号、全称两栏
        TCtldZoneBook zone = bindZone(record);

        if (StringUtils.isEmpty(record.getSwName())) {
            throw new SwitchCfgException("切换名目一栏空着：同一分区同名目才能并立比对，这一行进不了库");
        }

        // 第一节：三态／开桶成对校验
        SwitchCfgRules.validateKindGray(record);
        // 第四节：应覆盖／已切轧差（头一趟：写入）
        record.setLackNum(SwitchCfgRules.recountLack(record.getCrtNum(), record.getHitNum()));

        // 第四节：同分区同名目同时只容一行在走（旧行 status 空着也算在走，不当压行）
        Integer occupied = this.baseMapper.selectCount(new QueryWrapper<TCtldSwitchCfg>()
                .eq("zone_id", zone.getId())
                .eq("sw_name", record.getSwName())
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().lt("status", SwitchCfgRules.STATUS_LOCKED)));
        if (occupied != null && occupied > 0) {
            throw new SwitchCfgException("同一片分区、同一个切换名目已有一行在走：头一行没压行也没停住之前，第二行立不起来");
        }

        // 代号由系统排上，不留手填的格子；屏上录入的档不作数，新行一律落在待上
        record.setId(null);
        record.setSwNo("SW" + System.currentTimeMillis());
        record.setStatus(SwitchCfgRules.STATUS_PENDING);
        if (StringUtils.isEmpty(record.getLevelKind())) {
            record.setLevelKind(SwitchCfgRules.LEVEL_COUNTY);
        }
        if (record.getVerNo() == null) {
            record.setVerNo(1);
        }
        record.setDelFlag(0);
        record.setCreateTime(new Date());
        String actor = currentLoginName();
        record.setCreateBy(actor);
        // 建行与三态两笔都沉在这一行上，日后复核点得出落笔的人
        record.setKindBy(actor);

        this.baseMapper.insert(record);
        reloadAfterCommit();
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg change(TCtldSwitchCfg record) {
        if (record == null || record.getId() == null) {
            throw new SwitchCfgException("没点到要改的那一行：改不动");
        }
        TCtldSwitchCfg db = this.baseMapper.selectById(record.getId());
        if (db == null || (db.getDelFlag() != null && db.getDelFlag() == 1)) {
            throw new SwitchCfgException("这一行已经不在册：改不动，要改只能够另起新的一条");
        }
        // 压行是尽头：加不进去、改不动、也撤不下来
        if (db.getStatus() != null && db.getStatus() == SwitchCfgRules.STATUS_LOCKED) {
            throw new SwitchCfgException("这一行已压行到了尽头：改不动也撤不下，要改只能够另起新的一条");
        }

        // 落挂靠：带了新的代号/册子 id 才算改挂，要过分区册（在册、未撤出）；没带就沿用原挂靠——
        // 旧行挂的分区后来撤出的，改别处不能碰一下就报错，撤出挡的是新行，不挡旧行
        boolean movingZone = record.getZoneId() != null || StringUtils.isNotEmpty(record.getZoneNo());
        Integer finalZoneId;
        String finalZoneNo;
        String finalZoneName;
        if (movingZone) {
            TCtldZoneBook zone = bindZone(record);
            finalZoneId = zone.getId().intValue();
            finalZoneNo = zone.getZoneNo();
            finalZoneName = zone.getZoneName();
        } else {
            finalZoneId = db.getZoneId();
            finalZoneNo = db.getZoneNo();
            finalZoneName = db.getZoneName();
        }
        String finalName = StringUtils.isNotEmpty(record.getSwName()) ? record.getSwName() : db.getSwName();

        // 同分区同名目在途判重：放过“这次动的正是那一行”，改挂到别的分区时按新分区重判
        Integer occupied = this.baseMapper.selectCount(new QueryWrapper<TCtldSwitchCfg>()
                .eq("zone_id", finalZoneId)
                .eq("sw_name", finalName)
                .eq("del_flag", 0)
                .ne("id", db.getId())
                .and(w -> w.isNull("status").or().lt("status", SwitchCfgRules.STATUS_LOCKED)));
        if (occupied != null && occupied > 0) {
            throw new SwitchCfgException("同一片分区、同一个切换名目已有另一行在走：不许一分区两行叠着");
        }

        // 这次带了三态就按这次的三态＋桶数成对认（桶数空着即清空）；没带三态沿用旧的一对。
        // 旧行三态本来空着的（纸质簿搬进来的）照旧留空，不硬按缺栏顶回——读法上本就等效全旧。
        Integer kind;
        Integer gray;
        if (record.getSwKind() != null) {
            kind = record.getSwKind();
            gray = record.getGrayNum();
            TCtldSwitchCfg kindProbe = new TCtldSwitchCfg();
            kindProbe.setSwKind(kind);
            kindProbe.setGrayNum(gray);
            SwitchCfgRules.validateKindGray(kindProbe);
        } else {
            kind = db.getSwKind();
            gray = db.getGrayNum();
        }

        Integer crt = record.getCrtNum() != null ? record.getCrtNum() : db.getCrtNum();
        Integer hit = record.getHitNum() != null ? record.getHitNum() : db.getHitNum();
        // 每回落库之前重轧一遍（第三趟：改挂到别的分区底下也在这）；旧行处数两栏本来全空的，
        // 不硬轧，等真正填了应覆盖处数再轧——碰旧行不许报错
        Integer lack;
        if (crt != null || db.getCrtNum() != null) {
            lack = SwitchCfgRules.recountLack(crt, hit);
        } else {
            lack = db.getLackNum();
        }

        // 改了之后新起的核验按新行回话；已出结论的不追改——旧纸照出单那一刻的口径存着，
        // 这里只改行本身，不动任何历史单据。
        UpdateWrapper<TCtldSwitchCfg> uw = new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", db.getId()).eq("del_flag", 0);
        uw.set("sw_name", finalName);
        uw.set("sw_kind", kind);
        uw.set("gray_num", kind != null && kind == SwitchCfgRules.KIND_GRAY ? gray : null);
        uw.set("crt_num", crt);
        uw.set("hit_num", hit);
        uw.set("lack_num", lack);
        uw.set("zone_id", finalZoneId);
        uw.set("zone_no", finalZoneNo);
        uw.set("zone_name", finalZoneName);
        if (record.getVerNo() != null) {
            uw.set("ver_no", record.getVerNo());
        } else {
            // 改动次序随每一笔改动往后排：数值大的先说话
            uw.set("ver_no", (db.getVerNo() == null ? 1 : db.getVerNo()) + 1);
        }
        if (StringUtils.isNotEmpty(record.getRemark())) {
            uw.set("remark", record.getRemark());
        }
        // 屏上录入的档、经办人自记的档都不作数：步数只由推进／退回接口挪
        uw.set("update_time", new Date());

        boolean caliberChanged = !equalsNullable(kind, db.getSwKind()) || !equalsNullable(gray, db.getGrayNum());
        if (caliberChanged) {
            uw.set("kind_by", currentLoginName());
        }
        uw.set("update_by", currentLoginName());

        int rows = this.baseMapper.update(null, uw);
        if (rows <= 0) {
            throw new SwitchCfgException("这一行没落进库：改动作废");
        }
        reloadAfterCommit();
        return this.baseMapper.selectById(db.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg correctHit(Long id, Integer hitNum) {
        if (id == null) {
            throw new SwitchCfgException("没点到要更正的那一行");
        }
        TCtldSwitchCfg db = this.baseMapper.selectById(id);
        if (db == null || (db.getDelFlag() != null && db.getDelFlag() == 1)) {
            throw new SwitchCfgException("这一行已经不在册：已切处数更正不了");
        }
        if (db.getStatus() != null && db.getStatus() == SwitchCfgRules.STATUS_LOCKED) {
            throw new SwitchCfgException("这一行已压行：处数也改不动，要改只能够另起新的一条");
        }
        int hit = hitNum == null ? 0 : hitNum;
        // 第二趟：已切处数被更正，重轧结果盖掉上一轮落库的那一个
        int lack = SwitchCfgRules.recountLack(db.getCrtNum(), hit);
        int rows = this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0)
                .set("hit_num", hit)
                .set("lack_num", lack)
                .set("update_by", currentLoginName())
                .set("update_time", new Date()));
        if (rows <= 0) {
            throw new SwitchCfgException("已切处数没落进库：更正作废");
        }
        reloadAfterCommit();
        return this.baseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg advance(Long id) {
        TCtldSwitchCfg db = mustGetWalking(id);
        // 旧行步数空着，按待上接，绝不当作已压行
        int step = db.getStatus() == null ? SwitchCfgRules.STATUS_PENDING : db.getStatus();
        if (step >= SwitchCfgRules.STATUS_LOCKED) {
            throw new SwitchCfgException("这一行已压行到尽头：再迈不出去");
        }
        int next = step + 1;
        String trace = trace(db.getContent(), "→" + statusName(next));
        int rows = this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", next)
                .set("content", trace)
                .set("update_by", currentLoginName())
                .set("update_time", new Date()));
        if (rows <= 0) {
            throw new SwitchCfgException("这一级没送上去：步子作废");
        }
        reloadAfterCommit();
        return this.baseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TCtldSwitchCfg revert(Long id) {
        TCtldSwitchCfg db = mustGetWalking(id);
        int step = db.getStatus() == null ? SwitchCfgRules.STATUS_PENDING : db.getStatus();
        // 这一级没过，之后的路都不认：待上无处可退，压行根本到不了退这一步
        if (step == SwitchCfgRules.STATUS_PENDING) {
            throw new SwitchCfgException("这一行还在待上：没有可退回的上一步");
        }
        if (step == SwitchCfgRules.STATUS_LOCKED) {
            throw new SwitchCfgException("这一行已压行到尽头：撤不回来");
        }
        // 退了重来：先前那一趟的痕迹照旧留着（追加退回记号），不跟后补的那笔并排摆——不另建行
        String trace = trace(db.getContent(), "→退回待上");
        int rows = this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0)
                .set("status", SwitchCfgRules.STATUS_PENDING)
                .set("content", trace)
                .set("update_by", currentLoginName())
                .set("update_time", new Date()));
        if (rows <= 0) {
            throw new SwitchCfgException("这一步没退回来：作废");
        }
        reloadAfterCommit();
        return this.baseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdraw(String ids) {
        if (StringUtils.isEmpty(ids)) {
            return 0;
        }
        List<Long> idList = new ArrayList<>();
        for (String part : ids.split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() > 0) {
                idList.add(Long.valueOf(trimmed));
            }
        }
        int affected = 0;
        for (Long id : idList) {
            TCtldSwitchCfg db = this.baseMapper.selectById(id);
            if (db == null || (db.getDelFlag() != null && db.getDelFlag() == 1)) {
                continue;
            }
            if (db.getStatus() != null && db.getStatus() == SwitchCfgRules.STATUS_LOCKED) {
                throw new SwitchCfgException("这一行已压行到尽头：撤不下来，要改只能够另起新的一条");
            }
            affected += this.baseMapper.update(null, new UpdateWrapper<TCtldSwitchCfg>()
                    .eq("id", id).eq("del_flag", 0)
                    .set("del_flag", 1)
                    .set("update_by", currentLoginName())
                    .set("update_time", new Date()));
        }
        if (affected > 0) {
            reloadAfterCommit();
        }
        return affected;
    }

    @Override
    public TCtldSwitchCfg getBySwNo(String swNo) {
        return resolver.getBySwNo(swNo);
    }

    @Override
    public SwitchDecision decideBySwNo(String swNo, String plotNo) {
        return resolver.decideBySwNo(swNo, plotNo);
    }

    @Override
    public SwitchDecision resolve(Integer zoneId, String zoneNo, String swName, String plotNo) {
        return resolver.resolve(zoneId, zoneNo, swName, plotNo);
    }

    // ---------------------------------------------------------------------

    private TCtldSwitchCfg mustGetWalking(Long id) {
        if (id == null) {
            throw new SwitchCfgException("没点到要走的那一行");
        }
        TCtldSwitchCfg db = this.baseMapper.selectById(id);
        if (db == null || (db.getDelFlag() != null && db.getDelFlag() == 1)) {
            throw new SwitchCfgException("这一行已经不在册：步子挪不动");
        }
        return db;
    }

    /**
     * 第五节：行上的分区代号拿到分区册里寻不着，建行建不成（点名代号这一栏对不上）；
     * 已并走撤出的分区再也收不进新行；挂上之后以分区册为准回写代号、全称两栏。
     */
    private TCtldZoneBook bindZone(TCtldSwitchCfg record) {
        TCtldZoneBook zone = null;
        if (record.getZoneId() != null) {
            zone = zoneBookMapper.selectOne(new QueryWrapper<TCtldZoneBook>()
                    .eq("id", record.getZoneId()).eq("del_flag", 0));
        } else if (StringUtils.isNotEmpty(record.getZoneNo())) {
            zone = zoneBookMapper.selectOne(new QueryWrapper<TCtldZoneBook>()
                    .eq("zone_no", record.getZoneNo().trim()).eq("del_flag", 0));
        }
        if (zone == null) {
            throw new SwitchCfgException("分区代号这一栏对不上：分区册里寻不着这个分区，建行建不成");
        }
        if (zone.getStatus() != null && zone.getStatus() == 1) {
            throw new SwitchCfgException("分区代号这一栏对不上：这一段分区已并走撤出，再也收不进新行");
        }
        record.setZoneId(zone.getId().intValue());
        record.setZoneNo(zone.getZoneNo());
        record.setZoneName(zone.getZoneName());
        return zone;
    }

    private String trace(String oldContent, String action) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                + " " + currentLoginName() + " " + action;
        return StringUtils.isEmpty(oldContent) ? stamp : oldContent + "；" + stamp;
    }

    private String statusName(int status) {
        if (status == SwitchCfgRules.STATUS_PENDING) {
            return "待上";
        }
        if (status == SwitchCfgRules.STATUS_UP) {
            return "已上";
        }
        return "压行";
    }

    private boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private String currentLoginName() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "Task";
        }
    }

    /**
     * 第四节：库里的行与内存中备着的那一份两处必须同时落地。等库这头提交成功再重轧，
     * 避免回滚后内存反成残留；没有事务裹着时立刻重轧。
     */
    private void reloadAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    snapshot.reload();
                }
            });
        } else {
            snapshot.reload();
        }
    }
}
