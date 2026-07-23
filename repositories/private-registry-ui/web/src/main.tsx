import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import "./features/package-detail/styles";
import { router } from "./router";
import { loadRuntimeConfig } from "./runtime-config";
import "./styles.css";
import { initializeTheme } from "./theme";

initializeTheme();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
    },
  },
});

async function bootstrap() {
  await loadRuntimeConfig();
  const root = document.getElementById("root");
  if (root === null) throw new Error("Registry root element is missing");
  createRoot(root).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap().catch(() => {
  const root = document.getElementById("root");
  if (root !== null) root.textContent = "Registry could not be initialized.";
});
