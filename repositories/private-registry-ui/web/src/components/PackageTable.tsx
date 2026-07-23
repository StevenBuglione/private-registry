import { CaretRightIcon } from "@phosphor-icons/react";
import { Link } from "react-router";
import type { PackageSummary } from "../types";
import { formatRelativeDate, packageHref } from "../utils";
import { PackageIcon } from "./PackageIcon";

export function PackageTable({
  items,
  compact = false,
}: {
  items: PackageSummary[];
  compact?: boolean;
}) {
  return (
    <div className={compact ? "package-table compact" : "package-table"}>
      <table>
        <caption className="sr-only">Registry packages</caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Type</th>
            <th scope="col">Provider</th>
            {!compact ? <th scope="col">Description</th> : null}
            <th scope="col">Updated</th>
            <th scope="col">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={`${item.kind}:${item.namespace}:${item.name}:${item.target ?? ""}:${item.version}`}
            >
              <td>
                <Link className="package-name-cell" to={packageHref(item)}>
                  <PackageIcon kind={item.kind} name={item.name} size="small" />
                  <span>
                    <strong>{item.name}</strong>
                    <small>{item.namespace}</small>
                  </span>
                  <span className="version-pill">{item.version}</span>
                </Link>
              </td>
              <td className="capitalize">{item.kind}</td>
              <td>{item.provider}</td>
              {!compact ? (
                <td className="description-cell">{item.description}</td>
              ) : null}
              <td className="updated-cell">
                {formatRelativeDate(item.updatedAt)}
              </td>
              <td>
                <Link
                  className="row-link"
                  to={packageHref(item)}
                  aria-label={`Open ${item.name}`}
                >
                  <CaretRightIcon size={17} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
