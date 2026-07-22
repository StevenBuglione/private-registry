import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import { loadRuntimeConfig } from "./runtime-config";
import { router } from "./router";
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
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap().catch(() => {
  const root = document.getElementById("root");
  if (root) root.textContent = "Registry could not be initialized.";
});
