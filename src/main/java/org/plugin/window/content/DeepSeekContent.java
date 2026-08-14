package org.plugin.window.content;

import org.plugin.window.rpc.deepseek.Balance;
import org.plugin.window.rpc.deepseek.DeepSeekRestApi;
import org.plugin.window.settings.DeepSeekApiKeyStore;
import org.plugin.window.settings.DeepSeekSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 把 DeepSeek 余额接口的原始数据加工成卡片可直接显示的文案。
 * <p>
 * DeepSeek 没有官方用量接口，所以「今日已消耗」「较上次」由本地余额快照做差值推算：
 * 每次查询把余额与时间戳写回 {@link DeepSeekSettings}，下次查询再做减法。
 * 局限是只能覆盖插件运行期间的调用，且无法拆分到模型/请求维度。
 */
public class DeepSeekContent {

    /** 一次查询的展示结果。{@code error} 非空表示失败，此时余额字段无意义。 */
    public record Display(String balanceText, String usageText, String error) {

        public boolean isOk() {
            return error == null;
        }
    }

    /**
     * 查询余额并推算消耗。必须在非 EDT 线程调用。
     */
    public static Display getBalanceDisplay() {
        String apiKey = DeepSeekApiKeyStore.getKey();
        if (apiKey.isBlank()) {
            return new Display(null, null, "未配置 API Key，请在设置中填写");
        }

        DeepSeekSettings settings = DeepSeekSettings.getInstance();
        DeepSeekRestApi api = new DeepSeekRestApi();
        DeepSeekRestApi.Result result = api.Balance(apiKey);
        if (!result.isOk()) {
            return new Display(null, null, result.error());
        }

        Balance.BalanceInfo info = result.balance().getPreferred(settings.getPreferredCurrency());
        if (info == null) {
            return new Display(null, null, "账户无余额信息");
        }

        BigDecimal current = parseOrNull(info.getTotalBalance());
        if (current == null) {
            return new Display(null, null, "余额解析失败");
        }

        String symbol = currencySymbol(info.getCurrency());
        String balanceText = symbol + current.toPlainString();
        String usageText = buildUsageText(settings, current, symbol);

        // 余额不足时把状态直接摆到台面上，避免调用失败了还在盯着余额发呆
        if (!result.balance().isAvailable()) {
            usageText = "⚠️ 余额不足，API 不可用"
                    + (usageText == null ? "" : " · " + usageText);
        }

        saveSnapshot(settings, current);
        return new Display(balanceText, usageText, null);
    }

    /**
     * 依据历史快照生成「今日已消耗 · 较上次」文案，无可比数据时返回首次查询提示。
     */
    private static String buildUsageText(DeepSeekSettings settings, BigDecimal current, String symbol) {
        LocalDate today = LocalDate.now();
        BigDecimal baseline = parseOrNull(settings.getBaselineBalance());
        LocalDate baselineDate = parseDateOrNull(settings.getBaselineDate());

        String todayPart;
        if (baseline == null || baselineDate == null || !baselineDate.isEqual(today)) {
            // 首次查询或已跨天：以当前余额为今日新基准，消耗从 0 起算
            todayPart = "今日已消耗 " + symbol + "0.00";
        } else if (current.compareTo(baseline) > 0) {
            // 余额反而变多，说明期间充过值；显示充值额而不是负消耗，基准由 saveSnapshot 重置
            todayPart = "已充值 " + symbol + current.subtract(baseline).toPlainString();
        } else {
            todayPart = "今日已消耗 " + symbol + baseline.subtract(current).toPlainString();
        }

        BigDecimal last = parseOrNull(settings.getLastBalance());
        if (last == null) {
            return todayPart + " · 首次查询";
        }
        BigDecimal delta = last.subtract(current);
        String deltaPart;
        if (delta.signum() == 0) {
            deltaPart = "较上次无变化";
        } else if (delta.signum() > 0) {
            deltaPart = "较上次 -" + symbol + delta.toPlainString();
        } else {
            deltaPart = "较上次 +" + symbol + delta.negate().toPlainString();
        }
        return todayPart + " · " + deltaPart;
    }

    /**
     * 写回本次快照。跨天或检测到充值时同步重置今日基准。
     */
    private static void saveSnapshot(DeepSeekSettings settings, BigDecimal current) {
        LocalDate today = LocalDate.now();
        BigDecimal baseline = parseOrNull(settings.getBaselineBalance());
        LocalDate baselineDate = parseDateOrNull(settings.getBaselineDate());

        boolean resetBaseline = baseline == null
                || baselineDate == null
                || !baselineDate.isEqual(today)
                || current.compareTo(baseline) > 0;
        if (resetBaseline) {
            settings.setBaselineBalance(current.toPlainString());
            settings.setBaselineDate(today.toString());
        }

        settings.setLastBalance(current.toPlainString());
        settings.setLastCheckTime(LocalDateTime.now().toString());
    }

    private static String currencySymbol(String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return "$";
        }
        return "¥";
    }

    private static BigDecimal parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
