package com.zhe.mall.common.config;

import com.zhe.mall.common.auth.CurrentUserArgumentResolver;
import com.zhe.mall.common.auth.InternalTokenVerifier;
import com.zhe.mall.common.auth.RequireStaffInterceptor;
import com.zhe.mall.common.auth.UserContextFilter;
import com.zhe.mall.common.exception.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Servlet 服务的公共能力装配：统一异常兜底、登录用户上下文、{@code @CurrentUserId} 参数解析、内部凭据校验。
 */
@AutoConfiguration
@ConditionalOnClass({FilterRegistrationBean.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MallCommonAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MallCommonAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 内部凭据校验器：同时接受 current 与 previous（双令牌并行轮换，见 21.2）。
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalTokenVerifier internalTokenVerifier(
            @Value("${mall.auth.internal-token:}") String internalToken,
            @Value("${mall.auth.internal-token-previous:}") String previousInternalToken) {
        InternalTokenVerifier verifier = new InternalTokenVerifier(internalToken, previousInternalToken);
        log.info("内部凭据校验器初始化: configured={}, fingerprint={}, previous 已下发={}",
                verifier.configured(), verifier.fingerprint(),
                previousInternalToken != null && !previousInternalToken.isBlank());
        return verifier;
    }

    @Bean
    public FilterRegistrationBean<UserContextFilter> userContextFilter(InternalTokenVerifier verifier) {
        FilterRegistrationBean<UserContextFilter> registration =
                new FilterRegistrationBean<>(new UserContextFilter(verifier));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public WebMvcConfigurer currentUserWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }

            /**
             * 后台接口的角色鉴权（@{@code RequireStaff}，见 21.3 / ADR-013）。
             * 只对标注了注解的接口生效，因此可以全局注册而不影响 C 端接口。
             */
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequireStaffInterceptor()).addPathPatterns("/**");
            }
        };
    }
}