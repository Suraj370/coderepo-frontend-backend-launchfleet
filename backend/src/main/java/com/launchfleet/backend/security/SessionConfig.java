package com.launchfleet.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Backs Spring Security's cookie + HttpSession dashboard auth with MongoDB via
 * spring-session-data-mongodb. That library only ships a pre-GA milestone for this
 * Spring generation and has no Boot autoconfiguration module yet (unlike its Boot 3.x
 * counterpart), so the session-repository filter is registered explicitly here rather
 * than relying on Boot to discover and order it - pinned to Ordered.HIGHEST_PRECEDENCE
 * so it runs before Spring Security's own filter chain (registered at order -100 by
 * SecurityFilterAutoConfiguration), ensuring HttpSessionSecurityContextRepository reads
 * and writes through the Mongo-backed session rather than Tomcat's in-memory one.
 *
 * Session timeout and cookie flags are set explicitly below rather than left to
 * library defaults, even where the default happens to already be what we want.
 */
@Configuration
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = SessionConfig.MAX_INACTIVE_INTERVAL_SECONDS)
public class SessionConfig {

	static final int MAX_INACTIVE_INTERVAL_SECONDS = 1800;

	@Bean
	public FilterRegistrationBean<SessionRepositoryFilter<?>> sessionRepositoryFilterRegistration(
			SessionRepositoryFilter<?> sessionRepositoryFilter) {
		FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(
				sessionRepositoryFilter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		registration.addUrlPatterns("/*");

		return registration;
	}

	/**
	 * Spring Session's SpringHttpSessionConfiguration autowires a CookieSerializer bean
	 * if one is present (optional dependency), so defining this is enough to take effect -
	 * no extra wiring needed. HttpOnly and SameSite=Lax already matched the library
	 * default (confirmed via a real response), stated explicitly here anyway; Secure was
	 * not previously set at all, a real gap for a "sent only over HTTPS" cookie. It's
	 * driven by a property (default true) rather than hardcoded so a from-scratch local
	 * HTTP-only setup can flip it without a code change - production ships secure by default.
	 */
	@Bean
	public CookieSerializer cookieSerializer(
			@Value("${app.security.cookie.secure:true}") boolean secureCookie) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SecurityConstants.SESSION_COOKIE_NAME);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secureCookie);

		return serializer;
	}
}
