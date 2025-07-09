
package com.winten.greenlight.prototype.core.domain.token;

import com.winten.greenlight.prototype.core.domain.action.Action;
import com.winten.greenlight.prototype.core.db.repository.redis.token.TokenRepository;
import com.winten.greenlight.prototype.core.support.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // 순수 Mockito 테스트를 위한 설정
@DisplayName("TokenDomainService 단위 테스트")
class TokenDomainServiceTest {

    @Mock // 의존성 Mocking
    private TokenRepository tokenRepository;

    @Mock // 의존성 Mocking
    private JwtUtil jwtUtil;

    @InjectMocks // Mock 객체들을 주입하여 테스트 대상 인스턴스 생성
    private TokenDomainService tokenDomainService;

    private static final String TEST_CUSTOMER_ID = "customer-123";
    private static final Long TEST_ACTION_ID = 1L;
    private Action testAction;

    @BeforeEach
    void setUp() {
        testAction = new Action();
        testAction.setId(TEST_ACTION_ID);
    }

    @Test
    @DisplayName("신규 토큰 발급 시, 토큰 생성 및 저장 로직을 호출한다")
    void issueToken_forNewUser_shouldGenerateAndSaveToken() {
        // given
        String newJwt = "new-jwt-token";
        // 1. 기존 토큰은 없음
        when(tokenRepository.findJwtByCustomerIdAndActionId(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.empty());
        // 2. JWT 생성 성공
        when(jwtUtil.generateToken(any())).thenReturn(newJwt);
        // 3. 메타데이터 저장 성공
        when(tokenRepository.saveTokenMetadata(eq(newJwt), any(Map.class))).thenReturn(Mono.empty().then());

        // when
        Mono<String> result = tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, "WAITING");

        // then
        StepVerifier.create(result)
            .expectNext(newJwt)
            .verifyComplete();

        verify(tokenRepository, never()).deleteTokenMetadata(anyString()); // 삭제 로직은 호출되면 안됨
        verify(tokenRepository, times(1)).saveTokenMetadata(eq(newJwt), any(Map.class));
    }

    @Test
    @DisplayName("토큰 재발급 시, 기존 토큰을 만료시킨 후 신규 토큰을 발급한다")
    void issueToken_forExistingUser_shouldExpireOldAndIssueNew() {
        // given
        String oldJwt = "old-jwt-token";
        String newJwt = "new-jwt-token";
        // 1. 기존 토큰이 존재함
        when(tokenRepository.findJwtByCustomerIdAndActionId(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.just(oldJwt));
        // 2. JWT 유효성 검증 통과
        when(jwtUtil.validateToken(oldJwt)).thenReturn(true);
        // 3. 기존 토큰 삭제 성공
        when(tokenRepository.deleteTokenMetadata(oldJwt)).thenReturn(Mono.empty().then());
        // 4. 신규 JWT 생성 성공
        when(jwtUtil.generateToken(any())).thenReturn(newJwt);
        // 5. 신규 메타데이터 저장 성공
        when(tokenRepository.saveTokenMetadata(eq(newJwt), any(Map.class))).thenReturn(Mono.empty().then());

        // when
        Mono<String> result = tokenDomainService.issueToken(TEST_CUSTOMER_ID, testAction, "WAITING");

        // then
        StepVerifier.create(result)
            .expectNext(newJwt)
            .verifyComplete();

        // 순서 검증: delete가 save보다 먼저 호출되었는지 확인
        var inOrder = inOrder(tokenRepository);
        inOrder.verify(tokenRepository).deleteTokenMetadata(oldJwt);
        inOrder.verify(tokenRepository).saveTokenMetadata(eq(newJwt), any(Map.class));
    }

    @Test
    @DisplayName("유효한 토큰 조회 시, 토큰 문자열을 반환한다")
    void findValidTokenJwt_whenTokenIsValid_shouldReturnJwt() {
        // given
        String validJwt = "valid-jwt-token";
        when(tokenRepository.findJwtByCustomerIdAndActionId(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.just(validJwt));
        when(jwtUtil.validateToken(validJwt)).thenReturn(true);

        // when
        Mono<String> result = tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID);

        // then
        StepVerifier.create(result)
            .expectNext(validJwt)
            .verifyComplete();
    }

    @Test
    @DisplayName("만료된 토큰 조회 시, 비어있는 Mono를 반환한다")
    void findValidTokenJwt_whenTokenIsInvalid_shouldReturnEmpty() {
        // given
        String invalidJwt = "invalid-jwt-token";
        when(tokenRepository.findJwtByCustomerIdAndActionId(TEST_CUSTOMER_ID, TEST_ACTION_ID)).thenReturn(Mono.just(invalidJwt));
        when(jwtUtil.validateToken(invalidJwt)).thenReturn(false); // 유효성 검증 실패

        // when
        Mono<String> result = tokenDomainService.findValidTokenJwt(TEST_CUSTOMER_ID, TEST_ACTION_ID);

        // then
        StepVerifier.create(result)
            .verifyComplete(); // 아무것도 반환하지 않아야 함
    }

    @Test
    @DisplayName("토큰 만료 요청 시, Repository의 삭제 메소드를 호출한다")
    void expireToken_shouldCallRepositoryDelete() {
        // given
        String jwtToExpire = "jwt-to-expire";
        when(tokenRepository.deleteTokenMetadata(jwtToExpire)).thenReturn(Mono.empty().then());

        // when
        Mono<Void> result = tokenDomainService.expireToken(jwtToExpire);

        // then
        StepVerifier.create(result)
            .verifyComplete();

        verify(tokenRepository, times(1)).deleteTokenMetadata(jwtToExpire);
    }
}
