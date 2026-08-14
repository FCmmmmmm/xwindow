package org.plugin.window.reminder;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 提醒列表的表格模型。列：启用 / 文案 / Cron / 通知方式。
 */
public class ReminderTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"启用", "提示文案", "Cron 表达式", "通知方式"};

    private final List<ReminderItem> items = new ArrayList<>();

    public ReminderTableModel(List<ReminderItem> source) {
        for (ReminderItem item : source) {
            items.add(copy(item));
        }
    }

    private static ReminderItem copy(ReminderItem o) {
        return new ReminderItem(o.getId(), o.getText(), o.getCron(), o.getType(), o.isEnabled());
    }

    public List<ReminderItem> getItems() {
        List<ReminderItem> result = new ArrayList<>();
        for (ReminderItem item : items) {
            result.add(copy(item));
        }
        return result;
    }

    public ReminderItem getItem(int row) {
        return items.get(row);
    }

    public void addItem(ReminderItem item) {
        items.add(item);
        int row = items.size() - 1;
        fireTableRowsInserted(row, row);
    }

    public void updateItem(int row, ReminderItem item) {
        items.set(row, item);
        fireTableRowsUpdated(row, row);
    }

    public void removeItem(int row) {
        items.remove(row);
        fireTableRowsDeleted(row, row);
    }

    @Override
    public int getRowCount() {
        return items.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0; // 仅「启用」可在表内直接勾选
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ReminderItem item = items.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> item.isEnabled();
            case 1 -> item.getText();
            case 2 -> item.getCron();
            case 3 -> item.getType() == ReminderItem.NotifyType.DIALOG ? "弹窗" : "气泡";
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0 && aValue instanceof Boolean b) {
            items.get(rowIndex).setEnabled(b);
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
