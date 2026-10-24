package com.vm325.inventory_back.services;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    // Lax basta cuando frontend y backend comparten sitio (proxy de Netlify o
    // localhost) y, con CSRF deshabilitado, evita envíos cross-site. Solo se
    // usa None si el frontend vive en otro sitio (COOKIE_SAME_SITE=None).
    @Value("${cookie.same-site:Lax}")
    private String sameSite;

    @Value("${cookie.secure:true}")
    private boolean secure;

    public void addHttpOnlyCookie(String name, String value, int maxAge, HttpServletResponse response){
        response.addHeader("Set-Cookie", build(name, value, maxAge));
    }

    public void deleteCookie(String name, HttpServletResponse response){
        response.addHeader("Set-Cookie", build(name, "", 0));
    }

    private String build(String name, String value, int maxAge) {
        return String.format("%s=%s; Max-Age=%d; Path=/;%s HttpOnly; SameSite=%s",
                name, value, maxAge, secure ? " Secure;" : "", sameSite);
    }
}
