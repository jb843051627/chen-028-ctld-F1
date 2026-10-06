package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;

import java.util.List;

/**
 * 产地准出核验口径切换开关表 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITCtldSwitchCfgService {

    /** 按主键查询 */
    TCtldSwitchCfg selectTCtldSwitchCfgById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TCtldSwitchCfg> selectTCtldSwitchCfgList(Wrapper<TCtldSwitchCfg> queryWrapper);

    /**
     * 配置台台账：在条件之上再加「按层切」的可见范围。
     * 本层值守岗只见本层；县局岗三层都见、但县定行只限于划归它的分区。
     */
    List<TCtldSwitchCfg> selectScopedList(Wrapper<TCtldSwitchCfg> queryWrapper, Long userId);

    /** 新增（不合规矩抛 CtldRuleException，消息点名栏位） */
    int insertTCtldSwitchCfg(TCtldSwitchCfg record);

    /** 修改（压行后的行改不动；不合规矩抛 CtldRuleException） */
    int updateTCtldSwitchCfg(TCtldSwitchCfg record);

    /** 撤下（软删，腾名额；压行后的行撤不下来） */
    int deleteTCtldSwitchCfgByIds(String ids);

    /** 往上送一步：待上→已上→压行，步子不许乱迈，档位由系统数；返回落定行数 */
    int advance(Long id);

    /** 退回一步：已上→待上（只此一种退法；压行后不许退；痕迹留在原行不另起） */
    TCtldSwitchCfg revert(Long id);

    /**
     * 按代号取值（原通道，签名与返回冻结，不许改模样）。
     * 入参：分区代号、切换名目、地块码；返回：0 走旧口径、1 走新口径。
     * 不看问的人、不看通道。
     */
    Integer getByCode(String zoneNo, String swName, String plotNo);

    /**
     * 整表取优（新通道，等价方法，另起一条承接，不碰上面那条的签名）：
     * 沿 县定→市定→省定→系统兜底 取说话的那一行，回话与按代号取值必为同一个。
     */
    SwitchAnswer resolve(String zoneNo, String swName, String plotNo);

    /** 按主键删除 */
    int deleteTCtldSwitchCfgById(Long id);

    /** 回话明细（新通道返回） */
    class SwitchAnswer {
        /** 0 旧口径 1 新口径 */
        private final int answer;
        /** 这一答出自哪一层：县定／市定／省定／系统兜底 */
        private final String sourceLevel;
        /** 说话行的代号（兜底行为空） */
        private final String swNo;
        /** 落桶时的桶号（非按桶推进为 null） */
        private final Integer bucket;

        public SwitchAnswer(int answer, String sourceLevel, String swNo, Integer bucket) {
            this.answer = answer;
            this.sourceLevel = sourceLevel;
            this.swNo = swNo;
            this.bucket = bucket;
        }

        public int getAnswer() {
            return answer;
        }

        public String getSourceLevel() {
            return sourceLevel;
        }

        public String getSwNo() {
            return swNo;
        }

        public Integer getBucket() {
            return bucket;
        }
    }
}
