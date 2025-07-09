pluginManagement {
    repositories {
        // 国内镜像优先（Gradle插件）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle-plugin/") }

        // 保留原有Google仓库配置
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // 保持严格模式
    repositories {
        // 国内镜像优先（依赖库）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }

        // 保留原有配置
        google()
        mavenCentral()
    }
}

rootProject.name = "RCTankW"
include(":app")