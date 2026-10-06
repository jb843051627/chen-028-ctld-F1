package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.conf.CtldProperties;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.SwitchCfgException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.custom.SwitchCfgQuery;
import com.fc.v2.model.custom.Tablepar;
import com.fc.v2.service.ITCtldSwitchCfgService;
import com.fc.v2.service.ctld.SwitchDecision;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 产地准出核验口径切换开关表 Controller（配置台 + 两条回话通道）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "产地准出核验口径切换开关表")
@Controller
@RequestMapping("/CtldSwitchCfgController")
public class CtldSwitchCfgController extends BaseController {

    private final String prefix = "admin/ctldSwitchCfg";

    @Autowired
    private ITCtldSwitchCfgService ctldSwitchCfgService;

    @Autowired
    private CtldProperties ctldProperties;

    /** 规矩被违反时，消息点名栏位原样回配置台，不套“运行时异常”壳子 */
    @ExceptionHandler(SwitchCfgException.class)
    @ResponseBody
    public AjaxResult handleRule(SwitchCfgException e) {
        return AjaxResult.error(e.getMessage());
    }

    @ApiOperation(value = "配置台跳转", notes = "配置台跳转")
    @GetMapping("/view")
    @RequiresPermissions("ctld:ctldSwitchCfg:view")
    public String view(ModelMap model) {
        model.addAttribute("pageSize", ctldProperties.getSwitchPageSize());
        return prefix + "/list";
    }

    @Log(title = "产地准出核验口径切换开关表集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分区代号／切换名目支持前缀与全等，三态全等；分层范围自动收口")
    @GetMapping("/list")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public ResultTable list(SwitchCfgQuery query) {
        Tablepar tablepar = Tablepar.buildPageRequest();
        int pageNum = tablepar.getPage() <= 0 ? 1 : tablepar.getPage();
        // 一屏几条由县局写在配置里，页上传了 limit 从页上，没传走配置，一路能往下翻
        int pageSize = tablepar.getLimit() <= 0 ? ctldProperties.getSwitchPageSize() : tablepar.getLimit();
        PageHelper.startPage(pageNum, pageSize);
        PageInfo<TCtldSwitchCfg> page = new PageInfo<>(ctldSwitchCfgService.selectList(query));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "单行详情", notes = "单行详情")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public AjaxResult detail(@PathVariable Long id) {
        return retobject(200, ctldSwitchCfgService.selectTCtldSwitchCfgById(id));
    }

    @Log(title = "产地准出核验口径切换开关表新增", action = "add")
    @ApiOperation(value = "新增一行", notes = "校验三态/桶数/处数、挂靠校准、在途唯一、轧差、双落地")
    @PostMapping("/add")
    @RequiresPermissions("ctld:ctldSwitchCfg:add")
    @ResponseBody
    public AjaxResult add(TCtldSwitchCfg record) {
        return retobject(200, ctldSwitchCfgService.create(record));
    }

    @Log(title = "产地准出核验口径切换开关表修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "改三态/桶数/处数/挂靠；压行后改不动")
    @PostMapping("/edit")
    @RequiresPermissions("ctld:ctldSwitchCfg:edit")
    @ResponseBody
    public AjaxResult editSave(TCtldSwitchCfg record) {
        return retobject(200, ctldSwitchCfgService.change(record));
    }

    @Log(title = "产地准出核验口径切换开关表已切处数更正", action = "correctHit")
    @ApiOperation(value = "已切处数更正（第二趟重轧）", notes = "已切处数更正")
    @PostMapping("/correctHit")
    @RequiresPermissions("ctld:ctldSwitchCfg:edit")
    @ResponseBody
    public AjaxResult correctHit(Long id, Integer hitNum) {
        return retobject(200, ctldSwitchCfgService.correctHit(id, hitNum));
    }

    @Log(title = "产地准出核验口径切换开关表送一级", action = "advance")
    @ApiOperation(value = "送一级 待上→已上→压行", notes = "步数由系统数")
    @PostMapping("/advance")
    @RequiresPermissions("ctld:ctldSwitchCfg:advance")
    @ResponseBody
    public AjaxResult advance(Long id) {
        return retobject(200, ctldSwitchCfgService.advance(id));
    }

    @Log(title = "产地准出核验口径切换开关表退回", action = "revert")
    @ApiOperation(value = "已上退回待上重来", notes = "旧痕迹保留，不另起并排行")
    @PostMapping("/revert")
    @RequiresPermissions("ctld:ctldSwitchCfg:revert")
    @ResponseBody
    public AjaxResult revert(Long id) {
        return retobject(200, ctldSwitchCfgService.revert(id));
    }

    @Log(title = "产地准出核验口径切换开关表撤下", action = "remove")
    @ApiOperation(value = "撤下", notes = "压行撤不下来")
    @DeleteMapping("/remove")
    @RequiresPermissions("ctld:ctldSwitchCfg:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(ctldSwitchCfgService.withdraw(ids));
    }

    // ---------------- 第三节：两条回话通道，同一套读法 ----------------

    @ApiOperation(value = "旧通道：按代号取那一行", notes = "模样与返回定型，不碰签名")
    @GetMapping("/getBySwNo")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public TCtldSwitchCfg getBySwNo(@RequestParam String swNo) {
        return ctldSwitchCfgService.getBySwNo(swNo);
    }

    @ApiOperation(value = "旧通道问法：按代号+地块码回口径", notes = "没行落系统兜底（2配0等效全旧）")
    @GetMapping("/decide")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public SwitchDecision decide(@RequestParam String swNo, @RequestParam String plotNo) {
        return ctldSwitchCfgService.decideBySwNo(swNo, plotNo);
    }

    @ApiOperation(value = "新通道：按分区+名目+地块码整表取优回口径", notes = "县→市→省→兜底，等价于旧通道")
    @GetMapping("/resolve")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public SwitchDecision resolve(Integer zoneId, String zoneNo, String swName, String plotNo) {
        return ctldSwitchCfgService.resolve(zoneId, zoneNo, swName, plotNo);
    }
}
