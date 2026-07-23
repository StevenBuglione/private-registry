package com.stevenbuglione.registry.seed;

/** Indicates that an immutable release path already contains different content. */
final class ImmutableSeedConflictException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  ImmutableSeedConflictException(String artifact) {
    super("Refusing to replace immutable artifact " + artifact);
  }
}
