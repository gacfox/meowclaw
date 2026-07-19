package com.gacfox.meowclaw.config;

import com.gacfox.meowclaw.interceptor.web.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final AsyncTaskExecutor mvcAsyncTaskExecutor;

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    @Autowired
    public WebMvcConfig(AuthInterceptor authInterceptor, AsyncTaskExecutor mvcAsyncTaskExecutor) {
        this.authInterceptor = authInterceptor;
        this.mvcAsyncTaskExecutor = mvcAsyncTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/check-init",
                        "/api/auth/init"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(Paths.get(dataDir, "upload").toUri().toString());
    }
}
