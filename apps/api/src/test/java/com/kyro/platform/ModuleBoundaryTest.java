package com.kyro.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// FROZEN CONTRACT (test): module boundaries from PLAN section 10.
/**
 * A class in module X may depend only on: its own module, {@code common..}, {@code contract..} (generated), and the
 * {@code application..} package of identity, audit and education (which includes education.application.port).
 * No module may import another module's domain, infrastructure or api package; verification and discovery expose no
 * application package to others. Discovery reads the database view and never imports education code.
 */
class ModuleBoundaryTest {

    private static final String BASE = "com.kyro.platform.";
    private static final List<String> MODULES = List.of("identity", "audit", "education", "verification", "discovery", "devseed");
    /** Modules whose application package other modules may use. */
    private static final List<String> PUBLIC_APPLICATION = List.of("identity", "audit", "education");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.kyro.platform");

    @Test
    void modulesOnlyUseAllowedPartsOfOtherModules() {
        for (String module : MODULES) {
            List<String> forbidden = new ArrayList<>();
            for (String other : MODULES) {
                if (other.equals(module)) {
                    continue;
                }
                if (PUBLIC_APPLICATION.contains(other)) {
                    forbidden.add(BASE + other + ".api..");
                    forbidden.add(BASE + other + ".domain..");
                    forbidden.add(BASE + other + ".infrastructure..");
                } else {
                    forbidden.add(BASE + other + "..");
                }
            }
            noClasses()
                    .that()
                    .resideInAPackage(BASE + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(forbidden.toArray(String[]::new))
                    .because("module " + module + " may only use common, contract and other modules' application packages")
                    .check(CLASSES);
        }
    }

    @Test
    void devSeedDependsOnlyOnCommon() {
        noClasses()
                .that()
                .resideInAPackage(BASE + "devseed..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + "identity..", BASE + "audit..", BASE + "education..", BASE + "verification..", BASE + "discovery..")
                .check(CLASSES);
    }

    @Test
    void commonDoesNotDependOnModules() {
        noClasses()
                .that()
                .resideInAPackage(BASE + "common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(MODULES.stream().map(m -> BASE + m + "..").toArray(String[]::new))
                .check(CLASSES);
    }

    @Test
    void discoveryNeverTouchesEducationCode() {
        noClasses()
                .that()
                .resideInAPackage(BASE + "discovery..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + "education..")
                .check(CLASSES);
    }
}
