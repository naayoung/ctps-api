package com.ctps.ctps_api.domain.problem.dto.search;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Getter
public class ProblemSearchRequest {

    private String keyword;
    private List<String> platform;
    private List<Problem.Difficulty> difficulty;
    private List<String> tags;
    private List<Problem.Result> result;
    private Boolean needsReview;
    private Boolean bookmarked;
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
