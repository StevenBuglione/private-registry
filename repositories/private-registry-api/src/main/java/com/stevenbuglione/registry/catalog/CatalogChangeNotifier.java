package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CatalogChangeNotifier {

  SseEmitter subscribe(AccessContext accessContext);

  void publish(CatalogChangeEvent event);
}
