package io.soo.bueongi.storage.db.core.requestmetric

import org.springframework.data.redis.core.StringRedisTemplate

class RedisAdminRequestMetricStore(
    redis: StringRedisTemplate,
    private val delegate: RedisRequestMetricStore =
        RedisRequestMetricStore(
            redis = redis,
            key = "requestmetric:admin",
            cap = ADMIN_REQUEST_METRIC_CAP,
        ),
) : AdminRequestMetricStore, RequestMetricStore by delegate
