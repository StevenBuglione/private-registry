export function RegistryMark({ compact = false }: { compact?: boolean }) {
  return (
    <span
      className={compact ? "brand-mark-frame compact" : "brand-mark-frame"}
      aria-hidden="true"
    >
      <img alt="" src="/assets/registry-mark.svg" />
    </span>
  );
}

export function RegistryBrand() {
  return (
    <>
      <RegistryMark />
      <span className="brand-wordmark">
        <small>Oremus Labs</small>
        <strong>Terraform</strong>
      </span>
      <span className="brand-separator" aria-hidden="true" />
      <span className="brand-product-name">Registry</span>
    </>
  );
}
