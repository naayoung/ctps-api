package com.ctps.ctps_api.domain.problem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ctps-problem-search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=NUMBER",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(ProblemRepositoryImpl.class)
class ProblemSearchRepositoryTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("키워드, 태그, 복습 여부 조건으로 문제를 검색한다")
    void searchProblems_withKeywordAndFilters() {
        Problem graphProblem = saveProblem(
                "백준",
                "특정한 최단 경로",
                "1504",
                List.of("그래프", "다익스트라"),
                Problem.Difficulty.hard,
                Problem.Result.fail,
                true,
                LocalDate.of(2026, 3, 19),
                LocalDateTime.of(2026, 3, 10, 12, 0)
        );
        saveProblem(
                "프로그래머스",
                "단어 변환",
                "43163",
                List.of("DFS", "BFS"),
                Problem.Difficulty.medium,
                Problem.Result.success,
                false,
                LocalDate.of(2026, 3, 18),
                LocalDateTime.of(2026, 3, 11, 12, 0)
        );

        ProblemSearchRequest request = new ProblemSearchRequestFixture()
                .keyword("최단")
                .tags(List.of("그래프"))
                .needsReview(true)
                .sort("lastSolvedAt,desc")
                .build();

        var result = problemRepository.searchProblems(ensureUser().getId(), request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Problem::getId).containsExactly(graphProblem.getId());
    }

    @Test
    @DisplayName("난이도순 정렬은 hard, medium, easy 순으로 동작한다")
    void searchProblems_sortByDifficultyDesc() {
        saveProblem("백준", "쉬운 문제", "1", List.of("구현"), Problem.Difficulty.easy, Problem.Result.success, false,
                LocalDate.of(2026, 3, 10), LocalDateTime.of(2026, 3, 1, 9, 0));
        saveProblem("백준", "보통 문제", "2", List.of("DP"), Problem.Difficulty.medium, Problem.Result.partial, false,
                LocalDate.of(2026, 3, 11), LocalDateTime.of(2026, 3, 2, 9, 0));
        saveProblem("백준", "어려운 문제", "3", List.of("그래프"), Problem.Difficulty.hard, Problem.Result.fail, true,
                LocalDate.of(2026, 3, 12), LocalDateTime.of(2026, 3, 3, 9, 0));

        ProblemSearchRequest request = new ProblemSearchRequestFixture()
                .sort("difficulty,desc")
                .build();

        var result = problemRepository.searchProblems(ensureUser().getId(), request);

        assertThat(result.getContent()).extracting(Problem::getTitle)
                .containsExactly("어려운 문제", "보통 문제", "쉬운 문제");
    }

    private Problem saveProblem(
            String platform,
            String title,
            String number,
            List<String> tags,
            Problem.Difficulty difficulty,
            Problem.Result result,
            boolean needsReview,
            LocalDate lastSolvedAt,
            LocalDateTime createdAt
    ) {
        User user = ensureUser();

        Problem problem = Problem.builder()
                .user(user)
                .platform(platform)
                .title(title)
                .number(number)
                .link("https://example.com/" + number)
                .tags(tags)
                .difficulty(difficulty)
                .memo(title + " 메모")
                .result(result)
                .needsReview(needsReview)
                .reviewHistory(List.of())
                .createdAt(createdAt)
                .solvedDates(List.of())
                .lastSolvedAt(lastSolvedAt)
                .bookmarked(false)
                .build();

        Problem saved = problemRepository.save(problem);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private User ensureUser() {
        List<User> users = entityManager.getEntityManager()
                .createQuery("select u from User u where u.username = :username", User.class)
                .setParameter("username", "tester")
                .getResultList();

        if (!users.isEmpty()) {
            return users.get(0);
        }

        return entityManager.persist(User.builder()
                .username("tester")
                .passwordHash("hashed-password")
                .displayName("테스터")
                .createdAt(LocalDateTime.of(2026, 3, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 1, 0, 0))
                .primaryAuthProvider(AuthProvider.LOCAL)
                .build());
    }

    private static final class ProblemSearchRequestFixture {
        private final ProblemSearchRequest request = new ProblemSearchRequest();

        ProblemSearchRequestFixture keyword(String keyword) {
            setField("keyword", keyword);
            return this;
        }

        ProblemSearchRequestFixture tags(List<String> tags) {
            setField("tags", tags);
            return this;
        }

        ProblemSearchRequestFixture needsReview(Boolean needsReview) {
            setField("needsReview", needsReview);
            return this;
        }

        ProblemSearchRequestFixture sort(String sort) {
            setField("sort", sort);
            return this;
        }

        ProblemSearchRequest build() {
            return request;
        }

        private void setField(String name, Object value) {
            try {
                var field = ProblemSearchRequest.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(request, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
