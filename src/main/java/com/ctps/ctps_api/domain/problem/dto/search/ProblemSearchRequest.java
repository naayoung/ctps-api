package com.ctps.ctps_api.domain.problem.dto.search;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Getter
@Setter
public class ProblemSearchRequest {

    @Size(max = 120, message = "검색어는 120자 이하로 입력해 주세요.")
    private String keyword;
    @Size(max = 10, message = "플랫폼 필터는 최대 10개까지 선택할 수 있습니다.")
    private List<@NotBlank(message = "플랫폼 값은 비워둘 수 없습니다.") @Size(max = 50, message = "플랫폼 값은 50자 이하로 입력해 주세요.") String> platform;
    private List<Problem.Difficulty> difficulty;
    @Size(max = 20, message = "태그 필터는 최대 20개까지 선택할 수 있습니다.")
    private List<@NotBlank(message = "태그 값은 비워둘 수 없습니다.") @Size(max = 100, message = "태그 값은 100자 이하로 입력해 주세요.") String> tags;
    private List<Problem.Result> result;
    private Boolean needsReview;
    private Boolean bookmarked;
    @Size(max = 40, message = "정렬 조건이 올바르지 않습니다.")
    private String sort = "relevance,desc";

    @Min(0)
    private Integer page = 0;

    @Min(1)
    @Max(100)
    private Integer size = 15;

    public ProblemSearchRequest copyWithPageAndSize(int nextPage, int nextSize) {
        ProblemSearchRequest copied = new ProblemSearchRequest();
        copied.keyword = this.keyword;
        copied.platform = this.platform;
        copied.difficulty = this.difficulty;
        copied.tags = this.tags;
        copied.result = this.result;
        copied.needsReview = this.needsReview;
        copied.bookmarked = this.bookmarked;
        copied.sort = this.sort;
        copied.page = nextPage;
        copied.size = nextSize;
        return copied;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }

    public ProblemSearchSortOption getSortOption() {
        return ProblemSearchSortOption.from(sort);
    }

    public boolean isSortDescending() {
        if (sort == null || sort.isBlank()) {
            return true;
        }

        String[] tokens = sort.split(",");
        if (tokens.length < 2) {
            return true;
        }

        return !"asc".equalsIgnoreCase(tokens[1].trim());
    }

    public List<String> getPlatform() {
        return platform == null ? List.of() : platform;
    }

    public List<Problem.Difficulty> getDifficulty() {
        return difficulty == null ? List.of() : difficulty;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : tags;
    }

    public List<Problem.Result> getResult() {
        return result == null ? List.of() : result;
    }

    public String getKeyword() {
        return keyword == null ? null : keyword.trim();
    }

    public Boolean getBookmarked() {
        return bookmarked;
    }
}
