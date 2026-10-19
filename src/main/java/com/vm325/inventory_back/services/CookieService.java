package com.vm325.inventory_back.services;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    // SameSite=Lax: en producción el frontend llega por el proxy de Netlify
    // (mismo origen) y en desarrollo localhost:5173 -> localhost:8080 es el
    // mismo "sitio", así que la cookie siempre es de primera parte. Lax
    // además impide que otros sitios la envíen en peticiones cross-site,
    // que importa porque CSRF está deshabilitado en SecurityConfig.
    public void addHttpOnlyCookie(String name, String value, int maxAge, HttpServletResponse response){
        String cookieValue = String.format("%s=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=Lax",
                name, value, maxAge);

        response.addHeader("Set-Cookie", cookieValue);
    }

    public void deleteCookie(String name, HttpServletResponse response){
        String cookieValue = String.format("%s=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax", name);
        response.addHeader("Set-Cookie", cookieValue);
    }
}
