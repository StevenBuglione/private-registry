package com.stevenbuglione.registry.ingestion;

public interface CatalogActivationNotifier {

    void notifyChanged(String publicId, String version);
}
