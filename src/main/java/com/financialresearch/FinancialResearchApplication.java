/**
 * 应用启动入口：加载 Spring Boot 容器并扫描项目配置属性。
 */
package com.financialresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinancialResearchApplication {
    // 应用程序入口
    public static void main(String[] args) {
        SpringApplication.run(FinancialResearchApplication.class, args);
    }
}
