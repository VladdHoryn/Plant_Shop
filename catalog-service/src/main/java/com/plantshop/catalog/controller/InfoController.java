package com.plantshop.catalog.controller;

import com.plantshop.catalog.dto.InfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Tells which instance answered the request: shows the image version, the pod (host) name and
 * values that come from the Kubernetes ConfigMap. Used to watch load balancing and rolling updates.
 */
@RestController
@RequestMapping("/api/catalog/info")
public class InfoController {

    private final String version;
    private final String shopName;
    private final String currency;

    public InfoController(@Value("${plantshop.version}") String version,
                          @Value("${plantshop.shop-name}") String shopName,
                          @Value("${plantshop.currency}") String currency) {
        this.version = version;
        this.shopName = shopName;
        this.currency = currency;
    }

    @GetMapping
    public InfoResponse info() {
        return new InfoResponse("catalog-service", version, hostname(), shopName, currency);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
