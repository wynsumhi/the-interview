package com.theinterview.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "The Interview의 API 명세서",
                description = "The Interview의 Swagger 페이지 입니다."),
        security = @SecurityRequirement(name = " bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class SwaggerConfig {

    @Bean
    GroupedOpenApi exampleOpenApi() {
        String[] paths = {"/example/test/**"};
        return GroupedOpenApi.builder().group("Swagger Example Controller API")
                .pathsToMatch(paths)
                .build();
    }


}
