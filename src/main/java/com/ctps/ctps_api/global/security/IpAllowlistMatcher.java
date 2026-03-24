package com.ctps.ctps_api.global.security;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

public class IpAllowlistMatcher {

    private final List<String> allowedEntries;

    public IpAllowlistMatcher(String allowedIps) {
        this.allowedEntries = Arrays.stream((allowedIps == null ? "" : allowedIps).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public boolean matches(String candidateIp) {
        if (!StringUtils.hasText(candidateIp)) {
            return false;
        }

        if (allowedEntries.isEmpty()) {
            return true;
        }

        return allowedEntries.stream().anyMatch(entry -> matchesEntry(candidateIp, entry));
    }

    private boolean matchesEntry(String candidateIp, String entry) {
        try {
            if (!entry.contains("/")) {
                return InetAddress.getByName(candidateIp).equals(InetAddress.getByName(entry));
            }

            String[] parts = entry.split("/", 2);
            InetAddress candidate = InetAddress.getByName(candidateIp);
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] candidateBytes = candidate.getAddress();
            byte[] networkBytes = network.getAddress();

            if (candidateBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int index = 0; index < fullBytes; index++) {
                if (candidateBytes[index] != networkBytes[index]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            return (candidateBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        } catch (Exception ignored) {
            return false;
        }
    }
}
