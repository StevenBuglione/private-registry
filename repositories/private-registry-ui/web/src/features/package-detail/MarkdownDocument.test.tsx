import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MarkdownDocument } from "./MarkdownDocument";

const terraformExample = `\`\`\`hcl
module "iam_group" {
  source = "terraform-aws-modules/iam/aws//modules/iam-group"
  enabled = true
}
\`\`\``;

describe("MarkdownDocument", () => {
  it("renders Terraform code with semantic syntax tokens", () => {
    const { container } = render(<MarkdownDocument docs={terraformExample} />);

    const code = container.querySelector(
      "pre.language-hcl > code.language-hcl",
    );
    if (!(code instanceof HTMLElement)) {
      throw new Error("Expected the rendered HCL code block.");
    }
    expect(code).toHaveTextContent('module "iam_group"');
    expect(code).toHaveTextContent(
      'source = "terraform-aws-modules/iam/aws//modules/iam-group"',
    );
    expect(code.textContent.endsWith("\n")).toBe(false);
    expect(container.querySelector(".token.property")).toHaveTextContent(
      "source",
    );
    expect(
      Array.from(container.querySelectorAll(".token.string")).some(
        (token) =>
          token.textContent ===
          '"terraform-aws-modules/iam/aws//modules/iam-group"',
      ),
    ).toBe(true);
    expect(container.querySelector(".token.boolean")).toHaveTextContent("true");
  });

  it("leaves unsupported fenced languages readable as plain code", () => {
    const { container } = render(
      <MarkdownDocument docs={"```unsupported\nplain text\n```"} />,
    );

    expect(
      container.querySelector("code.language-unsupported"),
    ).toHaveTextContent("plain text");
    expect(container.querySelector(".token")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy" })).toBeVisible();
  });
});
