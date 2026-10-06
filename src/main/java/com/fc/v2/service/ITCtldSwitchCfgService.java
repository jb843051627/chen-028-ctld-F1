package com.fc.v2.service;

import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.custom.SwitchCfgQuery;
import com.fc.v2.service.ctld.SwitchDecision;

import java.util.List;

/**
 * 产地准出核验口径切换开关表 Service。
 * 五节规矩见 doc/schema/ctld.sql 与 SwitchCfgRules。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITCtldSwitchCfgService {

    /** 按主键查询（压行/撤下的行照查得到） */
    TCtldSwitchCfg selectTCtldSwitchCfgById(Long id);

    /** 配置台查行：三个通道（分区代号／切换名目／开关三态），分层范围自动收口 */
    List<TCtldSwitchCfg> selectList(SwitchCfgQuery query);

    /** 建行（校验、挂靠校准、轧差、留痕、内存册双落地都在这里） */
    TCtldSwitchCfg create(TCtldSwitchCfg record);

    /** 改这一行（压行后改不动；要改只能另起新行） */
    TCtldSwitchCfg change(TCtldSwitchCfg record);

    /** 已切处数被更正——重轧“还差几处”的第二趟 */
    TCtldSwitchCfg correctHit(Long id, Integer hitNum);

    /** 送一级：待上 → 已上 → 压行（步数由系统数，屏上录入的不作数） */
    TCtldSwitchCfg advance(Long id);

    /** 已上退回待上重来（先前那一趟的痕迹照旧留着，不并排摆） */
    TCtldSwitchCfg revert(Long id);

    /** 撤下：只容待上／已上两行，压行撤不下来 */
    int withdraw(String ids);

    /** 旧通道：按切换项代号取那一行，模样与返回定型，不碰签名 */
    TCtldSwitchCfg getBySwNo(String swNo);

    /** 旧通道问法：按代号 + 地块码，回这一处走新走旧 */
    SwitchDecision decideBySwNo(String swNo, String plotNo);

    /** 新通道（等价方法）：按分区 + 切换名目 + 地块码，顺继承链整表取优回话 */
    SwitchDecision resolve(Integer zoneId, String zoneNo, String swName, String plotNo);
}
