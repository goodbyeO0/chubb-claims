package com.chubb.claims.api;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.type.LogicalType;

@Configuration
class StrictJson {
  @Bean
  JsonMapperBuilderCustomizer strictInput() {
    return builder ->
        builder
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .withCoercionConfig(
                LogicalType.Textual,
                config ->
                    config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
  }
}
