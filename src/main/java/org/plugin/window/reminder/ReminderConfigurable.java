package org.plugin.window.reminder;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 设置页：Settings/Preferences → 工具) 中配置定时提醒。
 */
public class ReminderConfigurable implements Configurable {

    private ReminderTableModel model;
    private JBTable table;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱提醒";
    }

    @Override
    public @Nullable JComponent createComponent() {
        model = new ReminderTableModel(ReminderSettings.getInstance().getReminders());
        table = new JBTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setMaxWidth(80);

        JPanel tablePanel = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> {
                    ReminderEditDialog dialog = new ReminderEditDialog(null);
                    if (dialog.showAndGet()) {
                        model.addItem(dialog.getResult());
                    }
                })
                .setEditAction(button -> editSelected())
                .setRemoveAction(button -> {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        model.removeItem(row);
                    }
                })
                .createPanel();

        JBLabel hint = new JBLabel(
                "<html>双击或点击编辑可修改。Cron 为 Quartz 风格：秒 分 时 日 月 周。</html>");
        hint.setForeground(JBColor.GRAY);
        hint.setBorder(JBUI.Borders.empty(8, 2, 0, 0));

        root = new JPanel(new BorderLayout());
        root.add(tablePanel, BorderLayout.CENTER);
        root.add(hint, BorderLayout.SOUTH);
        return root;
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        ReminderEditDialog dialog = new ReminderEditDialog(model.getItem(row));
        if (dialog.showAndGet()) {
            ReminderItem edited = dialog.getResult();
            edited.setEnabled(model.getItem(row).isEnabled()); // 保留表内勾选状态
            model.updateItem(row, edited);
        }
    }

    @Override
    public boolean isModified() {
        return differs();
    }

    private boolean differs() {
        var current = model.getItems();
        var saved = ReminderSettings.getInstance().getReminders();
        if (current.size() != saved.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            ReminderItem a = current.get(i);
            ReminderItem b = saved.get(i);
            if (!a.getId().equals(b.getId())
                    || !a.getText().equals(b.getText())
                    || !a.getCron().equals(b.getCron())
                    || a.getType() != b.getType()
                    || a.isEnabled() != b.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply() {
        ReminderSettings.getInstance().setReminders(model.getItems());
        ReminderScheduler.getInstance().reload();
    }

    @Override
    public void reset() {
        model = new ReminderTableModel(ReminderSettings.getInstance().getReminders());
        table.setModel(model);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setMaxWidth(80);
    }

    @Override
    public void disposeUIResources() {
        model = null;
        table = null;
        root = null;
    }
}
