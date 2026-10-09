package com.lcblog.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 应用与 OSS 之间唯一的边界。生产实现为 AliyunOssGateway；
 * 测试中用内存假实现替换，从而无需真实云服务即可验证上传核验逻辑。
 */
public interface OssGateway {

    record PostPolicy(String host, Map<String, String> fields) {
    }

    record ObjectStat(long size, String contentType) {
    }

    /** 生成只允许向指定 key 上传指定大小与类型的表单直传策略。 */
    PostPolicy postPolicy(String bucket, String key, String contentType, String contentDisposition,
                          long exactSize, Instant expiresAt);

    Optional<ObjectStat> stat(String bucket, String key);

    byte[] readHead(String bucket, String key, int bytes);

    String presignGet(String bucket, String key, Duration ttl, String contentType, String contentDisposition);

    /** 公开资源的固定地址（配置了 CDN 回源域名时），否则为空。 */
    Optional<String> publicUrl(String key);

    void copy(String srcBucket, String srcKey, String dstBucket, String dstKey);

    /** 删除对象；对象不存在时视为成功（幂等，可重试）。 */
    void delete(String bucket, String key);
}
