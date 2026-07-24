package com.stevenbuglione.registry.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.stevenbuglione.registry.web.HealthController;
import com.stevenbuglione.registry.web.WorkerHealthController;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiRouteVersioningArchitectureTest {

  private static final String API_PREFIX = "/api/v1";
  private static final String HEALTH_PREFIX = "/health/";
  private static final Set<Class<?>> OPERATIONAL_CONTROLLERS =
      Set.of(HealthController.class, WorkerHealthController.class);

  @Test
  void applicationControllersUseTheVersionedApiPrefix() {
    var controllers =
        new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.stevenbuglione.registry")
                .stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .map(javaClass -> load(javaClass.getName()))
                .toList();

    assertThat(controllers).isNotEmpty();
    controllers.stream()
        .filter(controller -> !OPERATIONAL_CONTROLLERS.contains(controller))
        .forEach(
            controller ->
                assertThat(effectiveRequestPaths(controller))
                    .as("%s effective request paths", controller.getName())
                    .isNotEmpty()
                    .allMatch(path -> path.startsWith(API_PREFIX)));
  }

  @Test
  void operationalControllersExposeOnlyHealthOrVersionedApiRoutes() {
    OPERATIONAL_CONTROLLERS.forEach(
        controller ->
            assertThat(
                    Arrays.stream(controller.getDeclaredMethods())
                        .flatMap(ApiRouteVersioningArchitectureTest::requestPaths))
                .as("%s method-level request paths", controller.getName())
                .isNotEmpty()
                .allMatch(
                    path -> path.startsWith(HEALTH_PREFIX) || path.startsWith(API_PREFIX + "/")));
  }

  private static Stream<String> requestPaths(Class<?> controller) {
    return requestPaths(
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
  }

  private static List<String> effectiveRequestPaths(Class<?> controller) {
    var classPaths = requestPaths(controller).toList();
    var prefixes = classPaths.isEmpty() ? List.of("") : classPaths;
    return Arrays.stream(controller.getDeclaredMethods())
        .flatMap(
            method ->
                prefixes.stream()
                    .flatMap(
                        prefix ->
                            requestPaths(method)
                                .map(methodPath -> combinePaths(prefix, methodPath))))
        .toList();
  }

  private static Stream<String> requestPaths(java.lang.reflect.Method method) {
    return requestPaths(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class));
  }

  private static Stream<String> requestPaths(RequestMapping requestMapping) {
    if (requestMapping == null) {
      return Stream.empty();
    }
    var paths =
        Stream.concat(Arrays.stream(requestMapping.path()), Arrays.stream(requestMapping.value()))
            .distinct()
            .toList();
    return paths.isEmpty() ? Stream.of("") : paths.stream();
  }

  private static String combinePaths(String prefix, String methodPath) {
    return Stream.of(prefix, methodPath)
        .flatMap(path -> Arrays.stream(path.split("/")))
        .filter(segment -> !segment.isEmpty())
        .collect(Collectors.joining("/", "/", ""));
  }

  private static Class<?> load(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("Unable to load " + className, exception);
    }
  }
}
