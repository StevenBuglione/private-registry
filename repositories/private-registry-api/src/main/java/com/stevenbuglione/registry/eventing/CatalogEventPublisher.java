package com.stevenbuglione.registry.eventing;

public interface CatalogEventPublisher {

  PublishReceipt publish(CatalogArtifactChanged event);

  record PublishReceipt(String eventId) {}
}
