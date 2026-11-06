package org.plugin.window.content;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型返回的 Markdown 子集渲染成 {@link javax.swing.JEditorPane} 能吃的 HTML。
 * <p>
 * 只支持提示词里约定过的语法：{@code ##}/{@code ###} 标题、{@code -} 列表、{@code 1.} 有序列表、
 * {@code **加粗**}、{@code *斜体*}、{@code `代码`}、{@code ---} 分隔线。
 * 无第三方 Markdown 依赖（项目只引入了 cron-utils），也不打算引入——需求面窄，自己转更可控。
 * <p>
 * 颜色在渲染时按当前主题取（{@link JBColor#isBright()}），所以切换亮/暗主题后重新渲染即可换肤。
 */
public final class MarkdownContent {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern ORDERED_ITEM = Pattern.compile("^\\d+[.)]\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^([-*_])\\1{2,}$");

    private MarkdownContent() {
    }

    /** 渲染成完整 HTML 文档（含内联样式）。 */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return wrapBody("");
        }
        StringBuilder body = new StringBuilder();
        String openList = null; // 当前打开的列表标签："ul" / "ol" / null
        StringBuilder paragraph = new StringBuilder();

        for (String rawLine : markdown.split("\r?\n", -1)) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                openList = closeList(body, openList);
                paragraph = flushParagraph(body, paragraph);
                continue;
            }
            if (RULE.matcher(line).matches()) {
                openList = closeList(body, openList);
                paragraph = flushParagraph(body, paragraph);
                body.append("<hr/>");
                continue;
            }
            if (line.startsWith("### ")) {
                openList = closeList(body, openList);
                paragraph = flushParagraph(body, paragraph);
                body.append("<h3>").append(inline(line.substring(4))).append("</h3>");
                continue;
            }
            if (line.startsWith("## ") || line.startsWith("# ")) {
                openList = closeList(body, openList);
                paragraph = flushParagraph(body, paragraph);
                // 一级标题也降成 h2：弹窗里 h1 太大，视觉上会压过内容
                int offset = line.startsWith("## ") ? 3 : 2;
                body.append("<h2>").append(inline(line.substring(offset))).append("</h2>");
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                paragraph = flushParagraph(body, paragraph);
                openList = ensureList(body, openList, "ul");
                body.append("<li>").append(inline(line.substring(2))).append("</li>");
                continue;
            }
            Matcher ordered = ORDERED_ITEM.matcher(line);
            if (ordered.matches()) {
                paragraph = flushParagraph(body, paragraph);
                openList = ensureList(body, openList, "ol");
                body.append("<li>").append(inline(ordered.group(1))).append("</li>");
                continue;
            }

            // 普通文本行：连续多行合并进同一段落，用 <br/> 保持模型本来的换行意图
            openList = closeList(body, openList);
            if (paragraph.length() > 0) {
                paragraph.append("<br/>");
            }
            paragraph.append(inline(line));
        }

        closeList(body, openList);
        flushParagraph(body, paragraph);
        return wrapBody(body.toString());
    }

    /** 行内语法：先转义再做替换，顺序不能颠，否则用户文本里的 &lt; 会被二次解释。 */
    private static String inline(String text) {
        String escaped = escape(text);
        escaped = replaceAll(CODE, escaped, "<code>$1</code>");
        escaped = replaceAll(BOLD, escaped, "<b>$1</b>");
        escaped = replaceAll(ITALIC, escaped, "<i>$1</i>");
        return escaped;
    }

    private static String replaceAll(Pattern pattern, String input, String replacement) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String ensureList(StringBuilder body, String openList, String tag) {
        if (tag.equals(openList)) {
            return openList;
        }
        String closed = closeList(body, openList);
        body.append('<').append(tag).append('>');
        return tag;
    }

    private static String closeList(StringBuilder body, String openList) {
        if (openList != null) {
            body.append("</").append(openList).append('>');
        }
        return null;
    }

    private static StringBuilder flushParagraph(StringBuilder body, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            body.append("<p>").append(paragraph).append("</p>");
        }
        return new StringBuilder();
    }

    /** 拼上主题相关的 CSS，字体跟随 IDE 标签字体，保证与工具窗口观感一致。 */
    private static String wrapBody(String body) {
        boolean bright = JBColor.isBright();
        String fg = hex(JBColor.foreground());
        String muted = hex(new JBColor(new Color(0x8A8F98), new Color(0x9AA0AA)));
        String accent = bright ? "#5B4BE0" : "#A18BFF";
        String cardBg = bright ? "#FFFFFF" : "#2B2E35";
        String codeBg = bright ? "#F2F3F5" : "#3A3E46";
        String rule = bright ? "#E2E4E8" : "#454A53";
        String family = resolveFontFamily();

        return "<html><head><style>"
                + "body { font-family: '" + family + "'; font-size: 13px; color: " + fg
                + "; background: " + cardBg + "; margin: 4px 6px; line-height: 1.6; }"
                + "h2 { font-size: 15px; color: " + accent + "; margin: 16px 0 8px 0; }"
                + "h2:first-child { margin-top: 2px; }"
                + "h3 { font-size: 13px; color: " + fg + "; margin: 12px 0 6px 0; }"
                + "p { margin: 4px 0; }"
                + "ul, ol { margin: 4px 0 4px 18px; padding: 0; }"
                + "li { margin: 2px 0; }"
                + "b { color: " + fg + "; }"
                + "i { color: " + muted + "; }"
                + "code { background: " + codeBg + "; padding: 1px 4px; }"
                + "hr { border: 0; border-top: 1px solid " + rule + "; margin: 10px 0; }"
                + "</style></head><body>" + body + "</body></html>";
    }

    /** 标签字体优先；取不到时退回 macOS 上表现稳定的中文字体栈。 */
    private static String resolveFontFamily() {
        var font = UIUtil.getLabelFont();
        if (font != null && font.getFamily() != null && !font.getFamily().isBlank()) {
            return font.getFamily();
        }
        return "PingFang SC";
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
