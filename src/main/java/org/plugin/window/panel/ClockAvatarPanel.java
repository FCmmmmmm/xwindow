package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.plugin.window.settings.UserProfileSettings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 个人信息卡片：左侧「头像(+音乐徽标) + 名称 + 副标题」。
 * <p>
 * 时钟已拆到独立的 {@link ClockPanel}，本卡只负责用户身份展示。
 * 头像来自 {@link UserProfileSettings}（可配置），带右下角蓝紫音乐徽标；无有效图片时用「渐变 + 首字母」兜底。
 * 由 {@code MainWindow} 每秒调用 {@link #refreshName()}，让后台改头像/名称也能生效。
 */
public class ClockAvatarPanel {

    private static final int AVATAR_SIZE = 48;

    private JBLabel avatarLabel;
    private JBLabel nameLabel;
    private String cachedName = "";
    /** 上次生效的头像路径，用于检测设置里是否换了头像，避免每秒重复读盘。 */
    private String cachedAvatarPath = "";

    public JPanel GenerateClockAvatarCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        avatarLabel = new JBLabel();
        avatarLabel.setPreferredSize(new Dimension(AVATAR_SIZE + 12, AVATAR_SIZE + 12));

        nameLabel = new JBLabel();
        nameLabel.setForeground(JBColor.foreground());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));

        JBLabel subtitleLabel = new JBLabel("专注当下 · 享受编码");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        subtitleLabel.setForeground(JBColor.GRAY);

        JPanel nameStack = new JBPanel<>();
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        nameStack.setOpaque(false);
        nameStack.add(nameLabel);
        nameStack.add(Box.createVerticalStrut(3));
        nameStack.add(subtitleLabel);

        JPanel row = new JBPanel<>();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.add(avatarLabel);
        row.add(Box.createHorizontalStrut(12));
        row.add(nameStack);
        row.setAlignmentY(Component.CENTER_ALIGNMENT);

        // 用 GridBagLayout 让整块内容在卡片内居中
        JPanel wrapper = new JBPanel<>(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(row);

        card.add(wrapper, BorderLayout.CENTER);

        refreshName();
        return card;
    }

    /**
     * 刷新头像与名称（从设置读取，需在 EDT 调用）。
     * <p>
     * 由 MainWindow 每秒定时器驱动，以便「后台改了头像/名称」也能生效。
     * 头像路径缓存了上次值，只有路径或名称变化时才重读图片，避免每秒反复读盘。
     */
    public void refreshName() {
        if (nameLabel == null) {
            return;
        }
        UserProfileSettings settings = UserProfileSettings.getInstance();
        String name = settings.getUserName();
        String path = settings.getAvatarPath();
        boolean nameChanged = !cachedName.equals(name);
        boolean avatarDirty = nameChanged || !Objects.equals(cachedAvatarPath, path);

        cachedName = name;
        cachedAvatarPath = path;

        if (nameChanged) {
            nameLabel.setText(name);
        }
        if (avatarDirty) {
            Icon avatar = loadAvatar(path);
            if (avatar == null) {
                avatar = UiFactory.defaultAvatarIcon(name, AVATAR_SIZE);
            }
            avatarLabel.setIcon(avatar);
            avatarLabel.setText("");
        }
    }

    /** 加载并缩放本地头像图片（带音乐徽标），路径无效或读取失败时返回 null。 */
    private Icon loadAvatar(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        if (!file.isFile()) {
            return null;
        }
        try {
            // ImageIO 同步读取，保证像素数据完整，避免异步加载未完成时渲染成黑块
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return null;
            }
            return UiFactory.musicAvatarIcon(img, AVATAR_SIZE);
        } catch (IOException e) {
            return null;
        }
    }
}