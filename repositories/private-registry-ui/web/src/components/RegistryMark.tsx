import { faBox } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

export function RegistryMark({ compact = false }: { compact?: boolean }) {
  return (
    <span
      className={compact ? "brand-mark-frame compact" : "brand-mark-frame"}
      aria-hidden="true"
    >
      <FontAwesomeIcon icon={faBox} />
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
