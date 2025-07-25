package goodspace.backend.qna.dto;

import goodspace.backend.qna.domain.Answer;
import goodspace.backend.qna.domain.QuestionStatus;
import goodspace.backend.qna.domain.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
public class QuestionResponseDto {
    private String title;
    private String content;
    private Long userId;
    private QuestionType type;
    private QuestionStatus status;
    private Answer answer;

    private List<Long> fileIds = new ArrayList<>();
}
