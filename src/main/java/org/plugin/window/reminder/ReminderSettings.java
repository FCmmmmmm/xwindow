package org.plugin.window.reminder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用级持久化配置，保存所有定时提醒，存储于 IDE 配置目录的 xwindow-reminders.xml。
 */
@State(
        name = "XWindowReminderSettings",
        storages = @Storage("xwindow-reminders.xml")
)
public class ReminderSettings implements PersistentStateComponent<ReminderSettings> {

    @XCollection(style = XCollection.Style.v2)
    public List<ReminderItem> reminders = new ArrayList<>();

    public static ReminderSettings getInstance() {
        return ApplicationManager.getApplication().getService(ReminderSettings.class);
    }

    @Override
    public ReminderSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ReminderSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<ReminderItem> getReminders() {
        return reminders;
    }

    public void setReminders(List<ReminderItem> reminders) {
        this.reminders = reminders;
    }
}
