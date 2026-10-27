package com.fischer.heimdall.ui;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * The dashboard is served as static files from META-INF/resources/heimdall/, which
 * only answer at "/heimdall/" — redirect the bare "/heimdall" there instead of a 404.
 */
@ApplicationScoped
public class DashboardRedirect {

    void register(@Observes Router router) {
        // regex, because a plain "/heimdall" route would also match "/heimdall/" and loop
        router.routeWithRegex("^/heimdall$").handler(rc -> rc.redirect("/heimdall/"));
    }
}
