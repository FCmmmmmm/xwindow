package org.plugin.window.calendar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 应用级持久化配置，控制「今日指引」是否读取 macOS 系统日历，存储于 xwindow-calendar.xml。
 * <p>
 * 默认开启：只有开启后首次读取才会弹系统授权框，用户点「好」即完成授权，之后不再打扰。
 * 若用户拒绝过授权，可在设置页关掉此开关，插件会退回只用「工具箱提醒」的日程。
 */
@State(
        name = "XWindowCalendarSettings",
        storages = @Storage("xwindow-calendar.xml")
)
public class CalendarSettings implements PersistentStateComponent<CalendarSettings> {

    /** 是否允许通过 osascript 读取 macOS 系统日历（需一次「自动化」授权）。 */
    public boolean systemCalendarEnabled = true;

    /**
     * ICS 来源：本地 .ics 绝对路径，或 http(s)/webcal 订阅链接。为空表示不启用。
     * <p>
     * 这条通道不需要任何系统权限，是系统日历读不到时的兜底；
     * 内容可以来自 Calendar 导出、企业微信/Outlook 导出，或日历的「发布」订阅地址。
     */
    public String icsSource = "";

    public static CalendarSettings getInstance() {
        return ApplicationManager.getApplication().getService(CalendarSettings.class);
    }

    @Override
    public CalendarSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CalendarSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public boolean isSystemCalendarEnabled() {
        return systemCalendarEnabled;
    }

    public void setSystemCalendarEnabled(boolean systemCalendarEnabled) {
        this.systemCalendarEnabled = systemCalendarEnabled;
    }

    public String getIcsSource() {
        return icsSource;
    }

    public void setIcsSource(String icsSource) {
        this.icsSource = icsSource == null ? "" : icsSource.trim();
    }
}
