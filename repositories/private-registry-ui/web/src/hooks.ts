// Compatibility surface for existing consumers. Production code imports the
// endpoint-focused hook modules so lazy routes do not pull admin code eagerly.
export * from "./hooks/admin";
export * from "./hooks/auth";
export * from "./hooks/catalog";
export * from "./hooks/homepage";
