package com.zcshou.gogogo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.List;

/**
 * One-time, passive comparison of network routes + SIM registration state.
 * Does not request CellInfo, SIM identifiers, subscriber number or any Location.
 * Network capabilities and modem registration are DIFFERENT evidence layers.
 */
public final class LabCellularEthernetSnapshot {
    private LabCellularEthernetSnapshot() {}

    public static String capture(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("source=ANDROID_CONNECTIVITY_TELEPHONY_READ_ONLY\n");
        out.append("cell_and_network_country_are_not_geo_coordinates=true\n");
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            out.append("default_network=UNKNOWN\n");
        } else {
            try {
                Network current = cm.getActiveNetwork();
                NetworkCapabilities caps = current == null ? null
                        : cm.getNetworkCapabilities(current);
                out.append("app_default_network=")
                        .append(transports(caps)).append("\n");
                int cell = 0, wifi = 0, ethernet = 0, vpn = 0;
                for (Network network : cm.getAllNetworks()) {
                    NetworkCapabilities n = cm.getNetworkCapabilities(network);
                    if (n == null) continue;
                    if (n.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cell++;
                    if (n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi++;
                    if (n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet++;
                    if (n.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn++;
                }
                out.append("visible_network_count_by_transport=")
                        .append("CELLULAR:").append(cell)
                        .append(",WIFI:").append(wifi)
                        .append(",ETHERNET:").append(ethernet)
                        .append(",VPN:").append(vpn)
                        .append(" (counts not guaranteed to include every bearer)\n");
                out.append("vpn_underlay=UNKNOWN; other_app_routes=UNKNOWN\n");
            } catch (SecurityException denied) {
                out.append("network_transports=PERMISSION_DENIED\n");
            } catch (RuntimeException unavailable) {
                out.append("network_transports=UNAVAILABLE_")
                        .append(unavailable.getClass().getSimpleName()).append("\n");
            }
        }

        TelephonyManager tm = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            out.append("telephony=UNAVAILABLE\n");
            return out.toString();
        }
        String simIso = safe(() -> tm.getSimCountryIso());
        String simCode = safe(() -> tm.getSimOperator());
        out.append("default_sim_iso=").append(iso(simIso))
                .append(",sim_mccmnc=").append(code(simCode))
                .append(",sim_operator=").append(label(safe(() -> tm.getSimOperatorName())))
                .append("\n");
        out.append("default_sim_consistency=")
                .append(LabCarrierConsistency.check(simIso, simCode))
                .append(" (not proof of tampering or actual position)\n");
        out.append("registered_network_iso=")
                .append(iso(safe(() -> tm.getNetworkCountryIso())))
                .append(",mccmnc=").append(code(safe(() -> tm.getNetworkOperator())))
                .append(",operator=").append(label(safe(() -> tm.getNetworkOperatorName())))
                .append("\n");
        out.append("cellular_data_state=").append(dataState(tm))
                .append(",mobile_data_switch=").append(mobileDataSwitch(context, tm))
                .append(",registered_roaming=").append(bool(() -> tm.isNetworkRoaming()))
                .append("\n");
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        out.append("wifi_radio_switch=")
                .append(wifi == null ? "UNKNOWN" : bool(() -> wifi.isWifiEnabled()))
                .append(",airplane_mode=")
                .append(Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, -1))
                .append(" (-1 means unknown)\n");
        out.append("mobile_data_disabled_does_not_disable_radio_registration=true\n");

        int dataSub = SubscriptionManager.getDefaultDataSubscriptionId();
        int voiceSub = SubscriptionManager.getDefaultVoiceSubscriptionId();
        int generalSub = SubscriptionManager.getDefaultSubscriptionId();
        out.append("default_data_subscription=")
                .append(dataSub == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        ? "UNKNOWN" : "AVAILABLE")
                .append(",default_voice_subscription=")
                .append(voiceSub == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        ? "UNKNOWN" : "AVAILABLE")
                .append(",default_general_subscription=")
                .append(generalSub == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        ? "UNKNOWN" : "AVAILABLE")
                .append("\n");
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            out.append("per_slot_details=SKIPPED_READ_PHONE_STATE_NOT_GRANTED\n");
            return out.toString();
        }
        try {
            SubscriptionManager sm = (SubscriptionManager)
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> subs = sm == null ? null
                    : sm.getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) {
                out.append("per_slot_details=NONE_OR_NOT_VISIBLE\n");
            } else {
                int n = 0;
                for (SubscriptionInfo info : subs) {
                    if (n++ >= 4) { out.append("per_slot_details=TRUNCATED\n"); break; }
                    int subId = info.getSubscriptionId();
                    TelephonyManager own = tm.createForSubscriptionId(subId);
                    String ownIso = safe(() -> own.getSimCountryIso());
                    String ownMcc = safe(() -> own.getSimOperator());
                    String regIso = safe(() -> own.getNetworkCountryIso());
                    out.append("slot_").append(info.getSimSlotIndex() >= 0
                                    ? (info.getSimSlotIndex() + 1) : "UNKNOWN")
                            .append(":default_data=").append(subId == dataSub)
                            .append(",default_voice=").append(subId == voiceSub)
                            .append(",sim_iso=").append(iso(ownIso))
                            .append(",sim_mccmnc=").append(code(ownMcc))
                            .append(",sim_check=")
                            .append(LabCarrierConsistency.check(ownIso, ownMcc))
                            .append(",registered_iso=").append(iso(regIso))
                            .append(",registered_mccmnc=")
                            .append(code(safe(() -> own.getNetworkOperator())))
                            .append(",roaming=").append(bool(() -> own.isNetworkRoaming()))
                            .append("\n");
                }
            }
        } catch (SecurityException denied) {
            out.append("per_slot_details=PERMISSION_DENIED\n");
        } catch (RuntimeException unavailable) {
            out.append("per_slot_details=UNAVAILABLE_")
                    .append(unavailable.getClass().getSimpleName()).append("\n");
        }
        return out.toString();
    }

    private static String transports(NetworkCapabilities caps) {
        if (caps == null) return "UNKNOWN_OR_NO_DEFAULT";
        StringBuilder out = new StringBuilder();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) out.append("VPN+");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) out.append("ETHERNET+");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) out.append("WIFI+");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) out.append("CELLULAR+");
        if (out.length() == 0) return "OTHER_OR_UNKNOWN";
        return out.substring(0, out.length() - 1);
    }

    private static String mobileDataSwitch(Context context, TelephonyManager tm) {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return "UNKNOWN_READ_PHONE_STATE_NOT_GRANTED";
        }
        try {
            return Boolean.toString(tm.isDataEnabled());
        } catch (SecurityException denied) {
            return "UNKNOWN_PERMISSION_DENIED";
        } catch (RuntimeException unavailable) {
            return "UNKNOWN_UNAVAILABLE";
        }
    }

    private static String dataState(TelephonyManager tm) {
        try {
            switch (tm.getDataState()) {
                case TelephonyManager.DATA_CONNECTED: return "CONNECTED";
                case TelephonyManager.DATA_CONNECTING: return "CONNECTING";
                case TelephonyManager.DATA_DISCONNECTED: return "DISCONNECTED";
                case TelephonyManager.DATA_SUSPENDED: return "SUSPENDED";
                default: return "UNKNOWN";
            }
        } catch (RuntimeException unavailable) { return "UNAVAILABLE"; }
    }

    private interface Reader { String read(); }
    private interface BoolReader { boolean read(); }

    private static String safe(Reader read) {
        try {
            String value = read.read();
            return value == null ? "" : value;
        } catch (RuntimeException unavailable) { return ""; }
    }

    private static String bool(BoolReader read) {
        try { return Boolean.toString(read.read()); }
        catch (RuntimeException unavailable) { return "UNKNOWN"; }
    }

    private static String iso(String input) {
        return input != null && input.matches("[A-Za-z]{2}")
                ? input.toUpperCase(java.util.Locale.ROOT) : "UNKNOWN";
    }

    private static String code(String input) {
        return input != null && input.matches("[0-9]{5,6}") ? input : "UNKNOWN";
    }

    private static String label(String input) {
        if (input == null || input.trim().isEmpty()) return "UNKNOWN";
        // Sanitize any unexpected characters in OEM-supplied operator labels.
        String value = input.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("[^\\p{L}\\p{N} _+.-]", "");
        return value.length() <= 65 ? value : value.substring(0, 65);
    }
}
