package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class InMemoryCatalogChangeNotifier implements CatalogChangeNotifier {

  private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

  @Override
  public SseEmitter subscribe(AccessContext accessContext) {
    // Catalog events are a long-lived stream. A finite MVC timeout dispatches through the exception
    // resolver and logs a warning during ordinary browser use; disconnect/error callbacks still
    // remove abandoned subscriptions.
    var emitter = new SseEmitter(0L);
    var subscription = new Subscription(accessContext, emitter);
    subscriptions.add(subscription);
    emitter.onCompletion(() -> subscriptions.remove(subscription));
    emitter.onError(ignored -> subscriptions.remove(subscription));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ready"));
    } catch (IOException exception) {
      subscriptions.remove(subscription);
      emitter.completeWithError(exception);
    }
    return emitter;
  }

  @Override
  public void publish(CatalogChangeEvent event) {
    subscriptions.forEach(
        subscription -> {
          if (!visible(subscription.accessContext(), event)) {
            return;
          }
          try {
            subscription
                .emitter()
                .send(
                    SseEmitter.event()
                        .name("catalog-change")
                        .id(event.packageId() + ":" + event.occurredAt())
                        .data(
                            Map.of(
                                "package_id", event.packageId(),
                                "change_type", event.changeType(),
                                "occurred_at", event.occurredAt())));
          } catch (IOException | IllegalStateException exception) {
            subscriptions.remove(subscription);
            subscription.emitter().complete();
          }
        });
  }

  private static boolean visible(AccessContext context, CatalogChangeEvent event) {
    return context.registryAdmin() || event.apmIds().stream().anyMatch(context.apmIds()::contains);
  }

  private record Subscription(AccessContext accessContext, SseEmitter emitter) {}
}
