package com.vm325.inventory_back.config;

import com.vm325.inventory_back.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * La migración V2 siembra el usuario "admin" con una contraseña pública (está
 * en el repositorio). Como una migración ya aplicada no se puede editar, se
 * avisa al arrancar mientras siga igual y, si se activa
 * SECURITY_FAIL_ON_DEFAULT_ADMIN_PASSWORD=true (recomendado en producción),
 * se impide el arranque hasta cambiarla.
 */
@Component
public class DefaultAdminPasswordCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminPasswordCheck.class);
    private static final String SEEDED_USERNAME = "admin";
    private static final String SEEDED_PASSWORD = "rocaeladmin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.fail-on-default-admin-password:false}")
    private boolean failOnDefault;

    public DefaultAdminPasswordCheck(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findByUserName(SEEDED_USERNAME)
                .filter(admin -> passwordEncoder.matches(SEEDED_PASSWORD, admin.getPassword()))
                .ifPresent(admin -> {
                    String message = "El usuario 'admin' conserva la contraseña sembrada por defecto (pública en el "
                            + "repositorio). Cámbiela en Perfil > Seguridad o con 'olvidé mi contraseña'.";
                    if (failOnDefault) {
                        throw new IllegalStateException(message);
                    }
                    log.warn(message);
                });
    }
}
