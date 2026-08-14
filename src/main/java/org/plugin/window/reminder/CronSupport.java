package org.plugin.window.reminder;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

/**
 * cron 表达式解析工具，统一使用 Quartz 风格（支持秒级与 '?'）。
 * 例： "0 0 9 ? * MON-FRI"（工作日 9:00）、"0 0/30 * * * ?"（每 30 分钟）。
 */
public final class CronSupport {

    private static final CronDefinition DEFINITION =
            CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);

    private static final CronParser PARSER = new CronParser(DEFINITION);

    private CronSupport() {
    }

    /** 解析并校验，非法时抛出 IllegalArgumentException。 */
    public static Cron parse(String expression) {
        Cron cron = PARSER.parse(expression);
        cron.validate();
        return cron;
    }

    /** 校验表达式是否合法。 */
    public static boolean isValid(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            parse(expression.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
