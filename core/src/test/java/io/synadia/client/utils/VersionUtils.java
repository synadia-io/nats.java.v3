package io.synadia.client.utils;

import io.synadia.client.api.ServerInfo;
import io.synadia.client.impl.NatsConnection;

public abstract class VersionUtils {
    public static ServerInfo VERSION_SERVER_INFO;

    public interface VersionCheck {
        boolean runTest(ServerInfo si);
    }

    public static void initVersionServerInfo(NatsConnection nc) {
        if (VERSION_SERVER_INFO == null) {
            VERSION_SERVER_INFO = nc.getServerInfo();
        }
    }

    public static boolean atLeast2_10() {
        return atLeast2_10(VERSION_SERVER_INFO);
    }

    public static boolean atLeast2_10(ServerInfo si) {
        return si.isNewerVersionThan("2.9.99");
    }

    public static boolean atLeast2_10_3(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.10.3");
    }

    public static boolean atLeast2_10_26(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.10.26");
    }

    public static boolean atLeast2_11(ServerInfo si) {
        return si.isNewerVersionThan("2.10.99");
    }

    public static boolean before2_11() {
        return before2_11(VERSION_SERVER_INFO);
    }

    public static boolean before2_11(ServerInfo si) {
        return si.isOlderThanVersion("2.11");
    }

    public static boolean atLeast2_12() {
        return atLeast2_12(VERSION_SERVER_INFO);
    }

    public static boolean atLeast2_12(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.11.99");
    }

    public static boolean atLeast2_14() {
        return atLeast2_14(VERSION_SERVER_INFO);
    }

    public static boolean atLeast2_14(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.13.99");
    }

    public static boolean atLeast2_15() {
        return atLeast2_15(VERSION_SERVER_INFO);
    }

    public static boolean atLeast2_15(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.14.99");
    }

    public static boolean atLeast2_16() {
        return atLeast2_16(VERSION_SERVER_INFO);
    }

    public static boolean atLeast2_16(ServerInfo si) {
        return si.isSameOrNewerThanVersion("2.15.99");
    }
}
