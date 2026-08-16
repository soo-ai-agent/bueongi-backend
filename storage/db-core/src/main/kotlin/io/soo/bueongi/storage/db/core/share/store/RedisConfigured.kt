package io.soo.bueongi.storage.db.core.share.store

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/** [RedisNotConfigured] 의 정확한 여집합이어야 한다(둘 중 정확히 한쪽만 매칭). */
class RedisConfigured : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getProperty("bueongi.share.redis-url", "").isNotBlank()
}
