package com.stevenbuglione.registry.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.model.Approval;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.PackageVersion;
import com.stevenbuglione.registry.model.SearchResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private CatalogService catalog;

    private MockMvc mvc;
    private CatalogPackage module;

    @BeforeEach
    void setUp() {
        module = fixtureModule();
        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(catalog))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void searchesCatalog() throws Exception {
        when(catalog.search(eq("vpc"), eq(null), anyInt()))
                .thenReturn(List.of(new SearchResult(
                        module.id(), "module", "cloud-platform/vpc", module.description(),
                        "/module/cloud-platform/vpc/aws/latest", 1.0, module, null)));

        mvc.perform(get("/registry/docs/search").queryParam("q", "vpc"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AWS VPC")));
    }

    @Test
    void returnsVersionedModuleDocumentation() throws Exception {
        when(catalog.getPackage(module.id())).thenReturn(module);
        when(catalog.readDocument(module.id(), "README.md"))
                .thenReturn(new CatalogService.DocumentContent("# AWS VPC\n", "text/markdown; charset=utf-8"));

        mvc.perform(get("/registry/docs/modules/cloud-platform/vpc/aws/2.4.1/README.md"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AWS VPC")));
    }

    @Test
    void returnsEnterpriseGovernance() throws Exception {
        var governance = new Governance(
                module.id(), module.owners(), "supported", "approved", "medium",
                "enterprise-verified", List.of(new Approval("security", "approved", "reviewer", Instant.EPOCH)),
                module.sourceAddress(), "= 2.4.1", "https://support.example.invalid", null, null);
        when(catalog.getGovernance(module.id())).thenReturn(governance);

        mvc.perform(get("/api/v1/enterprise/packages/module/cloud-platform/vpc/aws/governance"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(module.sourceAddress())));
    }

    @Test
    void rejectsIncompleteModuleRoute() throws Exception {
        mvc.perform(get("/registry/docs/modules/incomplete"))
                .andExpect(status().isBadRequest());
    }

    private static CatalogPackage fixtureModule() {
        var published = Instant.parse("2026-07-21T14:00:00Z");
        var version = new PackageVersion(
                "2.4.1", published, "sha256:fixture", "sha256:fixture",
                "fixtures/modules/vpc/2.4.1", "8d08f7f", false, false, false);
        return new CatalogPackage(
                "module/cloud-platform/vpc/aws", PackageKind.MODULE, "cloud-platform", "vpc", "aws",
                "AWS VPC", "Approved VPC module", "2.4.1", List.of("cloud-network-engineering"),
                "supported", "approved", "enterprise-verified", "medium",
                "artifacts.example.invalid/iac-modules-virtual__cloud-platform/vpc/aws",
                published, List.of(version), List.of());
    }
}
