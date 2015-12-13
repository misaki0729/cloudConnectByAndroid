package com.misaki0729.cloudconnect.onedrive;

/**
 * Created by st on 15/12/12.
 */
public class AuthenticationInfo {
    public String token_type;

    public int expires_in; // 有効時間

    public String scope;

    public String access_token;

    public String refresh_token;

    public String user_id;

    public String toString() {
        return "tokenType=" + token_type + "\naccessToken = " + access_token + "\nrefreshToken=" + refresh_token + "\nuserId=" + user_id;
    }
}
