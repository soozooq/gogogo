package com.zcshou.gogogo;

/**
 * Privacy-minimal landing-page summary of Lab 31 prerequisites.
 * No coordinates, historical provider state, external app detection, or
 * automatic privileged action. This is not proof mock location was accepted.
 */
public final class LabHubReadiness {
    public enum Attention { NEED_ACTION, UNKNOWN, VERIFY_CONSUMERS }

    public static final class Summary {
        public final Attention attention;
        public final String headline;
        public final String detail;

        private Summary(Attention attention, String headline, String detail) {
            this.attention = attention;
            this.headline = headline;
            this.detail = detail;
        }
    }

    private LabHubReadiness() {}

    public static Summary summarize(LabQuickCheckReport.Snapshot snapshot) {
        if (snapshot == null) {
            return new Summary(Attention.UNKNOWN, "有些状态暂时读不到",
                    "暂时无法读取状态，也可以直接复制日志，无需先做实验。");
        }
        if (snapshot.systemLocation == LabQuickCheckReport.Signal.NO) {
            return new Summary(Attention.NEED_ACTION, "系统定位尚未开启",
                    "请到系统设置中打开定位；不会在此自动修改。");
        }
        if (snapshot.finePermission == LabQuickCheckReport.Signal.NO) {
            return new Summary(Attention.NEED_ACTION, "精确定位权限未授权",
                    "检查 GoGoGo 的应用定位权限；只有近似定位并不足以通过此项检查。");
        }
        if (snapshot.mockAppOp == LabQuickCheckReport.Signal.NO) {
            return new Summary(Attention.NEED_ACTION, "模拟位置应用尚未获授权",
                    "在开发者选项中检查模拟位置信息应用；本页不会自动申请。");
        }
        if (snapshot.systemLocation != LabQuickCheckReport.Signal.YES
                || snapshot.finePermission != LabQuickCheckReport.Signal.YES
                || snapshot.mockAppOp != LabQuickCheckReport.Signal.YES) {
            return new Summary(Attention.UNKNOWN, "有基础条件尚无法确认",
                    "部分权限状态暂时读不到；可以直接复制日志，无需逐项检查。");
        }
        if (snapshot.localServiceFlag) {
            return new Summary(Attention.VERIFY_CONSUMERS, "基础设置未发现明显问题",
                    "服务运行标记为运行中；遇到问题后直接复制日志。第三方应用的结果以实际使用为准。");
        }
        return new Summary(Attention.VERIFY_CONSUMERS, "基础设置未发现明显问题",
                "模拟服务未运行；如需模拟，请回首页选点并手动启动。仍可直接复制日志。");
    }
}
