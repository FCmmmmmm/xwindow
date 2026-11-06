package org.plugin.window.calendar;

import java.time.LocalDateTime;

/**
 * 统一的日程模型，供不同来源（系统日历 / ICS）共用。
 *
 * @param start  开始时间（本地时区）
 * @param end    结束时间（本地时区），缺失时等于 start
 * @param title  标题
 * @param allDay 是否全天事件
 * @param source 来源标签，如 "macOS 日历" / "ICS"
 */
public record CalendarEvent(LocalDateTime start, LocalDateTime end, String title,
                            boolean allDay, String source) {
}
