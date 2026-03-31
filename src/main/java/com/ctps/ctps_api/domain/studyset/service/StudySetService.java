package com.ctps.ctps_api.domain.studyset.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.studyset.dto.StudySetCreateRequest;
import com.ctps.ctps_api.domain.studyset.dto.StudySetResponse;
import com.ctps.ctps_api.domain.studyset.dto.StudySetUpdateRequest;
import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import com.ctps.ctps_api.domain.studyset.repository.StudySetRepository;
import com.ctps.ctps_api.global.exception.BadRequestException;
import com.ctps.ctps_api.global.exception.NotFoundException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudySetService {

    private final StudySetRepository studySetRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;

    public List<StudySetResponse> getStudySets() {
        Long userId = CurrentUserContext.getRequired().getId();
        return studySetRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(StudySetResponse::from).toList();
    }

    public StudySetResponse getStudySet(Long id) {
        return StudySetResponse.from(findById(id));
    }

    @Transactional
    public StudySetResponse createStudySet(StudySetCreateRequest request) {
        Long userId = CurrentUserContext.getRequired().getId();
        User user = userRepository.getReferenceById(userId);
        List<String> normalizedProblemIds = normalizeProblemIds(request.getProblemIds());
        validateOwnedProblemIds(normalizedProblemIds, userId);
        StudySet studySet = StudySet.builder()
                .user(user)
                .name(request.getName())
                .problemIds(normalizedProblemIds)
                .completedProblemIds(List.of())
                .createdAt(LocalDateTime.now())
                .build();
        return StudySetResponse.from(studySetRepository.save(studySet));
    }

    @Transactional
    public StudySetResponse updateStudySet(Long id, StudySetUpdateRequest request) {
        StudySet studySet = findById(id);
        List<String> normalizedProblemIds = request.getProblemIds() == null
                ? studySet.getProblemIds()
                : normalizeProblemIds(request.getProblemIds());
        List<String> normalizedCompletedProblemIds = request.getCompletedProblemIds() == null
                ? studySet.getCompletedProblemIds()
                : normalizeProblemIds(request.getCompletedProblemIds());

        validateOwnedProblemIds(normalizedProblemIds, studySet.getUser().getId());
        validateCompletedProblemIds(normalizedProblemIds, normalizedCompletedProblemIds);

        studySet.patch(request.getName(), normalizedProblemIds, normalizedCompletedProblemIds);
        return StudySetResponse.from(studySet);
    }

    @Transactional
    public void deleteStudySet(Long id) {
        StudySet studySet = findById(id);
        studySetRepository.delete(studySet);
    }

    private StudySet findById(Long id) {
        Long userId = CurrentUserContext.getRequired().getId();
        return studySetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("공부 세트를 찾을 수 없습니다. id=" + id));
    }

    private List<String> normalizeProblemIds(List<String> problemIds) {
        return problemIds.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void validateOwnedProblemIds(List<String> problemIds, Long userId) {
        if (problemIds.isEmpty()) {
            throw new BadRequestException("공부 세트에 포함할 문제를 하나 이상 선택해 주세요.");
        }

        for (String problemId : problemIds) {
            Long parsedProblemId;
            try {
                parsedProblemId = Long.parseLong(problemId);
            } catch (NumberFormatException exception) {
                throw new BadRequestException("공부 세트에 포함된 문제 ID 형식이 올바르지 않습니다.");
            }

            problemRepository.findByIdAndUserId(parsedProblemId, userId)
                    .orElseThrow(() -> new BadRequestException("본인 계정의 문제만 공부 세트에 담을 수 있습니다."));
        }
    }

    private void validateCompletedProblemIds(List<String> problemIds, List<String> completedProblemIds) {
        LinkedHashSet<String> allowedIds = new LinkedHashSet<>(problemIds);
        boolean hasInvalidCompletedProblem = completedProblemIds.stream().anyMatch(problemId -> !allowedIds.contains(problemId));
        if (hasInvalidCompletedProblem) {
            throw new BadRequestException("완료한 문제는 공부 세트에 포함된 문제만 선택할 수 있습니다.");
        }
    }
}
