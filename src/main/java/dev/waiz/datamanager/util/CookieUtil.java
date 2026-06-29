package dev.waiz.datamanager.util;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;



@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    public static final String REFRESH_TOKEN_COOKIE =
    "refresh_token";

    private static final int ACCESS_TOKEN_MAX_TIME = 15*60;

    private static final int REFRESH_TOKEN_MAX_TIME =
    7*24*60*60;

    public void addAuthCookies(HttpServletResponse response,String accessToken,String refreshToken){
        addCookies(response,ACCESS_TOKEN_COOKIE,accessToken,ACCESS_TOKEN_MAX_TIME);
        addCookies(response,REFRESH_TOKEN_COOKIE,refreshToken,REFRESH_TOKEN_MAX_TIME);
    }

    private void clearCookies(HttpServletResponse response,String name){
        response.addHeader("Set-Cookie",String.format("%s=; Max-Age=0; Path=/; HttpOnly;SameSite=Strict",name)); // add "Secure;" later when in prod
    }


    public void clearAuthCookies(HttpServletResponse response){
        clearCookies(response,ACCESS_TOKEN_COOKIE);
        clearCookies(response,REFRESH_TOKEN_COOKIE);
    }

    public Optional<String> readCookie(HttpServletRequest request, String name){
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(c -> name.equals(c.getName()))
            .map(c -> Objects.requireNonNull(c).getValue())
            .findFirst();
    }

    
    private void addCookies(HttpServletResponse response,String name,String value,int maxAgeSeconds){
        Cookie cookie = new Cookie(name,value);
        cookie.setHttpOnly(true); //javascript cant read this cookie
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);

        response.addHeader("Set-Cookie", String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Strict",name,value,maxAgeSeconds));// add "Secure;" later when in prod
        
    }

}
