package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.service.ITCtldSwitchCfgService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.github.pagehelper.PageHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 产地准出核验口径切换开关表 Controller
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

    @ApiOperation(value = "配置台跳转", notes = "配置台跳转")
    @GetMapping("/view")
    @RequiresPermissions("ctld:ctldSwitchCfg:view")
    public String view(ModelMap model) {
        model.put("pageSize", v2Config.getCtldSwitchPageSize());
        return prefix + "/list";
    }

    @Log(title = "核验口径切换开关台账查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分区代号、切换名目支持全等与前缀两种匹配，三态走全等")
    @GetMapping("/list")
    @RequiresPermissions("ctld:ctldSwitchCfg:list")
    @ResponseBody
    public ResultTable list(TCtldSwitchCfg record,
                            @RequestParam(value = "prefixMatch", defaultValue = "false") boolean prefixMatch) {
        QueryWrapper<TCtldSwitchCfg> queryWrapper = new QueryWrapper<TCtldSwitchCfg>();
        // 三个查询通道：分区代号、切换名目（全等/前缀二选一）、开关三态（全等）
        if (record.getZoneNo() != null && !record.getZoneNo().trim().isEmpty()) {
            if (prefixMatch) {
                queryWrapper.likeRight("zone_no", record.getZoneNo().trim());
            } else {
                queryWrapper.eq("zone_no", record.getZoneNo().trim());
            }
        }
        if (record.getSwName() != null && !record.getSwName().trim().isEmpty()) {
            if (prefixMatch) {
                queryWrapper.likeRight("sw_name", record.getSwName().trim());
            } else {
                queryWrapper.eq("sw_name", record.getSwName().trim());
            }
        }
        if (record.getSwKind() != null) {
            queryWrapper.eq("sw_kind", record.getSwKind());
        }
        if (record.getLevelKind() != null && !record.getLevelKind().trim().isEmpty()) {
            queryWrapper.eq("level_kind", record.getLevelKind().trim());
        }

        // 一屏几条由县局写在配置里；页面自带 limit 时以页面为准，能一路往下翻
        Integer pageNum = com.fc.v2.util.ServletUtils.getParameterToInt("page", 1);
        Integer pageSize = com.fc.v2.util.ServletUtils.getParameterToInt("limit", 0);
        if (pageSize == null || pageSize <= 0) {
            pageSize = v2Config.getCtldSwitchPageSize();
        }
        PageHelper.startPage(pageNum == null || pageNum <= 0 ? 1 : pageNum, pageSize);
        java.util.List<TCtldSwitchCfg> rows =
                ctldSwitchCfgService.selectScopedList(queryWrapper, ShiroUtils.getUserId());
        com.github.pagehelper.PageInfo<TCtldSwitchCfg> page =
                new com.github.pagehelper.PageInfo<TCtldSwitchCfg>(rows);
        return pageTable(page.getList(), page.getTotal());
    }

    @GetMapping("/add")
    @RequiresPermissions("ctld:ctldSwitchCfg:view")
    public String add() {
        return prefix + "/add";
    }

    @GetMapping("/edit/{id}")
    @RequiresPermissions("ctld:ctldSwitchCfg:view")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        TCtldSwitchCfg row = ctldSwitchCfgService.selectTCtldSwitchCfgById(id);
        if (row == null) {
            return "redirect:/CtldSwitchCfgController/view";
        }
        mmap.put("row", row);
        return prefix + "/edit";
    }

    @Log(title = "核验口径切换开关新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("ctld:ctldSwitchCfg:add")
    @ResponseBody
    public AjaxResult add(TCtldSwitchCfg record) {
        return toAjax(ctldSwitchCfgService.insertTCtldSwitchCfg(record));
    }

    @Log(title = "核验口径切换开关修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("ctld:ctldSwitchCfg:edit")
    @ResponseBody
    public AjaxResult editSave(TCtldSwitchCfg record) {
        return toAjax(ctldSwitchCfgService.updateTCtldSwitchCfg(record));
    }

    @Log(title = "核验口径切换开关上报", action = "advance")
    @ApiOperation(value = "往上送一步", notes = "待上→已上→压行")
    @PostMapping("/advance")
    @RequiresPermissions("ctld:ctldSwitchCfg:advance")
    @ResponseBody
    public AjaxResult advance(Long id) {
        return toAjax(ctldSwitchCfgService.advance(id));
    }

    @Log(title = "核验口径切换开关退回", action = "revert")
    @ApiOperation(value = "退回一步", notes = "已上→待上")
    @PostMapping("/revert")
    @RequiresPermissions("ctld:ctldSwitchCfg:revert")
    @ResponseBody
    public AjaxResult revert(Long id) {
        return ctldSwitchCfgService.revert(id) != null ? success() : error();
    }

    @Log(title = "核验口径切换开关删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("ctld:ctldSwitchCfg:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(ctldSwitchCfgService.deleteTCtldSwitchCfgByIds(ids));
    }

    @Log(title = "按代号取值", action = "getByCode")
    @ApiOperation(value = "按代号取值（原通道，签名/返回冻结）", notes = "返回 0 旧口径 1 新口径")
    @GetMapping("/getByCode")
    @RequiresPermissions("ctld:ctldSwitchCfg:resolve")
    @ResponseBody
    public AjaxResult getByCode(String zoneNo, String swName, String plotNo) {
        // 原通道：模样与返回一概不动——只回一个口径码
        return success(200, "ok", ctldSwitchCfgService.getByCode(zoneNo, swName, plotNo));
    }

    @Log(title = "整表取优", action = "resolve")
    @ApiOperation(value = "整表取优（新通道，等价问话）", notes = "回话口径与出自哪一层")
    @GetMapping("/resolve")
    @RequiresPermissions("ctld:ctldSwitchCfg:resolve")
    @ResponseBody
    public AjaxResult resolve(String zoneNo, String swName, String plotNo) {
        // 另起一条等价方法承接问法，带出来源层级与桶号，回话与 getByCode 必为同一个
        return retobject(200, ctldSwitchCfgService.resolve(zoneNo, swName, plotNo));
    }
}
