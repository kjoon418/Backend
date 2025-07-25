package goodspace.backend.admin.service;

import goodspace.backend.user.domain.GoodSpaceUser;
import goodspace.backend.user.repository.UserRepository;
import goodspace.backend.global.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInitializeService {
    private final UserRepository userRepository;

    private final String email;
    private final String password;

    public AdminInitializeService(
            UserRepository userRepository,
            @Value("${admin.email:default@adminEmail.com}") String email,
            @Value("${admin.password:defaultAdminPassword}") String password
    ) {
        this.userRepository = userRepository;
        this.email = email;
        this.password = password;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        GoodSpaceUser user = GoodSpaceUser.builder()
                .email(email)
                .password(password)
                .build();
        user.addRole(Role.USER);
        user.addRole(Role.ADMIN);

        saveIfNotExist(user);
    }

    private void saveIfNotExist(GoodSpaceUser user) {
        userRepository.findByEmailAndPassword(user.getEmail(), user.getPassword())
                .orElseGet(() -> userRepository.save(user));
    }
}
