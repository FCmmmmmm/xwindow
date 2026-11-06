package org.plugin.window.dialog;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.plugin.window.content.DailyFortuneContent;
import org.plugin.window.content.MarkdownContent;
import org.plugin.window.panel.UiFactory;
import org.plugin.window.settings.GaodeSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Future;

/**
 * 「今日指引」弹窗：点按钮后弹出，异步请求大模型，把 Markdown 渲成 HTML 展示。
 * <p>
 * 三个刻意的设计：
 * <ul>
 *   <li><b>非模态</b>：生成要几秒到几十秒，模态会把整个 IDE 锁住，不值当；</li>
 *   <li><b>线程池 + invokeLater</b>：网络请求走 {@link AppExecutorUtil}，UI 更新回 EDT，
 *       与项目里其它卡片的刷新方式保持一致；</li>
 *   <li><b>当天缓存</b>：重复打开弹窗不重复计费，只有点「重新生成」才会再请求一次。</li>
 * </ul>
 */
public class FortuneDialog extends DialogWrapper {

    private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final JEditorPane contentPane = new HtmlPane();
    private final JBLabel statusLabel = new JBLabel(" ");
    private final UiFactory.GradientButton regenerateButton =
            new UiFactory.GradientButton("🔄 重新生成");
    private final JButton copyButton = new JButton("复制");
    private final JButton closeButton = new JButton("关闭");

    /** 当前在飞的请求，关闭弹窗时取消，避免为已消失的窗口继续计费。 */
    private volatile Future<?> inFlight;
    /** 最近一次成功生成的 Markdown，用于「复制」。 */
    private volatile String lastMarkdown = "";

    public FortuneDialog(@Nullable Project project) {
        super(project, false);
        setTitle("今日指引");
        setModal(false);
        init();
    }

    @Override
    protected void init() {
        super.init();
        // 无标准按钮条，需要自己接 ESC 关闭
        if (getRootPane() != null) {
            getRootPane().registerKeyboardAction(
                    e -> doCancelAction(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
            getRootPane().setDefaultButton(closeButton);
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        contentPane.setEditable(false);
        contentPane.setOpaque(true);
        contentPane.setBackground(UIUtil.getPanelBackground());
        contentPane.setBorder(JBUI.Borders.empty(10, 12));
        contentPane.setContentType("text/html");

        JBScrollPane scrollPane = new JBScrollPane(contentPane);
        scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border()));
        scrollPane.setPreferredSize(new Dimension(620, 480));

        // 顶部信息条：日期 + 城市，让用户知道这份指引是基于哪天的数据算的
        JBLabel header = new JBLabel("📅 " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy年M月d日")) + " · " + GaodeSettings.getInstance().getCityName());
        header.setFont(header.getFont().deriveFont(Font.PLAIN, 12f));
        header.setForeground(JBColor.GRAY);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(JBColor.GRAY);

        regenerateButton.setFont(regenerateButton.getFont().deriveFont(Font.PLAIN, 12f));
        regenerateButton.setPreferredSize(new Dimension(110, 30));
        regenerateButton.addActionListener(e -> load(true));

        copyButton.addActionListener(e -> copyToClipboard());
        copyButton.setEnabled(false); // 没有内容前不可复制
        closeButton.addActionListener(e -> doCancelAction());

        JPanel bottomBar = new JBPanel<>(new BorderLayout());
        bottomBar.setBorder(JBUI.Borders.emptyTop(10));

        JPanel leftInfo = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 0, 6));
        leftInfo.setOpaque(false);
        leftInfo.add(statusLabel);

        JPanel buttons = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(regenerateButton);
        buttons.add(copyButton);
        buttons.add(closeButton);

        bottomBar.add(leftInfo, BorderLayout.WEST);
        bottomBar.add(buttons, BorderLayout.EAST);

        JPanel root = new JBPanel<>(new BorderLayout(0, 8));
        root.setBorder(JBUI.Borders.empty(12, 14));
        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(bottomBar, BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(660, 580));

        return root;
    }

