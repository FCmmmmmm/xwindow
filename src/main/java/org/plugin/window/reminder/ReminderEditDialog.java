package org.plugin.window.reminder;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

/**
 * 单条提醒的新增/编辑对话框。
 */
public class ReminderEditDialog extends DialogWrapper {

    private final JBTextField textField = new JBTextField();
    private final JBTextField cronField = new JBTextField();
    private final ComboBox<ReminderItem.NotifyType> typeCombo =
            new ComboBox<>(ReminderItem.NotifyType.values());

    private final String id;

    public ReminderEditDialog(@Nullable ReminderItem source) {
        super(true);
        setTitle(source == null ? "新增提醒" : "编辑提醒");
        if (source != null) {
            this.id = source.getId();
            textField.setText(source.getText());
            cronField.setText(source.getCron());
            typeCombo.setSelectedItem(source.getType());
        } else {
            this.id = UUID.randomUUID().toString();
            cronField.setText("0 0 9 ? * MON-FRI");
        }
        typeCombo.setRenderer(SimpleTypeRenderer.INSTANCE);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBLabel hint = new JBLabel(
                "<html>Quartz 风格：秒 分 时 日 月 周<br>" +
                        "例：0 0 9 ? * MON-FRI（工作日9点）、0 0/30 * * * ?（每30分钟）</html>");
        hint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("提示文案：", textField)
                .addLabeledComponent("Cron 表达式：", cronField)
                .addLabeledComponent("通知方式：", typeCombo)
                .addComponent(hint)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (textField.getText().trim().isEmpty()) {
            return new ValidationInfo("提示文案不能为空", textField);
        }
        if (!CronSupport.isValid(cronField.getText())) {
            return new ValidationInfo("Cron 表达式非法", cronField);
        }
        return null;
    }

    public ReminderItem getResult() {
        return new ReminderItem(
                id,
                textField.getText().trim(),
                cronField.getText().trim(),
                (ReminderItem.NotifyType) typeCombo.getSelectedItem(),
                true
        );
    }

    /** 下拉框中文展示。 */
    private static final class SimpleTypeRenderer extends DefaultListCellRenderer {
        static final SimpleTypeRenderer INSTANCE = new SimpleTypeRenderer();

        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value == ReminderItem.NotifyType.DIALOG) {
                setText("弹窗");
            } else if (value == ReminderItem.NotifyType.BALLOON) {
                setText("气泡");
            }
            return this;
        }
    }
}
