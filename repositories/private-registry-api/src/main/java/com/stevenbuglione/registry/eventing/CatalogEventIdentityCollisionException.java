package com.stevenbuglione.registry.eventing;

public final class CatalogEventIdentityCollisionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CatalogEventIdentityCollisionException(String eventId) {
    super("Catalog event transport ID was reused with different event semantics: " + eventId);
  }
}
