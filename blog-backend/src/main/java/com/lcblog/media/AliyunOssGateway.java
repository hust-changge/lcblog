package com.lcblog.media;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.lcblog.common.Web;
import com.lcblog.config.BlogProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 官方 SDK 实现。凭据只在服务端使用：优先 ECS RAM 角色，
 * 否则读取环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET（可选 OSS_SESSION_TOKEN）。
 * 未配置 OSS 时应用仍可启动，只是文件功能返回“OSS 未配置”。
 */
@Component
class AliyunOssGateway implements OssGateway {

    private final BlogProperties.Oss config;
    private volatile OSS client;
    private volatile CredentialsProvider credentialsProvider;

    AliyunOssGateway(BlogProperties props) {
        this.config = props.oss();
    }

    private OSS client() {
        if (!config.configured()) {
            throw Web.badRequest("OSS 未配置：请设置 OSS_ENDPOINT、OSS_PUBLIC_BUCKET 和 OSS_PRIVATE_BUCKET");
        }
        OSS c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    credentialsProvider = credentials();
                    client = new OSSClientBuilder().build(endpoint(), credentialsProvider, new ClientBuilderConfiguration());
                }
                c = client;
            }
        }
        return c;
    }

    private CredentialsProvider credentials() {
        try {
            String role = config.ecsRamRole();
            return role != null && !role.isBlank()
                    ? CredentialsProviderFactory.newInstanceProfileCredentialsProvider(role)
                    : CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        } catch (Exception e) {
            // 不把底层异常信息（可能包含凭据线索）透出给调用方
            throw Web.badRequest("OSS 凭据不可用，请检查环境变量或 RAM 角色配置");
        }
    }

    private String endpoint() {
        String e = config.endpoint().trim();
        return e.startsWith("http://") || e.startsWith("https://") ? e : "https://" + e;
    }

    @Override
    public PostPolicy postPolicy(String bucket, String key, String contentType, String contentDisposition,
                                 long exactSize, Instant expiresAt) {
        OSS oss = client();
        PolicyConditions conds = new PolicyConditions();
        conds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, exactSize, exactSize);
        conds.addConditionItem(MatchMode.Exact, PolicyConditions.COND_KEY, key);
        conds.addConditionItem(MatchMode.Exact, PolicyConditions.COND_CONTENT_TYPE, contentType);
        conds.addConditionItem(MatchMode.Exact, PolicyConditions.COND_CONTENT_DISPOSITION, contentDisposition);

        String policy = oss.generatePostPolicy(Date.from(expiresAt), conds);
        Credentials cred = credentialsProvider.getCredentials();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", key);
        fields.put("policy", BinaryUtil.toBase64String(policy.getBytes(StandardCharsets.UTF_8)));
        fields.put("OSSAccessKeyId", cred.getAccessKeyId());
        fields.put("Signature", oss.calculatePostSignature(policy));
        fields.put("success_action_status", "200");
        fields.put("Content-Type", contentType);
        fields.put("Content-Disposition", contentDisposition);
        if (cred.useSecurityToken()) {
            fields.put("x-oss-security-token", cred.getSecurityToken());
        }
        URI ep = URI.create(endpoint());
        return new PostPolicy(ep.getScheme() + "://" + bucket + "." + ep.getHost(), fields);
    }

    @Override
    public Optional<ObjectStat> stat(String bucket, String key) {
        try {
            ObjectMetadata meta = client().getObjectMetadata(bucket, key);
            return Optional.of(new ObjectStat(meta.getContentLength(), meta.getContentType()));
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readHead(String bucket, String key, int bytes) {
        GetObjectRequest req = new GetObjectRequest(bucket, key);
        req.setRange(0, bytes - 1);
        try (OSSObject obj = client().getObject(req); InputStream in = obj.getObjectContent()) {
            return in.readNBytes(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String presignGet(String bucket, String key, Duration ttl, String contentType, String contentDisposition) {
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.GET);
        req.setExpiration(Date.from(Instant.now().plus(ttl)));
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
        headers.setContentType(contentType);
        headers.setContentDisposition(contentDisposition);
        req.setResponseHeaders(headers);
        return client().generatePresignedUrl(req).toString();
    }

    @Override
    public Optional<String> publicUrl(String key) {
        String base = config.publicBaseUrl();
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(base.replaceAll("/+$", "") + "/" + key);
    }

    @Override
    public void copy(String srcBucket, String srcKey, String dstBucket, String dstKey) {
        client().copyObject(srcBucket, srcKey, dstBucket, dstKey);
    }

    @Override
    public void delete(String bucket, String key) {
        client().deleteObject(bucket, key); // OSS 删除不存在的对象同样返回成功
    }

    @PreDestroy
    void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