    @Override
    protected Action @NotNull [] createActions() {
        // 用自定义按钮条替代默认的 OK/Cancel，避免多出一排无意义的按钮
        return new Action[0];
    }

    @Override
    public void show() {
        super.show();
        load(false);
    }

    /**
     * 加载内容。EDT 调用。
     *
     * @param forceRefresh true 表示用户主动要求重新生成（忽略当天缓存）
     */
    private void load(boolean forceRefresh) {
        if (inFlight != null && !inFlight.isDone()) {
            return; // 上一次还没回来，忽略重复点击
        }

        if (!forceRefresh) {
            String cache = DailyFortuneContent.currentCache();
            if (!cache.isBlank()) {
                renderMarkdown(cache, "今日已有结果（本地缓存）");
                return;
            }
        }

        markLoading();
        inFlight = AppExecutorUtil.getAppExecutorService().submit(() -> {
            DailyFortuneContent.Fortune fortune = DailyFortuneContent.generate(forceRefresh);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!isShowing()) {
                    return; // 请求飞行期间用户已关窗
                }
                if (fortune.isOk()) {
                    renderMarkdown(fortune.markdown(), fortune.cached()
                            ? "今日已有结果（本地缓存）"
                            : "生成于 " + LocalDateTime.now().format(STAMP_FMT));
                } else {
                    renderError(fortune.error());
                }
            });
        });
    }

    /** 生成中：按钮置灰 + 占位文案，需在 EDT 调用。 */
    private void markLoading() {
        regenerateButton.setEnabled(false);
        // 渐变按钮自绘背景，置灰在视觉上不明显，用文案变化传达状态
        regenerateButton.setText("生成中…");
        copyButton.setEnabled(false);
        statusLabel.setText("正在结合今日天气与日程推演…");
        contentPane.setText(MarkdownContent.toHtml("""
                ## 正在推演…

                正在读取今日天气、未来几天预报与日程提醒，请稍候。
                首次生成通常需要几秒到十几秒。
                """));
        contentPane.setCaretPosition(0);
    }

    /** 成功态渲染，需在 EDT 调用。 */
    private void renderMarkdown(String markdown, String status) {
        lastMarkdown = markdown;
        contentPane.setText(MarkdownContent.toHtml(markdown));
        contentPane.setCaretPosition(0);
        statusLabel.setText(status);
        regenerateButton.setEnabled(true);
        regenerateButton.setText("🔄 重新生成");
        copyButton.setEnabled(true);
    }

    /** 失败态：把可读原因直接给出，并保留重试入口。 */
    private void renderError(String error) {
        contentPane.setText(MarkdownContent.toHtml("""
                ## 没能拿到结果

                """ + (StringUtil.isEmpty(error) ? "未知错误，请稍后重试。" : error) + """


                常见原因：未配置 API Key、Key 无效、账户余额不足，或网络不通。
                可到 设置 → 工具箱 DeepSeek 检查配置，然后点「重新生成」。
                """));
        contentPane.setCaretPosition(0);
        statusLabel.setText("生成失败");
        regenerateButton.setEnabled(true);
        copyButton.setEnabled(!lastMarkdown.isBlank());
    }

    private void copyToClipboard() {
        if (!lastMarkdown.isBlank()) {
            CopyPasteManager.getInstance().setContents(new StringSelection(lastMarkdown));
            statusLabel.setText("已复制 Markdown 到剪贴板");
        }
    }

    @Override
    protected void dispose() {
        Future<?> running = inFlight;
        if (running != null) {
            running.cancel(true);
        }
        super.dispose();
    }

    /** 让 JEditorPane 在滚动面板里按宽度换行，而不是撑出横向滚动条。 */
    private static class HtmlPane extends JEditorPane {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    }
}
