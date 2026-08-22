package io.soo.bueongi.storage.db.core.requestmetric

class InMemoryAdminRequestMetricStore(
    private val delegate: InMemoryRequestMetricStore =
        InMemoryRequestMetricStore(ADMIN_REQUEST_METRIC_CAP),
) : AdminRequestMetricStore, RequestMetricStore by delegate
