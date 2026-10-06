package com.fc.v2.common.exception;

/**
 * 核验口径切换开关业务规矩被违反时抛出。消息一律点名是哪一栏不合、为什么不作数，
 * 由控制器原样回给配置台。
 *
 * @author fuce
 */
public class SwitchCfgException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SwitchCfgException(String message) {
        super(message);
    }
}
