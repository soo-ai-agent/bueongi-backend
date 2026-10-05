package io.soo.bueongi.storage.db.core.share.store

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class RedisConfigured : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getProperty("bueongi.share.redis-url", "").isNotBlank()
}
