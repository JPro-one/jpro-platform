package one.jpro.platform.auth.core.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Combine authentication providers tests.
 *
 * @author Besmir Beqiri
 */
public class CombineAuthenticationProviderTests {

    private CombineAuthenticationProvider combineAuthProvider;
    private AuthenticationProvider<Credentials> mockProvider;
    private Credentials mockCredentials;

    @BeforeEach
    public void setup() {
        // Initialize the mock objects
        mockProvider = mock(AuthenticationProvider.class);
        mockCredentials = mock(Credentials.class);
    }

    @Test
    public void shouldFailCredentialValidation() {
        // Setup to simulate a failed credential validation
        CredentialValidationException exception = new CredentialValidationException("Invalid credentials");
        doThrow(exception).when(mockCredentials).validate(null);

        combineAuthProvider = CombineAuthenticationProvider.any(); // 'all' doesn't matter for this test
        CompletableFuture<User> result = combineAuthProvider.authenticate(mockCredentials);

        // Assert that the future completed exceptionally with the right exception
        ExecutionException thrown = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(CredentialValidationException.class, thrown.getCause());
    }

    @Test
    public void shouldFailWhenNoProviders() {
        combineAuthProvider = CombineAuthenticationProvider.any(); // 'all' doesn't matter here
        CompletableFuture<User> result = combineAuthProvider.authenticate(mockCredentials);

        // Assert that the authentication fails due to no providers available
        ExecutionException thrown = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(AuthenticationException.class, thrown.getCause());
        assertEquals("The combined providers list is empty.", thrown.getCause().getMessage());
    }

    @Test
    public void shouldAuthenticateWithSingleProvider() throws Exception {
        User mockUser = mock(User.class);
        when(mockProvider.authenticate(mockCredentials)).thenReturn(CompletableFuture.completedFuture(mockUser));

        combineAuthProvider = CombineAuthenticationProvider.any(); // With 'all' set to false, one success is enough
        combineAuthProvider.add(mockProvider);
        CompletableFuture<User> result = combineAuthProvider.authenticate(mockCredentials);

        // Assert successful authentication with the mock user
        assertEquals(mockUser, result.get());
    }

    @Test
    public void shouldFailWithAllProvidersWhenAnyIsRequired() {
        // Setup to simulate authentication failure
        AuthenticationException exception = new AuthenticationException("Authentication failed");
        when(mockProvider.authenticate(mockCredentials)).thenReturn(CompletableFuture.failedFuture(exception));

        combineAuthProvider = CombineAuthenticationProvider.any(); // 'all' set to true, requiring all to succeed
        combineAuthProvider.add(mockProvider);
        CompletableFuture<User> result = combineAuthProvider.authenticate(mockCredentials);

        // Assert that the authentication fails appropriately
        ExecutionException thrown = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(AuthenticationException.class, thrown.getCause());
        assertEquals("No provider capable of performing this operation.", thrown.getCause().getMessage());
    }

    @Test
    public void shouldSucceedWithAllProvidersWhenAllIsRequired() throws Exception {
        // Setup to simulate successful authentication with multiple providers
        User mockUser1 = mock(User.class);
        User mockUser2 = mock(User.class);
        User mergedUser = mock(User.class);

        AuthenticationProvider<Credentials> secondMockProvider = mock(AuthenticationProvider.class);
        when(mockProvider.authenticate(mockCredentials)).thenReturn(CompletableFuture.completedFuture(mockUser1));
        when(secondMockProvider.authenticate(mockCredentials)).thenReturn(CompletableFuture.completedFuture(mockUser2));
        when(mockUser1.merge(mockUser2)).thenReturn(mergedUser);

        combineAuthProvider = CombineAuthenticationProvider.all(); // 'all' set to true, requiring all to succeed
        combineAuthProvider.add(mockProvider).add(secondMockProvider);
        CompletableFuture<User> result = combineAuthProvider.authenticate(mockCredentials);

        // Assert successful authentication with the merged user
        assertEquals(mergedUser, result.get());
    }
}
