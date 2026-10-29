package com.tirocinioaltrama.llmorchestrator.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry per registrare e risolvere i Router disponibili nell'applicazione.
 */
public class RouterRegistry {

    private final Map<RouterType, Router> routers = new ConcurrentHashMap<>();
    private volatile RouterType defaultRouterType = RouterType.GEMINI;

    public RouterRegistry() {}

    public RouterRegistry(Map<RouterType, Router> initialRouters, RouterType defaultRouterType) {
        if (initialRouters != null) {
            this.routers.putAll(initialRouters);
        }
        if (defaultRouterType != null) {
            this.defaultRouterType = defaultRouterType;
        }
    }

    public void register(RouterType type, Router router) {
        if (type != null && router != null) {
            routers.put(type, router);
        }
    }

    public Optional<Router> get(RouterType type) {
        return Optional.ofNullable(routers.get(type));
    }

    public Router getDefaultRouter() {
        Router router = routers.get(defaultRouterType);
        if (router != null) {
            return router;
        }
        // Fallback sul primo router disponibile
        return routers.values().stream().findFirst().orElse(null);
    }

    public Router resolve(RouterType requestedType) {
        if (requestedType != null) {
            Router router = routers.get(requestedType);
            if (router != null) {
                return router;
            }
        }
        return getDefaultRouter();
    }

    public RouterType getDefaultRouterType() {
        return defaultRouterType;
    }

    public void setDefaultRouterType(RouterType defaultRouterType) {
        if (defaultRouterType != null) {
            this.defaultRouterType = defaultRouterType;
        }
    }

    public Map<RouterType, Router> getAll() {
        return Map.copyOf(routers);
    }
}
