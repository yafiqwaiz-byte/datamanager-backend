package dev.waiz.datamanager.service;


import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPS = 5;
    private static final int BLOCK_DURATION_MINUTES = 15;

    private final LoadingCache<String,Integer> attemptsCache;

    public LoginAttemptService(){
        attemptsCache = CacheBuilder.newBuilder()
                        .expireAfterWrite(BLOCK_DURATION_MINUTES,TimeUnit.MINUTES)
                        .build(new CacheLoader<>() {
                            @Override
                            public Integer load(String key){
                                return 0;
                            }
                        });
    }

    public void loginFailed(String ip){
        int attempts;
        try{
            attempts = attemptsCache.get(ip);
        } catch (ExecutionException a) {
            attempts =0;
        }
        attemptsCache.put(ip, attempts +1);
    }

    public void loginSucceeded(String
         ip){
            attemptsCache.invalidate(ip);
         }

    
    public boolean isBlocked(String ip){
        try {
            return attemptsCache.get(ip) >= MAX_ATTEMPS;
        } catch (ExecutionException a){
            return false;
        }
    }

    public int getRemainingAttempts(String ip){
        try{
            int used = attemptsCache.get(ip);
            return Math.max(0, MAX_ATTEMPS - used);
        } catch (ExecutionException a){
            return MAX_ATTEMPS;
        }
    }

}
