package org.plugin.window.panel;

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

public class PanelGenerator {

    // GenerateCardPanel 生成一个带有圆角边框的卡片面板
    public static JPanel GenerateCardPanel() {
        JPanel card = new JBPanel<>(new BorderLayout());
        // 卡片透明、不突出展示：去掉实色背景，只保留淡灰描边，内容直接融在窗口底色上
        card.setOpaque(false);

        // 圆角 12px + 加重描边，内层留白保证内容不贴边
        card.setBorder(new CompoundBorder(
                new UiFactory.RoundedBorder(UiFactory.CARD_ARC, UiFactory.CARD_BORDER, 1),
                JBUI.Borders.empty(15, 18)
        ));

        // 设置最大高度
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        return card;
    }
}
