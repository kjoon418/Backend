package goodspace.backend.user.service;

import goodspace.backend.email.entity.EmailVerification;
import goodspace.backend.email.repository.EmailVerificationRepository;
import goodspace.backend.global.password.PasswordValidator;
import goodspace.backend.global.security.TokenProvider;
import goodspace.backend.global.security.TokenType;
import goodspace.backend.user.domain.GoodSpaceUser;
import goodspace.backend.user.domain.User;
import goodspace.backend.user.dto.EmailUpdateRequestDto;
import goodspace.backend.user.dto.PasswordUpdateRequestDto;
import goodspace.backend.user.dto.RefreshTokenResponseDto;
import goodspace.backend.user.dto.UserMyPageDto;
import goodspace.backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Supplier<EntityNotFoundException> USER_NOT_FOUND = () -> new EntityNotFoundException("회원을 조회할 수 없습니다.");
    private static final Supplier<IllegalArgumentException> WRONG_PASSWORD = () -> new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    private static final Supplier<IllegalArgumentException> ILLEGAL_PASSWORD = () -> new IllegalArgumentException("부적절한 비밀번호입니다.");
    private static final Supplier<EntityNotFoundException> VERIFICATION_NOT_FOUND = () -> new EntityNotFoundException("이메일 인증 정보를 찾을 수 없습니다.");
    private static final Supplier<IllegalStateException> NOT_VERIFIED = () -> new IllegalStateException("인증되지 않은 이메일입니다.");

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordValidator passwordValidator;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    @Transactional
    public String updateMyPage(long id, UserMyPageDto userMyPageDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found while updating MyPage Information."));

        user.setUserFromUserMyPageDto(userMyPageDto);

        return "유저 정보가 수정되었습니다.";
    }

    @Transactional
    public RefreshTokenResponseDto updatePassword(long id, PasswordUpdateRequestDto requestDto) {
        GoodSpaceUser user = userRepository.findGoodSpaceUserById(id)
                .orElseThrow(USER_NOT_FOUND);
        String rawPrevPassword = requestDto.prevPassword();
        String rawNewPassword = requestDto.newPassword();

        if (isDifferentPassword(rawPrevPassword, user.getPassword())) {
            throw WRONG_PASSWORD.get();
        }

        validatePassword(rawNewPassword);

        String encodedPassword = passwordEncoder.encode(rawNewPassword);
        user.updatePassword(encodedPassword);

        return RefreshTokenResponseDto.builder()
                .refreshToken(createNewRefreshToken(user))
                .build();
    }

    @Transactional
    public RefreshTokenResponseDto updateEmail(long id, EmailUpdateRequestDto requestDto) {
        checkEmailVerification(requestDto.email());

        User user = userRepository.findById(id)
                .orElseThrow(USER_NOT_FOUND);

        user.setEmail(requestDto.email());

        return RefreshTokenResponseDto.builder()
                .refreshToken(createNewRefreshToken(user))
                .build();
    }

    private void validatePassword(String rawPassword) {
        if (passwordValidator.isIllegalPassword(rawPassword)) {
            throw ILLEGAL_PASSWORD.get();
        }
    }

    private boolean isDifferentPassword(String rawPassword, String encodedPassword) {
        return !passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private String createNewRefreshToken(User user) {
        String refreshToken = tokenProvider.createToken(user.getId(), TokenType.REFRESH, user.getRoles());
        user.updateRefreshToken(refreshToken);

        return refreshToken;
    }

    private void checkEmailVerification(String email) {
        EmailVerification emailVerification = emailVerificationRepository.findByEmail(email)
                .orElseThrow(VERIFICATION_NOT_FOUND);

        if (!emailVerification.isVerified()) {
            throw NOT_VERIFIED.get();
        }

        emailVerificationRepository.delete(emailVerification);
    }
}
