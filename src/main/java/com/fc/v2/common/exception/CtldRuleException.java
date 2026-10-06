package com.fc.v2.common.exception;

/**
 * 产地准出核验口径切换开关：行不合规矩、进不了库时抛出。
 * 消息一律点名是哪一栏不合，由配置台直接回话给录入人。
 *
 * @author fuce
 */
public class CtldRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CtldRuleException(String message) {
        super(message);
    }
}
