package com.fc.v2.service.ctld;

import com.fc.v2.common.exception.CtldRuleException;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 切换开关死规矩单测：三态/开桶配对、处数轧差、尾数落桶、并立取优、旧行与兜底。
 */
public class SwitchCfgRuleTest {

    private TCtldSwitchCfg row(Integer kind, Integer gray, Integer ver, String swNo) {
        TCtldSwitchCfg r = new TCtldSwitchCfg();
        r.setSwKind(kind);
        r.setGrayNum(gray);
        r.setVerNo(ver);
        r.setSwNo(swNo);
        return r;
    }

    // 第一节：三态与开桶个数配对

    @Test
    public void kind0AcceptsNoGray() {
        SwitchCfgRule.validateKind(0, null);
    }

    @Test
    public void kind1WithGrayRejected() {
        CtldRuleException e = assertThrows(CtldRuleException.class,
                () -> SwitchCfgRule.validateKind(1, 3));
        assert e.getMessage().contains("开桶个数");
    }

    @Test
    public void kind2WithoutGrayRejected() {
        CtldRuleException e = assertThrows(CtldRuleException.class,
                () -> SwitchCfgRule.validateKind(2, null));
        assert e.getMessage().contains("开桶个数");
    }

    @Test
    public void kind2GrayRangeOnly0to10() {
        SwitchCfgRule.validateKind(2, 0);
        SwitchCfgRule.validateKind(2, 10);
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.validateKind(2, -1));
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.validateKind(2, 11));
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.validateKind(9, null));
    }

    // 第四节：缺额轧差 8 配 5 得 3；6 配 6 得 0；反超/负数/空值回绝

    @Test
    public void lackReconcileSamples() {
        assertEquals(3, SwitchCfgRule.reconcileLack(8, 5));
        assertEquals(0, SwitchCfgRule.reconcileLack(6, 6));
        assertEquals(0, SwitchCfgRule.reconcileLack(0, 0));
        assertEquals(7, SwitchCfgRule.reconcileLack(7, null));
    }

    @Test
    public void lackReconcileRejectsBad() {
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.reconcileLack(null, 1));
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.reconcileLack(-1, 0));
        CtldRuleException e = assertThrows(CtldRuleException.class,
                () -> SwitchCfgRule.reconcileLack(5, 6));
        assert e.getMessage().contains("已切");
    }

    // 第三节：尾数落桶；写 3 即 0、1、2 走新

    @Test
    public void bucketByLastDigitOnly() {
        assertEquals(SwitchCfgRule.ANSWER_NEW, SwitchCfgRule.bucketOf("DK-102-0", 3));
        assertEquals(SwitchCfgRule.ANSWER_NEW, SwitchCfgRule.bucketOf("X1", 3));
        assertEquals(SwitchCfgRule.ANSWER_NEW, SwitchCfgRule.bucketOf("X2", 3));
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.bucketOf("X3", 3));
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.bucketOf("X9", 3));
        // 10 桶全开：尾数 9 也走新
        assertEquals(SwitchCfgRule.ANSWER_NEW, SwitchCfgRule.bucketOf("X9", 10));
        // 0 桶：尾数 0 也走旧，码尾不是数字也不拦（等效全旧）
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.bucketOf("X0", 0));
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.bucketOf("DKA", 0));
        // 只认数字尾（开了桶才验尾数）
        assertThrows(CtldRuleException.class, () -> SwitchCfgRule.bucketOf("DKA", 3));
    }

    @Test
    public void samePlotSameAnswerRegardless() {
        // 同一处地块，不管谁问，落桶结果唯一
        TCtldSwitchCfg r = row(2, 4, 1, "SW1");
        int morning = SwitchCfgRule.answerOf(r, "plot-705");
        int evening = SwitchCfgRule.answerOf(r, "plot-705");
        assertEquals(morning, evening);
        assertEquals(SwitchCfgRule.ANSWER_OLD, morning);
        assertEquals(SwitchCfgRule.ANSWER_NEW, SwitchCfgRule.answerOf(r, "plot-703"));
    }

    // 第二节：并立几行，改动次序大的说话；并列再比代号字面，靠后的说话

    @Test
    public void winnerByVerThenCode() {
        List<TCtldSwitchCfg> rows = new ArrayList<TCtldSwitchCfg>();
        rows.add(row(0, null, 2, "SW-A"));
        rows.add(row(1, null, 5, "SW-B"));
        assertEquals("SW-B", SwitchCfgRule.pickWinner(rows).getSwNo());

        List<TCtldSwitchCfg> tie = new ArrayList<TCtldSwitchCfg>();
        tie.add(row(0, null, 5, "SW-A"));
        tie.add(row(1, null, 5, "SW-B"));
        assertEquals("SW-B", SwitchCfgRule.pickWinner(tie).getSwNo());
    }

    // 旧行（三态空着）按旧口径接住，不报错、不当压行由调用方按 status 判

    @Test
    public void legacyRowAnswersOld() {
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.answerOf(null, "X1"));
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.answerOf(row(null, null, null, null), "X1"));
    }

    @Test
    public void fallbackIsKind2ZeroBuckets() {
        TCtldSwitchCfg fb = SwitchCfgRule.fallbackRow();
        assertEquals(SwitchCfgRule.KIND_BUCKET, fb.getSwKind().intValue());
        assertEquals(0, fb.getGrayNum().intValue());
        assertEquals(SwitchCfgRule.ANSWER_OLD, SwitchCfgRule.answerOf(fb, "X0"));
    }
}
