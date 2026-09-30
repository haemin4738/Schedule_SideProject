package com.lifelog.domain.user.session;

/**
 * {@link RefreshSessionStore#rotate} 결과와 발급에 사용할 jti.
 *
 * @param result  회전 결과
 * @param tokenId 새 refresh 토큰에 넣을 jti. {@link RefreshRotationResult#ROTATED} 이면 새 jti,
 *                {@link RefreshRotationResult#REISSUED} 이면 저장소의 현재 jti, 그 외에는 null
 */
public record RefreshRotationOutcome(RefreshRotationResult result, String tokenId) {

    /** tokenId 가 없는 결과 */
    public static RefreshRotationOutcome of(RefreshRotationResult result) {
        return new RefreshRotationOutcome(result, null);
    }
}
