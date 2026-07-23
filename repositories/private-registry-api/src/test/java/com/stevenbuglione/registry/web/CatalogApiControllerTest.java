package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stevenbuglione.registry.catalog.CatalogChangeNotifier;
import com.stevenbuglione.registry.catalog.CatalogPage;
import com.stevenbuglione.registry.catalog.CatalogQuery;
import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.catalog.NotFoundException;
import com.stevenbuglione.registry.config.CatalogPackageJsonMixin;
import com.stevenbuglione.registry.config.PackageKindJsonMixin;
import com.stevenbuglione.registry.config.SearchResultJsonMixin;
import com.stevenbuglione.registry.config.SymbolJsonMixin;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.SearchResult;
import com.stevenbuglione.registry.model.Symbol;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CatalogApiControllerTest {

  @Mock private CatalogService catalog;

  @Mock private RegistryIdentityService identities;

  @Mock private CatalogChangeNotifier notifier;

  private final AccessContext accessContext =
      new AccessContext("user", Set.of("APM0000001"), false);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    lenient()
        .when(identities.accessContext(nullable(Authentication.class)))
        .thenReturn(accessContext);
    var json =
        JsonMapper.builder()
            .addMixIn(
                com.stevenbuglione.registry.model.CatalogPackage.class,
                CatalogPackageJsonMixin.class)
            .addMixIn(PackageKind.class, PackageKindJsonMixin.class)
            .addMixIn(SearchResult.class, SearchResultJsonMixin.class)
            .addMixIn(Symbol.class, SymbolJsonMixin.class)
            .build();
    mvc =
        MockMvcBuilders.standaloneSetup(new CatalogApiController(catalog, identities, notifier))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter(json))
            .build();
  }

  @Test
  void forwardsTerraformRegistryFiltersWithTheAccessContext() throws Exception {
    var module = TestCatalogFixtures.module();
    var summary =
        new com.stevenbuglione.registry.model.CatalogPackage(
            module.id(),
            module.kind(),
            module.namespace(),
            module.name(),
            module.target(),
            module.title(),
            module.description(),
            module.latestVersion(),
            module.owners(),
            module.supportLevel(),
            module.lifecycle(),
            module.verification(),
            module.riskTier(),
            module.sourceAddress(),
            module.updatedAt(),
            module.versions(),
            List.of());
    when(catalog.findPackages(eq(accessContext), any(CatalogQuery.class)))
        .thenReturn(new CatalogPage<>(List.of(summary), "next", 17));

    mvc.perform(
            get("/api/v1/catalog/packages")
                .queryParam("q", "vpc")
                .queryParam("kind", "module")
                .queryParam("namespace", "Azure")
                .queryParam("provider", "aws,azurerm")
                .queryParam("tier", "partner")
                .queryParam("category", "public-cloud,networking")
                .queryParam("sort", "name")
                .queryParam("page", "2")
                .queryParam("limit", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(17))
        .andExpect(jsonPath("$.next_cursor").value("next"))
        .andExpect(jsonPath("$.items[0].id").value("module/cloud-platform/vpc/aws"))
        .andExpect(jsonPath("$.items[0].symbols").isEmpty())
        .andExpect(jsonPath("$.items[0].owners").doesNotExist())
        .andExpect(jsonPath("$.items[0].supportLevel").doesNotExist())
        .andExpect(jsonPath("$.items[0].lifecycle").doesNotExist())
        .andExpect(jsonPath("$.items[0].riskTier").doesNotExist());

    var query = ArgumentCaptor.forClass(CatalogQuery.class);
    verify(catalog).findPackages(eq(accessContext), query.capture());
    assertThat(query.getValue().q()).isEqualTo("vpc");
    assertThat(query.getValue().namespace()).isEqualTo("Azure");
    assertThat(query.getValue().providers()).containsExactly("aws", "azurerm");
    assertThat(query.getValue().tiers()).containsExactly("partner");
    assertThat(query.getValue().categories()).containsExactly("public-cloud", "networking");
    assertThat(query.getValue().sort()).isEqualTo("name");
    assertThat(query.getValue().limit()).isEqualTo(30);
    assertThat(query.getValue().page()).isEqualTo(2);
    assertThat(query.getValue().offset()).isEqualTo(30);
  }

  @Test
  void mapsUnauthorizedPackageToTheSameNotFoundResponse() throws Exception {
    when(catalog.getPackage(accessContext, "provider/secret/cloud", null))
        .thenThrow(new NotFoundException("Package not found"));

    mvc.perform(get("/api/v1/catalog/packages/provider/secret/cloud"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"))
        .andExpect(jsonPath("$.error.message").value("Package not found"));
  }

  @Test
  void rejectsInvalidOrAmbiguousPageRequests() throws Exception {
    mvc.perform(
            get("/api/v1/catalog/packages").queryParam("page", "2").queryParam("cursor", "opaque"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("bad_request"));

    mvc.perform(get("/api/v1/catalog/packages").queryParam("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("bad_request"));
  }

  @Test
  void acceptsVersionedModuleRoutesAndKeepsTheVersionOutOfThePackageIdentity() throws Exception {
    var module = TestCatalogFixtures.module();
    when(catalog.getPackage(accessContext, module.id(), "2.4.1")).thenReturn(module);

    mvc.perform(get("/api/v1/catalog/packages/module/cloud-platform/vpc/aws/2.4.1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(module.id()))
        .andExpect(jsonPath("$.latestVersion").value("2.4.1"))
        .andExpect(jsonPath("$.symbols[0].type").value("string"))
        .andExpect(jsonPath("$.symbols[0].default_value").value("\"us-east-1\""));

    verify(catalog).getPackage(accessContext, module.id(), "2.4.1");
  }

  @Test
  void acceptsVersionedProviderSummaryRoutes() throws Exception {
    var provider = TestCatalogFixtures.provider();
    when(catalog.getPackage(accessContext, provider.id(), "3.8.0")).thenReturn(provider);

    mvc.perform(get("/api/v1/catalog/packages/provider/platform/cloud/3.8.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(provider.id()))
        .andExpect(jsonPath("$.latestVersion").value("3.8.0"));

    verify(catalog).getPackage(accessContext, provider.id(), "3.8.0");
  }

  @Test
  void routesVersionedModuleAndProviderDocumentation() throws Exception {
    var module = TestCatalogFixtures.module();
    var provider = TestCatalogFixtures.provider();
    when(catalog.readDocument(accessContext, module.id(), "2.4.1", "README.md"))
        .thenReturn(new CatalogService.DocumentContent("# Module docs", "text/markdown"));
    when(catalog.readDocument(accessContext, provider.id(), "3.8.0", "guides/authentication.md"))
        .thenReturn(new CatalogService.DocumentContent("# Provider docs", "text/markdown"));

    mvc.perform(get("/api/v1/catalog/packages/module/cloud-platform/vpc/aws/2.4.1/documentation"))
        .andExpect(status().isOk())
        .andExpect(content().string("# Module docs"));
    mvc.perform(
            get("/api/v1/catalog/packages/provider/platform/cloud/3.8.0/documentation")
                .queryParam("path", "guides/authentication.md"))
        .andExpect(status().isOk())
        .andExpect(content().string("# Provider docs"));

    verify(catalog).readDocument(accessContext, module.id(), "2.4.1", "README.md");
    verify(catalog).readDocument(accessContext, provider.id(), "3.8.0", "guides/authentication.md");
  }

  @Test
  void rejectsUnsafeDocumentationPathsBeforeCatalogAccess() throws Exception {
    mvc.perform(
            get("/api/v1/catalog/packages/provider/platform/cloud/3.8.0/documentation")
                .queryParam("path", "../secrets.md"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("bad_request"));

    mvc.perform(
            get("/api/v1/catalog/packages/provider/platform/cloud/3.8.0/documentation")
                .queryParam("path", "/etc/passwd"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("bad_request"));
  }

  @Test
  void routesUnversionedDocumentation() throws Exception {
    var provider = TestCatalogFixtures.provider();
    when(catalog.readDocument(accessContext, provider.id(), null, "index.md"))
        .thenReturn(new CatalogService.DocumentContent("# Latest docs", "text/markdown"));

    mvc.perform(get("/api/v1/catalog/packages/provider/platform/cloud/documentation"))
        .andExpect(status().isOk())
        .andExpect(content().string("# Latest docs"));

    verify(catalog).readDocument(accessContext, provider.id(), null, "index.md");
  }

  @Test
  void rejectsNonRegistryGovernanceSubresources() throws Exception {
    mvc.perform(get("/api/v1/catalog/packages/provider/platform/cloud/3.8.0/governance"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("bad_request"));
  }

  @Test
  void versionedMissingAndUnauthorizedPackagesShareTheSameNotFoundResponse() throws Exception {
    var id = "provider/secret/cloud";
    when(catalog.getPackage(accessContext, id, "9.9.9"))
        .thenThrow(new NotFoundException("Package not found"));

    mvc.perform(get("/api/v1/catalog/packages/provider/secret/cloud/9.9.9"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"))
        .andExpect(jsonPath("$.error.message").value("Package not found"));
  }

  @Test
  void ignoresClientSuppliedApmAndUsesTheAuthenticatedAccessContext() throws Exception {
    var module = TestCatalogFixtures.module();
    when(catalog.getPackage(accessContext, module.id(), "2.4.1")).thenReturn(module);

    mvc.perform(
            get("/api/v1/catalog/packages/module/cloud-platform/vpc/aws/2.4.1")
                .queryParam("apm_id", "APM0000001"))
        .andExpect(status().isOk());

    verify(catalog).getPackage(accessContext, module.id(), "2.4.1");
  }

  @Test
  void returnsTruthfulAuthorizedCounts() throws Exception {
    when(catalog.countPackages(accessContext, com.stevenbuglione.registry.model.PackageKind.MODULE))
        .thenReturn(30L);
    when(catalog.countPackages(
            accessContext, com.stevenbuglione.registry.model.PackageKind.PROVIDER))
        .thenReturn(12L);

    mvc.perform(get("/api/v1/catalog/counts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules").value(30))
        .andExpect(jsonPath("$.providers").value(12));
  }
}
