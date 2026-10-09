package com.zcshou.gogogo;

/**
 * A small, deliberately privacy-minimal and read-only device readiness report.
 * It never consumes or displays coordinates, IP, Wi-Fi, device identifiers,
 * provider history, third-party app data, or a Shizuku permission token.
 *
 * This is a prerequisite check, NOT verification that a location consumer
 * accepted a mock fix or that provider teardown succeeded.
 */
public final class LabQuickCheckReport {
    public enum Signal { YES, NO, UNKNOWN }

    public static final class Snapshot {
        public final int apiLevel;
        public final Signal systemLocation;
        public final Signal finePermission;
        public final Signal coarsePermission;
        public final Signal mockAppOp;
        public final Signal shizukuBinder;
        public final boolean localServiceFlag;

        public Snapshot(int apiLevel, Signal systemLocation,
                        Signal finePermission, Signal coarsePermission,
                        Signal mockAppOp, Signal shizukuBinder,
                        boolean localServiceFlag) {
            this.apiLevel = apiLevel;
            this.systemLocation = safe(systemLocation);
            this.finePermission = safe(finePermission);
            this.coarsePermission = safe(coarsePermission);
            this.mockAppOp = safe(mockAppOp);
            this.shizukuBinder = safe(shizukuBinder);
            this.localServiceFlag = localServiceFlag;
        }
    }

    private LabQuickCheckReport() {}

    private static Signal safe(Signal signal) {
        return signal == null ? Signal.UNKNOWN : signal;
    }

    private static String yesNoUnknown(Signal signal) {
        switch (signal) {
            case YES: return "是";
            case NO: return "否";
            default: return "未知";
        }
    }

    public static String render(Snapshot s) {
        if (s == null) return "暂时无法生成基础诊断摘要";
        String permission = "读取失败";
        if (s.finePermission == Signal.YES) {
            permission = "精确定位已授权";
        } else if (s.coarsePermission == Signal.YES) {
            permission = "只有近似定位权限";
        } else if (s.finePermission == Signal.NO && s.coarsePermission == Signal.NO) {
            permission = "未授予定位权限";
        }

        StringBuilder out = new StringBuilder();
        out.append("GoGoGo Lab 31 · 基础快检（只读）\n");
        out.append("Android API: ").append(s.apiLevel).append('\n');
        out.append("系统定位开关：").append(yesNoUnknown(s.systemLocation)).append('\n');
        out.append("本应用定位权限：").append(permission).append('\n');
        out.append("模拟位置 AppOps 授权：").append(
                s.mockAppOp == Signal.YES ? "ALLOWED" :
                s.mockAppOp == Signal.NO ? "未授权" : "无法确认").append('\n');
        out.append("本进程服务运行标记：").append(
                s.localServiceFlag ? "RUNNING" : "STOPPED").append('\n');
        out.append("Shizuku Binder（可选）：").append(
                s.shizukuBinder == Signal.YES ? "已连接" :
                s.shizukuBinder == Signal.NO ? "未连接" : "读取失败").append("\n\n");

        out.append("下一步：");
        if (s.systemLocation == Signal.NO) {
            out.append("先在系统设置里打开定位，再重新检测。");
        } else if (s.finePermission == Signal.NO) {
            out.append("先返回首页检查定位权限；快检不会主动请求权限。");
        } else if (s.mockAppOp == Signal.NO) {
            out.append("检查开发者选项中的「选择模拟位置信息应用」。");
        } else if (s.systemLocation == Signal.UNKNOWN
                || s.finePermission == Signal.UNKNOWN
                || s.mockAppOp == Signal.UNKNOWN) {
            out.append("有基础状态无法确认，请到实验仪表盘查看详细诊断。");
        } else if (s.localServiceFlag) {
            out.append("可打开 Consumer Matrix 验证各定位消费者实际获得的值。");
        } else {
            out.append("基础条件具备；如需测试，请回到首页手动启动模拟，再检查 Consumer Matrix。");
        }
        out.append("\n\n");
        out.append("说明：RUNNING 只是当前进程的标记；AppOps ALLOWED 仅表示应用获准使用模拟位置。");
        out.append("这里不验证第三方应用、微信、GMS 缓存或 Provider 清理。");
        out.append("本摘要不包含坐标、IP、Wi-Fi、设备序列号或原始日志。");
        return out.toString();
    }
}
