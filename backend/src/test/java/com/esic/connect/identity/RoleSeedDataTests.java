package com.esic.connect.identity;

import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Vérifie la migration V2 (rôles système de référence). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RoleSeedDataTests {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void sixSystemRolesExist() {
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).extracting(Role::getCode).containsExactlyInAnyOrder(RoleCode.values());
        assertThat(roles).allMatch(Role::isActive);
    }
}
