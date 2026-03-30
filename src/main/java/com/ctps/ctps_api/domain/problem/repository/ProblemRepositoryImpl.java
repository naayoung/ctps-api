package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchSortOption;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ProblemRepositoryImpl implements ProblemSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final SearchTypeCanonicalizer searchTypeCanonicalizer;

    public ProblemRepositoryImpl(SearchTypeCanonicalizer searchTypeCanonicalizer) {
        this.searchTypeCanonicalizer = searchTypeCanonicalizer;
    }

    @Override
    public Page<Problem> searchProblems(Long userId, ProblemSearchRequest request) {
        Pageable pageable = request.toPageable();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        List<Long> ids = findProblemIds(userId, request, pageable, cb);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        long total = countProblems(userId, request, cb);
        List<Problem> problems = findProblemsWithTags(ids);

        Map<Long, Problem> problemMap = new LinkedHashMap<>();
        for (Problem problem : problems) {
            problemMap.put(problem.getId(), problem);
        }

        List<Problem> ordered = ids.stream()
                .map(problemMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new PageImpl<>(ordered, pageable, total);
    }

    private List<Long> findProblemIds(Long userId, ProblemSearchRequest request, Pageable pageable, CriteriaBuilder cb) {
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Problem> root = query.from(Problem.class);

        query.select(root.get("id"));
        query.where(buildPredicates(userId, request, query, cb, root).toArray(Predicate[]::new));
        query.orderBy(buildOrders(request, query, cb, root));

        TypedQuery<Long> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        return typedQuery.getResultList();
    }

    private long countProblems(Long userId, ProblemSearchRequest request, CriteriaBuilder cb) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Problem> root = countQuery.from(Problem.class);

        countQuery.select(cb.count(root));
        countQuery.where(buildPredicates(userId, request, countQuery, cb, root).toArray(Predicate[]::new));

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Problem> findProblemsWithTags(List<Long> ids) {
        return entityManager.createQuery(
                        "select distinct p from Problem p left join fetch p.tags where p.id in :ids",
                        Problem.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    private List<Predicate> buildPredicates(
            Long userId,
            ProblemSearchRequest request,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Problem> root
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("user").get("id"), userId));

        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = "%" + request.getKeyword().toLowerCase() + "%";
            predicates.add(
                    cb.or(
                            cb.like(cb.lower(root.get("title")), keyword),
                            cb.like(cb.lower(root.get("platform")), keyword),
                            cb.like(cb.lower(root.get("number")), keyword),
                            buildTagExistsPredicate(query, cb, root, keyword)
                    )
            );
        }

        if (!request.getPlatform().isEmpty()) {
            predicates.add(root.get("platform").in(request.getPlatform()));
        }

        if (!request.getDifficulty().isEmpty()) {
            predicates.add(root.get("difficulty").in(request.getDifficulty()));
        }

        if (!request.getTags().isEmpty()) {
            List<String> normalizedTags = request.getTags().stream()
                    .flatMap(tag -> searchTypeCanonicalizer.expandTagAliases(tag).stream())
                    .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (!normalizedTags.isEmpty()) {
                predicates.add(buildTagInPredicate(query, cb, root, normalizedTags));
            }
        }

        if (!request.getResult().isEmpty()) {
            predicates.add(root.get("result").in(request.getResult()));
        }

        if (request.getNeedsReview() != null) {
            predicates.add(cb.equal(root.get("needsReview"), request.getNeedsReview()));
        }

        if (request.getBookmarked() != null) {
            predicates.add(cb.equal(root.get("bookmarked"), request.getBookmarked()));
        }

        return predicates;
    }

    private List<Order> buildOrders(
            ProblemSearchRequest request,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Problem> root
    ) {
        ProblemSearchSortOption sortOption = request.getSortOption();
        boolean descending = request.isSortDescending();
        List<Order> orders = new ArrayList<>();

        if (sortOption == ProblemSearchSortOption.RELEVANCE && StringUtils.hasText(request.getKeyword())) {
            Expression<Integer> relevance = buildRelevanceExpression(request, query, cb, root);
            orders.add(descending ? cb.desc(relevance) : cb.asc(relevance));
            orders.add(cb.desc(root.get("createdAt")));
            return orders;
        }

        Path<?> sortPath = switch (sortOption) {
            case LAST_SOLVED_AT -> root.get("lastSolvedAt");
            case CREATED_AT, RELEVANCE -> root.get("createdAt");
            case PROBLEM_NUMBER -> root.get("number");
            case DIFFICULTY -> null;
        };

        if (sortOption == ProblemSearchSortOption.DIFFICULTY) {
            Expression<Integer> difficultyRank = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("difficulty"), Problem.Difficulty.easy), 1)
                    .when(cb.equal(root.get("difficulty"), Problem.Difficulty.medium), 2)
                    .when(cb.equal(root.get("difficulty"), Problem.Difficulty.hard), 3)
                    .otherwise(99);
            orders.add(descending ? cb.desc(difficultyRank) : cb.asc(difficultyRank));
        } else {
            orders.add(cb.asc(cb.isNull(sortPath)));
            orders.add(descending ? cb.desc(sortPath) : cb.asc(sortPath));
        }

        orders.add(cb.desc(root.get("createdAt")));
        return orders;
    }

    private Expression<Integer> buildRelevanceExpression(
            ProblemSearchRequest request,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Problem> root
    ) {
        String keyword = "%" + request.getKeyword().toLowerCase() + "%";

        return cb.sum(
                cb.sum(
                        cb.<Integer>selectCase()
                                .when(cb.like(cb.lower(root.get("title")), keyword), 5)
                                .otherwise(0),
                        cb.<Integer>selectCase()
                                .when(buildTagExistsPredicate(query, cb, root, keyword), 4)
                                .otherwise(0)
                ),
                cb.sum(
                        cb.<Integer>selectCase()
                                .when(cb.like(cb.lower(root.get("platform")), keyword), 3)
                                .otherwise(0),
                        cb.<Integer>selectCase()
                                .when(cb.like(cb.lower(root.get("number")), keyword), 2)
                                .otherwise(0)
                )
        );
    }

    private Predicate buildTagExistsPredicate(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Problem> root,
            String keyword
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Problem> correlatedRoot = subquery.correlate(root);
        Join<Problem, String> tags = correlatedRoot.join("tags");
        subquery.select(cb.literal(1));
        subquery.where(cb.like(cb.lower(tags), keyword));
        return cb.exists(subquery);
    }

    private Predicate buildTagInPredicate(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Problem> root,
            List<String> tags
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Problem> correlatedRoot = subquery.correlate(root);
        Join<Problem, String> tagJoin = correlatedRoot.join("tags");
        subquery.select(cb.literal(1));
        subquery.where(cb.lower(tagJoin).in(tags));
        return cb.exists(subquery);
    }
}
