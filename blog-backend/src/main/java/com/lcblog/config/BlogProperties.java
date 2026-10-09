package com.lcblog.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blog")
public record BlogProperties(Site site, Profile profile, Oss oss, Upload upload) {

    public record Site(String title, String description, String baseUrl) {
        /** 去掉末尾斜杠，便于拼接 canonical / sitemap 地址。 */
        public String root() {
            return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
    }

    /** 站长信息：只来自配置，前台“关于我”和侧边栏展示。头像与背景可填 /api/media/{id} 或站内静态路径。 */
    public record Profile(String name, String intro, String research, List<String> techStack,
                          List<Project> projects, String github, String avatar, String background,
                          String announcement, String startDate) {
    }

    public record Project(String name, String description, String url) {
    }

    public record Oss(String endpoint, String region, String publicBucket, String privateBucket,
                      String publicBaseUrl, String ecsRamRole) {
        public boolean configured() {
            return notBlank(endpoint) && notBlank(publicBucket) && notBlank(privateBucket);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public record Upload(Duration policyTtl, Duration downloadUrlTtl, long maxImageBytes,
                         long maxDocumentBytes, long maxArchiveBytes) {
    }
}
