# Java quality and architecture gates

The API uses complementary tools because no single analyzer covers formatting,
source correctness, nullness, architecture, bytecode defects, maintainability,
security, dependency risk, coverage, and test strength.

## Developer commands

Run these commands from `repositories/private-registry-api`:

```bash
bash scripts/quality.sh format   # Repair deterministic formatting.
bash scripts/quality.sh local    # Mandatory before each commit.
bash scripts/quality.sh pr       # The complete pull-request gate.
bash scripts/quality.sh nightly  # PR gate plus PITest mutation testing.
```

PowerShell users can run the equivalent `./scripts/quality.ps1 <mode>` command.
Both entry points call canonical Gradle lifecycle tasks, so developers and CI
execute the same checks.

`qualityLocal` runs Spotless, Java compilation with `-Xlint`/Error Prone/NullAway,
the unit tests, ArchUnit rules, and Spring Modulith verification. `qualityPr` adds
Checkstyle, PMD, SpotBugs with FindSecBugs, JaCoCo verification, OWASP
Dependency-Check, and a CycloneDX SBOM. `qualityNightly` adds PITest.

| Tool | Failure mode covered | Gate behavior |
| --- | --- | --- |
| Spotless + google-java-format | Formatting and import drift | Repair with `format`; any remaining drift fails every tier |
| Error Prone + NullAway/JSpecify | Compiler-accepted bugs and unsafe nullness | High-confidence findings are compilation errors |
| ArchUnit + Spring Modulith | Layer violations, cycles, and leaking module internals | Executable architecture tests fail every tier |
| SpotBugs + FindSecBugs | Bytecode defects and Java security bug patterns | High-confidence findings fail pull requests |
| PMD | Complexity, design, performance, and maintainability smells | Narrow project rules and an explicit legacy baseline fail new violations |
| Checkstyle | Naming, imports, modifiers, and project conventions | Narrow rules avoid duplicating the formatter |
| JaCoCo | Regressions in aggregate verified line and branch coverage | A reviewed legacy-floor ratchet fails pull requests on any decrease |
| OWASP Dependency-Check | Published runtime-dependency vulnerabilities | CVSS 7.0 or greater fails pull requests |
| CycloneDX | Missing dependency inventory | Machine-readable direct-dependency SBOM on every pull request |
| CodeQL | Interprocedural security and unsafe data flows | GitHub code-scanning gate on every pull request |
| SonarQube | Central new-code debt and security visibility | Conditional server quality gate; never emulated locally |
| PITest | Tests that execute code without proving behavior | Full mutation threshold runs nightly |

## Enforcement tiers

| Tier | Gradle task | Enforcement |
| --- | --- | --- |
| Developer | `qualityLocal` | Formatting, correctness, nullness, tests, architecture |
| Pull request | `qualityPr` | Developer tier plus maintainability, bytecode/security findings, coverage, dependency CVEs, SBOM |
| Nightly | `qualityNightly` | Pull-request tier plus mutation score |

The verified full-codebase PITest baseline is 41% mutation coverage (601 of
1,457 mutations killed), 77% test strength for covered mutations, and 39% line
coverage across mutated classes. The report contains no run-error mutations.
The enforced ratchets are 41% mutation coverage and 38% line coverage to
tolerate integer rounding while preventing regression. These thresholds are
legacy floors, not targets; raise them as tests improve. Sonar's 80% coverage
requirement applies to new code.

## Coverage policy

JaCoCo protects the existing aggregate coverage floor without misrepresenting
legacy coverage as 80%. The reviewed baseline is 1,464 of 3,743 lines (39.11%)
and 611 of 1,546 branches (39.52%). The gate compares exact covered and total
counts, so the floor can rise but any ratio decrease fails. Lowering it requires
an explicit, reviewed change to `config/jacoco/baseline.properties`; production
classes are not excluded.

SonarQube owns the separate new-code policy and requires at least 80% coverage
on new code. That target is not an aggregate-coverage claim.

The root GitHub workflows add CodeQL data-flow analysis, dependency-diff review,
Gitleaks history scanning, an image CycloneDX SBOM, and Trivy container scanning.
Generated Gradle, SBOM, and Trivy reports are retained as workflow artifacts even
when a gate fails. The primary report locations are:

- JaCoCo: `build/reports/jacoco/test/`
- PMD: `build/reports/pmd/`
- SpotBugs/FindSecBugs: `build/reports/spotbugs/`
- OWASP Dependency-Check: `build/reports/dependency-check/dependency-check-report.*`
- CycloneDX: `build/reports/cyclonedx-direct/bom.{json,xml}`
- PITest: `build/reports/pitest/`

## SonarQube

SonarQube is intentionally a separate conditional job. Enable it only after all
of these repository settings exist:

- Variable `SONAR_ENABLED=true`
- Variable `SONAR_HOST_URL`
- Variable `SONAR_PROJECT_KEY`
- Secret `SONAR_TOKEN`

The job is skipped when it is not configured or when a pull request comes from a
fork; it is not replaced by a no-op successful scan. Once enabled, the scanner
waits for the server quality gate and fails the workflow when that gate fails.
Configure the server gate against new code: no blocker/critical issues or
unreviewed security hotspots, less than 3% duplication, at least 80% coverage,
and A reliability, security, and maintainability ratings.

An optional `NVD_API_KEY` repository secret makes OWASP Dependency-Check feeds
faster and less susceptible to public rate limiting. The scan remains enabled
without it.

## Suppression policy

Suppressions are exceptional design decisions, not a way to make CI green.

1. Fix the code or narrow the analyzer rule first.
2. Keep a suppression at the smallest possible class, method, dependency, or
   finding scope.
3. Add a written reason and a tracking issue with an owner and review date.
4. Never suppress a whole package for NullAway, Error Prone, FindSecBugs, CodeQL,
   or architecture rules.
5. Dependency vulnerability suppressions must identify the exact advisory and
   include evidence that the vulnerable path is unreachable or otherwise
   mitigated.
6. Generated and third-party sources must be excluded explicitly, not silently
   added to baselines.

OpenRewrite is not enabled yet. Automated modernization should be added only with
an agreed, versioned recipe set and a dry-run review workflow; an unbounded recipe
catalog would turn scheduled analysis into an unpredictable source rewrite.

## Required branch checks

Protect `main`, require pull requests and code-owner review, dismiss stale
approvals, and require these checks without administrator bypass:

- `API quality gate / Gradle PR quality`
- `API CodeQL / Java and Kotlin`
- `API dependency review / Dependency review`
- `API security / Secret scan`
- `API security / Container security and SBOM`
- `Validate blueprint / validate`

Add `API quality gate / SonarQube new-code gate` only after SonarQube is enabled;
otherwise GitHub cannot require a job that is intentionally absent. GitHub
repository settings and organization policy enforce branch protection—the
workflow files cannot configure those settings themselves.
