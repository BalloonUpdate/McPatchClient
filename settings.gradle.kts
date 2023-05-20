//根项目名称
rootProject.name = "McPatchClient"
//插件列表
plugins {
    id("com.gradle.enterprise") version("3.13.2")
}

gradleEnterprise {
    if (System.getenv("CI") != null) {
        buildScan {
            publishAlways()
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}