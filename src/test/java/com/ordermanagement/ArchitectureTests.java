package com.ordermanagement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giữ cho các module "giao tiếp đúng quy tắc" (deliverable của phase này):
 * <ul>
 *   <li>Module chỉ được dùng module khác qua lớp {@code application} của nó.</li>
 *   <li>Trong một module: {@code domain} không phụ thuộc lớp nào; {@code api} không đi thẳng xuống
 *       {@code infrastructure}; {@code application} và {@code infrastructure} không phụ thuộc {@code api}.</li>
 *   <li>{@code shared} không phụ thuộc module nghiệp vụ; các module không phụ thuộc vòng.</li>
 * </ul>
 */
class ArchitectureTests {

    private static final String BASE = "com.ordermanagement";
    private static final List<String> MODULES = List.of("user", "inventory", "order", "payment");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    void modulesOnlyUseOtherModulesThroughTheApplicationLayer() {
        for (String module : MODULES) {
            List<String> forbidden = new ArrayList<>();
            for (String other : MODULES) {
                if (!other.equals(module)) {
                    forbidden.add(BASE + "." + other + "..api..");
                    forbidden.add(BASE + "." + other + "..domain..");
                    forbidden.add(BASE + "." + other + "..infrastructure..");
                }
            }
            noClasses().that().resideInAPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                    .because("module '" + module + "' chỉ được gọi lớp application của module khác")
                    .check(classes);
        }
    }

    @Test
    void domainDoesNotDependOnOtherLayers() {
        noClasses().that().resideInAPackage(BASE + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..api..", BASE + "..application..", BASE + "..infrastructure..")
                .check(classes);
    }

    @Test
    void apiDoesNotReachInfrastructureDirectly() {
        noClasses().that().resideInAPackage(BASE + "..api..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..infrastructure..")
                .because("controller phải đi qua application service")
                .check(classes);
    }

    @Test
    void applicationAndInfrastructureDoNotDependOnApi() {
        noClasses().that().resideInAnyPackage(BASE + "..application..", BASE + "..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..api..")
                .check(classes);
    }

    @Test
    void sharedDoesNotDependOnBusinessModules() {
        String[] modules = MODULES.stream().map(module -> BASE + "." + module + "..").toArray(String[]::new);
        noClasses().that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(modules)
                .check(classes);
    }

    @Test
    void modulesAreFreeOfCycles() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void componentsLiveInTheirOwnLayer() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(BASE + "..api..").check(classes);
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage(BASE + "..application..").check(classes);
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(BASE + "..domain..").check(classes);
        classes().that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage(BASE + "..infrastructure..").check(classes);
    }
}
