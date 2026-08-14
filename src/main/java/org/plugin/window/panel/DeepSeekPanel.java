package org.plugin.window.panel;

import com.intellij.icons.AllIcons;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.plugin.window.content.DeepSeekContent;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * DeepSeek 余额卡片：一键查询账户余额，并展示由本地快照推算出的今日消耗与较上次变化。
 * <p>
 * 卡片自带刷新按钮，但线程调度仍归 {@code MainWindow} —— 按钮只回调请求，
 * 由 MainWindow 派发到 executor，避免网络请求跑在 EDT 上。
 */
public class DeepSeekPanel {

    private static final String TIME_FORMAT = "HH:mm:ss";

    private UiFactory.GradientLabel balanceLabel;
    private JBLabel usageLabel;
    private JBLabel refreshTimeLabel;
    private InplaceButton refreshButton;

    /**
     * 构建卡片。
     *
     * @param onRefreshRequest 点击卡片内刷新按钮时的回调，应由调用方派发到后台线程
     */
    public JPanel GenerateDeepSeekCard(Runnable onRefreshRequest) {
        JPanel card = PanelGenerator.GenerateCardPanel();
        // 默认 120 高度装不下「图标行 + 余额 + 消耗 + 刷新时间」四行
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        // 顶行：图标 + 一键刷新按钮
        JBLabel iconLabel = new JBLabel("🤖 DeepSeek");
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 13f));
        iconLabel.setForeground(JBColor.foreground());

        // 原生图标按钮：无边框、悬停高亮，自动适配亮/暗主题，与 IDE 工具栏风格一致
        refreshButton = new InplaceButton("refresh", AllIcons.Actions.Refresh,
                e -> {
                    if (onRefreshRequest != null) {
                        onRefreshRequest.run();
                    }
                });
        refreshButton.setToolTipText("查询 DeepSeek 余额");

        JPanel headerPanel = new JBPanel<>(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);

        // 余额主文案（蓝紫渐变，突出核心数据）
        balanceLabel = new UiFactory.GradientLabel("点击 🔄 查询余额", SwingConstants.CENTER);
        balanceLabel.setFont(balanceLabel.getFont().deriveFont(Font.BOLD, 20f));

        // 消耗推算文案
        usageLabel = new JBLabel(" ");
        usageLabel.setFont(usageLabel.getFont().deriveFont(Font.PLAIN, 11f));
        usageLabel.setForeground(JBColor.GRAY);
        usageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 上次刷新时间
        refreshTimeLabel = new JBLabel(" ");
        refreshTimeLabel.setFont(refreshTimeLabel.getFont().deriveFont(Font.PLAIN, 9f));
        refreshTimeLabel.setForeground(JBColor.GRAY);
        refreshTimeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel contentPanel = new JBPanel<>();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.add(wrapCentered(headerPanel));
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(wrapCentered(balanceLabel));
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(wrapCentered(usageLabel));
        contentPanel.add(Box.createVerticalStrut(2));
        contentPanel.add(wrapCentered(refreshTimeLabel));

        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    /** BoxLayout 下让子组件横向铺满，避免居中设置被压缩成左对齐。 */
    private static JComponent wrapCentered(JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (component instanceof JBPanel) {
            return component;
        }
        JPanel wrapper = new JBPanel<>(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(component, BorderLayout.CENTER);
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        return wrapper;
    }

    /**
     * 查询余额并刷新卡片内容。必须在非 EDT 线程调用（内含网络请求）。
     */
    public void GenerateDisplayText() {
        if (this.balanceLabel == null) {
            return; // 卡片还未构建完成
        }

        SwingUtilities.invokeLater(this::markLoading);

        DeepSeekContent.Display display = DeepSeekContent.getBalanceDisplay();

        SwingUtilities.invokeLater(() -> {
            if (this.balanceLabel == null) {
                return;
            }
            if (display.isOk()) {
                // 成功态：蓝紫渐变大号余额
                this.balanceLabel.setGradientEnabled(true);
                this.balanceLabel.setFont(this.balanceLabel.getFont().deriveFont(Font.BOLD, 20f));
                this.balanceLabel.setText(display.balanceText());
                this.usageLabel.setText(display.usageText() == null ? " " : display.usageText());
            } else {
                // 失败态：回退灰色 + 小一号字体，长文案不至于撑破卡片
                this.balanceLabel.setGradientEnabled(false);
                this.balanceLabel.setForeground(JBColor.GRAY);
                this.balanceLabel.setFont(this.balanceLabel.getFont().deriveFont(Font.PLAIN, 12f));
                this.balanceLabel.setText(display.error());
                this.usageLabel.setText(" ");
            }
            this.refreshTimeLabel.setText(
                    "上次刷新: " + LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT)));
            setRefreshEnabled(true);
        });
    }

    /** 进入查询中状态：按钮置灰、主文案改提示，需在 EDT 调用。 */
    private void markLoading() {
        if (this.balanceLabel == null) {
            return;
        }
        setRefreshEnabled(false);
        this.balanceLabel.setGradientEnabled(false);
        this.balanceLabel.setForeground(JBColor.GRAY);
        this.balanceLabel.setFont(this.balanceLabel.getFont().deriveFont(Font.PLAIN, 12f));
        //noinspection DialogTitleCapitalization
        this.balanceLabel.setText("正在查询余额...");
    }

    private void setRefreshEnabled(boolean enabled) {
        if (this.refreshButton != null) {
            this.refreshButton.setEnabled(enabled);
        }
    }
}
