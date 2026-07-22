package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class InMemoryCatalogChangeNotifier implements CatalogChangeNotifier {

    private static final long EMITTER_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @Override
    public SseEmitter subscribe(AccessContext accessContext) {
        var emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        var subscription = new Subscription(accessContext, emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
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
        subscriptions.forEach(subscription -> {
            if (!visible(subscription.accessContext(), event)) {
                return;
            }
            try {
                subscription.emitter().send(SseEmitter.event()
                        .name("catalog-change")
                        .id(event.packageId() + ":" + event.occurredAt())
                        .data(Map.of(
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
        return context.registryAdmin()
                || event.apmIds().stream().anyMatch(context.apmIds()::contains);
    }

    private record Subscription(AccessContext accessContext, SseEmitter emitter) {}
}
