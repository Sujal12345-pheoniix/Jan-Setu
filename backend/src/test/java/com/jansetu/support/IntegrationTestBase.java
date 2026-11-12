package com.jansetu.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jansetu.modules.access.service.RoleService;
import com.jansetu.modules.department.domain.Department;
import com.jansetu.modules.department.service.DepartmentService;
import com.jansetu.modules.identity.service.AuthService;
import com.jansetu.modules.identity.service.StaffMembershipService;
import com.jansetu.modules.identity.service.UserAdminService;
import com.jansetu.modules.identity.service.UserView;
import com.jansetu.security.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: full Spring context, MockMvc and a real PostgreSQL 16 started once per JVM by
 * Testcontainers (Flyway migrates it from scratch). Tests create their own uniquely named data, so they are
 * independent of execution order and of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmailConfig.class)
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Integration-Test-Pass-42";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected DepartmentService departmentService;
    @Autowired
    protected UserAdminService userAdminService;
    @Autowired
    protected StaffMembershipService staffMembershipService;
    @Autowired
    protected AuthService authService;
    @Autowired
    protected RoleService roleService;
    @Autowired
    protected TestEmailConfig.RecordingEmailDeliveryPort emails;

    /** Logged-in test user. */
    public record TestUser(UUID id, String email, String accessToken, Cookie refreshCookie) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected static String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected AuthenticatedUser system() {
        Set<String> all = roleService.listPermissions().stream().map(p -> p.getCode()).collect(Collectors.toSet());
        return AuthenticatedUser.system(all);
    }

    protected UUID createDepartment(String prefix) {
        String code = (prefix + "_" + uniq()).toUpperCase();
        return departmentService.create(new DepartmentService.CreateDepartmentCommand(code, "Dept " + code, null, null),
                system(), null, UUID.class, Department::getId, id -> id).value();
    }

    /** Creates an active staff user with the given roles and (optionally) department memberships, then logs in. */
    protected TestUser staff(String role, UUID... departments) {
        String email = role.toLowerCase() + "." + uniq() + "@example.test";
        UserView user = userAdminService.createStaff(new UserAdminService.CreateStaffCommand(email, "Test " + role, null,
                PASSWORD, Set.of(role)), system(), null, UserView.class, v -> v, UserView::id).value();
        for (UUID department : departments) {
            staffMembershipService.addMember(department, user.id(), null, system());
        }
        return login(email, PASSWORD);
    }

    protected TestUser citizen() {
        String email = "citizen." + uniq() + "@example.test";
        authService.register(new AuthService.RegisterCommand(email, PASSWORD, "Test Citizen", null));
        return login(email, PASSWORD);
    }

    protected TestUser login(String email, String password) {
        try {
            MvcResult result = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            String token = body.at("/data/accessToken").asText();
            Cookie cookie = result.getResponse().getCookie("jansetu_rt");
            UUID id = UUID.fromString(objectMapper.readTree(mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                                    .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString()).at("/data/user/id").asText());
            return new TestUser(id, email, token, cookie);
        } catch (Exception e) {
            throw new IllegalStateException("Login failed for " + email, e);
        }
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
