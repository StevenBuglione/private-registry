package com.stevenbuglione.registry.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stevenbuglione.registry.catalog.CatalogPage;
import com.stevenbuglione.registry.catalog.CatalogQuery;
import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.model.Approval;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

  @Mock private CatalogService catalog;

  @Mock private RegistryIdentityService identities;

  private final AccessContext accessContext =
      new AccessContext("user", Set.of("APM0000001"), false);
  private MockMvc mvc;
  private CatalogPackage module;

  @BeforeEach
  void setUp() {
    module = TestCatalogFixtures.module();
    lenient()
        .when(identities.accessContext(nullable(Authentication.class)))
        .thenReturn(accessContext);
    mvc =
        MockMvcBuilders.standaloneSetup(new CatalogController(catalog, identities))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void searchesAuthorizedCatalog() throws Exception {
    when(catalog.findPackages(eq(accessContext), any(CatalogQuery.class)))
        .thenReturn(new CatalogPage<>(List.of(module), null, 1));

    mvc.perform(get("/registry/docs/search").queryParam("q", "vpc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("AWS VPC"))
        .andExpect(jsonPath("$[0].link_variables.target_system").value("aws"))
        .andExpect(jsonPath("$[0].link_variables.version").value("2.4.1"));
  }

  @Test
  void returnsRegistryModuleCompatibilityContract() throws Exception {
    when(catalog.findPackages(eq(accessContext), any(CatalogQuery.class)))
        .thenReturn(new CatalogPage<>(List.of(module), null, 1));
    when(catalog.getPackage(accessContext, module.id())).thenReturn(module);

    mvc.perform(get("/registry/docs/modules/index.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[0].addr.namespace").value("cloud-platform"))
        .andExpect(jsonPath("$.modules[0].versions[0].id").value("2.4.1"));

    mvc.perform(get("/registry/docs/modules/cloud-platform/vpc/aws/2.4.1/index.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("2.4.1"))
        .andExpect(jsonPath("$.readme").value(true))
        .andExpect(jsonPath("$.variables.region.type").value("string"))
        .andExpect(jsonPath("$.variables.region.default").value("\"us-east-1\""))
        .andExpect(jsonPath("$.variables.region.required").value(false))
        .andExpect(jsonPath("$.outputs.endpoint.sensitive").value(true));
  }

  @Test
  void returnsAuthorizedModuleDocumentation() throws Exception {
    when(catalog.getPackage(accessContext, module.id())).thenReturn(module);
    when(catalog.readDocument(accessContext, module.id(), "README.md"))
        .thenReturn(
            new CatalogService.DocumentContent("# AWS VPC\n", "text/markdown; charset=utf-8"));

    mvc.perform(get("/registry/docs/modules/cloud-platform/vpc/aws/latest/README.md"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("AWS VPC")));
  }

  @Test
  void returnsEveryProviderDocumentationGroup() throws Exception {
    var provider = TestCatalogFixtures.provider();
    when(catalog.getPackage(accessContext, provider.id())).thenReturn(provider);

    mvc.perform(get("/registry/docs/providers/platform/cloud/3.8.0/index.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.resources[0].name").value("cloud_project"))
        .andExpect(jsonPath("$.docs.datasources[0].name").value("cloud_identity"))
        .andExpect(jsonPath("$.docs.functions[0].name").value("cloud_normalize"))
        .andExpect(jsonPath("$.docs.guides[0].name").value("authentication"));
  }

  @Test
  void returnsAuthorizedEnterpriseGovernance() throws Exception {
    var governance =
        new Governance(
            module.id(),
            module.owners(),
            "supported",
            "approved",
            "medium",
            "enterprise-verified",
            List.of(new Approval("security", "approved", "reviewer", Instant.EPOCH)),
            module.sourceAddress(),
            "= 2.4.1",
            "https://support.example.invalid",
            null,
            null);
    when(catalog.getGovernance(accessContext, module.id())).thenReturn(governance);

    mvc.perform(get("/api/v1/enterprise/packages/module/cloud-platform/vpc/aws/governance"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(module.sourceAddress())));
  }

  @Test
  void rejectsIncompleteModuleRoute() throws Exception {
    mvc.perform(get("/registry/docs/modules/incomplete")).andExpect(status().isBadRequest());
  }
}
