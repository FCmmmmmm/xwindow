package org.plugin.window.rpc.deepseek;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * DeepSeek 余额查询接口（GET /user/balance）的响应模型。
 */
public class Balance {

    @SerializedName("is_available")
    private boolean isAvailable;

    @SerializedName("balance_infos")
    private List<BalanceInfo> balanceInfos;

    public boolean isAvailable() {
        return isAvailable;
    }

    public List<BalanceInfo> getBalanceInfos() {
        return balanceInfos;
    }

    public static Balance fromJson(String json) {
        return new Gson().fromJson(json, Balance.class);
    }

    /**
     * 取出指定币种的余额信息；找不到该币种时退回第一条，全空则返回 null。
     */
    public BalanceInfo getPreferred(String currency) {
        if (balanceInfos == null || balanceInfos.isEmpty()) {
            return null;
        }
        if (currency != null && !currency.isBlank()) {
            for (BalanceInfo info : balanceInfos) {
                if (currency.equalsIgnoreCase(info.getCurrency())) {
                    return info;
                }
            }
        }
        return balanceInfos.get(0);
    }

    /**
     * 单一币种下的余额构成。金额均为字符串，交由调用方用 BigDecimal 运算以避免精度误差。
     */
    public static class BalanceInfo {
        private String currency;

        @SerializedName("total_balance")
        private String totalBalance;

        @SerializedName("granted_balance")
        private String grantedBalance;

        @SerializedName("topped_up_balance")
        private String toppedUpBalance;

        public String getCurrency() {
            return currency;
        }

        public String getTotalBalance() {
            return totalBalance;
        }

        public String getGrantedBalance() {
            return grantedBalance;
        }

        public String getToppedUpBalance() {
            return toppedUpBalance;
        }
    }
}
