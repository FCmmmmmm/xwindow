package org.plugin.window.panel;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.plugin.window.content.PoetryContent;

import javax.swing.*;
import java.awt.*;

public class UserPanel {

    public static JPanel GenerateUserCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();

        //  图标标签
        JBLabel iconLabel = new JBLabel("👤 FCmmmmmm");
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 18f));
        // BoxLayout 需要通过 setAlignmentX 来控制水平对齐
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);


        //  描述标签
        JBLabel descLabel = new JBLabel();
        // 设置楷体字体（兼容不同系统）
        Font kaiTiFont = new Font("KaiTi", Font.PLAIN, 13);
        // macOS 使用 STKaiti，如果 KaiTi 不存在则尝试 STKaiti
        if (!kaiTiFont.getFamily().equals("KaiTi") && !kaiTiFont.getFamily().equals("楷体")) {
            kaiTiFont = new Font("STKaiti", Font.PLAIN, 13);
        }
        descLabel.setFont(kaiTiFont);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        descLabel.setHorizontalAlignment(SwingConstants.CENTER); // 文本居中
        descLabel.setText(PoetryContent.Get());
        // 如果诗句太长，建议设置最大宽度，防止撑破布局（可选）
        // descLabel.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));

        // --- 核心修改开始 ---

        // 使用 BoxLayout 实现紧凑排列
        JPanel contentPanel = new JBPanel<>();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // 设置面板内边距，让内容不贴边（可选）
        contentPanel.setBorder(JBUI.Borders.empty(10));

        // 添加组件
        contentPanel.add(iconLabel);
        // 添加固定高度的垂直间距，替代 GridLayout 的自动拉伸
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(descLabel);

        // 为了让这一整块内容在 card 中居中（而不是贴在顶部），
        // 我们将 contentPanel 放入一个 GridBagLayout 的容器中。
        JPanel wrapper = new JBPanel<>(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(contentPanel); // GridBagLayout 默认会将组件放在中间

        card.add(wrapper, BorderLayout.CENTER);

        // --- 核心修改结束 ---

        return card;
    }
}