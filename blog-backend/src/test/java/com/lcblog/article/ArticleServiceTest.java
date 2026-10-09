package com.lcblog.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lcblog.media.MediaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

class ArticleServiceTest {

    private ArticleMapper articles;
    private TaxonomyMapper taxonomy;
    private MediaMapper media;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        articles = mock(ArticleMapper.class);
        taxonomy = mock(TaxonomyMapper.class);
        media = mock(MediaMapper.class);
        service = new ArticleService(articles, taxonomy, media);
        doAnswer(inv -> {
            ((Article) inv.getArgument(0)).id = 7L;
            return null;
        }).when(articles).insert(any());
        doAnswer(inv -> {
            ((Tag) inv.getArgument(0)).id = 100L;
            return null;
        }).when(taxonomy).insertTag(any());
    }

    private static ArticleForm form(String slug, String tags) {
        return new ArticleForm("标题", slug, "摘要", "正文", null, tags, false, null);
    }

    @Test
    void saveReplacesTagsAfterWritingArticle() {
        Tag existing = Tag.of("Java", "java");
        existing.id = 1L;
        when(taxonomy.findTagByName("Java")).thenReturn(existing);

        long id = service.save(null, form("jvm-notes", "Java，并发, Java"));

        assertThat(id).isEqualTo(7L);
        InOrder order = inOrder(articles, taxonomy);
        order.verify(articles).insert(any());
        order.verify(taxonomy).deleteArticleTags(7L);
        order.verify(taxonomy).insertArticleTag(7L, 1L);
        order.verify(taxonomy).insertTag(any());
        order.verify(taxonomy).insertArticleTag(7L, 100L);
    }

    @Test
    void saveRunsInOneTransaction() throws Exception {
        assertThat(ArticleService.class.getMethod("save", Long.class, ArticleForm.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void tagFailurePropagatesSoTransactionRollsBack() {
        doThrow(new DuplicateKeyException("dup")).when(taxonomy).insertTag(any());

        assertThatThrownBy(() -> service.save(null, form("jvm-notes", "新标签")))
                .isInstanceOf(ResponseStatusException.class);
        verify(taxonomy, never()).insertArticleTag(anyLong(), anyLong());
    }

    @Test
    void invalidSlugRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> service.save(null, form("Bad Slug!", "")))
                .isInstanceOf(ResponseStatusException.class);
        verify(articles, never()).insert(any());
        verify(taxonomy, never()).deleteArticleTags(anyLong());
    }

    @Test
    void duplicateSlugBecomesFriendlyError() {
        doThrow(new DuplicateKeyException("dup")).when(articles).insert(any());
        assertThatThrownBy(() -> service.save(null, form("taken", "")))
                .hasMessageContaining("slug 已被其他文章使用");
    }

    @Test
    void publishAndUnpublishUpdateStatusOr404() {
        when(articles.publish(3L)).thenReturn(1);
        when(articles.unpublish(3L)).thenReturn(1);
        service.publish(3L);
        service.unpublish(3L);
        verify(articles).publish(3L);
        verify(articles).unpublish(3L);

        assertThatThrownBy(() -> service.publish(404L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.unpublish(404L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void parseTagsTrimsDedupesAndLimits() {
        assertThat(ArticleService.parseTags(" Java , 并发，Java,, ")).containsExactly("Java", "并发");
        assertThatThrownBy(() -> ArticleService.parseTags("a,b,c,d,e,f,g,h,i,j,k"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
