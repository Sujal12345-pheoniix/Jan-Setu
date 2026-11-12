package com.jansetu.devseed;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.modules.access.service.RoleService;
import com.jansetu.modules.catalogue.domain.RequirementType;
import com.jansetu.modules.catalogue.domain.ServiceDefinition;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.CreateServiceCommand;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.RequirementCommand;
import com.jansetu.modules.department.domain.Department;
import com.jansetu.modules.department.service.DepartmentService;
import com.jansetu.modules.department.service.DepartmentService.CreateDepartmentCommand;
import com.jansetu.modules.identity.service.AuthService;
import com.jansetu.modules.identity.service.StaffMembershipService;
import com.jansetu.modules.identity.service.UserAdminService;
import com.jansetu.modules.identity.service.UserAdminService.CreateStaffCommand;
import com.jansetu.modules.identity.service.UserView;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.UserAuthenticationToken;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Creates synthetic demonstration data for local development: departments, published services and (only when
 * {@code SEED_DEMO_PASSWORD} is set) one demo account per role. Runs only in the {@code local} profile, is
 * idempotent, and goes through the normal services so all business rules and audit logging apply.
 *
 * <p>All names, departments and services are fictional. JanSetu is not an official government portal.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "jansetu.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final String DEMO_DOMAIN = "demo.jansetu.test";

    private final DepartmentService departmentService;
    private final ServiceCatalogueService catalogueService;
    private final UserAdminService userAdminService;
    private final StaffMembershipService staffMembershipService;
    private final AuthService authService;
    private final RoleService roleService;
    private final String demoPassword;

    public DevDataSeeder(DepartmentService departmentService, ServiceCatalogueService catalogueService,
                         UserAdminService userAdminService, StaffMembershipService staffMembershipService,
                         AuthService authService, RoleService roleService,
                         @Value("${jansetu.seed.demo-password:}") String demoPassword) {
        this.departmentService = departmentService;
        this.catalogueService = catalogueService;
        this.userAdminService = userAdminService;
        this.staffMembershipService = staffMembershipService;
        this.authService = authService;
        this.roleService = roleService;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> allPermissions = roleService.listPermissions().stream()
                .map(p -> p.getCode()).collect(Collectors.toSet());
        AuthenticatedUser system = AuthenticatedUser.system(allPermissions);
        SecurityContextHolder.getContext().setAuthentication(new UserAuthenticationToken(system));
        try {
            Map<String, UUID> departments = seedDepartments(system);
            if (!departments.isEmpty()) {
                seedServices(departments, system);
            }
            seedUsers(system);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Map<String, UUID> seedDepartments(AuthenticatedUser system) {
        if (departmentService.findByCode("REVENUE").isPresent()) {
            log.info("Seed data already present - skipping departments and services.");
            return Map.of();
        }
        Map<String, UUID> ids = new HashMap<>();
        ids.put("REVENUE", createDepartment("REVENUE", "Revenue and Land Records (Demo)",
                "Fictional department issuing simulated certificates.", system));
        ids.put("MUNICIPAL", createDepartment("MUNICIPAL", "Municipal Services (Demo)",
                "Fictional municipal department for utility connections and trade licences.", system));
        ids.put("SOCIAL_WELFARE", createDepartment("SOCIAL_WELFARE", "Social Welfare (Demo)",
                "Fictional department administering simulated welfare schemes.", system));
        log.info("Seeded {} demo departments.", ids.size());
        return ids;
    }

    private UUID createDepartment(String code, String name, String description, AuthenticatedUser system) {
        return departmentService.create(new CreateDepartmentCommand(code, name, description,
                        code.toLowerCase() + "@" + DEMO_DOMAIN), system, null, UUID.class, Department::getId, id -> id)
                .value();
    }

    private void seedServices(Map<String, UUID> departments, AuthenticatedUser system) {
        UUID income = createService(departments.get("REVENUE"), "INCOME_CERT", "Income Certificate (Simulated)",
                "CERTIFICATE", "Apply for a simulated income certificate.", 14, "0.00",
                fields(field("applicantName", "Applicant name", "text"),
                        field("annualIncome", "Annual household income", "number"),
                        selectField("incomeSource", "Primary income source", List.of("Salary", "Business", "Agriculture", "Other"))),
                system);
        addRequirement(income, "ID_PROOF", "Identity proof (synthetic)", RequirementType.DOCUMENT, system);
        addRequirement(income, "INCOME_PROOF", "Income evidence (synthetic)", RequirementType.DOCUMENT, system);
        addRequirement(income, "TRUTH_DECLARATION", "Declaration that the information is true", RequirementType.DECLARATION, system);
        catalogueService.publish(income, null, system);

        UUID residence = createService(departments.get("REVENUE"), "RESIDENCE_CERT", "Residence Certificate (Simulated)",
                "CERTIFICATE", "Apply for a simulated residence certificate.", 10, "0.00",
                fields(field("address", "Residential address", "textarea"),
                        field("residingSince", "Residing since", "date")),
                system);
        addRequirement(residence, "ADDRESS_PROOF", "Address proof (synthetic)", RequirementType.DOCUMENT, system);
        catalogueService.publish(residence, null, system);

        UUID water = createService(departments.get("MUNICIPAL"), "WATER_CONNECTION", "New Water Connection (Simulated)",
                "UTILITY", "Request a simulated household water connection.", 21, "500.00",
                fields(field("propertyAddress", "Property address", "textarea"),
                        selectField("connectionType", "Connection type", List.of("Domestic", "Commercial"))),
                system);
        addRequirement(water, "PROPERTY_DOCUMENT", "Property document (synthetic)", RequirementType.DOCUMENT, system);
        catalogueService.publish(water, null, system);

        UUID trade = createService(departments.get("MUNICIPAL"), "TRADE_LICENSE", "Trade Licence (Simulated)",
                "LICENSE", "Apply for a simulated trade licence.", 30, "1500.00",
                fields(field("businessName", "Business name", "text"),
                        field("businessAddress", "Business address", "textarea")),
                system);
        addRequirement(trade, "PREMISES_PROOF", "Premises proof (synthetic)", RequirementType.DOCUMENT, system);
        catalogueService.publish(trade, null, system);

        UUID pension = createService(departments.get("SOCIAL_WELFARE"), "SENIOR_PENSION",
                "Senior Citizen Pension (Simulated)", "WELFARE",
                "Apply for a simulated senior citizen pension scheme.", 30, "0.00",
                fields(field("dateOfBirth", "Date of birth", "date"),
                        field("bankAccountHolder", "Bank account holder name", "text")),
                system);
        addRequirement(pension, "AGE_PROOF", "Age proof (synthetic)", RequirementType.DOCUMENT, system);
        catalogueService.publish(pension, null, system);

        // A draft service: visible to department admins only.
        createService(departments.get("SOCIAL_WELFARE"), "DISABILITY_SUPPORT", "Disability Support Scheme (Draft)",
                "WELFARE", "Draft service not yet published.", 45, "0.00", fields(), system);
        log.info("Seeded demo services.");
    }

    private void seedUsers(AuthenticatedUser system) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.info("SEED_DEMO_PASSWORD not set - demo accounts were not created.");
            return;
        }
        Map<String, UUID> departments = new HashMap<>();
        for (String code : List.of("REVENUE", "MUNICIPAL", "SOCIAL_WELFARE")) {
            departmentService.findByCode(code).ifPresent(d -> departments.put(code, d.id()));
        }
        staff("platform.admin", "Demo Platform Admin", Set.of("PLATFORM_ADMIN"), null, system, departments);
        staff("revenue.admin", "Demo Revenue Department Admin", Set.of("DEPARTMENT_ADMIN"), "REVENUE", system, departments);
        staff("revenue.officer", "Demo Revenue Officer", Set.of("OFFICER"), "REVENUE", system, departments);
        staff("revenue.reviewer", "Demo Revenue Reviewer", Set.of("REVIEWER"), "REVENUE", system, departments);
        staff("municipal.admin", "Demo Municipal Department Admin", Set.of("DEPARTMENT_ADMIN"), "MUNICIPAL", system, departments);
        staff("municipal.officer", "Demo Municipal Officer", Set.of("OFFICER"), "MUNICIPAL", system, departments);
        staff("auditor", "Demo Auditor", Set.of("AUDITOR"), null, system, departments);
        staff("management", "Demo Management Viewer", Set.of("MANAGEMENT_VIEWER"), null, system, departments);
        citizen("citizen.one", "Demo Citizen One");
        citizen("citizen.two", "Demo Citizen Two");
        log.info("Demo accounts ready: <role>@{} (see docs/LOCAL_SETUP.md).", DEMO_DOMAIN);
    }

    private void staff(String localPart, String name, Set<String> roles, String departmentCode, AuthenticatedUser system,
                       Map<String, UUID> departments) {
        try {
            UserView user = userAdminService.createStaff(new CreateStaffCommand(localPart + "@" + DEMO_DOMAIN, name,
                    null, demoPassword, roles), system, null, UserView.class, v -> v, UserView::id).value();
            if (departmentCode != null && departments.containsKey(departmentCode)) {
                staffMembershipService.addMember(departments.get(departmentCode), user.id(), name.replace("Demo ", ""), system);
            }
        } catch (ApiException e) {
            if (e.errorCode() != ErrorCode.DUPLICATE_RESOURCE) {
                throw e;
            }
        }
    }

    private void citizen(String localPart, String name) {
        try {
            authService.register(new AuthService.RegisterCommand(localPart + "@" + DEMO_DOMAIN, demoPassword, name, null));
        } catch (ApiException e) {
            if (e.errorCode() != ErrorCode.DUPLICATE_RESOURCE) {
                throw e;
            }
        }
    }

    private UUID createService(UUID departmentId, String code, String name, String category, String description,
                               int days, String fee, Map<String, Object> schema, AuthenticatedUser system) {
        return catalogueService.create(new CreateServiceCommand(departmentId, code, name, description, category,
                        "Synthetic eligibility text for demonstration only.", days, new BigDecimal(fee), schema),
                system, null, UUID.class, ServiceDefinition::getId, id -> id).value();
    }

    private void addRequirement(UUID serviceId, String code, String name, RequirementType type, AuthenticatedUser system) {
        catalogueService.addRequirement(serviceId, new RequirementCommand(code, name, null, type, true,
                type == RequirementType.DOCUMENT ? "application/pdf,image/jpeg,image/png" : null,
                type == RequirementType.DOCUMENT ? 5_242_880L : null, null), system);
    }

    @SafeVarargs
    private static Map<String, Object> fields(Map<String, Object>... fields) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (fields.length > 0) {
            schema.put("fields", List.of(fields));
        }
        return schema;
    }

    private static Map<String, Object> field(String key, String label, String type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("type", type);
        field.put("required", true);
        return field;
    }

    private static Map<String, Object> selectField(String key, String label, List<String> options) {
        Map<String, Object> field = field(key, label, "select");
        field.put("options", options);
        return field;
    }
}
