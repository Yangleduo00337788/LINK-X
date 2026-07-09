package com.linkx.server.generator;

import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.ColumnConfig;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

public class CodeGenerator {

    public static void main(String[] args) {
        // 1. 配置数据源
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/linkx?characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        dataSource.setUsername("root");
        dataSource.setPassword("linkx_password");

        // 2. 创建配置对象
        GlobalConfig globalConfig = new GlobalConfig();
        
        // 2.1 根包和文件生成位置配置
        globalConfig.setBasePackage("com.linkx.server");
        // 如果是从 linkx-server 目录下运行，使用相对路径即可
        globalConfig.setSourceDir(System.getProperty("user.dir") + "/src/main/java");
        globalConfig.setMapperXmlPath(System.getProperty("user.dir") + "/src/main/resources/mapper");

        // 2.2 实体类(Entity)配置
        globalConfig.setEntityGenerateEnable(true);
        globalConfig.setEntityWithLombok(true);
        // 主键配置：这里统一设置 id 为雪花算法生成
        ColumnConfig idConfig = new ColumnConfig();
        idConfig.setColumnName("id");
        idConfig.setOnInsertValue("snowFlakeId()"); // Flex 内置雪花算法
        globalConfig.getStrategyConfig().setColumnConfig("sys_user", idConfig);
        
        // 逻辑删除配置
        globalConfig.getStrategyConfig().setLogicDeleteColumn("deleted");

        // 2.3 Mapper 配置
        globalConfig.setMapperGenerateEnable(true);
        globalConfig.setMapperAnnotation(true);

        // 2.4 Service & ServiceImpl 配置
        globalConfig.setServiceGenerateEnable(true);
        globalConfig.setServiceImplGenerateEnable(true);

        // 2.5 Controller 配置
        globalConfig.setControllerGenerateEnable(true);

        // 指定要生成的表名
        globalConfig.getStrategyConfig().setGenerateTable("sys_user");

        // 3. 执行生成器
        Generator generator = new Generator(dataSource, globalConfig);
        
        generator.generate();
        
        System.out.println("====== MyBatis-Flex 代码生成完成 ======");
    }
}
