package com.fc.v2.service.ctld;

import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 核验口径回话解析（第二节、第三节）。
 *
 * 两个问法的位置：
 * 1）按代号取值——旧通道 {@link #getBySwNo(String)}，模样与返回定型，往后不碰它的签名；
 * 2）整表取优——新通道 {@link #resolve(Integer, String, String)}，另起的一条等价方法。
 * 两条通道最后都过同一套落桶读法 {@link SwitchCfgRules#hitNew}，同一处地块谁问、
 * 从哪个通道问，回的口径必须是同一个。
 *
 * @author fuce
 */
@Component
public class SwitchCfgResolver {

    @Resource
    private SwitchCfgSnapshot snapshot;

    /**
     * 旧通道：按切换项代号取那一行。有行给行，没行给 null——不在此处偷偷换成兜底，
     * 兜底只在 {@link #decideBySwNo} 落口径时补。
     */
    public TCtldSwitchCfg getBySwNo(String swNo) {
        return snapshot.bySwNo(swNo);
    }

    /** 旧通道问法：给代号 + 地块码，回这一处走新走旧。 */
    public SwitchDecision decideBySwNo(String swNo, String plotNo) {
        TCtldSwitchCfg row = getBySwNo(swNo);
        return toDecision(row, plotNo);
    }

    /**
     * 新通道（等价方法）：给分区与切换名目，顺县定 → 市定 → 省定链往上找，整链空着
     * 才落系统兜底（三态 2 配 0 桶，等效全旧）。每一层并立几行的按改动次序、代号字面取优。
     */
    public SwitchDecision resolve(Integer zoneId, String swName, String plotNo) {
        return resolve(zoneId, null, swName, plotNo);
    }

    /** 分区只认得出代号时走这一个入口，zoneId、zoneNo 任给一个即可收口县定行。 */
    public SwitchDecision resolve(Integer zoneId, String zoneNo, String swName, String plotNo) {
        if (swName == null || swName.length() == 0) {
            throw new IllegalArgumentException("切换名目空着：整表取优不知道在问哪一项");
        }
        TCtldSwitchCfg best = null;
        for (String level : SwitchCfgRules.LEVEL_CHAIN) {
            List<TCtldSwitchCfg> rows = snapshot.atLevel(level, swName, zoneId, zoneNo);
            best = SwitchCfgRules.pickBest(rows);
            if (best != null) {
                break;
            }
        }
        return toDecision(best, plotNo);
    }

    private SwitchDecision toDecision(TCtldSwitchCfg row, String plotNo) {
        if (row == null) {
            TCtldSwitchCfg fallback = SwitchCfgRules.fallbackRow();
            return new SwitchDecision(false, fallback.getSwKind(), fallback.getGrayNum(),
                    fallback.getLevelKind(), null);
        }
        boolean hit = SwitchCfgRules.hitNew(row, plotNo);
        return new SwitchDecision(hit,
                row.getSwKind() == null ? SwitchCfgRules.KIND_OLD : row.getSwKind(),
                row.getGrayNum(), row.getLevelKind(), row.getSwNo());
    }
}
