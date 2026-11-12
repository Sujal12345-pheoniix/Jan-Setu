package com.jansetu.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageQueryTest {

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "email", "emailNormalized");

    @Test
    void appliesDefaults() {
        Pageable pageable = new PageQuery(null, null, null).toPageable(SORTABLE, Sort.by("name"));
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by("name"));
    }

    @Test
    void mapsAllowlistedFieldsToEntityProperties() {
        Pageable pageable = new PageQuery(2, 50, List.of("email", "desc", "name")).toPageable(SORTABLE, Sort.unsorted());
        assertThat(pageable.getSort().getOrderFor("emailNormalized").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
    }

    @Test
    void acceptsCommaSeparatedForm() {
        Pageable pageable = new PageQuery(0, 10, List.of("name,desc")).toPageable(SORTABLE, Sort.unsorted());
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsUnknownSortFieldsAndOutOfRangeValues() {
        assertThatThrownBy(() -> new PageQuery(0, 10, List.of("passwordHash")).toPageable(SORTABLE, Sort.unsorted()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Unsupported sort field");
        assertThatThrownBy(() -> new PageQuery(-1, 10, null).toPageable(SORTABLE, Sort.unsorted()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PageQuery(0, 101, null).toPageable(SORTABLE, Sort.unsorted()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0, null).toPageable(SORTABLE, Sort.unsorted()))
                .isInstanceOf(ApiException.class);
    }
}
