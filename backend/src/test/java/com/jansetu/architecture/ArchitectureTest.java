package com.jansetu.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Enforces the modular-monolith boundaries documented in docs/ARCHITECTURE.md. */
class ArchitectureTest {

    private static final List<String> MODULES = List.of("access", "audit", "catalogue", "department", "identity",
            "application", "document", "grievance", "notification", "reporting");
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.jansetu");
    }

    @Test
    void modulesDoNotHaveCyclicDependencies() {
        slices().matching("com.jansetu.modules.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void repositoriesAreOnlyUsedInsideTheirOwnModule() {
        for (String module : MODULES) {
            noClasses().that().resideOutsideOfPackage("com.jansetu.modules." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage("com.jansetu.modules." + module + ".repository..")
                    .because("modules must use each other's public services, not repositories")
                    .check(classes);
        }
    }

    @Test
    void controllersDoNotAccessRepositoriesDirectly() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .check(classes);
    }

    @Test
    void sharedInfrastructureDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAnyPackage("com.jansetu.common..", "com.jansetu.security..", "com.jansetu.integration..")
                .should().dependOnClassesThat().resideInAPackage("com.jansetu.modules..")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnWebLayer() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(classes);
    }
}
