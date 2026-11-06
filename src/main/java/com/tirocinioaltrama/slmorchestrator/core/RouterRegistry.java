package com.tirocinioaltrama.slmorchestrator.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry estendibile per registrare e risolvere i Router disponibili nell'applicazione.
 * Supporta sia tipi strutturati ({@link RouterType}) sia nomi personalizzati per implementazioni locali o custom.
 */
public class RouterRegistry {

    private final Map<RouterType, Router> routers = new ConcurrentHashMap<>();
    private final Map<String, Router> namedRouters = new ConcurrentHashMap<>();
    private volatile RouterType defaultRouterType = RouterType.LOCAL_OLLAMA;
    private volatile String defaultRouterName = "local-ollama";

    public RouterRegistry() {}

    public RouterRegistry(Map<RouterType, Router> initialRouters, RouterType defaultRouterType) {
        if (initialRouters != null) {
            for (Map.Entry<RouterType, Router> entry : initialRouters.entrySet()) {
                register(entry.getKey(), entry.getValue());
            }
        }
        if (defaultRouterType != null) {
            this.defaultRouterType = defaultRouterType;
            this.defaultRouterName = defaultRouterType.getKey();
        }
    }

    public void register(Router router) {
        if (router != null) {
            register(router.getName(), router);
        }
    }

    public void register(RouterType type, Router router) {
        if (type != null && router != null) {
            routers.put(type, router);
            namedRouters.put(type.getKey().toLowerCase(Locale.ROOT), router);
            namedRouters.put(type.name().toLowerCase(Locale.ROOT), router);
        }
    }

    public void register(String name, Router router) {
        if (name != null && !name.isBlank() && router != null) {
            String clean = name.strip().toLowerCase(Locale.ROOT);
            namedRouters.put(clean, router);
            RouterType type = RouterType.fromString(clean);
            if (type != null) {
                routers.put(type, router);
            }
        }
    }

    public Optional<Router> get(RouterType type) {
        if (type == null) {
            return Optional.empty();
        }
        Router router = routers.get(type);
        if (router != null) {
            return Optional.of(router);
        }
        return Optional.ofNullable(namedRouters.get(type.getKey().toLowerCase(Locale.ROOT)));
    }

    public Optional<Router> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String clean = name.strip().toLowerCase(Locale.ROOT);
        Router router = namedRouters.get(clean);
        if (router != null) {
            return Optional.of(router);
        }
        RouterType type = RouterType.fromString(clean);
        if (type != null) {
            return Optional.ofNullable(routers.get(type));
        }
        return Optional.empty();
    }

    public boolean contains(String name) {
        return get(name).isPresent();
    }

    public boolean contains(RouterType type) {
        return get(type).isPresent();
    }

    public Router getDefaultRouter() {
        Router router = routers.get(defaultRouterType);
        if (router != null) {
            return router;
        }
        if (defaultRouterName != null) {
            router = namedRouters.get(defaultRouterName.toLowerCase(Locale.ROOT));
            if (router != null) {
                return router;
            }
        }
        // Fallback sul primo router disponibile
        return routers.values().stream().findFirst().orElseGet(() ->
                namedRouters.values().stream().findFirst().orElse(null)
        );
    }

    public Router resolve(RouterType requestedType) {
        if (requestedType != null) {
            Router router = routers.get(requestedType);
            if (router != null) {
                return router;
            }
            router = namedRouters.get(requestedType.getKey().toLowerCase(Locale.ROOT));
            if (router != null) {
                return router;
            }
        }
        return getDefaultRouter();
    }

    public Router resolve(String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            Router router = get(requestedName).orElse(null);
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
            this.defaultRouterName = defaultRouterType.getKey();
        }
    }

    public String getDefaultRouterName() {
        return defaultRouterName;
    }

    public void setDefaultRouterName(String defaultRouterName) {
        if (defaultRouterName != null && !defaultRouterName.isBlank()) {
            this.defaultRouterName = defaultRouterName.strip().toLowerCase(Locale.ROOT);
            RouterType type = RouterType.fromString(this.defaultRouterName);
            if (type != null) {
                this.defaultRouterType = type;
            }
        }
    }

    public Collection<String> getRegisteredNames() {
        Set<String> names = new LinkedHashSet<>(namedRouters.keySet());
        for (RouterType t : routers.keySet()) {
            names.add(t.getKey());
        }
        return Collections.unmodifiableSet(names);
    }

    public Map<RouterType, Router> getAll() {
        return Map.copyOf(routers);
    }

    public Map<String, Router> getAllByName() {
        return Map.copyOf(namedRouters);
    }
}
