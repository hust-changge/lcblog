package com.lcblog.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.lcblog.media.MediaMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 无数据库的 SQL 守卫：访客查询必须在 SQL 层固定只读取已发布 / 公开就绪的数据，
 * 防止以后修改查询时漏掉条件，把草稿或私有文件暴露给访客。
 */
class PublicQueryGuardTest {

    private static List<Method> selects(Class<?> mapper, String nameFragment) {
        return Arrays.stream(mapper.getDeclaredMethods())
                .filter(m -> m.getName().contains(nameFragment) && m.isAnnotationPresent(Select.class))
                .toList();
    }

    private static String sql(Method m) {
        return String.join(" ", m.getAnnotation(Select.class).value());
    }

    @Test
    void everyPublishedQueryFiltersOnPublishedStatus() {
        List<Method> methods = new java.util.ArrayList<>(selects(ArticleMapper.class, "Published"));
        methods.addAll(selects(TaxonomyMapper.class, "Published"));
        assertThat(methods).hasSizeGreaterThanOrEqualTo(12);
        for (Method m : methods) {
            assertThat(sql(m)).as(m.getName()).contains("status = 'PUBLISHED'");
        }
    }

    @Test
    void everyPublicMediaQueryRequiresReadyAndPublic() {
        List<Method> methods = new java.util.ArrayList<>(selects(MediaMapper.class, "Public"));
        methods.addAll(selects(com.lcblog.album.AlbumMapper.class, "Public"));
        assertThat(methods).hasSizeGreaterThanOrEqualTo(6);
        for (Method m : methods) {
            assertThat(sql(m)).as(m.getName()).contains("m.status = 'READY'", "m.visibility = 'PUBLIC'", "m.hidden = FALSE");
        }
    }

    @Test
    void publicMediaOfDraftArticlesIsHidden() throws Exception {
        String sql = sql(MediaMapper.class.getMethod("findPublicReady", long.class));
        assertThat(sql).contains("m.article_id IS NULL OR a.status = 'PUBLISHED'");
    }
}
