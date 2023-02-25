package com.planner.server.security.auth;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.planner.server.domain.refresh_token.entity.RefreshToken;
import com.planner.server.domain.refresh_token.repository.RefreshTokenRepository;
import com.planner.server.domain.user.entity.User;
import com.planner.server.domain.user.repository.UserRepository;
import com.planner.server.properties.AuthProperties;
import com.planner.server.utils.JwtUtils;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;

    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        super(authenticationManager);
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // 1. 액세스토큰을 담고 있는 쿠키를 확인
    // 2. 액세스토큰이 유효하다면 -> 인증된 객체 저장하고 doFilter, 그렇지 않다면 -> 리프레스토큰 검사
    // 3. DB에서 리프레시토큰 조회. 리프레시 토큰이 유효하다면 -> 새로운 액세스토큰 발급, 그렇지 않다면 -> 인증된 객체를 저장하지 않고 doFilter
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        System.out.println("#=====AUTHORIZATION FILTER=====#");

        Cookie cookie = Arrays.stream(request.getCookies())
            .filter(r -> r.getName().equals("access_token"))
            .findAny()
            .orElse(null);

        if(cookie == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String accessToken = cookie.getValue();
        DecodedJWT decodedAccessToken = null;
        DecodedJWT decodedRefreshToken = null;
        boolean isAccessTokenExpired = false;
        
        try {
            decodedAccessToken = JWT.require(Algorithm.HMAC512(AuthProperties.getAccessSecret())).build().verify(accessToken);
        } catch (TokenExpiredException e) {
            System.out.println("-----access token expired-----");
            
            // TODO :: access toekn 만료 시 claim값을 조회하는 방법 정하기
            // decodedAccessToken = null;
            isAccessTokenExpired = true;
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 토큰에 저장된 유저정보가 존재하지 않는 경우 예외처리
        User savedUser = userRepository.findByUsername(decodedAccessToken.getClaim("username").asString())
            .orElseThrow(() -> new UserPrincipalNotFoundException("not found user."));

        // 액세스토큰이 만료된 경우
        if(isAccessTokenExpired) {
            UUID refreshTokenId = UUID.fromString(decodedAccessToken.getClaim("refreshTokenId").asString());
            Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findById(refreshTokenId);

            // 액세스토큰과 매칭된 리프레시토큰을 DB에서 조회한다
            if(refreshTokenOpt.isPresent()) {
                String refreshToken = refreshTokenOpt.get().getRefreshToken();

                try {
                    decodedRefreshToken = JWT.require(Algorithm.HMAC512(AuthProperties.getRefreshSecret())).build().verify(refreshToken);
                } catch (TokenExpiredException e) {
                    // 리프레시 토큰이 만료된 경우
                    System.out.println("-----refresh token expired-----");
                    
                    // 만료된 리프레시 토큰을 제거 후 doFitler
                    refreshTokenRepository.delete(refreshTokenOpt.get());
                    filterChain.doFilter(request, response);
                    return;
                } catch (Exception e) {     // TODO :: 구체 예외 처리
                    filterChain.doFilter(request, response);
                    return;
                }

                // 리프레시 토큰이 존재한다면 액세스토큰 발급
                String newAccessToken = JwtUtils.createAccessToken(savedUser, refreshTokenId);

                if(decodedRefreshToken != null) {
                    ResponseCookie cookies = ResponseCookie.from("access_token", newAccessToken)
                        .httpOnly(true)
                        .domain("localhost")
                        // .secure(true)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(3 * 24 * 60 * 60)     // 3일
                        .build();

                    response.addHeader(HttpHeaders.SET_COOKIE, cookies.toString());
                }
            }else {
                System.out.println("-----not found access & refresh token-----");

                // TODO :: 인가 거절
                filterChain.doFilter(request, response);
                return;
            }
        }

        this.saveAuthenticationToSecurityContextHolder(savedUser);
        filterChain.doFilter(request, response);
    }

    private void saveAuthenticationToSecurityContextHolder(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        // 인가 처리가 정상적으로 완료된다면 Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
