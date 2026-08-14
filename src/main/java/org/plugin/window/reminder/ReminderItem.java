package org.plugin.window.reminder;

/**
 * 单条定时提醒配置。
 * <p>无参构造 + getter/setter 以便 IntelliJ 的 XML 序列化（PersistentStateComponent）。
 */
public class ReminderItem {

    /** 通知方式 */
    public enum NotifyType {
        /** 右下角气泡通知 */
        BALLOON,
        /** 模态弹窗对话框 */
        DIALOG
    }

    private String id = "";
    private String text = "";
    /** Quartz 风格 cron 表达式，如 "0 0 9 ? * MON-FRI" */
    private String cron = "";
    private NotifyType type = NotifyType.BALLOON;
    private boolean enabled = true;

    public ReminderItem() {
    }

    public ReminderItem(String id, String text, String cron, NotifyType type, boolean enabled) {
        this.id = id;
        this.text = text;
        this.cron = cron;
        this.type = type;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public NotifyType getType() {
        return type;
    }

    public void setType(NotifyType type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
