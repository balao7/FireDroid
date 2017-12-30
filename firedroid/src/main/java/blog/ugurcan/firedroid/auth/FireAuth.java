package blog.ugurcan.firedroid.auth;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.TwitterAuthProvider;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterAuthToken;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;

import java.util.List;

import blog.ugurcan.firedroid.FireDroid;
import blog.ugurcan.firedroid.view.FacebookLoginButton;
import blog.ugurcan.firedroid.view.TwitterLoginButton;

/**
 * Created by ugurcan on 30.12.2017.
 */
public class FireAuth implements GoogleApiClient.OnConnectionFailedListener {

    private final static int REQUEST_GOOGLE_LOGIN = 9001;

    private Class loginActivityClass;

    private FirebaseAuth.AuthStateListener authStateListener;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager fbCallbackManager;
    private TwitterAuthClient twitterAuthClient;

    private LoginListener loginListener;
    private LogoutListener logoutListener;

    public FireAuth() {
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (isLoggedIn() && isOnLoginActivity()) {
                    FireDroid.currentActivity().finish();
                } else if (!isLoggedIn() && !isOnLoginActivity()) {
                    goToLogin();
                }
            }
        };
    }

    private boolean isOnLoginActivity() {
        return FireDroid.currentActivity().getClass().equals(loginActivityClass);
    }

    private void goToLogin() {
        FireDroid.appContext().startActivity(
                new Intent(FireDroid.currentActivity(), loginActivityClass));
    }

    public FireAuth google(String googleWebClientId) {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(googleWebClientId)
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(FireDroid.appContext(), gso);

        return this;
    }

    public FireAuth facebook(String fbAppId) {
        FacebookSdk.setApplicationId(fbAppId);
        FacebookSdk.sdkInitialize(FireDroid.appContext());

        fbCallbackManager = CallbackManager.Factory.create();

        return this;
    }

    public FireAuth twitter(String twitterKey, String twitterSecret) {
        TwitterAuthConfig twitterAuthConfig
                = new TwitterAuthConfig(twitterKey, twitterSecret);

        TwitterConfig twitterConfig = new TwitterConfig
                .Builder(FireDroid.appContext())
                .twitterAuthConfig(twitterAuthConfig)
                .build();

        Twitter.initialize(twitterConfig);

        twitterAuthClient = new TwitterAuthClient();

        return this;
    }

    public void init(Class loginActivityClass) {
        this.loginActivityClass = loginActivityClass;
    }

    public void setLoginListener(LoginListener loginListener) {
        this.loginListener = loginListener;
    }

    public void setLogoutListener(LogoutListener logoutListener) {
        this.logoutListener = logoutListener;
    }

    public void addAuthStateListener() {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    public void removeAuthStateListener() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
    }

    private boolean isLoggedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public String getUserDisplayName() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return currentUser.getDisplayName();
        } else {
            return "-";
        }
    }

    public void logInGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        FireDroid.currentActivity()
                .startActivityForResult(signInIntent, FireAuth.REQUEST_GOOGLE_LOGIN);
    }

    public void logInFacebook(FacebookLoginButton fbLoginButton) {
        FacebookCallback<LoginResult> fbCallback = new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                authWith(AuthType.Facebook, loginResult.getAccessToken().getToken(), null);
            }

            @Override
            public void onCancel() {
                loginListener.onLoginCompleted(false);
            }

            @Override
            public void onError(FacebookException error) {
                loginListener.onLoginCompleted(false);
            }
        };

        fbLoginButton.registerCallback(fbCallbackManager, fbCallback);
    }

    public void logInTwitter(TwitterLoginButton twitterLoginButton) {
        Callback<TwitterSession> twitterCallback = new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                TwitterAuthToken twitterAuthToken = result.data.getAuthToken();
                authWith(AuthType.Twitter, twitterAuthToken.token, twitterAuthToken.secret);
            }

            @Override
            public void failure(TwitterException exception) {
                loginListener.onLoginCompleted(false);
            }
        };

        twitterLoginButton.setCallback(twitterCallback);
    }

    public void handleLoginResult(int requestCode, int resultCode, Intent data) {
        if (FacebookSdk.isFacebookRequestCode(requestCode)) {
            fbCallbackManager.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == FireAuth.REQUEST_GOOGLE_LOGIN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                authWith(AuthType.Google, account.getIdToken(), null);
            } catch (ApiException e) {
                loginListener.onLoginCompleted(false);
            }
        } else if (Twitter.getInstance().getTwitterAuthConfig().getRequestCode() == requestCode) {
            twitterAuthClient.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void authWith(AuthType authType, String token, String secret) {
        loginListener.onLoginStarted();

        AuthCredential credential;
        switch (authType) {
            case Google:
                credential = GoogleAuthProvider.getCredential(token, null);
                break;
            case Facebook:
                credential = FacebookAuthProvider.getCredential(token);
                break;
            case Twitter:
                credential = TwitterAuthProvider.getCredential(token, secret);
                break;
            default:
                return;
        }

        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(FireDroid.currentActivity(),
                        new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                loginListener.onLoginCompleted(task.isSuccessful());
                            }
                        });
    }

    public void logOut() {
        switch (getAuthType()) {
            case Google:
                logoutListener.onLogoutStarted();
                googleSignInClient.signOut().addOnCompleteListener(
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                logoutListener.onLogoutCompleted();
                                FirebaseAuth.getInstance().signOut();
                            }
                        });
                break;
            case Facebook:
                logoutListener.onLogoutStarted();
                LoginManager.getInstance().logOut();
                logoutListener.onLogoutCompleted();
                FirebaseAuth.getInstance().signOut();
                break;
            case Twitter:
                logoutListener.onLogoutStarted();
                TwitterCore.getInstance().getSessionManager().clearActiveSession();
                logoutListener.onLogoutCompleted();
                FirebaseAuth.getInstance().signOut();
                break;
        }
    }

    public AuthType getAuthType() {
        if (!isLoggedIn()) {
            return AuthType.NONE;
        }

        List<String> providers = getAuthProviders();
        if (providers != null && providers.size() > 0) {
            switch (providers.get(0)) {
                case GoogleAuthProvider.PROVIDER_ID:
                    return AuthType.Google;
                case FacebookAuthProvider.PROVIDER_ID:
                    return AuthType.Facebook;
                case TwitterAuthProvider.PROVIDER_ID:
                    return AuthType.Twitter;
            }
        }

        return AuthType.UNIDENTIFIED;
    }

    private List<String> getAuthProviders() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return currentUser.getProviders();
        } else {
            return null;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(FireDroid.appContext(), "Google Play Services error!",
                Toast.LENGTH_SHORT).show();
    }

    public enum AuthType {
        Google, Facebook, Twitter, UNIDENTIFIED, NONE;
    }

}