package org.plugin.window.panel;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.Nullable;
import org.plugin.window.content.DailyFortuneContent;
import org.plugin.window.dialog.FortuneDialog;

import javax.swing.*;
import java.awt.*;

/**
 * 「今日指引」入口卡片：只放一个按钮，点开 {@link FortuneDialog} 看大模型给出的今日注意事项与吉凶。
 * <p>
 * 卡片刻意不做自动刷新、不预请求模型 —— 大模型调用是花钱且有延迟的动作，
 * 必须由用户明确点击触发，而不是像余额/天气那样挂在定时器上。
 */
public class FortunePanel {

    private final Project project;

    public FortunePanel(@Nullable Project project) {
        this.project = project;
    }

    public JPanel GenerateFortuneCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 138));

        // 顶行：标题 + 状态徽标
        JBLabel titleLabel = new JBLabel("🧭 今日指引");
        titleLabel.setForeground(JBColor.foreground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        boolean generatedToday = !DailyFortuneContent.currentCache().isBlank();
        JBLabel badge = new JBLabel(generatedToday ? "已生成" : "AI");
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 10f));
        badge.setForeground(generatedToday ? UiFactory.PURPLE : JBColor.GRAY);

        JPanel header = new JBPanel<>(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);

        // 副标题：说清这个按钮会拿什么算、算出什么
        JBLabel subtitle = new JBLabel("结合天气、日程与节律，给出今日注意与吉凶");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        subtitle.setForeground(JBColor.GRAY);

        // 主按钮：整卡唯一的行动点
        UiFactory.GradientButton actionButton = new UiFactory.GradientButton(
                generatedToday ? "🔮 查看今日指引" : "🔮 占今日吉凶");
        actionButton.setFont(actionButton.getFont().deriveFont(Font.PLAIN, 13f));
        actionButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        actionButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actionButton.setPreferredSize(new Dimension(180, 34));
        actionButton.addActionListener(e -> openDialog());
        actionButton.setToolTipText("<html>日程来源：macOS 系统日历（需一次授权）或 ICS 文件/订阅链接，"
                + "外加「定时提醒」里今天会触发的条目。<br>"
                + "天气来自高德实况与预报。任一来源不可用都不会中断生成，只会少一份参考。</html>");

        JPanel buttonRow = new JBPanel<>(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(actionButton);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(header);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(10));
        content.add(buttonRow);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    /** 打开弹窗。创建与生成都在弹窗内部完成，按钮回调不阻塞 EDT。 */
    private void openDialog() {
        new FortuneDialog(project).show();
    }
}
