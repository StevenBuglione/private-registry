package com.stevenbuglione.registry.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.stevenbuglione.registry",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

  @ArchTest
  static final ArchRule modulesMustNotFormCycles =
      slices().matching("com.stevenbuglione.registry.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule domainModelMustRemainFrameworkIndependent =
      noClasses()
          .that()
          .resideInAPackage("..model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "software.amazon..",
              "org.jfrog..");

  @ArchTest
  static final ArchRule controllersMustNotReachRepositories =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule controllersMustBeInboundAdapters =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAnyPackage("..web..", "..eventing.webhook..");

  @ArchTest
  static final ArchRule repositoriesMustStayBehindModuleBoundaries =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAnyPackage("..catalog..", "..ingestion..", "..security.identity..");

  @ArchTest
  static final ArchRule webAdaptersMustNotReachBackgroundProcessing =
      noClasses()
          .that()
          .resideInAPackage("..web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..ingestion..", "..seed..");

  @ArchTest
  static final ArchRule productionCodeMustNotUseAutowiredFields =
      noFields().should().beAnnotatedWith(Autowired.class);

  @ArchTest
  static final ArchRule productionCodeMustNotUseValueFields =
      noFields().should().beAnnotatedWith(Value.class);

  @ArchTest
  static final ArchRule utilityDumpingGroundsAreForbidden =
      noClasses()
          .should()
          .haveSimpleNameEndingWith("Utils")
          .orShould()
          .haveSimpleNameEndingWith("Manager")
          .orShould()
          .haveSimpleNameEndingWith("Helper");
}
